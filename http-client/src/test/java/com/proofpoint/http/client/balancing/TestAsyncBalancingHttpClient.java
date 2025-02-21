package com.proofpoint.http.client.balancing;

import com.google.common.util.concurrent.AbstractFuture;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.HttpClient.HttpResponseFuture;
import com.proofpoint.http.client.LimitedRetryable;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.http.client.SyncToAsyncWrapperClient;
import org.testng.annotations.Test;

import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.proofpoint.concurrent.Threads.daemonThreadsNamed;
import static com.proofpoint.http.client.testing.BodySourceTester.writeBodySourceTo;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestAsyncBalancingHttpClient
    extends AbstractTestBalancingHttpClient<SyncToAsyncWrapperClient>
{
    @Override
    protected TestingHttpClient createTestingClient()
    {
        return new TestingHttpClient("PUT");
    }

    @Override
    protected SyncToAsyncWrapperClient createBalancingHttpClient()
    {
        ScheduledExecutorService realExecutor = newSingleThreadScheduledExecutor(daemonThreadsNamed("test-async-executor"));
        ScheduledExecutorService retryExecutor = mock(ScheduledExecutorService.class);
        when(retryExecutor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenAnswer((invocation -> realExecutor.schedule((Runnable) invocation.getArguments()[0], 0, SECONDS)));
        return new SyncToAsyncWrapperClient(
                new BalancingHttpClient(serviceBalancer,
                        httpClient,
                        balancingHttpClientConfig,
                        retryExecutor,
                        testingTicker));
    }

    @Override
    protected void assertHandlerExceptionThrown(ResponseHandler responseHandler, RuntimeException handlerException)
            throws Exception
    {
        HttpResponseFuture future = balancingHttpClient.executeAsync(request, responseHandler);
        try {
            future.get();
            fail("Exception not thrown");
        }
        catch (ExecutionException e) {
            assertSame(e.getCause(), handlerException, "Exception thrown by BalancingHttpClient");
        }
    }

    @Override
    protected void issueRequest()
    {
        balancingHttpClient.executeAsync(request, mock(ResponseHandler.class));
    }

    @Test
    public void testGetStats()
    {
        RequestStats requestStats = new RequestStats();
        HttpClient mockClient = mock(HttpClient.class);
        ScheduledExecutorService retryExecutor = mock(ScheduledExecutorService.class);
        when(mockClient.getStats()).thenReturn(requestStats);

        balancingHttpClient = new SyncToAsyncWrapperClient(
                new BalancingHttpClient(serviceBalancer, mockClient, new BalancingHttpClientConfig(), retryExecutor));
        assertSame(balancingHttpClient.getStats(), requestStats);

        verify(mockClient).getStats();
        verifyNoMoreInteractions(mockClient, serviceBalancer);
    }

    @Test
    public void testClose()
    {
        HttpClient mockClient = mock(HttpClient.class);
        ScheduledExecutorService retryExecutor = mock(ScheduledExecutorService.class);

        balancingHttpClient = new SyncToAsyncWrapperClient(
                new BalancingHttpClient(serviceBalancer, mockClient, new BalancingHttpClientConfig(), retryExecutor));
        balancingHttpClient.close();

        verify(retryExecutor).shutdown();
        verify(retryExecutor).shutdownNow();
        verifyNoMoreInteractions(retryExecutor);

        verify(mockClient).close();
        verifyNoMoreInteractions(mockClient, serviceBalancer);
    }

    // TODO tests for interruption and cancellation

    class TestingHttpClient
            implements HttpClient, TestingClient
    {
        private String method;
        private List<URI> uris = new ArrayList<>();
        private List<Object> responses = new ArrayList<>();
        private boolean skipBodyGenerator = false;

        TestingHttpClient(String method)
        {
            this.method = method;
            checkArgument(uris.size() == responses.size(), "uris same size as responses");
        }

        @Override
        public TestingHttpClient expectCall(String uri, Response response)
        {
            return expectCall(URI.create(uri), response);
        }

        @Override
        public TestingHttpClient expectCall(String uri, Exception exception)
        {
            return expectCall(URI.create(uri), exception);
        }

        private TestingHttpClient expectCall(URI uri, Object response)
        {
            uris.add(uri);
            responses.add(response);
            return this;
        }

        @Override
        public TestingClient firstCallNoBodyGenerator()
        {
            skipBodyGenerator = true;
            return this;
        }

        @Override
        public void assertDone()
        {
            assertEquals(uris.size(), 0, "all expected calls made");
        }

        @Override
        public <T, E extends Exception> HttpResponseFuture<T> executeAsync(Request request, ResponseHandler<T, E> responseHandler)
        {
            assertTrue(!uris.isEmpty(), "call was expected");
            assertEquals(request.getMethod(), method, "request method");
            assertEquals(request.getUri(), uris.remove(0), "request uri");
            assertEquals(request.getBodySource(), bodySource, "request body generator");

            if (skipBodyGenerator) {
                skipBodyGenerator = false;
            }
            else if (bodySource instanceof LimitedRetryable) {
                try {
                    writeBodySourceTo(bodySource, new OutputStream()
                    {
                        @Override
                        public void write(int b)
                        {
                        }
                    });
                }
                catch (Exception e) {
                    fail("BodySource exception", e);
                }
            }

            Object response = responses.remove(0);
            // TODO: defer availability of return values ?
            if (response instanceof Exception x) {
                try {
                    return new ImmediateAsyncHttpFuture<>(responseHandler.handleException(request, x));
                }
                catch (Exception e) {
                    return new ImmediateFailedAsyncHttpFuture<>((E) e);
                }
            }

            try {
                return new ImmediateAsyncHttpFuture<>(responseHandler.handle(request, (Response) response));
            }
            catch (Exception e) {
                return new ImmediateFailedAsyncHttpFuture<>((E) e);
            }
        }

        @Override
        public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
                throws E
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public RequestStats getStats()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isClosed()
        {
            throw new UnsupportedOperationException();
        }

        private class ImmediateAsyncHttpFuture<T, E extends Exception>
                extends AbstractFuture<T>
                implements HttpResponseFuture<T>
        {
            ImmediateAsyncHttpFuture(T value)
            {
                set(value);
            }

            @Override
            public String getState()
            {
                throw new UnsupportedOperationException();
            }
        }

        private class ImmediateFailedAsyncHttpFuture<T, E extends Exception>
                extends AbstractFuture<T>
                implements HttpResponseFuture<T>
        {
            private final E exception;

            ImmediateFailedAsyncHttpFuture(E exception)
            {
                this.exception = exception;
                setException(exception);
            }

            @Override
            public String getState()
            {
                throw new UnsupportedOperationException();
            }
        }
    }
}
