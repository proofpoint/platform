package com.proofpoint.discovery.client;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.proofpoint.log.Logger;
import com.proofpoint.units.Duration;

import javax.annotation.PostConstruct;
import java.net.ConnectException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static com.proofpoint.discovery.client.DiscoveryFutures.toDiscoveryFuture;

public class CachingServiceSelector implements ServiceSelector
{
    private final static Logger log = Logger.get(CachingServiceSelector.class);

    private final String type;
    private final String pool;
    private final DiscoveryClient client;
    private final AtomicReference<ServiceDescriptors> serviceDescriptors = new AtomicReference<ServiceDescriptors>();
    private final ScheduledExecutorService executor;
    private final AtomicBoolean serverUp = new AtomicBoolean(true);

    private final ReentrantLock lock = new ReentrantLock();
    private ScheduledFuture<?> restartRefreshSchedule;

    public CachingServiceSelector(String type, ServiceSelectorConfig selectorConfig, DiscoveryClient client, ScheduledExecutorService executor)
    {
        Preconditions.checkNotNull(type, "type is null");
        Preconditions.checkNotNull(selectorConfig, "selectorConfig is null");
        Preconditions.checkNotNull(client, "client is null");
        Preconditions.checkNotNull(executor, "executor is null");

        this.type = type;
        this.pool = selectorConfig.getPool();
        this.client = client;
        this.executor = executor;
    }

    @PostConstruct
    public void start()
            throws TimeoutException
    {
        lock.lock();
        try {
            // already running?
            if (restartRefreshSchedule != null) {
                return;
            }

            // make sure update runs at least every minutes
            // this will help the system restart if a task
            // hangs or dies without being rescheduled
            restartRefreshSchedule = executor.scheduleWithFixedDelay(new Runnable()
            {
                @Override
                public void run()
                {
                    scheduleRefresh(new Duration(0, TimeUnit.SECONDS));
                }
            }, 1, 1, TimeUnit.MINUTES);
        }
        finally {
            lock.unlock();
        }

        // refresh immediately
        refresh().checkedGet(30, TimeUnit.SECONDS);
    }

    @Override
    public String getType()
    {
        return type;
    }

    @Override
    public String getPool()
    {
        return pool;
    }

    @Override
    public List<ServiceDescriptor> selectAllServices()
    {
        ServiceDescriptors serviceDescriptors = this.serviceDescriptors.get();
        if (serviceDescriptors == null) {
            return ImmutableList.of();
        }
        return serviceDescriptors.getServiceDescriptors();
    }

    private CheckedFuture<Void, DiscoveryException> refresh()
    {
        final ServiceDescriptors oldDescriptors = this.serviceDescriptors.get();

        final CheckedFuture<ServiceDescriptors, DiscoveryException> future;
        if (oldDescriptors == null) {
            future = client.getServices(type, pool);
        }
        else {
            future = client.refreshServices(oldDescriptors);
        }

        final SettableFuture<Void> isDone = SettableFuture.create();
        future.addListener(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    ServiceDescriptors newDescriptors = future.checkedGet();
                    boolean updated = serviceDescriptors.compareAndSet(oldDescriptors, newDescriptors);
                    if (updated) {
                        scheduleRefresh(newDescriptors.getMaxAge());
                    }
                    if (serverUp.compareAndSet(false, true)) {
                        log.info("Discovery server connect succeeded for refresh (%s/%s)", type, pool);
                    }
                }
                catch (DiscoveryException e) {
                    if (e.getCause() instanceof ConnectException) {
                        if (serverUp.compareAndSet(true, false)) {
                            log.error("Cannot connect to discovery server for refresh: %s", e.getCause().getMessage());
                        }
                    } else {
                        log.error(e);
                    }
                }
                finally {
                    isDone.set(null);
                }
            }
        }, executor);
        return toDiscoveryFuture("refresh", isDone);
    }

    private void scheduleRefresh(Duration delay)
    {
        // already stopped?  avoids rejection exception
        if (executor.isShutdown()) {
            return;
        }
        executor.schedule(new Callable<Void>()
        {
            @Override
            public Void call()
                    throws Exception
            {
                refresh();

                return null;

            }

        }, (long) delay.toMillis(), TimeUnit.MILLISECONDS);
    }
}
