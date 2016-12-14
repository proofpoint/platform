package com.proofpoint.jaxrs;

import com.proofpoint.configuration.Config;

public class JaxrsConfig
{
    private boolean queryParamsAsFormParams = false;

    @Config("jaxrs.query-params-as-form-params")
    @Deprecated
    public JaxrsConfig setQueryParamsAsFormParams(boolean queryParamsAsFormParams)
    {
        this.queryParamsAsFormParams = queryParamsAsFormParams;
        return this;
    }

    @Deprecated
    public boolean isQueryParamsAsFormParams()
    {
        return queryParamsAsFormParams;
    }
}
