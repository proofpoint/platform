package com.proofpoint.http.server;

import org.eclipse.jetty.http2.FlowControlStrategy;
import org.eclipse.jetty.http2.HTTP2Connection;
import org.eclipse.jetty.http2.api.server.ServerSessionListener;
import org.eclipse.jetty.http2.generator.Generator;
import org.eclipse.jetty.http2.parser.ServerParser;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerSession;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.util.component.LifeCycle;

class HackHTTP2CServerConnectionFactory
    extends HTTP2CServerConnectionFactory
{
    private final Connection.Listener connectionListener = new ConnectionListener();

    HackHTTP2CServerConnectionFactory(HttpConfiguration httpConfiguration)
    {
        super(httpConfiguration);
    }

    @Override
    public Connection newConnection(Connector connector, EndPoint endPoint)
    {
        ServerSessionListener listener = newSessionListener(connector, endPoint);

        Generator generator = new Generator(connector.getByteBufferPool(), getMaxDynamicTableSize(), getMaxHeaderBlockFragment());
        FlowControlStrategy flowControl = newFlowControlStrategy();
        if (flowControl == null)
            flowControl = getFlowControlStrategyFactory().newFlowControlStrategy();
        HTTP2ServerSession session = new HTTP2ServerSession(connector.getScheduler(), endPoint, generator, listener, flowControl);
        session.setMaxLocalStreams(getMaxConcurrentStreams());
        session.setMaxRemoteStreams(getMaxConcurrentStreams());
        // For a single stream in a connection, there will be a race between
        // the stream idle timeout and the connection idle timeout. However,
        // the typical case is that the connection will be busier and the
        // stream idle timeout will expire earlier than the connection's.
        long streamIdleTimeout = getStreamIdleTimeout();
        if (streamIdleTimeout <= 0)
            streamIdleTimeout = endPoint.getIdleTimeout();
        session.setStreamIdleTimeout(streamIdleTimeout);
        session.setInitialSessionRecvWindow(getInitialSessionRecvWindow());

        ServerParser parser = newServerParser(connector, session);
        HTTP2Connection connection = new HackHTTP2ServerConnection(connector.getByteBufferPool(), connector.getExecutor(),
                        endPoint, getHttpConfiguration(), parser, session, getInputBufferSize(), getExecutionStrategyFactory(), listener);
        connection.addListener(connectionListener);
        return configure(connection, connector, endPoint);
    }

    private class ConnectionListener implements Connection.Listener
    {
        @Override
        public void onOpened(Connection connection)
        {
            addManaged((LifeCycle)((HTTP2Connection)connection).getSession());
        }

        @Override
        public void onClosed(Connection connection)
        {
            removeBean(((HTTP2Connection)connection).getSession());
        }
    }
}
