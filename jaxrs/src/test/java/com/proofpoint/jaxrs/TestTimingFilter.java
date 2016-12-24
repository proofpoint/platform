/*
 * Copyright 2016 Proofpoint, Inc.
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
package com.proofpoint.jaxrs;

import com.google.common.base.Ticker;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.google.inject.Injector;
import com.google.inject.util.Modules;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request.Builder;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.testing.ReportingTester;
import com.proofpoint.reporting.testing.TestingReportingModule;
import com.proofpoint.testing.Closeables;
import com.proofpoint.testing.TestingTicker;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import java.net.URI;
import java.util.Map;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.Request.Builder.prepareDelete;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.Request.Builder.preparePost;
import static com.proofpoint.http.client.Request.Builder.preparePut;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static java.lang.Thread.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.noContent;
import static org.testng.Assert.assertEquals;

public class TestTimingFilter
{
    private static TestingTicker ticker = new TestingTicker();
    private final HttpClient client = new JettyHttpClient();

    private LifeCycleManager lifeCycleManager;
    private TestingHttpServer server;
    private ReportingTester reportingTester;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        new TestingNodeModule(),
                        new TestingHttpServerModule(),
                        new JsonModule(),
                        Modules.override(explicitJaxrsModule())
                                .with(binder -> binder.bind(Ticker.class).annotatedWith(JaxrsTicker.class).toInstance(ticker)),
                        new TestingReportingModule(),
                        new TestingMBeanModule(),
                        binder -> jaxrsBinder(binder).bind(TestingTimingResource.class)
                )
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingHttpServer.class);
        reportingTester = injector.getInstance(ReportingTester.class);
    }

    @AfterMethod(alwaysRun = true)
    public void teardown()
            throws Exception
    {
        if (lifeCycleManager != null) {
            lifeCycleManager.stop();
        }
    }

    @AfterClass(alwaysRun = true)
    public void teardownClass()
    {
        Closeables.closeQuietly(client);
    }

    @Test
    public void testGet()
            throws Exception
    {
        assertTimingMeasurement(prepareGet(), "testGet", 1.0);
    }

    @Test
    public void testPut()
            throws Exception
    {
        assertTimingMeasurement(preparePut(), "testPut", 2.0);
    }

    @Test
    public void testPost()
            throws Exception
    {
        assertTimingMeasurement(preparePost(), "testPost", 3.0);
    }

    @Test
    public void testDelete()
            throws Exception
    {
        assertTimingMeasurement(prepareDelete(), "testDelete", 9.0);
    }

    private void assertTimingMeasurement(Builder requestBuilder, String expectedMethod, double expectedValue)
    {
        StatusResponse response = client.execute(
                requestBuilder.setUri(uriFor("/testing")).build(),
                createStatusResponseHandler());

        assertEquals(response.getStatusCode(), NO_CONTENT.getStatusCode());

        Table<String, Map<String, String>, Object> data = reportingTester.collectData();

        assertEquals(data.row("TestingTimingResource.RequestTime.Max"), ImmutableMap.of(ImmutableMap.of("method", expectedMethod, "responseCode", "204"), expectedValue));
    }

    private URI uriFor(String path)
    {
        return server.getBaseUrl().resolve(path);
    }

    @Path("/testing")
    public static class TestingTimingResource
        extends TestingTimingSuperclass
    {
        @GET
        public void testGet() {
            ticker.elapseTime(1, SECONDS);
        }

        @DELETE
        public void testDelete(@Suspended AsyncResponse asyncResponse)
        {
            ticker.elapseTime(4, SECONDS);
            new Thread(() -> {
                try {
                    sleep(10);
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                ticker.elapseTime(5, SECONDS);
                asyncResponse.resume(noContent().build());
            }).start();
        }


    }

    public static class TestingTimingSuperclass
        implements TestingTimingInterface
    {
        @PUT
        public void testPut() {
            ticker.elapseTime(2, SECONDS);
        }

        @Override
        public void testPost()
        {
            ticker.elapseTime(3, SECONDS);
        }
    }

    public interface TestingTimingInterface
    {
        @POST
        void testPost();
    }
}
