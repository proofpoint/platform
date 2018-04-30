package com.proofpoint.openapi;

import com.proofpoint.jaxrs.JaxrsResource;
import io.swagger.v3.jaxrs2.integration.resources.BaseOpenApiResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.OpenAPI;

import javax.inject.Inject;
import javax.servlet.ServletConfig;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.util.Set;
import java.util.stream.Collectors;

import static com.proofpoint.openapi.OpenApiResource.BASE_PATH;

@Path(BASE_PATH + "api.{type:json|yaml}")
public class OpenApiResource extends BaseOpenApiResource
{
    static final String BASE_PATH = "openapi/";
    private Set<String> resourceClasses;
    private Set<Object> jaxrsResources;
    private static final String SWAGGER_JAXRS_ANNOTATION_SCANNER = "io.swagger.v3.jaxrs2.integration.JaxrsAnnotationScanner";
    @Context
    ServletConfig config;

    @Context
    Application app;

    @Inject
    public OpenApiResource(@JaxrsResource Set<Object> jaxrsResources)
    {
        this.jaxrsResources = jaxrsResources;
    }

    @GET
    @Produces({MediaType.APPLICATION_JSON, "application/yaml"})
    @Operation(hidden = true)
    public Response getOpenApi(@Context HttpHeaders headers,
            @Context UriInfo uriInfo,
            @PathParam("type") String type)
            throws Exception
    {

        setOpenApiConfiguration(getSwaggerConfiguration());
        return super.getOpenApi(headers, config, app, uriInfo, type);
    }

    private SwaggerConfiguration getSwaggerConfiguration()
    {
        SwaggerConfiguration oasConfig = new SwaggerConfiguration()
                .openAPI(new OpenAPI())
                .prettyPrint(true);
        oasConfig.setResourceClasses(getJaxrsResourceClasses());
        oasConfig.setScannerClass(SWAGGER_JAXRS_ANNOTATION_SCANNER);
        return oasConfig;
    }

    private Set<String> getJaxrsResourceClasses()
    {
        if (resourceClasses == null && jaxrsResources != null && !jaxrsResources.isEmpty()) {
            resourceClasses = jaxrsResources.stream().map(classame -> classame.getClass().getName()).collect(Collectors.toSet());
        }
        return resourceClasses;
    }

}
