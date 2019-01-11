package com.proofpoint.jaxrs;


import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Path("/test")
public class HSTSResource
{
    @GET
    @Path("/nohsts")
    public Response nohsts()
    {
        return Response.ok().build();
    }

    @GET
    @Path("/hsts")
    @HSTS
    public Response hsts()
    {
        return Response.ok().build();
    }
}
