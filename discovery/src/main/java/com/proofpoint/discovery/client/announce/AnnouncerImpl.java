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

import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.proofpoint.bootstrap.StopTraffic;
import com.proofpoint.discovery.client.DiscoveryException;
import com.proofpoint.discovery.client.ExponentialBackOff;
import com.proofpoint.log.Logger;
import com.proofpoint.units.Duration;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import javax.inject.Inject;
import java.net.ConnectException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkState;
import static com.proofpoint.concurrent.MoreFutures.getFutureValue;
import static com.proofpoint.concurrent.Threads.daemonThreadsNamed;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class AnnouncerImpl
        implements Announcer
{
    private static final Logger log = Logger.get(AnnouncerImpl.class);
    private final ConcurrentMap<UUID, ServiceAnnouncement> announcements = new MapMaker().makeMap();

    private final DiscoveryAnnouncementClient announcementClient;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean(false);

    private final ExponentialBackOff errorBackOff = new ExponentialBackOff(
            new Duration(1, MILLISECONDS),
            new Duration(1, SECONDS),
            "Discovery server connect succeeded for announce",
            "Cannot connect to discovery server for announce",
            log);

    @Inject
    public AnnouncerImpl(DiscoveryAnnouncementClient announcementClient, Set<ServiceAnnouncement> serviceAnnouncements)
    {
        requireNonNull(announcementClient, "client is null");
        requireNonNull(serviceAnnouncements, "serviceAnnouncements is null");

        this.announcementClient = announcementClient;
        for (ServiceAnnouncement serviceAnnouncement : serviceAnnouncements) {
            announcements.put(serviceAnnouncement.getId(), serviceAnnouncement);
        }
        executor = new ScheduledThreadPoolExecutor(5, daemonThreadsNamed("Announcer-%s"));
    }

    @Override
    public void start()
    {
        checkState(!executor.isShutdown(), "Announcer has been destroyed");
        if (started.compareAndSet(false, true)) {
            // announce immediately, if discovery is running
            announce();
        }
    }

    @Override
    public void startIfNotKubernetes()
    {
        if (System.getenv("KUBERNETES_SERVICE_PORT") == null)
        {
            start();
        }
    }

    @Override
    @StopTraffic
    public void destroy()
    {
        executor.shutdownNow();
        try {
            executor.awaitTermination(30, SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        if (!started.get()) {
            return;
        }

        // unannounce
        try {
            getFutureValue(announcementClient.unannounce(), DiscoveryException.class);
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

    @Override
    public void addServiceAnnouncement(ServiceAnnouncement serviceAnnouncement)
    {
        requireNonNull(serviceAnnouncement, "serviceAnnouncement is null");
        announcements.put(serviceAnnouncement.getId(), serviceAnnouncement);
    }

    @Override
    public void removeServiceAnnouncement(UUID serviceId)
    {
        announcements.remove(serviceId);
    }

    private Set<ServiceAnnouncement> getServiceAnnouncements()
    {
        Set<ServiceAnnouncement> announcements = Set.copyOf(this.announcements.values());
        announcements.forEach(serviceAnnouncement -> {
            if (serviceAnnouncement.getError() != null) {
                throw new IllegalStateException(serviceAnnouncement.getError());
            }
        });
        return announcements;
    }

    private ListenableFuture<Duration> announce()
    {
        final ListenableFuture<Duration> future = announcementClient.announce(getServiceAnnouncements());

        Futures.addCallback(future, new FutureCallback<Duration>()
        {
            @Override
            @SuppressFBWarnings("NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE")
            public void onSuccess(Duration duration)
            {
                errorBackOff.success();

                // wait 80% of the suggested delay
                duration = new Duration(duration.toMillis() * 0.8, MILLISECONDS);
                scheduleNextAnnouncement(duration);
            }

            @Override
            public void onFailure(Throwable t)
            {
                Duration duration = errorBackOff.failed(t);
                scheduleNextAnnouncement(duration);
            }
        }, executor);

        return future;
    }

    @Override
    public ListenableFuture<?> forceAnnounce()
    {
        return announcementClient.announce(getServiceAnnouncements());
    }

    private void scheduleNextAnnouncement(Duration delay)
    {
        // already stopped?  avoids rejection exception
        if (executor.isShutdown()) {
            return;
        }
        executor.schedule((Runnable) this::announce, delay.toMillis(), MILLISECONDS);
    }
}
