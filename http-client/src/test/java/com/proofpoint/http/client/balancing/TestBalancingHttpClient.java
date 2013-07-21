package com.proofpoint.http.client.balancing;

import com.proofpoint.http.client.BodyGenerator;
import com.proofpoint.http.client.HttpClient;
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

public class TestBalancingHttpClient
    extends AbstractTestBalancingHttpClient<HttpClient>
{
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
        httpClient = createTestingClient();
        balancingHttpClient = createBalancingHttpClient();
        bodyGenerator = mock(BodyGenerator.class);
        request = preparePut().setUri(URI.create("v1/service")).setBodyGenerator(bodyGenerator).build();
        response = mock(Response.class);
        when(response.getStatusCode()).thenReturn(204);
    }

    @Override
    protected TestingHttpClient createTestingClient()
    {
        return new TestingHttpClient("PUT");
    }

    @Override
    protected BalancingHttpClient createBalancingHttpClient()
    {
        return new BalancingHttpClient(serviceBalancer, httpClient,
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

        assertHandlerExceptionThrown(responseHandler, handlerException);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(responseHandler).handleException(same(request), captor.capture());
        assertSame(captor.getValue(), balancerException, "Exception passed to ResponseHandler");
        verifyNoMoreInteractions(responseHandler);
    }

    @Override
    protected void assertHandlerExceptionThrown(ResponseHandler responseHandler, RuntimeException handlerException)
            throws Exception
    {
        try {
            balancingHttpClient.execute(request, responseHandler);
            fail("Exception not thrown");
        }
        catch (RuntimeException e) {
            assertSame(e, handlerException, "Exception thrown by BalancingHttpClient");
        }
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

        assertHandlerExceptionThrown(responseHandler, handlerException);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(responseHandler).handleException(same(request), captor.capture());
        assertSame(captor.getValue(), balancerException, "Exception passed to ResponseHandler");
        verifyNoMoreInteractions(responseHandler);
    }

    @Test
    public void testGetStats()
    {
        RequestStats requestStats = new RequestStats();
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.getStats()).thenReturn(requestStats);

        balancingHttpClient = new BalancingHttpClient(serviceBalancer, mockClient, new BalancingHttpClientConfig());
        assertSame(balancingHttpClient.getStats(), requestStats);

        verify(mockClient).getStats();
        verifyNoMoreInteractions(mockClient, serviceBalancer);
    }

    @Test
    public void testClose()
    {
        HttpClient mockClient = mock(HttpClient.class);

        balancingHttpClient = new BalancingHttpClient(serviceBalancer, mockClient, new BalancingHttpClientConfig());
        balancingHttpClient.close();

        verify(mockClient).close();
        verifyNoMoreInteractions(mockClient, serviceBalancer);
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".* is not a relative URI")
    public void testUriWithScheme()
            throws Exception
    {
        request = preparePut().setUri(new URI("http", null, "/v1/service", null)).setBodyGenerator(bodyGenerator).build();
        issueRequest();
    }

    @Override
    protected void issueRequest()
            throws Exception
    {
        balancingHttpClient.execute(request, mock(ResponseHandler.class));
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".* has a host component")
    public void testUriWithHost()
            throws Exception
    {
        request = preparePut().setUri(new URI(null, "example.com", "v1/service", null)).setBodyGenerator(bodyGenerator).build();
        issueRequest();
    }

    @Test(expectedExceptions = IllegalArgumentException.class, expectedExceptionsMessageRegExp = ".* path starts with '/'")
    public void testUriWithAbsolutePath()
            throws Exception
    {
        request = preparePut().setUri(new URI(null, null, "/v1/service", null)).setBodyGenerator(bodyGenerator).build();
        issueRequest();
    }

    class TestingHttpClient
            implements HttpClient, TestingClient
    {

        private String method;
        private List<URI> uris = new ArrayList<>();
        private List<Object> responses = new ArrayList<>();

        TestingHttpClient(String method)
        {
            this.method = method;
            checkArgument(uris.size() == responses.size(), "uris same size as responses");
        }

        public TestingHttpClient expectCall(String uri, Response response)
        {
            return expectCall(URI.create(uri), response);
        }

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

        public void assertDone()
        {
            assertEquals(uris.size(), 0, "all expected calls made");
        }

        @Override
        public <T, E extends Exception> T execute(Request request, ResponseHandler<T, E> responseHandler)
                throws E
        {
            assertTrue(uris.size() > 0, "call was expected");
            assertEquals(request.getMethod(), method, "request method");
            assertEquals(request.getUri(), uris.remove(0), "request uri");
            assertEquals(request.getBodyGenerator(), bodyGenerator, "request body generator");

            Object response = responses.remove(0);
            if (response instanceof Exception) {
                return responseHandler.handleException(request, (Exception) response);
            }
            return responseHandler.handle(request, (Response) response);
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
    }
}
