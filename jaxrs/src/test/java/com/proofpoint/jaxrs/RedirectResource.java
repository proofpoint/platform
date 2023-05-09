package com.proofpoint.jaxrs;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;

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
