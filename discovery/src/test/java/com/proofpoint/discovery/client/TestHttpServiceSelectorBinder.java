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
import com.google.common.collect.MoreCollectors;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.discovery.client.testing.InMemoryDiscoveryClient;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.node.ApplicationNameModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static com.proofpoint.discovery.client.DiscoveryBinder.discoveryBinder;
import static com.proofpoint.discovery.client.ServiceTypes.serviceType;
import static com.proofpoint.discovery.client.announce.ServiceAnnouncement.serviceAnnouncement;
import static com.proofpoint.testing.Assertions.assertEqualsIgnoreOrder;
import static org.testng.Assert.assertEquals;

@SuppressWarnings("deprecation")
public class TestHttpServiceSelectorBinder
{
    private Injector injector;

    @BeforeMethod
    public void setUp()
    {
        injector = Guice.createInjector(
                new ApplicationNameModule("test-application"),
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.of())),
                new TestingNodeModule(),
                new TestingDiscoveryModule(),
                new ReportingModule(),
                binder -> {
                    discoveryBinder(binder).bindHttpSelector("apple");
                }
        );
    }

    @Test
    public void testHttpSelectorString()
    {
        InMemoryDiscoveryClient discoveryClient = injector.getInstance(InMemoryDiscoveryClient.class);
        discoveryClient.announce(Set.of(serviceAnnouncement("apple").addProperty("http", "fake://server-http").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, com.proofpoint.http.client.ServiceTypes.serviceType("apple")));

        assertEquals(selector.selectHttpService().stream().collect(MoreCollectors.onlyElement()), URI.create("fake://server-http"));
        HttpServiceSelector legacySelector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("apple")));
        assertEquals(legacySelector.selectHttpService().stream().collect(MoreCollectors.onlyElement()), URI.create("fake://server-http"));
    }

    @Test
    public void testHttpSelectorAnnotation()
    {
        injector = Guice.createInjector(
                new ApplicationNameModule("test-application"),
                new ConfigurationModule(new ConfigurationFactory(ImmutableMap.of())),
                new TestingNodeModule(),
                new TestingDiscoveryModule(),
                new ReportingModule(),
                binder -> {
                    discoveryBinder(binder).bindHttpSelector(com.proofpoint.http.client.ServiceTypes.serviceType("apple"));
                }
        );

        InMemoryDiscoveryClient discoveryClient = injector.getInstance(InMemoryDiscoveryClient.class);
        discoveryClient.announce(Set.of(serviceAnnouncement("apple").addProperty("http", "fake://server-http").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, com.proofpoint.http.client.ServiceTypes.serviceType("apple")));
        assertEquals(selector.selectHttpService().stream().collect(MoreCollectors.onlyElement()), URI.create("fake://server-http"));
    }

    @Test
    public void testHttpsSelector()
    {
        InMemoryDiscoveryClient discoveryClient = injector.getInstance(InMemoryDiscoveryClient.class);
        discoveryClient.announce(Set.of(serviceAnnouncement("apple").addProperty("https", "fake://server-https").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, com.proofpoint.http.client.ServiceTypes.serviceType("apple")));
        assertEquals(selector.selectHttpService().stream().collect(MoreCollectors.onlyElement()), URI.create("fake://server-https"));
        HttpServiceSelector legacySelector = injector.getInstance(Key.get(HttpServiceSelector.class, serviceType("apple")));
        assertEquals(legacySelector.selectHttpService().stream().collect(MoreCollectors.onlyElement()), URI.create("fake://server-https"));
    }

    @Test
    public void testFavorHttpsOverHttpSelector()
    {
        InMemoryDiscoveryClient discoveryClient = injector.getInstance(InMemoryDiscoveryClient.class);
        discoveryClient.announce(Set.of(serviceAnnouncement("apple").addProperty("http", "fake://server-http").build(),
                serviceAnnouncement("apple").addProperty("http", "fake://server-http-dontuse").addProperty("https", "fake://server-https").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, com.proofpoint.http.client.ServiceTypes.serviceType("apple")));
        assertEqualsIgnoreOrder(selector.selectHttpService(), List.of(URI.create("fake://server-https"), URI.create("fake://server-http")));
    }

    @Test
    public void testNoHttpServices()
    {
        InMemoryDiscoveryClient discoveryClient = injector.getInstance(InMemoryDiscoveryClient.class);
        discoveryClient.announce(Set.of(serviceAnnouncement("apple").addProperty("foo", "fake://server-https").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, com.proofpoint.http.client.ServiceTypes.serviceType("apple")));
        assertEquals(selector.selectHttpService(), List.of());
    }


    @Test
    public void testInvalidUris()
    {
        InMemoryDiscoveryClient discoveryClient = injector.getInstance(InMemoryDiscoveryClient.class);
        discoveryClient.announce(Set.of(serviceAnnouncement("apple").addProperty("http", ":::INVALID:::").build()));
        discoveryClient.announce(Set.of(serviceAnnouncement("apple").addProperty("https", ":::INVALID:::").build()));

        HttpServiceSelector selector = injector.getInstance(Key.get(HttpServiceSelector.class, com.proofpoint.http.client.ServiceTypes.serviceType("apple")));
        assertEquals(selector.selectHttpService(), List.of());
    }
}
