package com.proofpoint.http.server;

import org.eclipse.jetty.http.HttpCompliance;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpChannelOverHttp;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnection;

class HackHttpConnection
    extends HttpConnection
{
    HackHttpConnection(HttpConfiguration config, Connector connector, EndPoint endPoint, HttpCompliance compliance, boolean recordComplianceViolations)
    {
        super(config, connector, endPoint, compliance, recordComplianceViolations);
    }

    @Override
    protected HttpChannelOverHttp newHttpChannel()
    {
        return new HackHttpChannelOverHttp(this, getConnector(), getHttpConfiguration(), getEndPoint(), this);
    }
}
