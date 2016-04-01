package com.proofpoint.http.client.jetty;

import com.proofpoint.http.client.HttpClientConfig;

public class TestAsyncJettyHttpClientHttp2
        extends TestAsyncJettyHttpClient
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return super.createClientConfig()
                .setHttp2Enabled(true);
    }
}
