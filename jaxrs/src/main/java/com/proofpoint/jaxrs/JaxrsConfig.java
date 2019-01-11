package com.proofpoint.jaxrs;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.DefunctConfig;

@DefunctConfig("jaxrs.query-params-as-form-params")
public class JaxrsConfig
{
    private int hstsMaxAge = 31536000;
    private boolean includeSubDomains = false;
    private boolean preload = false;

    @Config("jaxrs.hsts.max-age-seconds")
    public JaxrsConfig setHstsMaxAge(int hstsMaxAge)
    {
        this.hstsMaxAge = hstsMaxAge;
        return this;
    }

    public int getHstsMaxAge()
    {
        return hstsMaxAge;
    }

    @Config("jaxrs.hsts.include-sub-domains")
    public JaxrsConfig setIncludeSubDomains(boolean includeSubDomains)
    {
        this.includeSubDomains = includeSubDomains;
        return this;
    }

    public boolean isIncludeSubDomains()
    {
        return includeSubDomains;
    }

    @Config("jaxrs.hsts.preload")
    public JaxrsConfig setPreload(boolean preload)
    {
        this.preload = preload;
        return this;
    }

    public boolean isPreload()
    {
        return preload;
    }
}
