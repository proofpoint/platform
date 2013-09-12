/*
 * Copyright 2010 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.discovery.client.announce;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import com.proofpoint.discovery.client.DiscoveryException;
import com.proofpoint.discovery.client.ExponentialBackOff;
import com.proofpoint.log.Logger;
import com.proofpoint.reporting.HealthCheck;
import com.proofpoint.reporting.HealthExporter;
import com.proofpoint.units.Duration;

import javax.annotation.PreDestroy;
import javax.management.InstanceAlreadyExistsException;
import java.net.ConnectException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Throwables.propagate;
import static com.proofpoint.concurrent.Threads.daemonThreadsNamed;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class Announcer
{
    private static final Logger log = Logger.get(Announcer.class);
    private final ConcurrentMap<UUID, ServiceAnnouncement> announcements = new MapMaker().makeMap();

    private final DiscoveryAnnouncementClient announcementClient;
    private final ScheduledExecutorService executor;
    private final HealthExporter healthExporter;

    private final AtomicBoolean started = new AtomicBoolean(false);

    private final ExponentialBackOff errorBackOff = new ExponentialBackOff(
            new Duration(1, MILLISECONDS),
            new Duration(1, SECONDS),
            "Discovery server connect succeeded for announce",
            "Cannot connect to discovery server for announce",
            log);
    private final AtomicReference<Throwable> failed = new AtomicReference<>();
    private final AtomicLong nextAnnouncementTime = new AtomicLong();

    @Inject
    public Announcer(DiscoveryAnnouncementClient announcementClient, Set<ServiceAnnouncement> serviceAnnouncements, HealthExporter healthExporter)
    {
        checkNotNull(announcementClient, "client is null");
        checkNotNull(serviceAnnouncements, "serviceAnnouncements is null");

        this.announcementClient = announcementClient;
        for (ServiceAnnouncement serviceAnnouncement : serviceAnnouncements) {
            announcements.put(serviceAnnouncement.getId(), serviceAnnouncement);
        }
        executor = new ScheduledThreadPoolExecutor(5, daemonThreadsNamed("Announcer-%s"));
        this.healthExporter = checkNotNull(healthExporter, "healthExporter is null");
        nextAnnouncementTime.set(System.nanoTime());
    }

    public void start()
    {
        checkState(!executor.isShutdown(), "Announcer has been destroyed");
        if (started.compareAndSet(false, true)) {
            try {
                healthExporter.export(null, this);
            }
            catch (InstanceAlreadyExistsException e) {
                throw propagate(e);
            }
            // announce immediately, if discovery is running
            announce();
        }
    }

    @PreDestroy
    public void destroy()
    {
        executor.shutdownNow();
        try {
            executor.awaitTermination(30, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // unannounce
        try {
            getFutureResult(announcementClient.unannounce(), DiscoveryException.class);
        }
        catch (DiscoveryException e) {
            if (e.getCause() instanceof ConnectException) {
                log.error("Cannot connect to discovery server for unannounce: %s", e.getCause().getMessage());
            }
            else {
                log.error(e);
            }
        }
    }

    public void addServiceAnnouncement(ServiceAnnouncement serviceAnnouncement)
    {
        checkNotNull(serviceAnnouncement, "serviceAnnouncement is null");
        announcements.put(serviceAnnouncement.getId(), serviceAnnouncement);
    }

    public void removeServiceAnnouncement(UUID serviceId)
    {
        announcements.remove(serviceId);
    }

    private ListenableFuture<Duration> announce()
    {
        final ListenableFuture<Duration> future = announcementClient.announce(ImmutableSet.copyOf(announcements.values()));

        Futures.addCallback(future, new FutureCallback<Duration>()
        {
            @Override
            public void onSuccess(Duration duration)
            {
                errorBackOff.success();
                failed.set(null);
                nextAnnouncementTime.set(System.nanoTime() + duration.roundTo(NANOSECONDS));

                // wait 80% of the suggested delay
                duration = new Duration(duration.toMillis() * 0.8, MILLISECONDS);
                scheduleNextAnnouncement(duration);
            }

            @Override
            public void onFailure(Throwable t)
            {
                Duration duration = errorBackOff.failed(t);
                failed.set(t);
                scheduleNextAnnouncement(duration);
            }
        }, executor);

        return future;
    }

    @HealthCheck("Discovery announcement")
    Object checkAnnouncementHealth()
    {
        Throwable throwable = failed.get();
        if (throwable != null) {
            return throwable;
        }

        long overdue = System.nanoTime() - nextAnnouncementTime.get();
        if (overdue > 0) {
            return "Overdue for " + new Duration(overdue, NANOSECONDS).convertToMostSuccinctTimeUnit();
        }

        return null;
    }

    private void scheduleNextAnnouncement(Duration delay)
    {
        // already stopped?  avoids rejection exception
        if (executor.isShutdown()) {
            return;
        }
        executor.schedule(new Runnable()
        {
            @Override
            public void run()
            {
                announce();
            }
        }, delay.toMillis(), MILLISECONDS);
    }

    // TODO: move this to a utility package
    private static <T, X extends Throwable> T getFutureResult(Future<T> future, Class<X> type)
            throws X
    {
        try {
            return future.get();
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw propagate(e);
        }
        catch (ExecutionException e) {
            Throwables.propagateIfPossible(e.getCause(), type);
            throw propagate(e.getCause());
        }
    }
}
