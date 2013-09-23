package com.proofpoint.jaxrs;

import com.google.common.base.Charsets;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.http.client.ApacheHttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.StaticBodyGenerator;
import com.proofpoint.http.client.StringResponseHandler.StringResponse;
import com.proofpoint.http.server.testing.TestingHttpServer;
import com.proofpoint.jaxrs.testing.MockFilterFactory;
import com.proofpoint.jaxrs.testing.TestResource;
import com.sun.jersey.spi.container.ResourceFilterFactory;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.http.client.StringResponseHandler.createStringResponseHandler;
import static com.proofpoint.jaxrs.util.HttpTestUtils.createServerWithFilter;
import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestJaxrsBinder
{
    JaxrsBinder jaxrsBinder;
    ResourceFilterFactory filterFactory;

    @BeforeTest
    public void setup ()
    {
        filterFactory = mock(ResourceFilterFactory.class);
    }

    @Test
    public void testInstantiation ()
    {
        Injector injector = Guice.createInjector(new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                jaxrsBinder = JaxrsBinder.jaxrsBinder(binder);
                jaxrsBinder.bindFilterToResource(filterFactory.getClass());
                binder.bind (filterFactory.getClass()).in(Scopes.SINGLETON);
            }
        });
        try {
            assertNotNull (injector.getInstance (filterFactory.getClass()));
        } catch (Exception e) {
            fail ("Failed to run test instantiation : " + e.getMessage());
        }
    }

    @Test
    public void testMultipleFilterFactories ()
    {
        final ResourceFilterFactory filterFactory1 = mock(ResourceFilterFactory.class);
        final ResourceFilterFactory filterFactory2 = mock(ResourceFilterFactory.class);

        Injector injector = Guice.createInjector(new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                jaxrsBinder = JaxrsBinder.jaxrsBinder(binder);
                jaxrsBinder.bindFilterToResource(filterFactory1.getClass());
                jaxrsBinder.bindFilterToResource(filterFactory2.getClass());
                binder.bind (filterFactory1.getClass()).in(Scopes.SINGLETON);
                binder.bind (filterFactory2.getClass()).in(Scopes.SINGLETON);
            }
        });
        try {
            assertNotNull (injector.getInstance (filterFactory1.getClass()));
            assertNotNull (injector.getInstance (filterFactory2.getClass()));
        } catch (Exception e) {
            fail ("Failed to run test instantiation : " + e.getMessage());
        }
    }

    @Test
    public void testServerInstantiation () throws Exception
    {
        TestResource resource = new TestResource();
        final ResourceFilterFactory filterFactory = new MockFilterFactory();

        TestingHttpServer server = createServerWithFilter(resource, filterFactory);
        ApacheHttpClient client = new ApacheHttpClient();
        server.start();

        Request request = prepareGet()
                .setUri(server.getBaseUrl())
                .setBodyGenerator(StaticBodyGenerator.createStaticBodyGenerator("", Charsets.US_ASCII))
                .build();

        StringResponse response = client.execute(request, createStringResponseHandler());
        assertEquals(response.getStatusCode(), 201);
        assertTrue (resource.getCalled());
    }
}
