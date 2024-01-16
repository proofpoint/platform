package com.proofpoint.http.server;

public class HttpServerModuleOptions
{
    private boolean allowAmbiguousUris = false;

    public boolean isAllowAmbiguousUris()
    {
        return allowAmbiguousUris;
    }

    public void setAllowAmbiguousUris()
    {
        this.allowAmbiguousUris = true;
    }
}
