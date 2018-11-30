package com.proofpoint.http.client.jetty;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.proofpoint.http.client.AbstractHttpClientTest;
import com.proofpoint.http.client.HttpClient.HttpResponseFuture;
import com.proofpoint.http.client.HttpClientConfig;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.http.client.TestingRequestFilter;
import com.proofpoint.tracetoken.TraceToken;
import com.proofpoint.units.Duration;
import org.testng.annotations.Test;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.tracetoken.TraceTokenManager.createAndRegisterNewRequestToken;
import static com.proofpoint.tracetoken.TraceTokenManager.getCurrentTraceToken;
import static com.proofpoint.tracetoken.TraceTokenManager.registerTraceToken;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestAsyncJettyHttpClient
        extends AbstractHttpClientTest
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return new HttpClientConfig()
                .setHttp2Enabled(false);
    }

    @Override
    public ClientTester clientTester(final HttpClientConfig config)
    {
        return new ClientTester()
        {
            JettyHttpClient client = new JettyHttpClient("test-private", config, ImmutableList.of(new TestingRequestFilter()));

            @Override
            public <T, E extends Exception> T executeRequest(Request request, ResponseHandler<T, E> responseHandler)
                    throws Exception
            {
                return executeAsync(client, request, responseHandler);
            }

            @Override
            public RequestStats getRequestStats()
            {
                return client.getStats();
            }

            @Override
            public void close()
            {
                client.close();
            }
        };
    }

    @Test
    public void testFutureExceptionSeesTraceToken()
            throws Exception
    {
        CountDownLatch responseHandlerLatch = new CountDownLatch(1);
        CountDownLatch listenerLatch = new CountDownLatch(1);
        createAndRegisterNewRequestToken("somekey", "somevalue");
        TraceToken token = getCurrentTraceToken();
        int port = findUnusedPort();

        HttpClientConfig config = createClientConfig();
        config.setConnectTimeout(new Duration(5, MILLISECONDS));

        try (JettyHttpClient client = new JettyHttpClient("test-private", config, ImmutableList.of(new TestingRequestFilter()))) {
            Request request = prepareGet()
                    .setUri(new URI("http", null, "127.0.0.1", port, "/", null, null))
                    .build();

            HttpResponseFuture<Void> future = null;
            try {
                future = client.executeAsync(request, new ResponseHandler<Void, RuntimeException>()
            {
                @Override
                public Void handleException(Request request1, Exception exception)
                        throws RuntimeException
                {
                    try {
                        responseHandlerLatch.await();
                    }
                    catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    throw new RuntimeException(exception);
                }

                @Override
                public Void handle(Request request1, Response response)
                        throws RuntimeException
                {
                    fail("unexpected response");
                    return null;
                }
            });
            }
            catch (Exception e) {
                fail("Unexpected exception", e);
            }

            AtomicReference<TraceToken> callbackToken = new AtomicReference<>();
            future.addListener(() -> {
                callbackToken.set(getCurrentTraceToken());
                listenerLatch.countDown();
            }, MoreExecutors.directExecutor());

            registerTraceToken(null);
            responseHandlerLatch.countDown();

            listenerLatch.await();
            assertEquals(callbackToken.get(), token);
        }
    }

    @Test
    public void testFutureResponseSeesTraceToken()
            throws Exception
    {
        CountDownLatch responseHandlerLatch = new CountDownLatch(1);
        CountDownLatch listenerLatch = new CountDownLatch(1);
        createAndRegisterNewRequestToken("somekey", "somevalue");
        TraceToken token = getCurrentTraceToken();
        Request request = prepareGet()
                .setUri(baseURI)
                .build();

        HttpResponseFuture<Void> future = null;
        try (JettyHttpClient client = new JettyHttpClient("test-private", createClientConfig(), ImmutableList.of(new TestingRequestFilter()))) {
            future = client.executeAsync(request, new ResponseHandler<Void, RuntimeException>()
        {
            @Override
            public Void handleException(Request request1, Exception exception)
                    throws RuntimeException
            {
                fail("unexpected request exception", exception);
                return null;
            }

            @Override
            public Void handle(Request request1, Response response)
                    throws RuntimeException
            {
                try {
                    responseHandlerLatch.await();
                }
                catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                return null;
            }
        });
        }
        catch (Exception e) {
            fail("Unexpected exception", e);
        }

        AtomicReference<TraceToken> callbackToken = new AtomicReference<>();
        future.addListener(() -> {
            callbackToken.set(getCurrentTraceToken());
            listenerLatch.countDown();
        }, MoreExecutors.directExecutor());

        registerTraceToken(null);
        responseHandlerLatch.countDown();

        listenerLatch.await();
        assertEquals(callbackToken.get(), token);
    }
}
