package com.proofpoint.http.client.jetty;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.LegacyConfig;

import javax.validation.constraints.Min;

public class JettyIoPoolConfig
{
    private int maxThreads = 200;
    private int minThreads = 8;
    private int bodyGeneratorMaxThreads = 200;
    private int bodyGeneratorMinThreads = 1;

    @Min(1)
    public int getMaxThreads()
    {
        return maxThreads;
    }

    @Config("http-client.max-threads")
    @LegacyConfig("http-client.threads")
    public JettyIoPoolConfig setMaxThreads(int maxThreads)
    {
        this.maxThreads = maxThreads;
        return this;
    }

    @Min(1)
    public int getMinThreads()
    {
        return minThreads;
    }

    @Config("http-client.min-threads")
    public JettyIoPoolConfig setMinThreads(int minThreads)
    {
        this.minThreads = minThreads;
        return this;
    }

    @Min(1)
    public int getBodyGeneratorMaxThreads()
    {
        return bodyGeneratorMaxThreads;
    }

    @Config("http-client.bodygenerator.max-threads")
    public JettyIoPoolConfig setBodyGeneratorMaxThreads(int bodyGeneratorMaxThreads)
    {
        this.bodyGeneratorMaxThreads = bodyGeneratorMaxThreads;
        return this;
    }

    @Min(1)
    public int getBodyGeneratorMinThreads()
    {
        return bodyGeneratorMinThreads;
    }

    @Config("http-client.bodygenerator.min-threads")
    public JettyIoPoolConfig setBodyGeneratorMinThreads(int bodyGeneratorMinThreads)
    {
        this.bodyGeneratorMinThreads = bodyGeneratorMinThreads;
        return this;
    }
}
