package com.proofpoint.experimental.statusresource;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

public class StatusResourceModule
    extends AbstractModule
{
    @Override
    protected void configure()
    {
        bind(StatusResource.class).in(Scopes.SINGLETON);
    }
}
