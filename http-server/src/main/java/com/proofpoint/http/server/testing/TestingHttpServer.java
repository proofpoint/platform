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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.proofpoint.http.server.ClientAddressExtractor;
import com.proofpoint.http.server.HttpServer;
import com.proofpoint.http.server.HttpServerBinder.HttpResourceBinding;
import com.proofpoint.http.server.HttpServerConfig;
import com.proofpoint.http.server.HttpServerInfo;
import com.proofpoint.http.server.QueryStringFilter;
import com.proofpoint.http.server.RequestStats;
import com.proofpoint.http.server.TheServlet;
import com.proofpoint.node.NodeInfo;
import com.proofpoint.stats.SparseTimeStat;

import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.Servlet;
import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * HTTP server that binds to localhost on a random port
 */
public class TestingHttpServer extends HttpServer
{

    private final HttpServerInfo httpServerInfo;

    public TestingHttpServer(
            HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            HttpServerConfig config,
            Servlet servlet,
            Map<String, String> initParameters)
    {
        this(httpServerInfo,
                nodeInfo,
                config,
                servlet,
                initParameters,
                ImmutableSet.of(),
                ImmutableSet.of(),
                new QueryStringFilter(),
                new ClientAddressExtractor()
        );
    }

    /**
     * @deprecated Use {@link #TestingHttpServer(HttpServerInfo, NodeInfo, HttpServerConfig, Servlet,
     * Map, Set, Set, QueryStringFilter, ClientAddressExtractor)}
     */
    @Deprecated
    public TestingHttpServer(HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            HttpServerConfig config,
            Servlet servlet,
            Map<String, String> initParameters,
            Set<Filter> filters,
            Set<HttpResourceBinding> resources,
            QueryStringFilter queryStringFilter) {
        this(httpServerInfo,
                nodeInfo,
                config,
                servlet,
                initParameters,
                filters,
                resources,
                queryStringFilter,
                new ClientAddressExtractor());
    }

    @Inject
    public TestingHttpServer(HttpServerInfo httpServerInfo,
            NodeInfo nodeInfo,
            HttpServerConfig config,
            @TheServlet Servlet servlet,
            @TheServlet Map<String, String> initParameters,
            @TheServlet Set<Filter> filters,
            @TheServlet Set<HttpResourceBinding> resources,
            QueryStringFilter queryStringFilter,
            ClientAddressExtractor clientAddressExtractor)
    {
        super(httpServerInfo,
                nodeInfo,
                config,
                servlet,
                initParameters,
                ImmutableSet.copyOf(filters),
                resources,
                null,
                ImmutableMap.of(),
                ImmutableSet.of(),
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
        return httpServerInfo.getHttpUri();
    }

    public int getPort()
    {
        return httpServerInfo.getHttpUri().getPort();
    }

    public static class DetailedRequestStats implements com.proofpoint.http.server.DetailedRequestStats
    {
        @Override
        public SparseTimeStat requestTimeByCode(int responseCode, int responseCodeFamily)
        {
            return new SparseTimeStat();
        }
    }
}
