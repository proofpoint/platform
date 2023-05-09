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

import com.google.inject.Injector;
import com.google.inject.Module;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.discovery.client.StaticDiscoveryModule;
import com.proofpoint.discovery.client.announce.Announcer;
import com.proofpoint.discovery.client.testing.TestingDiscoveryModule;
import com.proofpoint.http.client.Request.Builder;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.server.testing.TestingAdminHttpServer;
import com.proofpoint.http.server.testing.TestingAdminHttpServerModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

import static com.google.inject.util.Modules.override;
import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.Request.Builder.preparePut;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static jakarta.ws.rs.core.Response.Status.NO_CONTENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.testng.Assert.assertEquals;

public class TestStopAnnouncingResource
        extends AbstractTestAuthorizedResource
{
    private Announcer announcer;
    private Module discoveryModule;

    @BeforeMethod
    public void setup()
    {
        announcer = mock(Announcer.class);
        discoveryModule = override(new TestingDiscoveryModule())
                .with(binder -> binder.bind(Announcer.class).toInstance(announcer));
    }

    @Override
    protected void createServer()
            throws Exception
    {
        Injector injector = bootstrapTest()
                .withModules(
                        new TestingNodeModule(),
                        new TestingAdminHttpServerModule(),
                        new JsonModule(),
                        explicitJaxrsModule(),
                        new ReportingModule(),
                        new TestingMBeanModule(),
                        discoveryModule,
                        jmxHttpModule
                )
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingAdminHttpServer.class);
    }

    @Override
    protected Builder createRequestBuilder()
    {
        return preparePut()
                .setUri(uriFor("/admin/stop-announcing"));
    }

    @Override
    protected void assertActionTaken(Response response)
    {
        assertEquals(response.getStatusCode(), NO_CONTENT.getStatusCode());
        verify(announcer).destroy();
        verifyNoMoreInteractions(announcer);
    }

    @Override
    protected void assertActionNotTaken()
    {
        verifyNoMoreInteractions(announcer);
    }

    @Test
    public void testStaticDiscovery()
            throws Exception
    {
        discoveryModule = new StaticDiscoveryModule();
        createServer();

        StatusResponse response = client.execute(
                createRequestBuilder()
                        .addHeader("Authorization", "authHeader")
                        .build(),
                createStatusResponseHandler());

        assertEquals(response.getStatusCode(), NO_CONTENT.getStatusCode());
    }
}
