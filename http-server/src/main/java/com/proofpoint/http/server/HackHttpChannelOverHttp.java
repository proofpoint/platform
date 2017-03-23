package com.proofpoint.http.server;

import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpChannelOverHttp;
import org.eclipse.jetty.server.HttpChannelState;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.HttpInput;
import org.eclipse.jetty.server.HttpTransport;

class HackHttpChannelOverHttp
    extends HttpChannelOverHttp
{
    HackHttpChannelOverHttp(HttpConnection httpConnection, Connector connector, HttpConfiguration config, EndPoint endPoint, HttpTransport transport)
    {
        super(httpConnection, connector, config, endPoint, transport);
    }

    @Override
    protected HttpInput newHttpInput(HttpChannelState state)
    {
        return new HackHttpInputOverHttp(state);
    }
}
