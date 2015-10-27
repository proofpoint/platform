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
package com.proofpoint.http.server.testing;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.proofpoint.http.server.HttpServer;
import com.proofpoint.http.server.HttpServerBinder.HttpResourceBinding;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.http.server.QueryStringFilter;
import com.proofpoint.http.server.RequestStats;
import com.proofpoint.http.server.TheAdminServlet;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.stats.SparseTimeStat;
import org.eclipse.jetty.server.handler.RequestLogHandler;

import javax.servlet.Filter;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * Admin HTTP server that binds to localhost on a random port
 */
public class TestingAdminHttpServer extends HttpServer
{

    private final HttpServerInfo httpServerInfo;

    public TestingAdminHttpServer(
            HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            HttpServerConfig config,
            Servlet servlet,
            Map<String, String> initParameters)
            throws IOException
    {
        this(httpServerInfo,
                nodeInfo,
                config,
                servlet,
                initParameters,
                ImmutableSet.<Filter>of(),
                new QueryStringFilter()
        );
    }

    @Inject
    public TestingAdminHttpServer(HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            HttpServerConfig config,
            @TheAdminServlet Servlet servlet,
            @TheAdminServlet Map<String, String> initParameters,
            @TheAdminServlet Set<Filter> filters,
            QueryStringFilter queryStringFilter)
            throws IOException
    {
        super(httpServerInfo,
                nodeInfo,
                config,
                new NullServlet(),
                ImmutableMap.<String, String>of(),
                ImmutableSet.<Filter>of(),
                ImmutableSet.<HttpResourceBinding>of(),
                servlet,
                initParameters,
                ImmutableSet.copyOf(filters),
                null,
                null,
                queryStringFilter,
                new RequestStats(),
                new DetailedRequestStats()
        );
        this.httpServerInfo = httpServerInfo;
    }

    @Override
    public RequestLogHandler createLogHandler(HttpServerConfig config)
            throws IOException
    {
        return null;
    }

    public URI getBaseUrl()
    {
        return httpServerInfo.getAdminUri();
    }

    public int getPort()
    {
        return httpServerInfo.getAdminUri().getPort();
    }

    public static class DetailedRequestStats implements com.proofpoint.http.server.DetailedRequestStats
    {
        @Override
        public SparseTimeStat requestTimeByCode(int responseCode)
        {
            return new SparseTimeStat();
        }
    }

    private static class NullServlet
            extends HttpServlet
    {
    }
}
