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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.proofpoint.discovery.client.balancing.HttpServiceBalancerListenerAdapter;
import com.proofpoint.http.client.ServiceType;
import com.proofpoint.http.client.balancing.HttpServiceBalancerImpl;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.units.Duration;
import org.weakref.jmx.Managed;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Lists.newArrayList;
import static com.proofpoint.concurrent.Threads.daemonThreadsNamed;
import static java.nio.file.Files.readAllBytes;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toList;

public class ServiceInventory
{
    private static final Logger log = Logger.get(ServiceInventory.class);

    private final String environment;
    private final URI serviceInventoryUri;
    private final URI discoveryServiceURI;
    private final Duration updateInterval;
    private final JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec;
    private final DiscoveryAddressLookup discoveryAddressLookup;
    private final ServiceDescriptorsListener discoveryListener;

    private final AtomicReference<List<ServiceDescriptor>> serviceDescriptors = new AtomicReference<>(List.of());
    private final ScheduledExecutorService executorService = newSingleThreadScheduledExecutor(daemonThreadsNamed("service-inventory-%s"));
    private final AtomicBoolean serverUp = new AtomicBoolean(true);
    private ScheduledFuture<?> scheduledFuture = null;

    public ServiceInventory(ServiceInventoryConfig serviceInventoryConfig,
            DiscoveryClientConfig discoveryClientConfig,
            NodeInfo nodeInfo,
            JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec,
            @com.proofpoint.http.client.ServiceType("discovery") HttpServiceBalancerImpl discoveryBalancer)
    {
        this(serviceInventoryConfig,
                discoveryClientConfig,
                nodeInfo,
                serviceDescriptorsCodec,
                discoveryBalancer,
                new DiscoveryAddressLookup());
    }

    @Inject
    @SuppressWarnings("deprecation")
    ServiceInventory(ServiceInventoryConfig serviceInventoryConfig,
            DiscoveryClientConfig discoveryClientConfig,
            NodeInfo nodeInfo,
            JsonCodec<ServiceDescriptorsRepresentation> serviceDescriptorsCodec,
            @ServiceType("discovery") HttpServiceBalancerImpl discoveryBalancer,
            DiscoveryAddressLookup discoveryAddressLookup)
    {
        requireNonNull(serviceInventoryConfig, "serviceInventoryConfig is null");
        requireNonNull(discoveryClientConfig, "discoveryClientConfig is null");
        requireNonNull(nodeInfo, "nodeInfo is null");
        requireNonNull(serviceDescriptorsCodec, "serviceDescriptorsCodec is null");
        requireNonNull(discoveryBalancer, "discoveryBalancer is null");
        requireNonNull(discoveryAddressLookup, "discoveryAddressLookup is null");

        environment = nodeInfo.getEnvironment();
        serviceInventoryUri = serviceInventoryConfig.getServiceInventoryUri();
        if (serviceInventoryUri == null) {
            discoveryServiceURI = discoveryClientConfig.getDiscoveryServiceURI();
        } else {
            discoveryServiceURI = null;
            String scheme = serviceInventoryUri.getScheme().toLowerCase();
            checkArgument(scheme.equals("file"), "Service inventory uri must have a file scheme");
        }
        updateInterval = serviceInventoryConfig.getUpdateInterval();
        this.serviceDescriptorsCodec = serviceDescriptorsCodec;
        this.discoveryAddressLookup = discoveryAddressLookup;
        discoveryListener = new HttpServiceBalancerListenerAdapter(discoveryBalancer);

        if (discoveryServiceURI != null) {
            discoveryBalancer.updateHttpUris(ImmutableMultiset.of(discoveryServiceURI));
        }
        else {
            try {
                updateServiceInventory();
            }
            catch (Exception ignored) {
            }
        }
    }

    @PostConstruct
    public synchronized void start()
    {
        if (discoveryServiceURI != null || scheduledFuture != null) {
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

    public Iterable<ServiceDescriptor> getServiceDescriptors(String type)
    {
        return serviceDescriptors.get().stream()
                .filter(serviceDescriptor -> serviceDescriptor.getType().equals(type))
                .collect(toList());
    }

    public Iterable<ServiceDescriptor> getServiceDescriptors(String type, String pool)
    {
        return serviceDescriptors.get().stream()
                .filter(serviceDescriptor -> serviceDescriptor.getType().equals(type))
                .filter(serviceDescriptor -> serviceDescriptor.getPool().equals(pool))
                .collect(toList());
    }

    @Managed
    public final void updateServiceInventory()
    {
        if (discoveryServiceURI != null) {
            return;
        }

        List<ServiceDescriptor> descriptors;
        if (serviceInventoryUri != null) {
            try {
                ServiceDescriptorsRepresentation serviceDescriptorsRepresentation;
                File file = new File(serviceInventoryUri);
                serviceDescriptorsRepresentation = serviceDescriptorsCodec.fromJson(readAllBytes(file.toPath()));

                if (!environment.equals(serviceDescriptorsRepresentation.getEnvironment())) {
                    logServerError("Expected environment to be %s, but was %s", environment, serviceDescriptorsRepresentation.getEnvironment());
                }

                descriptors = newArrayList(serviceDescriptorsRepresentation.getServiceDescriptors());
            }
            catch (Exception e) {
                logServerError(e, "Error loading service inventory from %s", serviceInventoryUri.toASCIIString());
                return;
            }
        }
        else {
            try {
                descriptors = discoveryAddressLookup.get().stream()
                        .map(inetAddress -> {
                            try {
                                return new ServiceDescriptor(
                                        UUID.nameUUIDFromBytes(inetAddress.getAddress()),
                                        null,
                                        "discovery",
                                        "general",
                                        null,
                                        ServiceState.RUNNING,
                                        ImmutableMap.of("http", new URI("http", null, inetAddress.getHostAddress(), 4111, null, null, null).toASCIIString()));
                            }
                            catch (URISyntaxException e) {
                                log.error(e, "Invalid discovery server address %s", inetAddress);
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(toCollection(ArrayList::new));
            }
            catch (Exception e) {
                logServerError(e, "Error looking up discovery in DNS");
                return;
            }
        }
        Collections.shuffle(descriptors);
        serviceDescriptors.set(ImmutableList.copyOf(descriptors));
        discoveryListener.updateServiceDescriptors(Collections2.filter(descriptors, input -> "discovery".equals(input.getType())));

        if (serverUp.compareAndSet(false, true)) {
            log.info("ServiceInventory update succeeded");
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
