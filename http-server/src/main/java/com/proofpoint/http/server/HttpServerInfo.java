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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.InetAddresses;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.ServerSocketChannel;

public class HttpServerInfo
{
    private final URI httpUri;
    private final URI httpExternalUri;
    private final URI httpsUri;
    private final URI adminUri;
    private final URI adminExternalUri;

    private final ServerSocketChannel httpChannel;
    private final ServerSocketChannel httpsChannel;
    private final ServerSocketChannel adminChannel;

    @Inject
    public HttpServerInfo(HttpServerConfig config, NodeInfo nodeInfo)
    {
        if (config.isHttpEnabled()) {
            httpChannel = createChannel(nodeInfo.getBindIp(), config.getHttpPort(), config.getHttpAcceptQueueSize());
            httpUri = buildUri("http", InetAddresses.toUriString(nodeInfo.getInternalIp()), port(httpChannel));
            httpExternalUri = buildUri("http", nodeInfo.getExternalAddress(), httpUri.getPort());
        }
        else {
            httpChannel = null;
            httpUri = null;
            httpExternalUri = null;
        }

        if (config.isHttpsEnabled()) {
            httpsChannel = createChannel(nodeInfo.getBindIp(), config.getHttpsPort(), config.getHttpAcceptQueueSize());
            httpsUri = buildUri("https", nodeInfo.getInternalHostname(), port(httpsChannel));
        }
        else {
            httpsChannel = null;
            httpsUri = null;
        }

        if (config.isAdminEnabled()) {
            adminChannel = createChannel(nodeInfo.getBindIp(), config.getAdminPort(), config.getHttpAcceptQueueSize());
            if (config.isHttpsEnabled()) {
                adminUri = buildUri("https", nodeInfo.getInternalHostname(), port(adminChannel));
                adminExternalUri = null;
            }
            else {
                adminUri = buildUri("http", InetAddresses.toUriString(nodeInfo.getInternalIp()), port(adminChannel));
                adminExternalUri = buildUri("http", nodeInfo.getExternalAddress(), adminUri.getPort());
            }
            Logger.get("Bootstrap").info("Admin service on %s", adminUri);
        }
        else {
            adminChannel = null;
            adminUri = null;
            adminExternalUri = null;
        }
    }

    @Nullable
    public URI getHttpUri()
    {
        return httpUri;
    }

    @Nullable
    public URI getHttpExternalUri()
    {
        return httpExternalUri;
    }

    @Nullable
    public URI getHttpsUri()
    {
        return httpsUri;
    }

    @Nullable
    public URI getAdminUri()
    {
        return adminUri;
    }

    @Nullable
    public URI getAdminExternalUri()
    {
        return adminExternalUri;
    }

    @Nullable
    ServerSocketChannel getHttpChannel()
    {
        return httpChannel;
    }

    @Nullable
    ServerSocketChannel getHttpsChannel()
    {
        return httpsChannel;
    }

    @Nullable
    ServerSocketChannel getAdminChannel()
    {
        return adminChannel;
    }

    private static URI buildUri(String scheme, String host, int port)
    {
        try {
            return new URI(scheme, null, host, port, null, null, null);
        }
        catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @VisibleForTesting
    static int port(ServerSocketChannel channel)
    {
        try {
            return ((InetSocketAddress) channel.getLocalAddress()).getPort();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ServerSocketChannel createChannel(InetAddress address, int port, int acceptQueueSize)
    {
        try {
            ServerSocketChannel channel = ServerSocketChannel.open();
            channel.socket().setReuseAddress(true);
            channel.socket().bind(new InetSocketAddress(address, port), acceptQueueSize);
            return channel;
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
