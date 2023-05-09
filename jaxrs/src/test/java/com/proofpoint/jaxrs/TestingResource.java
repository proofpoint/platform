package com.proofpoint.jaxrs;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;

@Path("/")
public class TestingResource
{
    private volatile boolean post;
    private volatile boolean put;
    private volatile boolean get;
    private volatile boolean delete;

    @POST
    public void post()
    {
        post = true;
    }

    @GET
    public boolean get()
    {
        get = true;
        return true;
    }

    @DELETE
    public void delete()
    {
        delete = true;
    }

    @PUT
    public void put()
    {
        put = true;
    }

    public boolean postCalled()
    {
        return post;
    }

    public boolean putCalled()
    {
        return put;
    }

    public boolean getCalled()
    {
        return get;
    }

    public boolean deleteCalled()
    {
        return delete;
    }
}
