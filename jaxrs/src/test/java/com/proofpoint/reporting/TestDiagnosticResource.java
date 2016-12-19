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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.Injector;
import com.proofpoint.bootstrap.Bootstrap;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingAdminHttpServer;
import com.proofpoint.http.server.testing.TestingAdminHttpServerModule;
import com.proofpoint.json.JsonCodec;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.testing.Closeables;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.internal.guava.collect.ImmutableMap;
import org.weakref.jmx.testing.TestingMBeanModule;

import java.net.URI;
import java.util.Map;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapApplication;
import static com.proofpoint.http.client.JsonResponseHandler.createJsonResponseHandler;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static com.proofpoint.json.JsonCodec.mapJsonCodec;
import static javax.ws.rs.core.Response.Status.OK;
import static org.testng.Assert.assertEquals;

public class TestDiagnosticResource
{
    private final HttpClient client = new JettyHttpClient();
    private final JsonCodec<Map<String, Object>> mapCodec = mapJsonCodec(String.class, Object.class);

    private LifeCycleManager lifeCycleManager;
    private TestingAdminHttpServer server;
    private DiagnosticExporter diagnosticExporter;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        Bootstrap app = bootstrapApplication("test-application")
                .doNotInitializeLogging()
                .withModules(
                        new TestingNodeModule(),
                        new TestingAdminHttpServerModule(),
                        new TestingDiscoveryModule(),
                        new JsonModule(),
                        explicitJaxrsModule(),
                        new ReportingModule(),
                        new TestingMBeanModule()
                )
                .quiet();

        Injector injector = app
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingAdminHttpServer.class);
        diagnosticExporter = injector.getInstance(DiagnosticExporter.class);
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
            throws Exception
    {
        Object actual = client.execute(
                prepareGet().setUri(uriFor("/admin/diagnostic")).build(),
                createJsonResponseHandler(mapCodec, OK.getStatusCode()));

        assertEquals(actual, ImmutableMap.of());
    }

    @Test
    public void testSingle()
            throws Exception
    {
        diagnosticExporter.export(new Object() {
            @Diagnostic
            public DiagnosticData getCheckOne()
            {
                return new DiagnosticData();
            }
        }, "ExportedPrefix");

        Object actual = client.execute(
                prepareGet().setUri(uriFor("/admin/diagnostic")).build(),
                createJsonResponseHandler(mapCodec, OK.getStatusCode()));

        assertEquals(actual, ImmutableMap.of("ExportedPrefix.CheckOne", ImmutableMap.of("key1", "value1")));
    }

    @Test
    public void testDouble()
            throws Exception
    {
        diagnosticExporter.export(new Object() {
            @Diagnostic
            public DiagnosticData getCheckOne()
            {
                return new DiagnosticData();
            }
        }, "ExportedPrefix");
        diagnosticExporter.export(new Object() {
            @Diagnostic
            public DiagnosticData getCheckTwo()
            {
                return new DiagnosticData();
            }
        }, "Second");

        Object actual = client.execute(
                prepareGet().setUri(uriFor("/admin/diagnostic")).build(),
                createJsonResponseHandler(mapCodec, OK.getStatusCode()));

        assertEquals(actual, ImmutableMap.of(
                "ExportedPrefix.CheckOne", ImmutableMap.of("key1", "value1"),
                "Second.CheckTwo", ImmutableMap.of("key1", "value1")
        ));
    }


    private URI uriFor(String path)
    {
        return server.getBaseUrl().resolve(path);
    }

    private static class DiagnosticData
    {
        @JsonProperty
        public String getKey1() {
            return "value1";
        }
    }
}
