package com.proofpoint.http.server;

import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.ErrorCode;
import org.eclipse.jetty.http2.ISession;
import org.eclipse.jetty.http2.IStream;
import org.eclipse.jetty.http2.api.server.ServerSessionListener;
import org.eclipse.jetty.http2.frames.HeadersFrame;
import org.eclipse.jetty.http2.frames.ResetFrame;
import org.eclipse.jetty.http2.parser.ServerParser;
import org.eclipse.jetty.http2.server.HTTP2ServerConnection;
import org.eclipse.jetty.http2.server.HttpChannelOverHTTP2;
import org.eclipse.jetty.http2.server.HttpTransportOverHTTP2;
import org.eclipse.jetty.io.ByteBufferPool;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpChannelState;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpInput;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.thread.ExecutionStrategy;
import org.eclipse.jetty.util.thread.ExecutionStrategy.Factory;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

class HackHTTP2ServerConnection
        extends HTTP2ServerConnection
{
    private final HttpConfiguration httpConfig;
    private final Queue<HttpChannelOverHTTP2> channels = new ArrayDeque<>();
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong totalResponses = new AtomicLong();

    HackHTTP2ServerConnection(ByteBufferPool byteBufferPool, Executor executor, EndPoint endPoint, HttpConfiguration httpConfig, ServerParser parser, ISession session, int inputBufferSize, Factory executionFactory, ServerSessionListener listener)
    {
        super(byteBufferPool, executor, endPoint, httpConfig, parser, session, inputBufferSize, executionFactory, listener);
        this.httpConfig = httpConfig;
    }

    @Override
    public int getMessagesIn()
    {
        return totalRequests.intValue();
    }

    @Override
    public int getMessagesOut()
    {
        return totalResponses.intValue();
    }

    @Override
    public void onNewStream(Connector connector, IStream stream, HeadersFrame frame)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("Processing {} on {}", frame, stream);
        HttpChannelOverHTTP2 channel = provideHttpChannel(connector, stream);
        Runnable task = channel.onRequest(frame);
        if (task != null)
            offerTask(task, false);
    }

    @Override
    public void push(Connector connector, IStream stream, MetaData.Request request)
    {
        if (LOG.isDebugEnabled())
            LOG.debug("Processing push {} on {}", request, stream);
        HttpChannelOverHTTP2 channel = provideHttpChannel(connector, stream);
        Runnable task = channel.onPushRequest(request);
        if (task != null)
            offerTask(task, true);
    }

    private HttpChannelOverHTTP2 provideHttpChannel(Connector connector, IStream stream)
    {
        HttpChannelOverHTTP2 channel = pollChannel();
        if (channel != null)
        {
            channel.getHttpTransport().setStream(stream);
            if (LOG.isDebugEnabled())
                LOG.debug("Recycling channel {} for {}", channel, this);
        }
        else
        {
            HttpTransportOverHTTP2 transport = new HttpTransportOverHTTP2(connector, this);
            transport.setStream(stream);
            channel = new HackServerHttpChannelOverHTTP2(connector, httpConfig, getEndPoint(), transport);
            if (LOG.isDebugEnabled())
                LOG.debug("Creating channel {} for {}", channel, this);
        }
        stream.setAttribute(IStream.CHANNEL_ATTRIBUTE, channel);
        return channel;
    }

    private void offerChannel(HttpChannelOverHTTP2 channel)
    {
        synchronized (this)
        {
            channels.offer(channel);
        }
    }

    private HttpChannelOverHTTP2 pollChannel()
    {
        synchronized (this)
        {
            return channels.poll();
        }
    }

    private class HackServerHttpChannelOverHTTP2 extends HttpChannelOverHTTP2 implements ExecutionStrategy.Rejectable
    {
        public HackServerHttpChannelOverHTTP2(Connector connector, HttpConfiguration configuration, EndPoint endPoint, HttpTransportOverHTTP2 transport)
        {
            super(connector, configuration, endPoint, transport);
        }

        @Override
        protected HttpInput newHttpInput(HttpChannelState state)
        {
            return new HackHttpInput(state);
        }

        // Below from ServerHttpChannelOverHttp2
        @Override
        public Runnable onRequest(HeadersFrame frame)
        {
            totalRequests.incrementAndGet();
            return super.onRequest(frame);
        }

        @Override
        public void onCompleted()
        {
            totalResponses.incrementAndGet();
            super.onCompleted();
            if (!getStream().isReset()) {
                recycle();
            }
        }

        @Override
        public void recycle()
        {
            getStream().removeAttribute(IStream.CHANNEL_ATTRIBUTE);
            super.recycle();
            offerChannel(this);
        }

        @Override
        public void reject()
        {
            IStream stream = getStream();
            if (LOG.isDebugEnabled()) {
                LOG.debug("HTTP2 Request #{}/{} rejected", stream.getId(), Integer.toHexString(stream.getSession().hashCode()));
            }
            stream.reset(new ResetFrame(stream.getId(), ErrorCode.ENHANCE_YOUR_CALM_ERROR.code), Callback.NOOP);
            // Consume the existing queued data frames to
            // avoid stalling the session flow control.
            consumeInput();
        }
    }
}
