package com.proofpoint.http.client.jetty;

import com.proofpoint.http.client.AbstractHttpClientTest;
import com.proofpoint.http.client.HttpClientConfig;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.RequestStats;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.http.client.TestingRequestFilter;

import java.util.List;

public class TestJettyHttpClient
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
            JettyHttpClient client = new JettyHttpClient("test-private", config, List.of(new TestingRequestFilter()));

            @Override
            public <T, E extends Exception> T executeRequest(Request request, ResponseHandler<T, E> responseHandler)
                    throws Exception
            {
                return client.execute(request, responseHandler);
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
}
