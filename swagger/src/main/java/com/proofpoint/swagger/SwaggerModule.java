/*
 * Copyright 2012 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.swagger;

import com.google.inject.Binder;

import com.proofpoint.configuration.AbstractConfigurationAwareModule;
import io.swagger.v3.jaxrs2.integration.resources.AcceptHeaderOpenApiResource;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;

import io.swagger.v3.oas.integration.SwaggerConfiguration;


import io.swagger.v3.oas.models.OpenAPI;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
public class SwaggerModule extends AbstractConfigurationAwareModule
{
    private static final String SWAGGER_JAXRS_ANNOTATION_SCANNER = "io.swagger.v3.jaxrs2.integration.JaxrsAnnotationScanner";

    @Override
    protected void setup(Binder binder)
    {
        jaxrsBinder(binder).bindAdmin(ResponseCorsFilter.class);
        SwaggerConfig swaggerConfig=buildConfigObject(SwaggerConfig.class);
        setOpenApiResource(binder, Stream.of(swaggerConfig.getPackages()).collect(Collectors.toSet()));


    }

    private void setOpenApiResource(Binder binder, Set<String> packages)
    {
        SwaggerConfiguration oasConfig = new SwaggerConfiguration()
                .openAPI(new OpenAPI())
                .prettyPrint(true);
        oasConfig.setScannerClass(SWAGGER_JAXRS_ANNOTATION_SCANNER);
        oasConfig.setResourcePackages(packages);
        OpenApiResource openApiResource=new OpenApiResource();
        openApiResource.openApiConfiguration(oasConfig);
        jaxrsBinder(binder).bindAdminInstance(openApiResource);
        jaxrsBinder(binder).bindAdmin(AcceptHeaderOpenApiResource.class);
    }
}
