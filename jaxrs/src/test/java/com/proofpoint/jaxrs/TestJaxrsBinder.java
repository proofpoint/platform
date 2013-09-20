package com.proofpoint.jaxrs;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.jaxrs.testing.MockFilterFactory;
import org.testng.annotations.Test;

import static org.testng.Assert.fail;
import static org.testng.Assert.assertNotNull;

public class TestJaxrsBinder
{
    JaxrsBinder jaxrsBinder;

    @Test
    public void testInstantiation ()
    {
        Injector injector = Guice.createInjector(new Module()
        {
            @Override
            public void configure(Binder binder)
            {
                jaxrsBinder = JaxrsBinder.jaxrsBinder(binder);
                jaxrsBinder.bindFilterToResource(MockFilterFactory.class);
                binder.bind (MockFilterFactory.class).in(Scopes.SINGLETON);
            }
        });
        try {
            assertNotNull (injector.getInstance (MockFilterFactory.class));
        } catch (Exception e) {
            fail ("Failed to run test instantiation : " + e.getMessage());
        }
    }
}
