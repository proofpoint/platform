package com.proofpoint.http.client.jetty;

import com.proofpoint.http.client.HttpClientConfig;

public class TestJettyHttpsClientHttp2
        extends TestJettyHttpsClient
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return super.createClientConfig()
                .setHttp2Enabled(true);
    }
}
