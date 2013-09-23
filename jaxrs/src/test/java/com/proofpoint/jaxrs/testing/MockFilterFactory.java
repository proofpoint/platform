package com.proofpoint.jaxrs.testing;

import com.sun.jersey.api.model.AbstractMethod;
import com.sun.jersey.spi.container.ResourceFilter;
import com.sun.jersey.spi.container.ResourceFilterFactory;

import java.util.List;

import static com.proofpoint.jaxrs.util.HttpTestUtils.getPassThroughResourceFilters;

public class MockFilterFactory implements ResourceFilterFactory
{
    @Override
    public List<ResourceFilter> create(AbstractMethod am)
    {
        return getPassThroughResourceFilters();
    }
}
