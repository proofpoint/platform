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
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.management.MBeanServer;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import static com.google.common.base.Charsets.UTF_8;
import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.StaticBodyGenerator.createStaticBodyGenerator;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.mockito.Mockito.mock;
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
        createServer(new TestParsingResource());
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
            throws Exception
    {
        StatusResponse response = client.execute(buildRequestWithBody("123"), createStatusResponseHandler());
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode());
    }

    @Test
    public void testGetWithInvalidBodyBadRequest()
            throws Exception
    {
        StringResponse response = client.execute(buildRequestWithBody("string"), createStringResponseHandler());
        assertEquals(response.getStatusCode(), Status.BAD_REQUEST.getStatusCode());
        assertEquals(response.getHeader("Content-Type"), "text/plain");
    }

    private Request buildRequestWithBody(String override)
    {
        return Request.builder()
                .setUri(server.getBaseUrl())
                .setHeader("Content-Type", "application/json")
                .setBodySource(createStaticBodyGenerator(override, UTF_8))
                .setMethod(GET)
                .build();
    }

    private void createServer(final TestParsingResource resource)
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
    public class TestParsingResource
    {
        @GET
        @Produces(APPLICATION_JSON)
        public Response get(Integer count)
        {
            return Response.ok().build();
        }
    }

}
