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
import com.google.errorprone.annotations.concurrent.GuardedBy;
import com.proofpoint.log.Logger;
import com.proofpoint.node.NodeInfo;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.ServerSocketChannel;

public class HttpServerInfo
{
    private final ChannelHolder httpChannel;
    private final ChannelHolder httpsChannel;
    private final ChannelHolder adminChannel;

    @Inject
    public HttpServerInfo(HttpServerConfig config, NodeInfo nodeInfo)
    {
        if (config.isHttpEnabled()) {
            httpChannel = new LazyChannel(
                    "http",
                    InetAddresses.toUriString(nodeInfo.getInternalIp()),
                    nodeInfo.getExternalAddress(),
                    nodeInfo.getBindIp(),
                    config.getHttpPort(),
                    config.getHttpAcceptQueueSize(),
                    false
            );
        }
        else {
            httpChannel = new NullChannel();
        }

        if (config.isHttpsEnabled()) {
            httpsChannel = new LazyChannel("https",
                    nodeInfo.getInternalHostname(),
                    null,
                    nodeInfo.getBindIp(),
                    config.getHttpsPort(),
                    config.getHttpAcceptQueueSize(),
                    false
            );
        }
        else {
            httpsChannel = new NullChannel();
        }

        if (config.isAdminEnabled()) {
            if (config.isHttpsEnabled()) {
                adminChannel = new LazyChannel(
                        "https",
                        nodeInfo.getInternalHostname(),
                        null,
                        nodeInfo.getBindIp(),
                        config.getAdminPort(),
                        config.getHttpAcceptQueueSize(),
                        true
                );
            }
            else {
                adminChannel = new LazyChannel(
                        "http",
                        InetAddresses.toUriString(nodeInfo.getInternalIp()),
                        nodeInfo.getExternalAddress(),
                        nodeInfo.getBindIp(),
                        config.getAdminPort(),
                        config.getHttpAcceptQueueSize(),
                        true
                );
            }
        }
        else {
            adminChannel = new NullChannel();
        }
    }

    @Nullable
    public URI getHttpUri()
    {
        return httpChannel.getUri();
    }

    @Nullable
    public URI getHttpExternalUri()
    {
        return httpChannel.getExternalUri();
    }

    @Nullable
    public URI getHttpsUri()
    {
        return httpsChannel.getUri();
    }

    @Nullable
    public URI getAdminUri()
    {
        return adminChannel.getUri();
    }

    @Nullable
    public URI getAdminExternalUri()
    {
        return adminChannel.getExternalUri();
    }

    @Nullable
    ServerSocketChannel getHttpChannel()
    {
        return httpChannel.getChannel();
    }

    @Nullable
    ServerSocketChannel getHttpsChannel()
    {
        return httpsChannel.getChannel();
    }

    @Nullable
    ServerSocketChannel getAdminChannel()
    {
        return adminChannel.getChannel();
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

    interface ChannelHolder
    {
        ServerSocketChannel getChannel();

        URI getUri();

        URI getExternalUri();
    }

    static class NullChannel implements ChannelHolder
    {
        @Override
        public ServerSocketChannel getChannel()
        {
            return null;
        }

        @Override
        public URI getUri()
        {
            return null;
        }

        @Override
        public URI getExternalUri()
        {
            return null;
        }
    }

    static class LazyChannel implements ChannelHolder
    {
        private final String scheme;
        private final String hostname;
        private final String externalHostname;
        private final InetAddress address;
        private final int port;
        private final int acceptQueueSize;
        private final boolean isAdmin;

        @GuardedBy("this")
        private volatile ServerSocketChannel channel;
        @GuardedBy("this")
        private volatile URI uri;
        @GuardedBy("this")
        private volatile URI externalUri;

        LazyChannel(String scheme, String hostname, String externalHostname, InetAddress address, int port, int acceptQueueSize, boolean isAdmin)
        {
            this.scheme = scheme;
            this.hostname = hostname;
            this.externalHostname = externalHostname;
            this.address = address;
            this.port = port;
            this.acceptQueueSize = acceptQueueSize;
            this.isAdmin = isAdmin;
        }

        @Override
        public ServerSocketChannel getChannel()
        {
            if (channel != null) {
                return channel;
            }
            synchronized (this) {
                if (channel == null) {
                    try {
                        ServerSocketChannel newChannel = ServerSocketChannel.open();
                        try {
                            newChannel.socket().setReuseAddress(true);
                            newChannel.socket().bind(new InetSocketAddress(address, port), acceptQueueSize);
                            channel = newChannel;
                        }
                        catch (IOException e) {
                            newChannel.close();
                            throw new UncheckedIOException(e);
                        }
                    }
                    catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
            return channel;
        }

        @Override
        public URI getUri()
        {
            if (uri != null) {
                return uri;
            }
            synchronized (this) {
                if (uri == null) {
                    uri = buildUri(scheme, hostname, port(getChannel()));
                    if (isAdmin) {
                        Logger.get("Bootstrap").info("Admin service on %s", uri);
                    }
                }
            }
            return uri;
        }

        @Override
        public URI getExternalUri()
        {
            if (externalUri != null) {
                return externalUri;
            }
            if (externalHostname == null) {
                return null;
            }
            synchronized (this) {
                if (externalUri == null) {
                    externalUri = buildUri(scheme, externalHostname, port(getChannel()));
                }
            }
            return externalUri;
        }
    }
}
