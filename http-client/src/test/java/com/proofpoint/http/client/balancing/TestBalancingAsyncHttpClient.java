package com.proofpoint.http.client.balancing;

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.CheckedFuture;
import com.proofpoint.http.client.AsyncHttpClient;
import com.proofpoint.http.client.BodyGenerator;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.ConnectException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkArgument;
import static com.proofpoint.http.client.Request.Builder.preparePut;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class TestBalancingAsyncHttpClient
    extends AbstractTestBalancingHttpClient<AsyncHttpClient>
{
    private TestingAsyncHttpClient asyncHttpClient;

    @BeforeMethod
    protected void setUp()
            throws Exception
    {
        serviceBalancer = mock(HttpServiceBalancer.class);
        serviceAttempt1 = mock(HttpServiceAttempt.class);
        serviceAttempt2 = mock(HttpServiceAttempt.class);
        serviceAttempt3 = mock(HttpServiceAttempt.class);
        when(serviceBalancer.createAttempt()).thenReturn(serviceAttempt1);
        when(serviceAttempt1.getUri()).thenReturn(URI.create("http://s1.example.com"));
        when(serviceAttempt1.next()).thenReturn(serviceAttempt2);
        when(serviceAttempt2.getUri()).thenReturn(URI.create("http://s2.example.com/"));
        when(serviceAttempt2.next()).thenReturn(serviceAttempt3);
        when(serviceAttempt3.getUri()).thenReturn(URI.create("http://s1.example.com"));
        when(serviceAttempt3.next()).thenThrow(new AssertionError("Unexpected call to serviceAttempt3.next()"));
        asyncHttpClient = new TestingAsyncHttpClient("PUT");
        httpClient = asyncHttpClient;
        balancingHttpClient = createBalancingHttpClient();
        bodyGenerator = mock(BodyGenerator.class);
        request = preparePut().setUri(URI.create("v1/service")).setBodyGenerator(bodyGenerator).build();
        response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(204);
    }

    @Override
    protected BalancingAsyncHttpClient createBalancingHttpClient()
    {
        return new BalancingAsyncHttpClient(serviceBalancer, asyncHttpClient,
                new BalancingHttpClientConfig().setMaxAttempts(3));
    }

    @Test
    public void testCreateAttemptException()
            throws Exception
    {
        serviceBalancer = mock(HttpServiceBalancer.class);
        RuntimeException balancerException = new RuntimeException("test balancer exception");
        when(serviceBalancer.createAttempt()).thenThrow(balancerException);

        balancingHttpClient = createBalancingHttpClient();

        ResponseHandler responseHandler = mock(ResponseHandler.class);
        RuntimeException handlerException = new RuntimeException("test responseHandler exception");
        when(responseHandler.handleException(any(Request.class), any(Exception.class))).thenThrow(handlerException);

        CheckedFuture future = balancingHttpClient.executeAsync(request, responseHandler);
        try {
            future.checkedGet();
            fail("Exception not thrown");
        }
        catch (RuntimeException e) {
            assertSame(e, handlerException, "Exception thrown by BalancingAsyncHttpClient");
        }

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(responseHandler).handleException(same(request), captor.capture());
        assertSame(captor.getValue(), balancerException, "Exception passed to ResponseHandler");
        verifyNoMoreInteractions(responseHandler);
    }

    @Test
    public void testNextAttemptException()
            throws Exception
    {
        httpClient.expectCall("http://s1.example.com/v1/service", new ConnectException());

        serviceBalancer = mock(HttpServiceBalancer.class);
        serviceAttempt1 = mock(HttpServiceAttempt.class);
        when(serviceBalancer.createAttempt()).thenReturn(serviceAttempt1);
        when(serviceAttempt1.getUri()).thenReturn(URI.create("http://s1.example.com"));
        RuntimeException balancerException = new RuntimeException("test balancer exception");
        when(serviceAttempt1.next()).thenThrow(balancerException);

        balancingHttpClient = createBalancingHttpClient();

        ResponseHandler responseHandler = mock(ResponseHandler.class);
        RuntimeException handlerException = new RuntimeException("test responseHandler exception");
        when(responseHandler.handleException(any(Request.class), any(Exception.class))).thenThrow(handlerException);

        CheckedFuture future = balancingHttpClient.executeAsync(request, responseHandler);
        try {
            future.checkedGet();
            fail("Exception not thrown");
        }
        catch (RuntimeException e) {
            assertSame(e, handlerException, "Exception thrown by BalancingHttpClient");
        }

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(responseHandler).handleException(same(request), captor.capture());
        assertSame(captor.getValue(), balancerException, "Exception passed to ResponseHandler");
        verifyNoMoreInteractions(responseHandler);
    }

    @Test
    public void testGetStats()
    {
        RequestStats requestStats = new RequestStats();
        AsyncHttpClient mockClient = mock(AsyncHttpClient.class);
        when(mockClient.getStats()).thenReturn(requestStats);

        balancingHttpClient = new BalancingAsyncHttpClient(serviceBalancer, mockClient, new BalancingHttpClientConfig());
        assertSame(balancingHttpClient.getStats(), requestStats);

        verify(mockClient).getStats();
        verifyNoMoreInteractions(mockClient, serviceBalancer);
    }

    @Test
    public void testClose()
    {
        AsyncHttpClient mockClient = mock(AsyncHttpClient.class);

        balancingHttpClient = new BalancingAsyncHttpClient(serviceBalancer, mockClient, new BalancingHttpClientConfig());
        balancingHttpClient.close();

        verify(mockClient).close();
        verifyNoMoreInteractions(mockClient, serviceBalancer);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".* is not a relative URI")
    public void testUriWithScheme()
            throws Exception
    {
        request = preparePut().setUri(new URI("http", null, "/v1/service", null)).setBodyGenerator(bodyGenerator).build();
        balancingHttpClient.executeAsync(request, mock(ResponseHandler.class));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".* has a host component")
    public void testUriWithHost()
            throws Exception
    {
        request = preparePut().setUri(new URI(null, "example.com", "v1/service", null)).setBodyGenerator(bodyGenerator).build();
        balancingHttpClient.executeAsync(request, mock(ResponseHandler.class));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".* path starts with '/'")
    public void testUriWithAbsolutePath()
            throws Exception
    {
        request = preparePut().setUri(new URI(null, null, "/v1/service", null)).setBodyGenerator(bodyGenerator).build();
        balancingHttpClient.executeAsync(request, mock(ResponseHandler.class));
    }

    // TODO tests for interruption and cancellation

    class TestingAsyncHttpClient
            implements AsyncHttpClient, TestingClient
    {

        private String method;
        private List<URI> uris = new ArrayList<>();
        private List<Object> responses = new ArrayList<>();

        TestingAsyncHttpClient(String method)
        {
            this.method = method;
            checkArgument(uris.size() == responses.size(), "uris same size as responses");
        }

        public TestingAsyncHttpClient expectCall(String uri, Response response)
        {
            return expectCall(URI.create(uri), response);
        }

        public TestingAsyncHttpClient expectCall(String uri, Exception exception)
        {
            return expectCall(URI.create(uri), exception);
        }

        private TestingAsyncHttpClient expectCall(URI uri, Object response)
        {
            uris.add(uri);
            responses.add(response);
            return this;
        }

        public void assertDone()
        {
            assertEquals(uris.size(), 0, "all expected calls made");
        }

        @Override
        public <T, E extends Exception> AsyncHttpResponseFuture<T, E> executeAsync(Request request, ResponseHandler<T, E> responseHandler)
        {
            assertTrue(uris.size() > 0, "call was expected");
            assertEquals(request.getMethod(), method, "request method");
            assertEquals(request.getUri(), uris.remove(0), "request uri");
            assertEquals(request.getBodyGenerator(), bodyGenerator, "request body generator");

            Object response = responses.remove(0);
            // TODO: defer availability of return values ?
            if (response instanceof Exception) {
                try {
                    return new ImmediateAsyncHttpFuture<>(responseHandler.handleException(request, (Exception) response));
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

        private class ImmediateAsyncHttpFuture<T, E extends Exception>
                extends AbstractFuture<T>
                implements AsyncHttpResponseFuture<T, E>
        {
            public ImmediateAsyncHttpFuture(T value)
            {
                set(value);
            }

            @Override
            public String getState()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public T checkedGet()
                    throws E
            {
                try {
                    return get();
                }
                catch (InterruptedException | ExecutionException ignored) {
                    throw new UnsupportedOperationException();
                }
            }

            @Override
            public T checkedGet(long timeout, TimeUnit unit)
                    throws TimeoutException, E
            {
                return checkedGet();
            }
        }

        private class ImmediateFailedAsyncHttpFuture<T, E extends Exception>
                extends AbstractFuture<T>
                implements AsyncHttpResponseFuture<T, E>
        {

            private final E exception;

            public ImmediateFailedAsyncHttpFuture(E exception)
            {
                this.exception = exception;
                setException(exception);
            }

            @Override
            public String getState()
            {
                throw new UnsupportedOperationException();
            }

            @Override
            public T checkedGet()
                    throws E
            {
                throw exception;
            }

            @Override
            public T checkedGet(long timeout, TimeUnit unit)
                    throws TimeoutException, E
            {
                throw exception;
            }
        }
    }
}
