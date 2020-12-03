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
package com.proofpoint.discovery.client.balancing;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import com.proofpoint.discovery.client.ServiceDescriptor;
import com.proofpoint.discovery.client.ServiceDescriptorsUpdater;
import com.proofpoint.discovery.client.ServiceSelectorConfig;
import com.proofpoint.discovery.client.ServiceState;
import com.proofpoint.discovery.client.testing.InMemoryDiscoveryClient;
import com.proofpoint.http.client.balancing.HttpServiceBalancerImpl;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.testing.SerialScheduledExecutorService;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static com.proofpoint.concurrent.Threads.daemonThreadsNamed;
import static com.proofpoint.testing.Assertions.assertEqualsIgnoreOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.testng.Assert.assertEquals;

public class TestHttpServiceBalancerListenerAdapter
{
    private static final ServiceDescriptor APPLE_1_SERVICE = new ServiceDescriptor(UUID.randomUUID(), "node-A", "apple", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("http", "http://apple-a.example.com"));
    private static final ServiceDescriptor APPLE_1_SERVICE_REPLACEMENT = new ServiceDescriptor(APPLE_1_SERVICE.getId(), "node-A", "apple", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("http", "http://apple-b.example.com"));
    private static final ServiceDescriptor APPLE_2_SERVICE = new ServiceDescriptor(UUID.randomUUID(), "node-B", "apple", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("http", "http://apple-c.example.com", "https", "https://apple-b.example.com"));
    private static final ServiceDescriptor APPLE_2_SERVICE_WEIGHTED = new ServiceDescriptor(UUID.randomUUID(), "node-B", "apple", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("http", "http://apple-c.example.com", "https", "https://apple-b.example.com", "weight", "2.5"));
    private static final ServiceDescriptor DIFFERENT_TYPE = new ServiceDescriptor(UUID.randomUUID(), "node-A", "banana", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("https", "https://banana.example.com"));
    private static final ServiceDescriptor DIFFERENT_POOL = new ServiceDescriptor(UUID.randomUUID(), "node-B", "apple", "fool", "location", ServiceState.RUNNING, ImmutableMap.of("http", "http://apple-fool.example.com"));

    private ScheduledExecutorService executor;
    private NodeInfo nodeInfo;
    private InMemoryDiscoveryClient discoveryClient;
    private HttpServiceBalancerImpl httpServiceBalancer;
    private ServiceDescriptorsUpdater updater;

    @BeforeMethod
    protected void setUp()
    {
        executor = new ScheduledThreadPoolExecutor(10, daemonThreadsNamed("Discovery-%s"));
        nodeInfo = new NodeInfo("environment");
        discoveryClient = new InMemoryDiscoveryClient(nodeInfo);
        httpServiceBalancer = mock(HttpServiceBalancerImpl.class);
        updater = new ServiceDescriptorsUpdater(new HttpServiceBalancerListenerAdapter(httpServiceBalancer),
                "apple",
                new ServiceSelectorConfig().setPool("pool"),
                nodeInfo,
                discoveryClient,
                executor);
    }

    @AfterMethod
    public void tearDown()
    {
        executor.shutdownNow();
    }

    @Test
    public void testNotStartedEmpty()
    {
        verifyNoMoreInteractions(httpServiceBalancer);
    }

    @Test
    public void testStartedEmpty()
    {
        updater.start();

        ArgumentCaptor<Multiset> captor = ArgumentCaptor.forClass(Multiset.class);
        verify(httpServiceBalancer).updateHttpUris(captor.capture());

        assertEquals(captor.getValue(), ImmutableMultiset.of());
    }

    @Test
    public void testNotStartedWithServices()
    {
        discoveryClient.addDiscoveredService(APPLE_1_SERVICE);
        discoveryClient.addDiscoveredService(APPLE_2_SERVICE);
        discoveryClient.addDiscoveredService(DIFFERENT_TYPE);
        discoveryClient.addDiscoveredService(DIFFERENT_POOL);

        verifyNoMoreInteractions(httpServiceBalancer);
    }

