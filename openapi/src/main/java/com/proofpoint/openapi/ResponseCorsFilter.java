/*
 * Copyright 2018 Proofpoint, Inc.
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

package com.proofpoint.openapi;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

import static com.proofpoint.openapi.OpenApiResource.BASE_PATH;


@Provider
public class ResponseCorsFilter implements ContainerResponseFilter
{
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
    {
        if (requestContext.getUriInfo().getPath().startsWith(BASE_PATH)) {
            responseContext.getHeaders().add("Access-Control-Allow-Origin", "*");
        }
    }
}
