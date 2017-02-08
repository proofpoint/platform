/*
 * Copyright 2015 Proofpoint, Inc.
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
package com.proofpoint.jaxrs;

import org.glassfish.jersey.server.ParamException.QueryParamException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

/**
 * Map {@link org.glassfish.jersey.server.ParamException.QueryParamException} to status 400 errors.
 * <p>
 * When a {@link javax.ws.rs.QueryParam} annotated parameter fails to parse, the default behavior for Jersey is to map this to 404.
 * We want to change it to a saner 400 status.
 */
@Provider
public class QueryParamExceptionMapper implements ExceptionMapper<QueryParamException>
{
    @Override
    public Response toResponse(QueryParamException e)
    {
        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.TEXT_PLAIN_TYPE)
                .entity(e.getMessage())
                .build();
    }
}
