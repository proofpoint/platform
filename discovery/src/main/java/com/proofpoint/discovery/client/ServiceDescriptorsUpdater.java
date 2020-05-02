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
package com.proofpoint.discovery.client;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.annotation.PostConstruct;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkState;
import static com.proofpoint.discovery.client.announce.DiscoveryAnnouncementClient.DEFAULT_DELAY;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public final class ServiceDescriptorsUpdater
{
    private static final Logger log = Logger.get(ServiceDescriptorsUpdater.class);

    private final ServiceDescriptorsListener target;
    private final String type;
    private final String pool;
    private final DiscoveryLookupClient discoveryClient;
    private final AtomicReference<ServiceDescriptors> serviceDescriptors = new AtomicReference<>();
    private final ScheduledExecutorService executor;

    private final AtomicBoolean started = new AtomicBoolean(false);
    private final ExponentialBackOff errorBackOff;

    public ServiceDescriptorsUpdater(ServiceDescriptorsListener target, String type, ServiceSelectorConfig selectorConfig, NodeInfo nodeInfo, DiscoveryLookupClient discoveryClient, ScheduledExecutorService executor)
    {
        requireNonNull(target, "target is null");
        requireNonNull(type, "type is null");
        requireNonNull(selectorConfig, "selectorConfig is null");
        requireNonNull(nodeInfo, "nodeInfo is null");
        requireNonNull(discoveryClient, "discoveryClient is null");
        requireNonNull(executor, "executor is null");

        this.target = target;
        this.type = type;
        this.pool = firstNonNull(selectorConfig.getPool(), nodeInfo.getPool());
        this.discoveryClient = discoveryClient;
        this.executor = executor;
        this.errorBackOff = new ExponentialBackOff(
                new Duration(1, MILLISECONDS),
                new Duration(1, SECONDS),
                String.format("Discovery server connect succeeded for refresh (%s/%s)", type, pool),
                String.format("Cannot connect to discovery server for refresh (%s/%s)", type, pool),
                log);
    }

    @PostConstruct
    public void start()
    {
        if (started.compareAndSet(false, true)) {
            checkState(!executor.isShutdown(), "CachingServiceSelector has been destroyed");

            // if discovery is available, get the initial set of servers before starting
            try {
                refresh().get(1, TimeUnit.SECONDS);
            }
            catch (Exception ignored) {
            }
        }
    }

    private ListenableFuture<ServiceDescriptors> refresh()
    {
        final ServiceDescriptors oldDescriptors = this.serviceDescriptors.get();

        final ListenableFuture<ServiceDescriptors> future;
        if (oldDescriptors == null) {
            future = discoveryClient.getServices(type, pool);
        }
        else {
            future = discoveryClient.refreshServices(oldDescriptors);
        }

        return chainedCallback(future, new FutureCallback<ServiceDescriptors>()
        {
            @Override
            @SuppressFBWarnings("NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE")
            public void onSuccess(ServiceDescriptors newDescriptors)
            {
                serviceDescriptors.set(newDescriptors);
                target.updateServiceDescriptors(newDescriptors.getServiceDescriptors());
                errorBackOff.success();

                Duration delay = newDescriptors.getMaxAge();
                if (delay == null) {
                    delay = DEFAULT_DELAY;
                }
                scheduleRefresh(delay);
            }

            @Override
            public void onFailure(Throwable t)
            {
                Duration duration = errorBackOff.failed(t);
                scheduleRefresh(duration);
            }
        }, executor);
    }

    private void scheduleRefresh(Duration delay)
    {
        // already stopped?  avoids rejection exception
        if (executor.isShutdown()) {
            return;
        }
        executor.schedule((Runnable) this::refresh, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static <V> ListenableFuture<V> chainedCallback(
            ListenableFuture<V> future,
            final FutureCallback<? super V> callback,
            Executor executor)
    {
        final SettableFuture<V> done = SettableFuture.create();
        Futures.addCallback(future, new FutureCallback<V>()
        {
            @Override
            public void onSuccess(V result)
            {
                try {
                    callback.onSuccess(result);
                }
                finally {
                    done.set(result);
                }
            }

            @Override
            public void onFailure(Throwable t)
            {
                try {
                    callback.onFailure(t);
                }
                finally {
                    done.setException(t);
                }
            }
        }, executor);
        return done;
    }
}
