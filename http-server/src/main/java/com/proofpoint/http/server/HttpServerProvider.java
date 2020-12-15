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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.http.server.HttpServerBinder.HttpResourceBinding;
import com.proofpoint.node.NodeInfo;
import org.eclipse.jetty.security.LoginService;

import javax.annotation.Nullable;
import javax.inject.Provider;
import javax.management.MBeanServer;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Throwables.throwIfUnchecked;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Provides an instance of a Jetty server ready to be configured with
 * com.google.inject.servlet.ServletModule
 */
public class HttpServerProvider
        implements Provider<HttpServer>
{
    private final HttpServerInfo httpServerInfo;
    private final NodeInfo nodeInfo;
    private final HttpServerConfig config;
    private final Servlet theServlet;
    private final Set<HttpResourceBinding> resources;
    private Map<String, String> servletInitParameters = ImmutableMap.of();
    private final Servlet theAdminServlet;
    private Map<String, String> adminServletInitParameters = ImmutableMap.of();
    private MBeanServer mbeanServer;
    private LoginService loginService;
    private final RequestStats stats;
    private final DetailedRequestStats detailedRequestStats;
    private final Set<Filter> filters;
    private final Set<Filter> adminFilters;
    private final QueryStringFilter queryStringFilter;
    private final ClientAddressExtractor clientAddressExtractor;
    private final LifeCycleManager lifeCycleManager;

    @Inject
    public HttpServerProvider(HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            HttpServerConfig config,
            @TheServlet Servlet theServlet,
            @TheServlet Set<Filter> filters,
            @TheServlet Set<HttpResourceBinding> resources,
            @TheAdminServlet Servlet theAdminServlet,
            @TheAdminServlet Set<Filter> adminFilters,
            RequestStats stats,
            DetailedRequestStats detailedRequestStats,
            QueryStringFilter queryStringFilter,
            ClientAddressExtractor clientAddressExtractor,
            LifeCycleManager lifeCycleManager)
    {
        requireNonNull(httpServerInfo, "httpServerInfo is null");
        requireNonNull(nodeInfo, "nodeInfo is null");
        requireNonNull(config, "config is null");
        requireNonNull(theServlet, "theServlet is null");
        requireNonNull(filters, "filters is null");
        requireNonNull(resources, "resources is null");
        requireNonNull(theAdminServlet, "theAdminServlet is null");
        requireNonNull(adminFilters, "adminFilters is null");
        requireNonNull(stats, "stats is null");
        requireNonNull(detailedRequestStats, "detailedRequestStats is null");
        requireNonNull(clientAddressExtractor, "clientAddressExtractor is null");
        requireNonNull(queryStringFilter, "queryStringFilter is null");

        this.httpServerInfo = httpServerInfo;
        this.nodeInfo = nodeInfo;
        this.config = config;
        this.theServlet = theServlet;
        this.filters = ImmutableSet.copyOf(filters);
        this.resources = ImmutableSet.copyOf(resources);
        this.theAdminServlet = theAdminServlet;
        this.adminFilters = ImmutableSet.copyOf(adminFilters);
        this.stats = stats;
        this.detailedRequestStats = detailedRequestStats;
        this.queryStringFilter = queryStringFilter;
        this.clientAddressExtractor = clientAddressExtractor;
        this.lifeCycleManager = requireNonNull(lifeCycleManager, "lifeCycleManager is null");
    }

    @Inject(optional = true)
    public void setServletInitParameters(@TheServlet Map<String, String> parameters)
    {
        this.servletInitParameters = ImmutableMap.copyOf(parameters);
    }

    @Inject(optional = true)
    public void setAdminServletInitParameters(@TheAdminServlet Map<String, String> parameters)
    {
        this.adminServletInitParameters = ImmutableMap.copyOf(parameters);
    }

    @Inject(optional = true)
    public void setMBeanServer(MBeanServer server)
    {
        mbeanServer = server;
    }

    @Inject(optional = true)
    public void setLoginService(@Nullable LoginService loginService)
    {
        this.loginService = loginService;
    }

    @Override
    public HttpServer get()
    {
        try {
            RequestLog requestLog = null;
            if (config.isLogEnabled()) {
                requestLog = createRequestLog(config);
            }

            HttpServer httpServer = new HttpServer(httpServerInfo,
                    nodeInfo,
                    config,
                    theServlet,
                    servletInitParameters,
                    filters,
                    resources,
                    theAdminServlet,
                    adminServletInitParameters,
                    adminFilters,
                    mbeanServer,
                    loginService,
                    queryStringFilter,
                    stats,
                    detailedRequestStats,
                    requestLog,
                    clientAddressExtractor
            );
            lifeCycleManager.addInstance(httpServer);
            return httpServer;
        }
        catch (Exception e) {
            throwIfUnchecked(e);
            throw new RuntimeException(e);
        }
    }

    protected RequestLog createRequestLog(HttpServerConfig config)
            throws IOException
    {
        File logFile = new File(config.getLogPath());
        if (logFile.exists() && !logFile.isFile()) {
            throw new IOException(format("Log path %s exists but is not a file", logFile.getAbsolutePath()));
        }

        File logPath = logFile.getParentFile();
        if (!logPath.mkdirs() && !logPath.exists()) {
            throw new IOException(format("Cannot create %s and path does not already exist", logPath.getAbsolutePath()));
        }

        return config.getLogFormat().createLog(config);
    }
}
