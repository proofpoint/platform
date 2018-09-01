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
package com.proofpoint.http.server;

import com.google.inject.Binder;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.proofpoint.discovery.client.announce.AnnouncementHttpServerInfo;
import com.proofpoint.http.server.HttpServerBinder.HttpResourceBinding;

import javax.servlet.Filter;

import static com.proofpoint.configuration.ConfigBinder.bindConfig;
import static com.proofpoint.reporting.ReportBinder.reportBinder;
import static org.weakref.jmx.guice.ExportBinder.newExporter;

/**
 * Provides a fully configured instance of an HTTP server,
 * ready to use with Guice.
 * <p>
 * Features:
 * <ul>
 * <li>HTTP/HTTPS</li>
 * <li>Basic Auth</li>
 * <li>Request logging</li>
 * <li>JMX</li>
 * </ul>
 * Configuration options are provided via {@link HttpServerConfig}
 * <p>
 * To enable JMX, an {@link javax.management.MBeanServer} must be bound elsewhere
 * <p>
 * To enable Basic Auth, a {@link org.eclipse.jetty.security.LoginService} must be bound elsewhere
 * <p>
 * To enable HTTPS, {@link HttpServerConfig#isHttpsEnabled()} must return true
 * and {@link HttpServerConfig#getKeystorePath()}
 * and {@link HttpServerConfig#getKeystorePassword()} must return the path to
 * the keystore containing the SSL cert and the password to the keystore, respectively.
 * The HTTPS port is specified via {@link HttpServerConfig#getHttpsPort()}.
 */
public class HttpServerModule
        implements Module
{
    public static final String REALM_NAME = "Proofpoint";

    @Override
    public void configure(Binder binder)
    {
        binder.disableCircularProxies();

        binder.bind(HttpServer.class).toProvider(HttpServerProvider.class).in(Scopes.SINGLETON);
        binder.bind(HttpServerInfo.class).in(Scopes.SINGLETON);
        binder.bind(QueryStringFilter.class).in(Scopes.SINGLETON);
        binder.bind(RequestStats.class).in(Scopes.SINGLETON);
        binder.bind(ClientAddressExtractor.class).in(Scopes.SINGLETON);
        Multibinder.newSetBinder(binder, Filter.class, TheServlet.class);
        Multibinder.newSetBinder(binder, Filter.class, TheAdminServlet.class);
        Multibinder.newSetBinder(binder, HttpResourceBinding.class, TheServlet.class);

        reportBinder(binder).export(HttpServer.class);
        newExporter(binder).export(HttpServer.class).withGeneratedName();
        reportBinder(binder).bindReportCollection(DetailedRequestStats.class).withNamePrefix("HttpServer");

        bindConfig(binder).bind(HttpServerConfig.class);
        bindConfig(binder).bind(InternalNetworkConfig.class);

        binder.bind(AnnouncementHttpServerInfo.class).to(LocalAnnouncementHttpServerInfo.class).in(Scopes.SINGLETON);
    }
}
