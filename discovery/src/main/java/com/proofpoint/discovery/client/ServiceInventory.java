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

import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Streams;
import com.google.inject.Inject;
import com.proofpoint.discovery.client.balancing.HttpServiceBalancerListenerAdapter;
import com.proofpoint.http.client.balancing.HttpServiceBalancerImpl;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;
import org.weakref.jmx.Managed;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.concurrent.Threads.daemonThreadsNamed;
import static java.nio.file.Files.readAllBytes;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;

public class ServiceInventory
{
    private static final Logger log = Logger.get(ServiceInventory.class);

    private final String environment;
    private final URI serviceInventoryUri;
    private final URI discoveryServiceURI;
    private final Duration updateInterval;
    private final JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec;
    private final ServiceDescriptorsListener discoveryListener;

    private final AtomicReference<List<ServiceDescriptor>> serviceDescriptors = new AtomicReference<List<ServiceDescriptor>>(ImmutableList.<ServiceDescriptor>of());
    private final ScheduledExecutorService executorService = newSingleThreadScheduledExecutor(daemonThreadsNamed("service-inventory-%s"));
    private final AtomicBoolean serverUp = new AtomicBoolean(true);
    private ScheduledFuture<?> scheduledFuture = null;

    @Inject
    public ServiceInventory(ServiceInventoryConfig serviceInventoryConfig,
            DiscoveryClientConfig discoveryClientConfig,
            NodeInfo nodeInfo,
            JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec,
            @ServiceType("discovery") HttpServiceBalancerImpl discoveryBalancer)
    {
        requireNonNull(serviceInventoryConfig, "serviceInventoryConfig is null");
        requireNonNull(discoveryClientConfig, "discoveryClientConfig is null");
        requireNonNull(nodeInfo, "nodeInfo is null");
        requireNonNull(serviceDescriptorsCodec, "serviceDescriptorsCodec is null");
        requireNonNull(discoveryBalancer, "discoveryBalancer is null");

        environment = nodeInfo.getEnvironment();
        serviceInventoryUri = serviceInventoryConfig.getServiceInventoryUri();
        discoveryServiceURI = discoveryClientConfig.getDiscoveryServiceURI();
        updateInterval = serviceInventoryConfig.getUpdateInterval();
        this.serviceDescriptorsCodec = serviceDescriptorsCodec;
        discoveryListener = new HttpServiceBalancerListenerAdapter(discoveryBalancer);

        if (serviceInventoryUri != null) {
            String scheme = serviceInventoryUri.getScheme().toLowerCase();
            checkArgument(scheme.equals("file"), "Service inventory uri must have a file scheme");

            try {
                updateServiceInventory();
            }
            catch (Exception ignored) {
            }
        } else if (discoveryServiceURI != null) {
            discoveryBalancer.updateHttpUris(ImmutableSet.of(discoveryServiceURI));
        }
    }

    @PostConstruct
    public synchronized void start()
    {
        if (serviceInventoryUri == null || scheduledFuture != null) {
            return;
        }
        scheduledFuture = executorService.scheduleAtFixedRate(() -> {
            try {
                updateServiceInventory();
            }
            catch (Throwable e) {
                log.error(e, "Unexpected exception from service inventory update");
            }
        }, updateInterval.toMillis(), updateInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public synchronized void stop()
    {
        if (scheduledFuture != null) {
            scheduledFuture.cancel(true);
            scheduledFuture = null;
        }
    }

    public Iterable<ServiceDescriptor> getServiceDescriptors()
    {
        return serviceDescriptors.get();
    }

    public Iterable<ServiceDescriptor> getServiceDescriptors(final String type)
    {
        return Streams.stream(getServiceDescriptors())
                .filter(serviceDescriptor -> serviceDescriptor.getType().equals(type))
                .collect(Collectors.toList());
    }

    public Iterable<ServiceDescriptor> getServiceDescriptors(final String type, final String pool)
    {
        return Streams.stream(getServiceDescriptors())
                .filter(serviceDescriptor -> serviceDescriptor.getType().equals(type) && serviceDescriptor.getPool().equals(pool))
                .collect(Collectors.toList());
    }

    @Managed
    public final void updateServiceInventory()
    {
        if (serviceInventoryUri == null) {
            return;
        }

        try {
            ServiceDescriptorsRepresentation serviceDescriptorsRepresentation;
            File file = new File(serviceInventoryUri);
            serviceDescriptorsRepresentation = serviceDescriptorsCodec.fromJson(readAllBytes(file.toPath()));

            if (!environment.equals(serviceDescriptorsRepresentation.getEnvironment())) {
                logServerError("Expected environment to be %s, but was %s", environment, serviceDescriptorsRepresentation.getEnvironment());
            }

            List<ServiceDescriptor> descriptors = newArrayList(serviceDescriptorsRepresentation.getServiceDescriptors());
            Collections.shuffle(descriptors);
            serviceDescriptors.set(ImmutableList.copyOf(descriptors));
            discoveryListener.updateServiceDescriptors(Collections2.filter(descriptors, input -> "discovery".equals(input.getType())));

            if (serverUp.compareAndSet(false, true)) {
                log.info("ServiceInventory connect succeeded");
            }
        }
        catch (Exception e) {
            logServerError(e, "Error loading service inventory from %s", serviceInventoryUri.toASCIIString());
        }
    }

    private void logServerError(String message, Object... args)
    {
        if (serverUp.compareAndSet(true, false)) {
            log.error(message, args);
        }
    }
    private void logServerError(Exception e, String message, Object... args)
    {
        if (serverUp.compareAndSet(true, false)) {
            log.error(e, message, args);
        }
    }

}
