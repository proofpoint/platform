package com.proofpoint.openapi;

import com.proofpoint.jaxrs.AccessDoesNotRequireAuthentication;
import com.proofpoint.jaxrs.JaxrsResource;
import io.swagger.v3.jaxrs2.integration.resources.BaseOpenApiResource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.OpenAPI;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.servlet.ServletConfig;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.*;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

@Path("/admin/openapi-admin.{type:json|yaml}")
@AccessDoesNotRequireAuthentication
public class OpenApiAdminResource extends BaseOpenApiResource
{
    private static final String SWAGGER_JAXRS_ANNOTATION_SCANNER = "io.swagger.v3.oas.integration.GenericOpenApiScanner";

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
        if(getOpenApiConfiguration() == null){
            SwaggerConfiguration oasConfig = new SwaggerConfiguration()
                    .openAPI(new OpenAPI())
                    .prettyPrint(true);
            oasConfig.setResourceClasses(app.getSingletons().stream().map(classame -> classame.getClass().getName()).collect(Collectors.toSet()));
            oasConfig.setScannerClass(SWAGGER_JAXRS_ANNOTATION_SCANNER);
            setOpenApiConfiguration(oasConfig);
        }

        Response response = getOpenApi(headers, config, app, uriInfo, type);
        response.getHeaders().add("Access-Control-Allow-Origin", "*");
        return response;
    }
}