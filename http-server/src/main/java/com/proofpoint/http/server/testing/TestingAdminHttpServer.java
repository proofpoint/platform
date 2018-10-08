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
import com.proofpoint.http.server.ClientAddressExtractor;
import com.proofpoint.http.server.HttpServer;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.http.server.QueryStringFilter;
import com.proofpoint.http.server.RequestStats;
import com.proofpoint.http.server.TheAdminServlet;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.stats.SparseTimeStat;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.Servlet;
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
            Servlet adminServlet,
            Map<String, String> initParameters)
            throws IOException
    {
        this(httpServerInfo,
                nodeInfo,
                config,
                new NullServlet(),
                adminServlet,
                initParameters,
                ImmutableSet.of(),
                new QueryStringFilter(),
                new ClientAddressExtractor()
        );
    }

    @Deprecated
    public TestingAdminHttpServer(HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            HttpServerConfig config,
            Servlet servlet,
            Servlet adminServlet,
            Map<String, String> initParameters,
            Set<Filter> filters,
            QueryStringFilter queryStringFilter)
            throws IOException
    {
        this(httpServerInfo,
                nodeInfo,
                config,
                servlet,
                adminServlet,
                initParameters,
                filters,
                queryStringFilter,
                new ClientAddressExtractor());
    }

    @Inject
    public TestingAdminHttpServer(HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            HttpServerConfig config,
            @ForTestingAdminHttpServer Servlet servlet,
            @TheAdminServlet Servlet adminServlet,
            @TheAdminServlet Map<String, String> initParameters,
            @TheAdminServlet Set<Filter> filters,
            QueryStringFilter queryStringFilter,
            ClientAddressExtractor clientAddressExtractor)
            throws IOException
    {
        super(httpServerInfo,
                nodeInfo,
                config,
                servlet,
                ImmutableMap.of(),
                ImmutableSet.of(),
                ImmutableSet.of(),
                adminServlet,
                initParameters,
                ImmutableSet.copyOf(filters),
                null,
                null,
                queryStringFilter,
                new RequestStats(),
                new DetailedRequestStats(),
                null,
                clientAddressExtractor
        );
        this.httpServerInfo = httpServerInfo;
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
        public SparseTimeStat requestTimeByCode(int responseCode, int resposeCodeFamily)
        {
            return new SparseTimeStat();
        }
    }

    static class NullServlet
            extends HttpServlet
    {
    }
}
