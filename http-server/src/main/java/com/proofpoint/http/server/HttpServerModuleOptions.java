package com.proofpoint.http.server;

public class HttpServerModuleOptions
{
    private boolean allowAmbiguousUris = false;
    private boolean enableVirtualThreads = false;

    public boolean isAllowAmbiguousUris()
    {
        return allowAmbiguousUris;
    }

    public void setAllowAmbiguousUris()
    {
        this.allowAmbiguousUris = true;
    }

    public boolean isEnableVirtualThreads()
    {
        return enableVirtualThreads;
    }

    public void setEnableVirtualThreads()
    {
        this.enableVirtualThreads = true;
    }
}
