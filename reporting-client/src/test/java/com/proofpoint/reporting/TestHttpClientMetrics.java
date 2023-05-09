package com.proofpoint.reporting;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.testing.ReportingTester;
import com.proofpoint.reporting.testing.TestingReportingModule;
import jakarta.inject.Qualifier;
import org.assertj.guava.api.Assertions;
import org.testng.annotations.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.net.URI;
import java.util.List;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.HttpClientBinder.httpClientBinder;
import static com.proofpoint.http.client.ServiceTypes.serviceType;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

// Put here to avoid module dependency loops
public class TestHttpClientMetrics
{
    @Test
    public void testHttpClientMetrics()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        new TestingNodeModule(),
                        new TestingReportingModule(),
                        binder -> httpClientBinder(binder).bindHttpClient("foo", FooClient.class)
                )
                .initialize();
        Assertions.assertThat(injector.getInstance(ReportingTester.class).collectData())
                .containsCell("HttpClient.FooClient.IoPool.FreeThreadCount", ImmutableMap.of(), 198);
    }

    @Test
    public void testBalancingHttpClientMetrics()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        new TestingNodeModule(),
                        new TestingReportingModule(),
                        binder -> httpClientBinder(binder).bindBalancingHttpClient("foo", serviceType("foo"), "foo", List.of(URI.create("http://127.0.0.1")))
                )
                .initialize();
        Assertions.assertThat(injector.getInstance(ReportingTester.class).collectData())
                .containsCell("HttpClient.foo.IoPool.FreeThreadCount", ImmutableMap.of(), 198);
    }

    @Retention(RUNTIME)
    @Target(ElementType.PARAMETER)
    @Qualifier
    public @interface FooClient
    {
    }
}
