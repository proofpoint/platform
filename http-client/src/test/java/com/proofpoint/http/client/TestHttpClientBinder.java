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
package com.proofpoint.http.client;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.PrivateBinder;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.reporting.ReportingModule;
import org.testng.annotations.Test;
import org.weakref.jmx.Managed;
import org.weakref.jmx.testing.TestingMBeanModule;

import javax.inject.Inject;
import javax.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import java.util.Set;

import static com.google.inject.name.Names.named;
import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.HttpClientBinder.HttpClientBindingBuilder;
import static com.proofpoint.http.client.HttpClientBinder.httpClientBinder;
import static com.proofpoint.http.client.ServiceTypes.serviceType;
import static com.proofpoint.testing.Assertions.assertInstanceOf;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

public class TestHttpClientBinder
{
    @Test
    public void testBindingMultipleFiltersAndClients()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        binder -> {
                            httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                                    .withFilter(TestingRequestFilter.class)
                                    .withFilter(AnotherHttpRequestFilter.class);

                            HttpClientBindingBuilder builder = httpClientBinder(binder).bindHttpClient("bar", BarClient.class)
                                    .withoutTracing();
                            builder.withFilter(TestingRequestFilter.class);
                            builder.addFilterBinding().to(AnotherHttpRequestFilter.class);
                        },
                        new ReportingModule()
                )
                .initialize();

        assertFilterCount(injector.getInstance(Key.get(HttpClient.class, FooClient.class)), 3);
        assertFilterCount(injector.getInstance(Key.get(HttpClient.class, BarClient.class)), 2);
    }

    @Test
    public void testBindClientWithFilter()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        binder -> httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                                .withFilter(TestingRequestFilter.class)
                                .withFilter(AnotherHttpRequestFilter.class),
                        new ReportingModule()
                )
                .initialize();


        HttpClient httpClient = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertFilterCount(httpClient, 3);
    }

    @Test
    public void testWithoutFilters()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        binder -> httpClientBinder(binder).bindHttpClient("foo", FooClient.class),
                        new ReportingModule()
                )
                .initialize();

        assertNotNull(injector.getInstance(Key.get(HttpClient.class, FooClient.class)));
    }

    @Test
    public void testAliases()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        binder -> httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                                .withAlias(FooAlias1.class)
                                .withAliases(List.of(FooAlias2.class, FooAlias3.class)),
                        new ReportingModule()
                )

                .initialize();

        HttpClient client = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias1.class)), client);
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias2.class)), client);
        assertSame(injector.getInstance(Key.get(HttpClient.class, FooAlias3.class)), client);
    }

    @Test
    public void testMultipleClients()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        binder -> {
                            httpClientBinder(binder).bindHttpClient("foo", FooClient.class);
                            httpClientBinder(binder).bindHttpClient("bar", BarClient.class);
                        },
                        new ReportingModule()
                )
                .initialize();

        HttpClient fooClient = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        HttpClient barClient = injector.getInstance(Key.get(HttpClient.class, BarClient.class));
        assertNotSame(fooClient, barClient);
    }

    @Test
    public void testPrivateBindClient()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        binder -> {
                            newExporter(binder).export(ManagedClass.class);
                            PrivateBinder privateBinder = binder.newPrivateBinder();
                            HttpClientBinder.httpClientPrivateBinder(privateBinder, binder).bindHttpClient("foo", FooClient.class);
                            privateBinder.bind(ExposeHttpClient.class);
                            privateBinder.expose(ExposeHttpClient.class);
                        },
                        new ReportingModule()
                )
                .initialize();

        assertNotNull(injector.getInstance(ExposeHttpClient.class).httpClient);
    }

    @Test
    public void testNormalAndPrivateBindClients()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        binder -> {
                            newExporter(binder).export(ManagedClass.class);
                            PrivateBinder privateBinder = binder.newPrivateBinder();
                            HttpClientBinder.httpClientPrivateBinder(privateBinder, binder).bindHttpClient("foo", FooClient.class);
                            privateBinder.bind(ExposeHttpClient.class);
                            privateBinder.expose(ExposeHttpClient.class);
                            HttpClientBinder.httpClientBinder(binder).bindHttpClient("bar", BarClient.class);
                        },
                        new ReportingModule()
                )
                .initialize();

        assertNotNull(injector.getInstance(ExposeHttpClient.class).httpClient);
        assertNotNull(injector.getInstance(Key.get(HttpClient.class, BarClient.class)));
    }

    @Test
    public void testBindBalancingHttpClientConfigured()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        binder -> httpClientBinder(binder).bindBalancingHttpClient("foo"),
                        new ReportingModule(),
                        new TestingMBeanModule())
                .setRequiredConfigurationProperty("service-client.foo.uri", "http://nonexistent.nonexistent")
                .initialize();

        assertNotNull(injector.getInstance(Key.get(HttpClient.class, serviceType("foo"))));
    }

    @Test
    public void testBindBalancingHttpClientSimple()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        binder -> httpClientBinder(binder).bindBalancingHttpClient("foo", "http://nonexistent.nonexistent"),
                        new ReportingModule(),
                        new TestingMBeanModule())
                .initialize();

        assertNotNull(injector.getInstance(Key.get(HttpClient.class, serviceType("foo"))));
    }

    @Test
    public void testBindBalancingHttpClientConfiguredUris()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        binder -> httpClientBinder(binder).bindBalancingHttpClient("foo", FooClient.class),
                        new ReportingModule(),
                        new TestingMBeanModule())
                .setRequiredConfigurationProperty("service-client.FooClient.uri", "http://nonexistent.nonexistent")
                .initialize();

        assertNotNull(injector.getInstance(Key.get(HttpClient.class, FooClient.class)));
    }

    @Test
    public void testBindBalancingHttpClientUris()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        binder -> httpClientBinder(binder).bindBalancingHttpClient("foo", FooClient.class, Set.of(URI.create("http://nonexistent.nonexistent"))),
                        new ReportingModule(),
                        new TestingMBeanModule())
                .initialize();

        assertNotNull(injector.getInstance(Key.get(HttpClient.class, FooClient.class)));
    }

    @Test
    public void testBindBalancingHttpClientConfigParameterizedAnnotation()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        binder -> httpClientBinder(binder).bindBalancingHttpClient("foo", named("bar"), "baz"),
                        new ReportingModule(),
                        new TestingMBeanModule())
                .setRequiredConfigurationProperty("service-client.baz.uri", "http://nonexistent.nonexistent")
                .initialize();

        assertNotNull(injector.getInstance(Key.get(HttpClient.class, named("bar"))));
    }

    @Test
    public void testBindBalancingHttpClientUrisParameterizedAnnotation()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        binder -> httpClientBinder(binder).bindBalancingHttpClient("foo", named("bar"), "baz", Set.of(URI.create("http://nonexistent.nonexistent"))),
                        new ReportingModule(),
                        new TestingMBeanModule())
                .initialize();

        assertNotNull(injector.getInstance(Key.get(HttpClient.class, named("bar"))));
    }

    @Test
    public void testBindKubernetesServiceBalancingHttp()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        binder -> httpClientBinder(binder).bindKubernetesServiceHttpClient("foo", "default"),
                        new ReportingModule(),
                        new TestingMBeanModule())
                .initialize();

        assertNotNull(injector.getInstance(Key.get(HttpClient.class, serviceType("foo"))));
    }

    @Test
    public void testBindWithoutCertificateVerification()
            throws Exception
    {
        Injector injector = bootstrapTest()
            .withModules(
                binder -> httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                    .withoutCertificateVerification(),
                new ReportingModule(),
                new TestingMBeanModule())
            .initialize();
        HttpClient fooClient = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        assertCertificateVerificationDisabled(fooClient);
    }

    @Test
    public void testClientShutdown()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        binder -> {
                            httpClientBinder(binder).bindHttpClient("foo", FooClient.class);
                            httpClientBinder(binder).bindHttpClient("bar", BarClient.class);
                        },
                        new ReportingModule()
                )
                .initialize();

        HttpClient fooClient = injector.getInstance(Key.get(HttpClient.class, FooClient.class));
        HttpClient barClient = injector.getInstance(Key.get(HttpClient.class, BarClient.class));

        assertFalse(fooClient.isClosed());
        assertFalse(barClient.isClosed());

        injector.getInstance(LifeCycleManager.class).stop();

        assertTrue(fooClient.isClosed());
        assertTrue(barClient.isClosed());
    }

    private static void assertFilterCount(HttpClient httpClient, int filterCount)
    {
        assertNotNull(httpClient);
        assertInstanceOf(httpClient, JettyHttpClient.class);
        assertEquals(((JettyHttpClient) httpClient).getRequestFilters().size(), filterCount);
    }

    private static void assertCertificateVerificationDisabled(HttpClient httpClient) throws Exception {
        assertNotNull(httpClient);
        assertInstanceOf(httpClient, JettyHttpClient.class);

        Field httpClientField = httpClient.getClass().getDeclaredField("httpClient");
        httpClientField.setAccessible(true);
        org.eclipse.jetty.client.HttpClient jettyClient = (org.eclipse.jetty.client.HttpClient) httpClientField.get(httpClient);
        assertTrue(jettyClient.getSslContextFactory().isTrustAll());
    }

    @Retention(RUNTIME)
    @Target(ElementType.PARAMETER)
    @Qualifier
    public @interface FooClient
    {
    }

    @Retention(RUNTIME)
    @Target(ElementType.PARAMETER)
    @Qualifier
    public @interface FooAlias1
    {
    }

    @Retention(RUNTIME)
    @Target(ElementType.PARAMETER)
    @Qualifier
    public @interface FooAlias2
    {
    }

    @Retention(RUNTIME)
    @Target(ElementType.PARAMETER)
    @Qualifier
    public @interface FooAlias3
    {
    }

    @Retention(RUNTIME)
    @Target(ElementType.PARAMETER)
    @Qualifier
    public @interface BarClient
    {
    }

    public static class AnotherHttpRequestFilter
            implements HttpRequestFilter
    {
        @Override
        public Request filterRequest(Request request)
        {
            return request;
        }
    }

    private static class ExposeHttpClient
    {
        public final HttpClient httpClient;

        @Inject
        private ExposeHttpClient(@FooClient HttpClient httpClient)
        {
            this.httpClient = httpClient;
        }
    }

    private static class ManagedClass
    {
        @Managed
        public int getInt()
        {
            return 0;
        }
    }
}
