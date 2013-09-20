package com.proofpoint.jaxrs.testing;

import com.sun.jersey.api.model.AbstractMethod;
import com.sun.jersey.spi.container.ResourceFilter;
import com.sun.jersey.spi.container.ResourceFilterFactory;

import java.util.ArrayList;
import java.util.List;

public class MockFilterFactory implements ResourceFilterFactory
{
    @Override
    public List<ResourceFilter> create(AbstractMethod am)
    {
        return new ArrayList<ResourceFilter>();

    }
}
