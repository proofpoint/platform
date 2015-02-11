package com.proofpoint.jaxrs;

import com.google.common.base.Supplier;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.StringResponseHandler.StringResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.http.server.testing.TestingHttpServerModule;
import com.proofpoint.json.JsonModule;
import com.proofpoint.node.ApplicationNameModule;
import com.proofpoint.node.testing.TestingNodeModule;
import com.proofpoint.reporting.ReportingModule;
import com.proofpoint.testing.Closeables;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.inject.Inject;
import javax.management.MBeanServer;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static com.proofpoint.jaxrs.JaxrsModule.explicitJaxrsModule;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class TestInjectedContext
{
    InjectedResource resource;
    TestingHttpServer server;
    JettyHttpClient client;
    private static final String INJECTED_MESSSAGE = "Hello, World!";

    @BeforeMethod
    public void setup()
            throws Exception
    {
        resource = new InjectedResource();
        server = createServer(resource);

        client = new JettyHttpClient();

        server.start();
    }

    @AfterMethod
    public void teardown()
            throws Exception
    {
        try {
            if (server != null) {
                server.stop();
            }
        }
        catch (Throwable ignored) {
        }
        Closeables.closeQuietly(client);
    }

    @Test
    public void testInjectableProvider()
    {
        Request request = Request.builder()
                            .setUri(server.getBaseUrl().resolve("/injectedresource"))
                            .setMethod("GET")
                            .build();
        StringResponse response = client.execute(request, createStringResponseHandler());
        assertEquals(response.getStatusCode(), Status.OK.getStatusCode(), "Status code");
        assertTrue(response.getBody().contains(INJECTED_MESSSAGE), response.getBody());
    }

    private static TestingHttpServer createServer(final InjectedResource resource)
    {
        return Guice.createInjector(
                new ApplicationNameModule("test-application"),
                new TestingNodeModule(),
                explicitJaxrsModule(),
                new JsonModule(),
                new ReportingModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        binder.bind(MBeanServer.class).toInstance(mock(MBeanServer.class));
                    }
                },
                new TestingHttpServerModule(),
                new Module()
                {
                    @Override
                    public void configure(Binder binder)
                    {
                        jaxrsBinder(binder).bindInstance(resource);
                        jaxrsBinder(binder).bindInjectionProvider(InjectedContextObject.class).to(InjectedContextObjectSupplier.class);
                    }
                }).getInstance(TestingHttpServer.class);
    }

    @Path("/injectedresource")
    public static class InjectedResource
    {
        @GET
        public Response getContextInjectable(@Context InjectedContextObject injectedContextObject)
        {
            return Response.ok(injectedContextObject.getMessage()).build();
        }
    }

    public static class InjectedContextObjectSupplier
        implements Supplier<InjectedContextObject>
    {
        @Override
        public InjectedContextObject get()
        {
            return new InjectedContextObject();
        }
    }

    public static class InjectedContextObject
    {
        @Inject
        private HttpServletRequest request;

        public String getMessage()
        {
            return String.format("%s %s", request.getServletPath(), INJECTED_MESSSAGE);
        }
    }
}
