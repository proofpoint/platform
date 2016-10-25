package com.proofpoint.jaxrs;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Path("/test")
public class RedirectResource
{
    @GET
    public Response get() {
        return Response.status(302)
                .header("Location", "https://maps.example.com/maps?q=Dirtt&#43;Environmental&#43;Solutions&#43;Ltd")
                .build();
    }
}
