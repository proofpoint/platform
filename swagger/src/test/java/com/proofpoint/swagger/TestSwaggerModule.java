package com.proofpoint.swagger;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.proofpoint.bootstrap.LifeCycleManager;

import com.proofpoint.http.client.FullJsonResponseHandler.JsonResponse;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingAdminHttpServer;


import com.proofpoint.json.JsonCodec;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.swagger.testapi.TestingResource;
import com.proofpoint.testing.Closeables;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static com.proofpoint.http.client.FullJsonResponseHandler.createFullJsonResponseHandler;
import static com.proofpoint.http.server.testing.TestingAdminHttpServerModule.initializesMainServletTestingAdminHttpServerModule;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static com.proofpoint.json.JsonCodec.mapJsonCodec;


import java.net.URI;
import java.util.Map;

import static com.proofpoint.bootstrap.Bootstrap.bootstrapTest;
import static com.proofpoint.http.client.Request.Builder.prepareGet;

import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static org.testng.Assert.assertNotNull;


public class TestSwaggerModule
{
    private final HttpClient client = new JettyHttpClient();
    private static final JsonCodec<Map<String, Object>> MAP_CODEC = mapJsonCodec(String.class, Object.class);

    private LifeCycleManager lifeCycleManager;
    private TestingAdminHttpServer server;

    @BeforeMethod
    public void setup()
            throws Exception
    {
        createServer(binder -> jaxrsBinder(binder).bind(TestingResource.class));
    }

    private void createServer(Module module)
            throws Exception
    {
        ImmutableMap<String, String> configMap = ImmutableMap.<String, String>builder().put("swagger.api.packages", "com.proofpoint.swagger.testapi").build();
        Injector injector = bootstrapTest()
                .withModules(
                        new TestingNodeModule(),
                        explicitJaxrsModule(),
                        initializesMainServletTestingAdminHttpServerModule(),
                        new JsonModule(),
                        new ReportingModule(),
                        new SwaggerModule(),
                        module)
                .withApplicationDefaults(configMap).initialize();
        lifeCycleManager = injector.getInstance(LifeCycleManager.class);
        server = injector.getInstance(TestingAdminHttpServer.class);
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
    public void testSwaggerJson()
    {
        JsonResponse<Map<String, Object>> response = client.execute(
                prepareGet().setUri(uriForOpenJSon("/openapi.json")).build(),
                createFullJsonResponseHandler(MAP_CODEC));
        assertNotNull(response.getResponseBody());

    }

    @Test
    public void testSwaggerYaml()
    {
        JsonResponse<Map<String, Object>> response = client.execute(
                prepareGet().setUri(uriForOpenJSon("/openapi.yaml")).build(),
                createFullJsonResponseHandler(MAP_CODEC));
        assertNotNull(response.getResponseBody());
    }


    private URI uriForOpenJSon(String specLocation)
    {
        return server.getBaseUrl().resolve(specLocation);
    }
}