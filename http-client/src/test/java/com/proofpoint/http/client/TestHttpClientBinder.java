package com.proofpoint.http.client;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.proofpoint.configuration.ConfigurationFactory;
import com.proofpoint.configuration.ConfigurationModule;
import com.proofpoint.tracetoken.TraceTokenModule;
import org.testng.annotations.Test;

import javax.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Collections;

import static com.proofpoint.http.client.HttpClientBinder.HttpClientBindingBuilder;
import static com.proofpoint.http.client.HttpClientBinder.httpClientBinder;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

public class TestHttpClientBinder
{
    @Test
    public void testBindingMultipleFiltersAndClients()
    {
        Injector injector = Guice.createInjector(
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                                .withFilter(TestingRequestFilter.class)
                                .withFilter(AnotherHttpRequestFilter.class)
                                .withTracing();

                        HttpClientBindingBuilder builder = httpClientBinder(binder).bindHttpClient("bar", BarClient.class);
                        builder.withFilter(TestingRequestFilter.class);
                        builder.addFilterBinding().to(AnotherHttpRequestFilter.class);
                    }
                },
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())),
                new TraceTokenModule());

        assertFilterCount(injector.getInstance(Key.get(HttpClient.class, FooClient.class)), 3);
        assertFilterCount(injector.getInstance(Key.get(HttpClient.class, BarClient.class)), 2);
    }

    @Test
    public void testSeparateFiltersForClientAndAsyncClient()
    {
        Injector injector = Guice.createInjector(
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                                .withFilter(TestingRequestFilter.class)
                                .withFilter(AnotherHttpRequestFilter.class)
                                .withTracing();

                        httpClientBinder(binder).bindAsyncHttpClient("foo", FooClient.class)
                                .withFilter(AnotherHttpRequestFilter.class);
                    }
                },
                new ConfigurationModule(new ConfigurationFactory(Collections.<String, String>emptyMap())),
                new TraceTokenModule());

        assertFilterCount(injector.getInstance(Key.get(HttpClient.class, FooClient.class)), 3);
        assertFilterCount(injector.getInstance(Key.get(AsyncHttpClient.class, FooClient.class)), 1);
    }

    private static void assertFilterCount(HttpClient httpClient, int filterCount)
    {
        assertNotNull(httpClient);
        assertEquals(httpClient.getClass(), ApacheHttpClient.class);
        assertEquals(((ApacheHttpClient) httpClient).getRequestFilters().size(), filterCount);
    }

    private static void assertFilterCount(AsyncHttpClient asyncHttpClient, int filterCount)
    {
        assertNotNull(asyncHttpClient);
        assertEquals(asyncHttpClient.getRequestFilters().size(), filterCount);
    }

    @Retention(RUNTIME)
    @Target({ElementType.PARAMETER})
    @Qualifier
    public @interface FooClient
    {
    }

    @Retention(RUNTIME)
    @Target({ElementType.PARAMETER})
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
}
