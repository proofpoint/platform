package com.proofpoint.jaxrs;

import com.google.inject.Binder;
import com.proofpoint.http.server.TheServlet;
import com.sun.jersey.spi.container.ResourceFilterFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.inject.multibindings.MapBinder.newMapBinder;

public class JaxrsBinder
{
    public static final String RESOURCE_FILTERS = "com.sun.jersey.spi.container.ResourceFilters";
    private final Binder binder;

    private JaxrsBinder(Binder binder)
    {
        this.binder = checkNotNull (binder, "binder cannot be null");
    }

    public static JaxrsBinder jaxrsBinder(Binder binder)
    {
        return new JaxrsBinder(binder);
    }

    public void bindFilterToResource (Class<? extends ResourceFilterFactory> filterFactoryClass)
    {
        checkNotNull(filterFactoryClass, "Resource Filter Factory Class cannot be null");
        newMapBinder(binder, String.class, String.class, TheServlet.class)
                .addBinding(RESOURCE_FILTERS)
                .toInstance(filterFactoryClass.getName());
    }
}