    @Test
    public void testStartedWithServices()
    {
        discoveryClient.addDiscoveredService(APPLE_1_SERVICE);
        discoveryClient.addDiscoveredService(APPLE_2_SERVICE);
        discoveryClient.addDiscoveredService(DIFFERENT_TYPE);
        discoveryClient.addDiscoveredService(DIFFERENT_POOL);

        updater.start();

        ArgumentCaptor<Multiset> captor = ArgumentCaptor.forClass(Multiset.class);
        verify(httpServiceBalancer).updateHttpUris(captor.capture());

        assertEqualsIgnoreOrder(captor.getValue(), ImmutableMultiset.of(URI.create("http://apple-a.example.com"), URI.create("https://apple-b.example.com")));
    }

    @Test
    public void testServiceReplacedWithEmptySet()
            throws InterruptedException
    {
        SerialScheduledExecutorService serialExecutor = new SerialScheduledExecutorService();
        updater = new ServiceDescriptorsUpdater(new HttpServiceBalancerListenerAdapter(httpServiceBalancer),
                "apple",
                new ServiceSelectorConfig().setPool("pool"),
                nodeInfo,
                discoveryClient,
                serialExecutor);

        discoveryClient.addDiscoveredService(APPLE_1_SERVICE);

        // start the updater and verify that we get the initial call
        updater.start();
        ArgumentCaptor<Multiset> captor = ArgumentCaptor.forClass(Multiset.class);
        verify(httpServiceBalancer).updateHttpUris(captor.capture());

        // we remove the service we just added.
        discoveryClient.remove(APPLE_1_SERVICE.getId());

        // this is the default delay to ensure our updater re-triggers, plus 1s padding.
        serialExecutor.elapseTime(11, TimeUnit.SECONDS);

        // verify that even though we removed the service, it was not removed from the balancer.
        verifyNoMoreInteractions(httpServiceBalancer);

        assertEqualsIgnoreOrder(captor.getValue(), ImmutableMultiset.of(URI.create("http://apple-a.example.com")));
    }

    @Test
    public void testServiceReplacedWithNonEmptySet()
            throws InterruptedException
    {
        SerialScheduledExecutorService serialExecutor = new SerialScheduledExecutorService();
        updater = new ServiceDescriptorsUpdater(new HttpServiceBalancerListenerAdapter(httpServiceBalancer),
                "apple",
                new ServiceSelectorConfig().setPool("pool"),
                nodeInfo,
                discoveryClient,
                serialExecutor);

        discoveryClient.addDiscoveredService(APPLE_1_SERVICE);

        // start the updater and verify that we get the initial call
        updater.start();

        // we replace the service we just added with an updated descriptor for the same ID and node ID.
        discoveryClient.addDiscoveredService(APPLE_1_SERVICE_REPLACEMENT);

        // this is the default delay to ensure our updater re-triggers, plus 1s padding.
        serialExecutor.elapseTime(11, TimeUnit.SECONDS);

        ArgumentCaptor<Multiset> captor = ArgumentCaptor.forClass(Multiset.class);

        // we verify that our update method is called twice...
        verify(httpServiceBalancer, times(2)).updateHttpUris(captor.capture());

        // ...and that each time the argument points to the (single) correct URL for that point in time.
        assertEquals(captor.getAllValues(), Arrays.asList(ImmutableMultiset.of(URI.create("http://apple-a.example.com")), ImmutableMultiset.of(URI.create("http://apple-b.example.com"))));
    }

    @Test
    public void testWeightedServices()
    {
        discoveryClient.addDiscoveredService(APPLE_1_SERVICE);
        discoveryClient.addDiscoveredService(APPLE_2_SERVICE_WEIGHTED);
        discoveryClient.addDiscoveredService(DIFFERENT_TYPE);
        discoveryClient.addDiscoveredService(DIFFERENT_POOL);

        updater.start();

        ArgumentCaptor<Multiset> captor = ArgumentCaptor.forClass(Multiset.class);
        verify(httpServiceBalancer).updateHttpUris(captor.capture());

        assertEqualsIgnoreOrder(captor.getValue(), ImmutableMultiset.of(URI.create("http://apple-a.example.com"), URI.create("https://apple-b.example.com"), URI.create("https://apple-b.example.com")));
    }
}
