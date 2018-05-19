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
import com.google.inject.CreationException;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.discovery.client.announce.AnnouncementHttpServerInfo;
import com.proofpoint.discovery.client.announce.Announcer;
import com.proofpoint.discovery.client.announce.DiscoveryAnnouncementClient;
import com.proofpoint.discovery.client.announce.StaticAnnouncementHttpServerInfoImpl;
import com.proofpoint.http.client.balancing.HttpServiceBalancer;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.discovery.client.DiscoveryBinder.discoveryBinder;
import static com.proofpoint.discovery.client.ServiceTypes.serviceType;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class TestDiscoveryModule
{
    private Injector injector;

    @BeforeMethod
    public void setup()
            throws Exception
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
                        new JsonModule(),
                        new TestingNodeModule(),
                        new ReportingModule(),
                        new DiscoveryModule()
                )
                .setRequiredConfigurationProperties(ImmutableMap.of("testing.discovery.uri", "fake://server"))
                .initialize();

        // should produce a discovery announcement client and a lookup client
        assertNotNull(injector.getInstance(DiscoveryAnnouncementClient.class));
        assertNotNull(injector.getInstance(DiscoveryLookupClient.class));
        // should produce an Announcer
        assertNotNull(injector.getInstance(Announcer.class));
    }

    @Test
    public void testBindHttpBalancer()
            throws Exception
    {
        injector = bootstrapTest()
                .withModules(
                        new JsonModule(),
                        new TestingNodeModule(),
                        new ReportingModule(),
                        new DiscoveryModule(),
                        binder -> discoveryBinder(binder).bindHttpBalancer("foo"),
                        binder -> discoveryBinder(binder).bindHttpBalancer("bar")
                )
                .setRequiredConfigurationProperties(ImmutableMap.of("testing.discovery.uri", "fake://server"))
                .initialize();

        assertNotNull(injector.getInstance(Key.get(HttpServiceBalancer.class, serviceType("foo"))));
        assertNotNull(injector.getInstance(Key.get(HttpServiceBalancer.class, serviceType("bar"))));
    }

    @Test
    public void testExecutorShutdown()
            throws Exception
    {
        injector = bootstrapTest()
                .withModules(
                        new JsonModule(),
                        new TestingNodeModule(),
                        new DiscoveryModule(),
                        new ReportingModule(),
                        new TestingMBeanModule()
                )
                .initialize();

        ExecutorService executor = injector.getInstance(Key.get(ScheduledExecutorService.class, ForDiscoveryClient.class));
        LifeCycleManager lifeCycleManager = injector.getInstance(LifeCycleManager.class);

        assertFalse(executor.isShutdown());
        lifeCycleManager.stop();
        assertTrue(executor.isShutdown());
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = ".*HttpServer's HTTPS URI host \"example\" must be a FQDN.*")
    public void testHttpsAnnouncementWithoutFqdnFailsStart()
            throws Exception
    {
        Announcer announcer = getAnnouncerWithUnqualifiedHttpsAnnouncement();

        announcer.start();
    }

    @Test(expectedExceptions = IllegalStateException.class, expectedExceptionsMessageRegExp = ".*HttpServer's HTTPS URI host \"example\" must be a FQDN.*")
    public void testHttpsAnnouncementWithoutFqdnFailsForceAnnounce()
            throws Exception
    {
        Announcer announcer = getAnnouncerWithUnqualifiedHttpsAnnouncement();

        announcer.forceAnnounce();
    }

    @Test
    public void testHttpsAnnouncementWithoutFqdnOkayWithNoStart()
            throws Exception
    {
        getAnnouncerWithUnqualifiedHttpsAnnouncement();
    }

    private Announcer getAnnouncerWithUnqualifiedHttpsAnnouncement()
            throws Exception
    {
        injector = bootstrapTest()
                .withModules(
                        new JsonModule(),
                        new TestingNodeModule(),
                        new ReportingModule(),
                        new DiscoveryModule(),
                        binder -> {
                            binder.bind(AnnouncementHttpServerInfo.class).toInstance(new StaticAnnouncementHttpServerInfoImpl(
                                    null,
                                    null,
                                    URI.create("https://example:4444")
                            ));
                            discoveryBinder(binder).bindHttpAnnouncement("service");
                        }

                )
                .setRequiredConfigurationProperties(ImmutableMap.of("testing.discovery.uri", "fake://server"))
                .initialize();

        return injector.getInstance(Announcer.class);
    }
}
