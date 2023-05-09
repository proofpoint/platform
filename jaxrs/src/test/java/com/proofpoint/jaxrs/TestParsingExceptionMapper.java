/*
* Copyright 2015 Proofpoint, Inc.
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

import com.google.inject.Injector;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.client.StringResponseHandler.StringResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.testing.Closeables;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response.Status;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static com.proofpoint.testing.Assertions.assertContains;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.testng.Assert.assertEquals;

@Test(singleThreaded = true)
public class TestParsingExceptionMapper
{
    private static final String GET = "GET";

    private final HttpClient client = new JettyHttpClient();

    private LifeCycleManager lifeCycleManager;
    private TestingHttpServer server;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        new TestingNodeModule(),
                        explicitJaxrsModule(),
                        new JsonModule(),
                        new ReportingModule(),
                        new TestingHttpServerModule(),
                        binder -> jaxrsBinder(binder).bind(TestParsingResource.class)
                )
                .initialize();
        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingHttpServer.class);
    }

    @AfterMethod
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
    public void testGetWithValidBodySucceeds()
    {
        StatusResponse response = client.execute(buildRequestWithBody("/integer", "123"), createStatusResponseHandler());
        assertEquals(response.getStatusCode(), Status.NO_CONTENT.getStatusCode());
    }

    @Test
    public void testGetWithInvalidBodyBadRequest()
    {
        StringResponse response = client.execute(buildRequestWithBody("/integer", "string"), createStringResponseHandler());
        assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode());
        assertEquals(response.getHeader("Content-Type"), "text/plain");
        assertContains(response.getBody(), "Invalid json line 1 column"); // Column number is inaccurate
    }

    @Test
    public void testGetWithInvalidListBadRequest()
    {
        StringResponse response = client.execute(buildRequestWithBody("/list", "{}"), createStringResponseHandler());
        assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode());
        assertEquals(response.getHeader("Content-Type"), "text/plain");
        assertEquals(response.getBody(), "Invalid json line 1 column 1 field ?");
    }

    @Test
    public void testGetWithInvalidNestedBadRequest()
    {
        StringResponse response = client.execute(buildRequestWithBody("/nested", "{\"foo\": [\"bar\", \"baz\" }"), createStringResponseHandler());
        assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode());
        assertEquals(response.getHeader("Content-Type"), "text/plain");
        assertEquals(response.getBody(), "Invalid json line 1 column 24 field foo.[2]");
    }

    private Request buildRequestWithBody(String resource, String body)
    {
        return Request.builder()
                .setUri(server.getBaseUrl().resolve(resource))
                .setHeader("Content-Type", "application/json")
                .setBodySource(createStaticBodyGenerator(body, StandardCharsets.UTF_8))
                .setMethod(GET)
                .build();
    }

    @Path("/")
    public static class TestParsingResource
    {
        @GET
        @Path("/integer")
        @Produces(APPLICATION_JSON)
        public void getInteger(Integer count)
        {
        }

        @GET
        @Path("/list")
        @Produces(APPLICATION_JSON)
        public void getList(List<Object> list)
        {
        }

        @GET
        @Path("/nested")
        @Produces(APPLICATION_JSON)
        public void getList(Map<String, List<String>> nested)
        {
        }
    }

}
