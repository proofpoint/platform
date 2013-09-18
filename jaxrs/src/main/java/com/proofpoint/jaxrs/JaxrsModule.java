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
package com.proofpoint.jaxrs;

import com.google.common.collect.Maps;
import com.google.inject.Binder;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.proofpoint.http.server.TheServlet;
import com.sun.jersey.guice.spi.container.servlet.GuiceContainer;
import com.sun.jersey.spi.container.ResourceFilterFactory;

import javax.servlet.Servlet;
import java.util.HashMap;
import java.util.Map;

import static com.google.inject.multibindings.MapBinder.newMapBinder;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;

public class JaxrsModule implements Module
{
    @Override
    public void configure(Binder binder)
    {
        binder.requireExplicitBindings();
        binder.disableCircularProxies();

        binder.bind(GuiceContainer.class).in(Scopes.SINGLETON);
        binder.bind(Servlet.class).annotatedWith(TheServlet.class).to(Key.get(GuiceContainer.class));
        binder.bind(JsonMapper.class).in(Scopes.SINGLETON);
        binder.bind(ParsingExceptionMapper.class).in(Scopes.SINGLETON);
        newMapBinder(binder, String.class, String.class, TheServlet.class)
                .addBinding("com.sun.jersey.spi.container.ContainerRequestFilters")
                .toInstance(OverrideMethodFilter.class.getName());
    }
}
