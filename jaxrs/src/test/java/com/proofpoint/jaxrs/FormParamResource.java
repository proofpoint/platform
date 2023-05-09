package com.proofpoint.jaxrs;

import com.google.common.base.Strings;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/test")
public class FormParamResource
{
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response post(@FormParam("testParam") String testParam)
    {
        if (Strings.isNullOrEmpty(testParam)) {
            return Response.ok("Empty param").build();
        }
        return Response.ok(testParam).build();
    }
}
