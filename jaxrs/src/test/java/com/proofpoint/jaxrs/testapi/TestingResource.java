package com.proofpoint.jaxrs.testapi;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;

@Path("/")
@Tag(name = "TestingResource")
public class TestingResource
{

    @POST
    @Operation(summary = "Testing POST request")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "SUCESSFUL"),
            @ApiResponse(responseCode = "400", description = "One or more query parameter(s) is null or empty"),
            @ApiResponse(responseCode = "409", description = "State of the resource doesn't permit request."),
            @ApiResponse(responseCode = "503", description = "Failed to process")})
    public void post()
    {
    }

    @GET
    @Operation(summary = "Testing GET request")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "SUCESSFUL"),
            @ApiResponse(responseCode = "400", description = "One or more query parameter(s) is null or empty"),
            @ApiResponse(responseCode = "503", description = "Failed to process")})
    public boolean get()
    {
        return true;
    }

    @DELETE
    @Operation(summary = "Testing DELETE request")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "SUCESSFUL"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "503", description = "Failed to process")})
    public void delete()
    {
    }

    @PUT
    @Operation(summary = "Testing PUT request")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "SUCESSFUL"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "503", description = "Failed to process")})
    public void put()
    {
    }
}
