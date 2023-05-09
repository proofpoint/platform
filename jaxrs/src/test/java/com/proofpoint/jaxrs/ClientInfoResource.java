package com.proofpoint.jaxrs;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;

@Path("/test")
public class ClientInfoResource
{
    @GET
    public String get(@Context ClientInfo clientInfo)
    {
        return clientInfo.getAddress();
    }
}
