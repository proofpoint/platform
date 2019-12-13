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

import com.google.common.collect.ImmutableMap;
import com.google.inject.ConfigurationException;
import com.google.inject.CreationException;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.discovery.client.announce.Announcer;
import com.proofpoint.http.client.balancing.HttpServiceBalancer;
import com.proofpoint.reporting.ReportingModule;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.discovery.client.DiscoveryBinder.discoveryBinder;
import static com.proofpoint.discovery.client.ServiceTypes.serviceType;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

public class TestStaticDiscoveryModule
{
    private Injector injector;

    @BeforeMethod
    public void setup()
    {
        injector = null;
    }

    @AfterMethod(alwaysRun = true)
    public void teardown()
    {
        try {
            LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);
            lifeCycleManager.stop();
        }
        catch (Exception ignored) {
        }
    }

    @Test
    public void testBinding()
            throws Exception
    {
        injector = bootstrapTest()
                .withModules(
                        new ReportingModule(),
                        new StaticDiscoveryModule()
                )
                .initialize();

        try {
            // should not produce an Announcer
            injector.getInstance(Announcer.class);
            fail("expected ConfigurationException");
        }
        catch (ConfigurationException ignored)
        {}
    }

    @Test
    public void testBindHttpBalancer()
            throws Exception
    {
        injector = bootstrapTest()
                .withModules(
                        new ReportingModule(),
                        new StaticDiscoveryModule(),
                        binder -> discoveryBinder(binder).bindHttpBalancer("foo"),
                        binder -> discoveryBinder(binder).bindHttpBalancer("bar")
                )
                .setRequiredConfigurationProperties(ImmutableMap.of(
                        "service-balancer.foo.uri", "fake://server",
                        "service-balancer.bar.uri", "fake://server")
                )
                .initialize();

        assertNotNull(injector.getInstance(Key.get(HttpServiceBalancer.class, com.proofpoint.http.client.ServiceTypes.serviceType("foo"))));
        assertNotNull(injector.getInstance(Key.get(HttpServiceBalancer.class, serviceType("foo"))));
        assertNotNull(injector.getInstance(Key.get(HttpServiceBalancer.class, com.proofpoint.http.client.ServiceTypes.serviceType("bar"))));
        assertNotNull(injector.getInstance(Key.get(HttpServiceBalancer.class, serviceType("bar"))));
    }

    @Test(expectedExceptions = CreationException.class)
    public void testBindAnnouncementFails()
            throws Exception
    {
        injector = bootstrapTest()
                .withModules(
                        new ReportingModule(),
                        new StaticDiscoveryModule(),
                        binder -> discoveryBinder(binder).bindHttpAnnouncement("foo")
                )
                .initialize();
    }
}
