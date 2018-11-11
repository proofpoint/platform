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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.PrivateBinder;
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
import java.net.URI;

import static com.google.inject.name.Names.named;
import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.HttpClientBinder.HttpClientBindingBuilder;
import static com.proofpoint.http.client.HttpClientBinder.httpClientBinder;
import static com.proofpoint.testing.Assertions.assertInstanceOf;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNotSame;
import static org.testng.Assert.assertSame;
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
                                .withAliases(ImmutableList.of(FooAlias2.class, FooAlias3.class)),
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
    public void testBindBalancingHttpClientUris()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        binder -> httpClientBinder(binder).bindBalancingHttpClient("foo", FooClient.class, ImmutableSet.of(URI.create("http://nonexistent.nonexistent"))),
                        new ReportingModule(),
                        new TestingMBeanModule())
                .initialize();

        assertNotNull(injector.getInstance(Key.get(HttpClient.class, FooClient.class)));
    }

    @Test
    public void testBindBalancingHttpClientUrisParameterizedAnnotation()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        binder -> httpClientBinder(binder).bindBalancingHttpClient("foo", named("bar"), "baz", ImmutableSet.of(URI.create("http://nonexistent.nonexistent"))),
                        new ReportingModule(),
                        new TestingMBeanModule())
                .initialize();

        assertNotNull(injector.getInstance(Key.get(HttpClient.class, named("bar"))));
    }

    private static void assertFilterCount(HttpClient httpClient, int filterCount)
    {
        assertNotNull(httpClient);
        assertInstanceOf(httpClient, JettyHttpClient.class);
        assertEquals(((JettyHttpClient) httpClient).getRequestFilters().size(), filterCount);
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
