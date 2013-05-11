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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.proofpoint.discovery.client.testing.InMemoryDiscoveryClient;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.testing.Assertions;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class TestCachingServiceSelector
{
    private static final ServiceDescriptor APPLE_1_SERVICE = new ServiceDescriptor(UUID.randomUUID(), "node-A", "apple", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple"));
    private static final ServiceDescriptor APPLE_2_SERVICE = new ServiceDescriptor(UUID.randomUUID(), "node-B", "apple", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple"));
    private static final ServiceDescriptor DIFFERENT_TYPE = new ServiceDescriptor(UUID.randomUUID(), "node-A", "banana", "pool", "location", ServiceState.RUNNING, ImmutableMap.of("b", "banana"));
    private static final ServiceDescriptor DIFFERENT_POOL = new ServiceDescriptor(UUID.randomUUID(), "node-B", "apple", "fool", "location", ServiceState.RUNNING, ImmutableMap.of("a", "apple"));

    private ScheduledExecutorService executor;
    private NodeInfo nodeInfo;
    private InMemoryDiscoveryClient discoveryClient;
    private CachingServiceSelector serviceSelector;
    private ServiceDescriptorsUpdater updater;

    @BeforeMethod
    protected void setUp()
            throws Exception
    {
        executor = new ScheduledThreadPoolExecutor(10,
                new ThreadFactoryBuilder().setNameFormat("Discovery-%s").setDaemon(true).build());
        nodeInfo = new NodeInfo("environment");
        discoveryClient = new InMemoryDiscoveryClient(nodeInfo);
        serviceSelector = new CachingServiceSelector("apple",
                new ServiceSelectorConfig().setPool("pool")
        );
        updater = new ServiceDescriptorsUpdater(serviceSelector, "apple",
                new ServiceSelectorConfig().setPool("pool"),
                discoveryClient,
                executor);
    }

    @AfterMethod
    public void tearDown()
            throws Exception
    {
        executor.shutdownNow();
    }

    @Test
    public void testBasics()
    {
        Assert.assertEquals(serviceSelector.getType(), "apple");
        Assert.assertEquals(serviceSelector.getPool(), "pool");
    }

    @Test
    public void testNotStartedEmpty()
    {
        Assert.assertEquals(serviceSelector.selectAllServices(), ImmutableList.of());
    }

    @Test
    public void testStartedEmpty()
            throws Exception
    {
        updater.start();

        Assert.assertEquals(serviceSelector.selectAllServices(), ImmutableList.of());
    }

    @Test
    public void testNotStartedWithServices()
    {
        discoveryClient.addDiscoveredService(APPLE_1_SERVICE);
        discoveryClient.addDiscoveredService(APPLE_2_SERVICE);
        discoveryClient.addDiscoveredService(DIFFERENT_TYPE);
        discoveryClient.addDiscoveredService(DIFFERENT_POOL);

        Assert.assertEquals(serviceSelector.selectAllServices(), ImmutableList.of());
    }

    @Test
    public void testStartedWithServices()
            throws Exception
    {
        discoveryClient.addDiscoveredService(APPLE_1_SERVICE);
        discoveryClient.addDiscoveredService(APPLE_2_SERVICE);
        discoveryClient.addDiscoveredService(DIFFERENT_TYPE);
        discoveryClient.addDiscoveredService(DIFFERENT_POOL);

        updater.start();

        Thread.sleep(100);

        Assertions.assertEqualsIgnoreOrder(serviceSelector.selectAllServices(), ImmutableList.of(APPLE_1_SERVICE, APPLE_2_SERVICE));
    }
}
