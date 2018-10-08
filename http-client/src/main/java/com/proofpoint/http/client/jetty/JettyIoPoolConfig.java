package com.proofpoint.http.client.jetty;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigDescription;
import com.proofpoint.configuration.LegacyConfig;

import javax.validation.constraints.Min;

public class JettyIoPoolConfig
{
    private int maxThreads = 200;
    private int minThreads = 8;
    private int timeoutThreads = 1;
    private int timeoutConcurrency = 1;

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
    public int getTimeoutThreads()
    {
        return timeoutThreads;
    }

    @Config("http-client.timeout-threads")
    @ConfigDescription("Total number of timeout threads")
    public JettyIoPoolConfig setTimeoutThreads(int timeoutThreads)
    {
        this.timeoutThreads = timeoutThreads;
        return this;
    }

    @Min(1)
    public int getTimeoutConcurrency()
    {
        return timeoutConcurrency;
    }

    @Config("http-client.timeout-concurrency")
    @ConfigDescription("Number of concurrent locks for timeout")
    public JettyIoPoolConfig setTimeoutConcurrency(int timeoutConcurrency)
    {
        this.timeoutConcurrency = timeoutConcurrency;
        return this;
    }
}
