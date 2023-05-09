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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.management.MBeanServer;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.lang.String.format;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;

@Test(singleThreaded = true)
public class TestQueryParamExceptionMapper
{
    private static final String GET = "GET";

    private final HttpClient client = new JettyHttpClient();

    private LifeCycleManager lifeCycleManager;
    private TestingHttpServer server;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        createServer(new TestQueryParamResource());
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
    public void testGetWithValidQueryParamSucceeds()
    {
        StatusResponse response = client.execute(buildRequestWithQueryParam("123"), createStatusResponseHandler());
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
    }

    @Test
    public void testGetWithInvalidQueryParamReturnsBadRequest()
    {
        StringResponse response = client.execute(buildRequestWithQueryParam("string"), createStringResponseHandler());
        assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode());
        assertEquals(response.getHeader("Content-Type"), "text/plain");
    }

    private Request buildRequestWithQueryParam(String override)
    {
        return Request.builder().setUri(server.getBaseUrl().resolve(format("/?count=%s", override))).setMethod(GET).build();
    }

    private void createServer(final TestQueryParamResource resource)
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                new TestingNodeModule(),
                explicitJaxrsModule(),
                new JsonModule(),
                new ReportingModule(),
                binder -> binder.bind(MBeanServer.class).toInstance(mock(MBeanServer.class)),
                new TestingHttpServerModule(),
                binder -> jaxrsBinder(binder).bindInstance(resource))
                .initialize();
        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingHttpServer.class);
    }

    @Path("/")
    public static class TestQueryParamResource
    {
        @GET
        @Produces(APPLICATION_JSON)
        public Response get(@QueryParam("count") Integer count)
        {
            return Response.ok().build();
        }
    }

}
