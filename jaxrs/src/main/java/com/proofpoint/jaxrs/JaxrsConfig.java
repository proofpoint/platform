package com.proofpoint.jaxrs;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.DefunctConfig;
import com.proofpoint.units.Duration;
import com.proofpoint.units.MinDuration;

import javax.annotation.Nullable;

@DefunctConfig("jaxrs.query-params-as-form-params")
public class JaxrsConfig
{
    private Duration hstsMaxAge = null;
    private boolean includeSubDomains = false;
    private boolean preload = false;
    private boolean overrideMethodFilter = true;

    @Config("jaxrs.hsts.max-age")
    public JaxrsConfig setHstsMaxAge(Duration hstsMaxAge)
    {
        this.hstsMaxAge = hstsMaxAge;
        return this;
    }

    @Nullable
    @MinDuration(value = "1m", message = "must be greater than or equal to 1m")
    public Duration getHstsMaxAge()
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

    @Config("testing.jaxrs.override-method-filter")
    public JaxrsConfig setOverrideMethodFilter(boolean overrideMethodFilter)
    {
        this.overrideMethodFilter = overrideMethodFilter;
        return this;
    }

    public boolean isOverrideMethodFilter()
    {
        return overrideMethodFilter;
    }
}
