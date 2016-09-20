package com.proofpoint.jaxrs;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;

@Path("/test")
public class ClientInfoResource
{
    @GET
    public String get(@Context ClientInfo clientInfo)
    {
        return clientInfo.getAddress();
    }
}
