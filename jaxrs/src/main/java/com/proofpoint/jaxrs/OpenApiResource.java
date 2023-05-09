package com.proofpoint.jaxrs;

import io.swagger.v3.jaxrs2.integration.resources.BaseOpenApiResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.servlet.ServletConfig;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

@Path("/admin/openapi.{type:json|yaml}")
@AccessDoesNotRequireAuthentication
public class OpenApiResource extends BaseOpenApiResource
{
    private final Set<Object> jaxrsResources;
    private static final String SWAGGER_JAXRS_ANNOTATION_SCANNER = "io.swagger.v3.oas.integration.GenericOpenApiScanner";

    @Inject
    public OpenApiResource(@JaxrsResource Set<Object> jaxrsResources)
    {
        this.jaxrsResources = requireNonNull(jaxrsResources, "jaxrsResources is null");
    }

    @GET
    @Operation(hidden = true)
    public Response getOpenApiSpec(
            @Context HttpHeaders headers,
            @Context UriInfo uriInfo,
            @PathParam("type") String type,
            @Context ServletConfig config,
            @Context Application app)
            throws Exception
    {
        Response response = getOpenApi(headers, config, app, uriInfo, type);
        response.getHeaders().add("Access-Control-Allow-Origin", "*");
        return response;
    }

    @PostConstruct
    void configureSwagger()
    {
        SwaggerConfiguration oasConfig = new SwaggerConfiguration()
                .openAPI(new OpenAPI())
                .prettyPrint(true);
        oasConfig.setResourceClasses(jaxrsResources.stream().map(classame -> classame.getClass().getName()).collect(Collectors.toSet()));
        oasConfig.setScannerClass(SWAGGER_JAXRS_ANNOTATION_SCANNER);
        setOpenApiConfiguration(oasConfig);
    }
}