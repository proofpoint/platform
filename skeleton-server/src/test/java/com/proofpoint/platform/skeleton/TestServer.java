package com.proofpoint.platform.skeleton;

import com.google.inject.Injector;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.jaxrs.JaxrsModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.testing.Closeables;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.weakref.jmx.testing.TestingMBeanModule;

import java.net.URI;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static jakarta.ws.rs.core.Response.Status.NOT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;

public class TestServer
{
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
                        new TestingHttpServerModule(),
                        new JsonModule(),
                        new JaxrsModule(),
                        new ReportingModule(),
                        new TestingMBeanModule(),
                        new MainModule()
                )
                .initialize();

        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingHttpServer.class);
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
    public void testNothing()
    {
        StatusResponse response = client.execute(
                prepareGet().setUri(uriFor("/nothing")).build(),
                createStatusResponseHandler());

        assertThat(response.getStatusCode()).isEqualTo(NOT_FOUND.getStatusCode());
    }

    private URI uriFor(String path)
    {
        return server.getBaseUrl().resolve(path);
    }
}
