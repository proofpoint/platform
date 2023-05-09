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
package com.proofpoint.reporting;

import com.google.inject.Injector;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.StringResponseHandler.StringResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.testing.Closeables;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

import java.net.URI;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static com.proofpoint.testing.Assertions.assertContains;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.OK;
import static org.testng.Assert.assertEquals;

public class TestLivenessResource
{
    private final HttpClient client = new JettyHttpClient();

    private LifeCycleManager lifeCycleManager;
    private TestingHttpServer server;
    private HealthExporter healthExporter;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        new TestingNodeModule(),
                        new TestingHttpServerModule(),
                        new TestingDiscoveryModule(),
                        new JsonModule(),
                        explicitJaxrsModule(),
                        new ReportingModule(),
                        new TestingMBeanModule(),
                        new ReportingModule()
                )
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingHttpServer.class);
        healthExporter = injector.getInstance(HealthExporter.class);
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
    public void testNothingExported()
    {
        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/liveness")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), OK.getStatusCode());
        assertEquals(response.getHeader(CONTENT_TYPE), "text/plain");
        assertEquals(response.getBody(), "OK");
    }

    @Test
    public void testStatusOK()
            throws Exception
    {
        healthExporter.export(null, new Object() {
            @HealthCheckRemoveFromRotation("Check one")
            public Object getCheckOne()
            {
                return null;
            }
        });
        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/liveness")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), OK.getStatusCode());
        assertEquals(response.getHeader(CONTENT_TYPE), "text/plain");
        assertEquals(response.getBody(), "OK");
    }

    @Test
    public void testIgnoresHealthCheckRemoveFromRotation()
            throws Exception
    {
        healthExporter.export(null, new Object() {
            @HealthCheckRemoveFromRotation("Check one")
            public String getCheckOne()
            {
                return "failed";
            }
        });
        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/liveness")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), OK.getStatusCode());
        assertEquals(response.getHeader(CONTENT_TYPE), "text/plain");
        assertEquals(response.getBody(), "OK");
    }

    @Test
    public void testFailed()
            throws Exception
    {
        healthExporter.export(null, new Object() {
            @HealthCheckRestartDesired("Check one")
            public String getCheckOne()
            {
                return "failed";
            }

            @HealthCheckRestartDesired("Check two")
            public String getCheckTwo()
            {
                return null;
            }
        });
        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/liveness")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), INTERNAL_SERVER_ERROR.getStatusCode());
        assertEquals(response.getHeader(CONTENT_TYPE), "text/plain");
        assertEquals(response.getBody(), "failed\n");
    }

    @Test
    public void testMultipleFailed()
            throws Exception
    {
        healthExporter.export(null, new Object() {
            @HealthCheckRestartDesired("Check one")
            public String getCheckOne()
            {
                return "failed";
            }

            @HealthCheckRestartDesired("Check two")
            public Object getCheckTwo()
            {
                return new Object() {
                    @Override
                    public String toString() {
                        return "second failure";
                    }
                };
            }
        });
        StringResponse response = client.execute(
                prepareGet().setUri(uriFor("/liveness")).build(),
                createStringResponseHandler());

        assertEquals(response.getStatusCode(), INTERNAL_SERVER_ERROR.getStatusCode());
        assertEquals(response.getHeader(CONTENT_TYPE), "text/plain");
        assertContains(response.getBody(), "failed\n");
        assertContains(response.getBody(), "second failure\n");
    }

    private URI uriFor(String path)
    {
        return server.getBaseUrl().resolve(path);
    }
}
