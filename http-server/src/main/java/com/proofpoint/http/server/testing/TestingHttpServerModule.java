/*
 * Copyright 2010 Proofpoint, Inc.
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
package com.proofpoint.http.server.testing;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.proofpoint.discovery.client.announce.AnnouncementHttpServerInfo;
import com.proofpoint.http.server.ClientAddressExtractor;
import com.proofpoint.http.server.HttpServer;
import com.proofpoint.http.server.HttpServerBinder.HttpResourceBinding;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.http.server.HttpServerModuleOptions;
import com.proofpoint.http.server.InternalNetworkConfig;
import com.proofpoint.http.server.LocalAnnouncementHttpServerInfo;
import com.proofpoint.http.server.QueryStringFilter;
import com.proofpoint.http.server.TheServlet;
import jakarta.servlet.Filter;

import static com.google.inject.multibindings.Multibinder.newSetBinder;

public class TestingHttpServerModule
        implements Module
{
    private final int httpPort;
    private final HttpServerModuleOptions moduleOptions = new HttpServerModuleOptions();

    public TestingHttpServerModule()
    {
        this(0);
    }

    public TestingHttpServerModule(int httpPort)
    {
        this.httpPort = httpPort;
    }

    @Override
    public void configure(Binder binder)
    {
        binder.disableCircularProxies();

        // Jetty scales required threads based on processor count, so pick a safe number
        HttpServerConfig config = new HttpServerConfig()
                .setMinThreads(1)
                .setMaxThreads(20)
                .setHttpPort(httpPort)
                .setShowStackTrace(true);

        binder.bind(HttpServerModuleOptions.class).toInstance(moduleOptions);
        binder.bind(HttpServerConfig.class).toInstance(config);
        binder.bind(InternalNetworkConfig.class).toInstance(new InternalNetworkConfig());
        binder.bind(HttpServerInfo.class).in(Scopes.SINGLETON);
        binder.bind(TestingHttpServer.class).in(Scopes.SINGLETON);
        binder.bind(HttpServer.class).to(Key.get(TestingHttpServer.class));
        binder.bind(QueryStringFilter.class).in(Scopes.SINGLETON);
        binder.bind(ClientAddressExtractor.class).in(Scopes.SINGLETON);
        newSetBinder(binder, Filter.class, TheServlet.class);
        newSetBinder(binder, HttpResourceBinding.class, TheServlet.class);
        binder.bind(AnnouncementHttpServerInfo.class).to(LocalAnnouncementHttpServerInfo.class);
    }

    public TestingHttpServerModule allowAmbiguousUris()
    {
        moduleOptions.setAllowAmbiguousUris();
        return this;
    }


}
