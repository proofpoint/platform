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
package com.proofpoint.discovery.client.testing;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MapMaker;
import com.google.common.util.concurrent.ListenableFuture;
import com.proofpoint.discovery.client.DiscoveryLookupClient;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.discovery.client.ServiceDescriptors;
import com.proofpoint.discovery.client.ServiceState;
import com.proofpoint.discovery.client.announce.DiscoveryAnnouncementClient;
import com.proofpoint.discovery.client.announce.ServiceAnnouncement;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;
import jakarta.inject.Inject;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.util.concurrent.Futures.immediateFuture;
import static java.util.Objects.requireNonNull;

public class InMemoryDiscoveryClient implements DiscoveryAnnouncementClient, DiscoveryLookupClient
{
    private final AtomicReference<Set<ServiceDescriptor>> announcements = new AtomicReference<>(Set.of());
    private final ConcurrentMap<UUID, ServiceDescriptor> discovered = new MapMaker().makeMap();

    private final NodeInfo nodeInfo;
    private final Duration maxAge;

    @Inject
    public InMemoryDiscoveryClient(NodeInfo nodeInfo)
    {
        requireNonNull(nodeInfo, "nodeInfo is null");
        this.nodeInfo = nodeInfo;
        maxAge = DEFAULT_DELAY;
    }

    public InMemoryDiscoveryClient(NodeInfo nodeInfo, Duration maxAge)
    {
        requireNonNull(nodeInfo, "nodeInfo is null");
        requireNonNull(maxAge, "maxAge is null");
        this.nodeInfo = nodeInfo;
        this.maxAge = maxAge;
    }

    public ServiceDescriptor addDiscoveredService(ServiceDescriptor serviceDescriptor)
    {
        requireNonNull(serviceDescriptor, "serviceDescriptor is null");

        return discovered.put(serviceDescriptor.getId(), serviceDescriptor);
    }

    public ServiceDescriptor addDiscoveredService(String type, String pool, String uri)
    {
        requireNonNull(type, "type is null");
        requireNonNull(pool, "pool is null");
        requireNonNull(uri, "uri is null");

        String nodeId = UUID.randomUUID().toString();
        Map<String, String> properties = new HashMap<>();
        URI serviceUri = URI.create(uri);
        String scheme = serviceUri.getScheme();
        checkArgument(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"),
                "Unsupported scheme in uri %s", uri);
        properties.put(scheme, serviceUri.toString());

        return addDiscoveredService(new ServiceDescriptor(
                UUID.randomUUID(),
                nodeId,
                type,
                pool,
                "/somewhere/" + nodeId,
                ServiceState.RUNNING,
                properties
        ));
    }

    public ServiceDescriptor remove(UUID uuid)
    {
        requireNonNull(uuid, "uuid is null");

        return discovered.remove(uuid);
    }

    @Override
    public ListenableFuture<Duration> announce(Set<ServiceAnnouncement> services)
    {
        requireNonNull(services, "services is null");

        ImmutableSet.Builder<ServiceDescriptor> builder = ImmutableSet.builder();
        for (ServiceAnnouncement service : services) {
            builder.add(service.toServiceDescriptor(nodeInfo));
        }
        announcements.set(builder.build());
        return immediateFuture(maxAge);
    }

    @Override
    public ListenableFuture<Void> unannounce()
    {
        announcements.set(Set.of());
        return immediateFuture(null);
    }

    @Override
    public ListenableFuture<ServiceDescriptors> getServices(String type)
    {
        requireNonNull(type, "type is null");

        ImmutableList.Builder<ServiceDescriptor> builder = ImmutableList.builder();
        for (ServiceDescriptor serviceDescriptor : this.announcements.get()) {
            if (serviceDescriptor.getType().equals(type)) {
                builder.add(serviceDescriptor);
            }
        }
        for (ServiceDescriptor serviceDescriptor : this.discovered.values()) {
            if (serviceDescriptor.getType().equals(type)) {
                builder.add(serviceDescriptor);
            }
        }
        return immediateFuture(new ServiceDescriptors(type, null, builder.build(), maxAge, UUID.randomUUID().toString()));
    }

    @Override
    public ListenableFuture<ServiceDescriptors> getServices(String type, String pool)
    {
        requireNonNull(type, "type is null");
        requireNonNull(pool, "pool is null");

        ImmutableList.Builder<ServiceDescriptor> builder = ImmutableList.builder();
        for (ServiceDescriptor serviceDescriptor : this.announcements.get()) {
            if (serviceDescriptor.getType().equals(type) && serviceDescriptor.getPool().equals(pool)) {
                builder.add(serviceDescriptor);
            }
        }
        for (ServiceDescriptor serviceDescriptor : this.discovered.values()) {
            if (serviceDescriptor.getType().equals(type) && serviceDescriptor.getPool().equals(pool)) {
                builder.add(serviceDescriptor);
            }
        }
        return immediateFuture(new ServiceDescriptors(type, pool, builder.build(), maxAge, UUID.randomUUID().toString()));
    }

    @Override
    public ListenableFuture<ServiceDescriptors> refreshServices(ServiceDescriptors serviceDescriptors)
    {
        requireNonNull(serviceDescriptors, "serviceDescriptors is null");

        return getServices(serviceDescriptors.getType(), serviceDescriptors.getPool());
    }
}
