package com.proofpoint.jaxrs;

import com.google.common.base.Strings;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

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
