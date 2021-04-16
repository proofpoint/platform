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
package com.proofpoint.jmx;

import com.google.common.collect.ImmutableMap;
import com.google.common.reflect.TypeToken;
import com.google.inject.Injector;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingAdminHttpServer;
import com.proofpoint.http.server.testing.TestingAdminHttpServerModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.NodeConfig;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.testing.Closeables;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Map;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.JsonResponseHandler.createJsonResponseHandler;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static com.proofpoint.json.JsonCodec.jsonCodec;
import static org.testng.Assert.assertEquals;

public class TestVersionResource
{
    private final HttpClient client = new JettyHttpClient();

    private LifeCycleManager lifeCycleManager;
    private TestingAdminHttpServer server;

    @BeforeMethod
    public void setup()
    {
        lifeCycleManager = null;
        server = null;
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
    public void testGetVersion()
            throws Exception
    {
        Map<String, String> response = queryServer("test-application", "1.0", "2.0");

        assertEquals(response, ImmutableMap.of(
                "application", "test-application",
                "applicationVersion", "1.0",
                "platformVersion", "2.0"
        ));
    }

    @Test
    public void testNoApplicationVersion()
            throws Exception
    {
        Map<String, String> response = queryServer("test-application", "", "2.0");

        assertEquals(response, ImmutableMap.of(
                "application", "test-application",
                "platformVersion", "2.0"
        ));
    }


    @Test
    public void testNoPlatformVersion()
            throws Exception
    {
        Map<String, String> response = queryServer("test-application", "1.0", "");

        assertEquals(response, ImmutableMap.of(
                "application", "test-application",
                "applicationVersion", "1.0"
        ));
    }

    private Map<String, String> queryServer(String application, String applicationVersion, String platformVersion)
            throws Exception
    {
        NodeConfig nodeConfig = new NodeConfig()
                .setEnvironment("testing")
                .setNodeInternalIp(getV4Localhost())
                .setNodeBindIp(getV4Localhost());
        NodeInfo nodeInfo = new NodeInfo(application, applicationVersion, platformVersion, nodeConfig);
        Injector injector = bootstrapTest()
                .withModules(
                        binder -> binder.bind(NodeInfo.class).toInstance(nodeInfo),
                        new TestingAdminHttpServerModule(),
                        new JsonModule(),
                        explicitJaxrsModule(),
                        new ReportingModule(),
                        new TestingMBeanModule(),
                        new JmxHttpModule(),
                        new TestingDiscoveryModule()
                )
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingAdminHttpServer.class);
        return client.execute(
                    prepareGet().setUri(uriFor("/admin/version")).build(),
                    createJsonResponseHandler(jsonCodec(new TypeToken<Map<String, String>>()
                    {
                    })));
    }

    private URI uriFor(String path)
    {
        return server.getBaseUrl().resolve(path);
    }

    @SuppressWarnings("ImplicitNumericConversion")
    private static InetAddress getV4Localhost()
    {
        try {
            return InetAddress.getByAddress("localhost", new byte[] {127, 0, 0, 1});
        }
        catch (UnknownHostException e) {
            throw new AssertionError("Could not create localhost address");
        }
    }
}
