package com.proofpoint.http.server;

import com.google.common.base.Throwables;
import com.google.common.net.InetAddresses;
import com.google.inject.Inject;
import com.proofpoint.node.NodeInfo;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;

public class HttpServerInfo
{
    private final URI httpUri;
    private final URI httpsUri;

    public HttpServerInfo()
    {
        this(new HttpServerConfig(), new NodeInfo());
    }

    @Inject
    public HttpServerInfo(HttpServerConfig config, NodeInfo nodeInfo)
    {
        if (config.isHttpEnabled()) {
            httpUri = buildUri("http", nodeInfo, config.getHttpPort());
        }
        else {
            httpUri = null;
        }

        if (config.isHttpsEnabled()) {
            httpsUri = buildUri("http", nodeInfo, config.getHttpsPort());
        }
        else {
            httpsUri = null;
        }
    }

    public URI getHttpUri()
    {
        return httpUri;
    }

    public URI getHttpsUri()
    {
        return httpsUri;
    }

    private static URI buildUri(String scheme, NodeInfo nodeInfo, int port)
    {
        try {
            // 0 means select a random port
            if (port == 0) {
                ServerSocket socket = new ServerSocket();
                try {
                    socket.bind(new InetSocketAddress(0));
                    port = socket.getLocalPort();
                }
                finally {
                    socket.close();
                }
            }

            return new URI(scheme, null, InetAddresses.toUriString(nodeInfo.getPublicIp()), port, null, null, null);
        }
        catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
}
