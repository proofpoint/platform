package com.proofpoint.http.server;

import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.HttpConnectionFactory;

class HackHttpConnectionFactory
    extends HttpConnectionFactory
{
    HackHttpConnectionFactory(HttpConfiguration httpConfiguration)
    {
        super(httpConfiguration);
    }

    @Override
    public Connection newConnection(Connector connector, EndPoint endPoint)
    {
        HttpConnection conn = new HackHttpConnection(getHttpConfiguration(), connector, endPoint, getHttpCompliance(),isRecordHttpComplianceViolations());
        return configure(conn, connector, endPoint);
    }
}
