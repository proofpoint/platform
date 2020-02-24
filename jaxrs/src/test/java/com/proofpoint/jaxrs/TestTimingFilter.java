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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import com.google.inject.CreationException;
import com.google.inject.Injector;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request.Builder;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.Key;
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
import javax.ws.rs.QueryParam;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static com.google.inject.multibindings.OptionalBinder.newOptionalBinder;
import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.Request.Builder.prepareDelete;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.Request.Builder.preparePost;
import static com.proofpoint.http.client.Request.Builder.preparePut;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static com.proofpoint.jaxrs.TimingFilter.TAGS_KEY;
import static java.lang.Thread.sleep;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.NO_CONTENT;
import static javax.ws.rs.core.Response.noContent;
import static org.testng.Assert.assertEquals;

public class TestTimingFilter
{
    private static final TestingTicker ticker = new TestingTicker();
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
                        explicitJaxrsModule(),
                        new TestingReportingModule(),
                        new TestingMBeanModule(),
                        binder -> {
                            jaxrsBinder(binder).bind(TestingTimingResource.class);
                            jaxrsBinder(binder).bind(TestingAnnotatedTimingResource.class);
                            newOptionalBinder(binder,
                                    com.google.inject.Key.get(Ticker.class, JaxrsTicker.class))
                                    .setBinding().toInstance(ticker);
                        }
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
    {
        assertTimingMeasurement(prepareGet(), "testGet", 1.0);
    }

    @Test
    public void testPut()
    {
        assertTimingMeasurement(preparePut(), "testPut", 2.0);
    }

    @Test
    public void testPost()
    {
        assertTimingMeasurement(preparePost(), "testPost", 3.0);
    }

    @Test
    public void testDelete()
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

