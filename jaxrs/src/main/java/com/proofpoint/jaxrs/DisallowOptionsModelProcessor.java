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

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Configuration;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;
import org.glassfish.jersey.process.Inflector;
import org.glassfish.jersey.server.model.ModelProcessor;
import org.glassfish.jersey.server.model.ResourceModel;
import org.glassfish.jersey.server.model.internal.ModelProcessorUtil;
import org.glassfish.jersey.server.model.internal.ModelProcessorUtil.Method;

import java.util.ArrayList;
import java.util.List;

@Provider
public class DisallowOptionsModelProcessor
    implements ModelProcessor
{
    private final List<Method> methodList;

    /**
     * Creates new instance.
     */
    public DisallowOptionsModelProcessor() {
        methodList = new ArrayList<>();
        methodList.add(new ModelProcessorUtil.Method(HttpMethod.OPTIONS, MediaType.WILDCARD_TYPE, MediaType.WILDCARD_TYPE,
                GenericOptionsInflector.class));
    }

    private static class GenericOptionsInflector implements Inflector<ContainerRequestContext, Response> {
        @Override
        public Response apply(ContainerRequestContext containerRequestContext) {
            return Response.status(Status.METHOD_NOT_ALLOWED)
                    .build();
        }
    }

    @Override
    public ResourceModel processResourceModel(ResourceModel resourceModel, Configuration configuration) {
        return ModelProcessorUtil.enhanceResourceModel(resourceModel, false, methodList, true).build();
    }

    @Override
    public ResourceModel processSubResource(ResourceModel subResourceModel, Configuration configuration) {
        return ModelProcessorUtil.enhanceResourceModel(subResourceModel, true, methodList, true).build();
    }
}
