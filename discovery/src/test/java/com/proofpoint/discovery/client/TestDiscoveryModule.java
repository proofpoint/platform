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
import com.google.inject.Injector;
import com.google.inject.Key;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.discovery.client.announce.AnnouncementHttpServerInfo;
import com.proofpoint.discovery.client.announce.Announcer;
import com.proofpoint.discovery.client.announce.DiscoveryAnnouncementClient;
import com.proofpoint.discovery.client.announce.NullAnnouncer;
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

import static com.google.inject.Key.get;
import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.discovery.client.DiscoveryBinder.discoveryBinder;
import static com.proofpoint.discovery.client.ServiceTypes.serviceType;
import static org.assertj.core.api.Assertions.assertThat;

public class TestDiscoveryModule
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
                        new JsonModule(),
                        new TestingNodeModule(),
                        new ReportingModule(),
                        new DiscoveryModule()
                )
                .setRequiredConfigurationProperties(ImmutableMap.of("testing.discovery.uri", "fake://server"))
                .initialize();

        // should produce a discovery announcement client and a lookup client
        assertThat(injector.getInstance(DiscoveryAnnouncementClient.class)).isNotNull();
        assertThat(injector.getInstance(DiscoveryLookupClient.class)).isNotNull();
        // should produce an Announcer
        assertThat(injector.getInstance(Announcer.class)).isNotNull();
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

        assertThat(injector.getInstance(get(HttpServiceBalancer.class, serviceType("foo")))).isNotNull();
        assertThat(injector.getInstance(get(HttpServiceBalancer.class, com.proofpoint.http.client.ServiceTypes.serviceType("foo")))).isNotNull();
        assertThat(injector.getInstance(get(HttpServiceBalancer.class, serviceType("bar")))).isNotNull();
        assertThat(injector.getInstance(get(HttpServiceBalancer.class, com.proofpoint.http.client.ServiceTypes.serviceType("bar")))).isNotNull();
    }

    @Test
    public void testStaticBindHttpBalancer()
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
                .setRequiredConfigurationProperties(ImmutableMap.of(
                        "testing.discovery.static", "true",
                        "service-balancer.foo.uri", "http://127.0.0.1/foo",
                        "service-balancer.bar.uri", "http://127.0.0.1/bar"))
                .initialize();

        HttpServiceBalancer fooBalancer = injector.getInstance(Key.get(HttpServiceBalancer.class, com.proofpoint.http.client.ServiceTypes.serviceType("foo")));
        assertThat(fooBalancer.createAttempt().getUri()).isEqualTo(URI.create("http://127.0.0.1/foo"));
        HttpServiceBalancer barBalancer = injector.getInstance(Key.get(HttpServiceBalancer.class, com.proofpoint.http.client.ServiceTypes.serviceType("bar")));
        assertThat(barBalancer.createAttempt().getUri()).isEqualTo(URI.create("http://127.0.0.1/bar"));
        HttpServiceBalancer fooLegacyBalancer = injector.getInstance(Key.get(HttpServiceBalancer.class, serviceType("foo")));
        assertThat(fooLegacyBalancer.createAttempt().getUri()).isEqualTo(URI.create("http://127.0.0.1/foo"));
        HttpServiceBalancer barLegacyBalancer = injector.getInstance(Key.get(HttpServiceBalancer.class, serviceType("bar")));
        assertThat(barLegacyBalancer.createAttempt().getUri()).isEqualTo(URI.create("http://127.0.0.1/bar"));
        assertThat(injector.getInstance(Announcer.class)).isInstanceOf(NullAnnouncer.class);
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

        assertThat(executor.isShutdown()).isFalse();
        lifeCycleManager.stop();
        assertThat(executor.isShutdown()).isTrue();
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