        assertEquals(data.row("TestingTimingResource.RequestTime.Max"), ImmutableMap.of(ImmutableMap.of("method", expectedMethod, "responseCode", "204", "responseCodeFamily", "2"), expectedValue));
    }

    @Test
    public void testAnnotatedGet()
    {
        StatusResponse response = client.execute(
                prepareGet().setUri(uriFor("/annotated?param1=value1")).build(),
                createStatusResponseHandler());

        assertEquals(response.getStatusCode(), NO_CONTENT.getStatusCode());

        Table<String, Map<String, String>, Object> data = reportingTester.collectData();

        assertEquals(data.row("TestingAnnotatedTimingResource.RequestTime.Max"), ImmutableMap.of(ImmutableMap.of("method", "testGet", "responseCode", "204", "responseCodeFamily", "2", "tag", "value1"), 1.0));
    }

    @Test
    public void testAnnotatedPut()
    {
        StatusResponse response = client.execute(
                preparePut().setUri(uriFor("/annotated?param4=1")).build(),
                createStatusResponseHandler());

        assertEquals(response.getStatusCode(), NO_CONTENT.getStatusCode());

        Table<String, Map<String, String>, Object> data = reportingTester.collectData();

        assertEquals(data.row("TestingAnnotatedTimingResource.RequestTime.Max"), ImmutableMap.of(ImmutableMap.of("method", "testPut", "responseCode", "204", "responseCodeFamily", "2", "tag2", "1"), 2.0));
    }

    @Test
    public void testAnnotatedPost()
    {
        StatusResponse response = client.execute(
                preparePost().setUri(uriFor("/annotated?param1=1.5&param2=3.5&param3=9")).build(),
                createStatusResponseHandler());

        assertEquals(response.getStatusCode(), NO_CONTENT.getStatusCode());

        Table<String, Map<String, String>, Object> data = reportingTester.collectData();

        assertEquals(data.row("TestingAnnotatedTimingResource.RequestTime.Max"), ImmutableMap.of(
                ImmutableMap.<String, String>builder()
                        .put("method", "testPost")
                        .put("responseCode", "204")
                        .put("responseCodeFamily", "2")
                        .put("tag", "1.5")
                        .put("tag2", "3.5")
                        .put("tag3", "9")
                        .build(),
                3.0)
        );
    }


    @Test
    public void testAnnotatedDelete()
    {
        StatusResponse response = client.execute(
                prepareDelete().setUri(uriFor("/annotated?param3=true")).build(),
                createStatusResponseHandler());

        assertEquals(response.getStatusCode(), NO_CONTENT.getStatusCode());

        Table<String, Map<String, String>, Object> data = reportingTester.collectData();

        assertEquals(data.row("TestingAnnotatedTimingResource.RequestTime.Max"), ImmutableMap.of(ImmutableMap.of("method", "testDelete", "responseCode", "204", "responseCodeFamily", "2", "tag", "true"), 9.0));
    }

    @Test(expectedExceptions = CreationException.class,
            expectedExceptionsMessageRegExp = ".*Caused by: java\\.lang\\.RuntimeException: \"method\" tag name in @Key annotation on parameter of method.*testGet.*duplicates standard tag name.*")
    public void testDuplicateMethodTagThrowsException()
        throws Exception
    {
        bootstrapTest()
                        .withModules(
                                new TestingNodeModule(),
                                new TestingHttpServerModule(),
                                new JsonModule(),
                                explicitJaxrsModule(),
                                new TestingReportingModule(),
                                new TestingMBeanModule(),
                                binder -> {
                                    jaxrsBinder(binder).bind(MethodTaggedResource.class);
                                }
                        )
                        .initialize();
    }

    @Test(expectedExceptions = CreationException.class,
            expectedExceptionsMessageRegExp = ".*Caused by: java\\.lang\\.RuntimeException: \"responseCode\" tag name in @Key annotation on parameter of method.*testGet.*duplicates standard tag name.*")
    public void testDuplicateResponseCodeTagThrowsException()
        throws Exception
    {
        bootstrapTest()
                        .withModules(
                                new TestingNodeModule(),
                                new TestingHttpServerModule(),
                                new JsonModule(),
                                explicitJaxrsModule(),
                                new TestingReportingModule(),
                                new TestingMBeanModule(),
                                binder -> {
                                    jaxrsBinder(binder).bind(ResponseCodeTaggedResource.class);
                                }
                        )
                        .initialize();
    }

    @Test(expectedExceptions = CreationException.class,
            expectedExceptionsMessageRegExp = ".*Caused by: java\\.lang\\.RuntimeException: Duplicate \"foo\" tag name in @Key annotation on parameter of method.*testGet.*")
    public void testDuplicateTagsThrowsException()
        throws Exception
    {
        bootstrapTest()
                        .withModules(
                                new TestingNodeModule(),
                                new TestingHttpServerModule(),
                                new JsonModule(),
                                explicitJaxrsModule(),
                                new TestingReportingModule(),
                                new TestingMBeanModule(),
                                binder -> {
                                    jaxrsBinder(binder).bind(DuplicateTaggedResource.class);
                                }
                        )
                        .initialize();
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
            completeOnNewThread(asyncResponse);
        }
    }

    private static void completeOnNewThread(AsyncResponse asyncResponse)
    {
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


    @Path("/annotated")
    public static class TestingAnnotatedTimingResource
        extends TestingAnnotatedTimingSuperclass
    {
        @GET
        public void testGet(@Key("tag") @QueryParam("param1") String tagValue, @QueryParam("param2") String notTag,@Context ContainerRequestContext request) {
            request.setProperty(TAGS_KEY, ImmutableList.builder()
                    .add(Optional.ofNullable(tagValue))
                    .add(Optional.ofNullable(notTag))
                    .build());
            ticker.elapseTime(1, SECONDS);
        }

        @DELETE
        public void testDelete(@Suspended AsyncResponse asyncResponse, @Key("tag") @QueryParam("param3") boolean boolValue)
        {
            ticker.elapseTime(4, SECONDS);
            completeOnNewThread(asyncResponse);
        }
    }

    public static class TestingAnnotatedTimingSuperclass
    {
        @PUT
        public void testPut(@Key("tag") @QueryParam("param1") String tagValue, @Key("tag2") @QueryParam("param4") long longValue) {
            ticker.elapseTime(2, SECONDS);
        }

        // The TimingWrapper implementation doesn't follow annotations on interfaces
        @POST
        public void testPost(@Key("tag") @QueryParam("param1") double doubleValue, @Key("tag2") @QueryParam("param2") float floatValue, @Key("tag3") @QueryParam("param3") int intValue)
        {
            ticker.elapseTime(3, SECONDS);
        }
    }

    @Path("/")
    public static class MethodTaggedResource
    {
        @GET
        public void testGet(@Key("method") String param)
        {}
    }

    @Path("/")
    public static class ResponseCodeTaggedResource
    {
        @GET
        public void testGet(@Key("responseCode") String param)
        {}
    }

    @Path("/")
    public static class DuplicateTaggedResource
    {
        @GET
        public void testGet(@Key("foo") String param1, @Key("foo") String param2)
        {}
    }
}
