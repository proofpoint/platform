package com.proofpoint.http.client.jetty;

import com.proofpoint.http.client.HttpClientConfig;
import org.testng.SkipException;
import org.testng.annotations.Test;

public class TestJettyHttpsClientHttp2
        extends TestJettyHttpsClient
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        return super.createClientConfig()
                .setHttp2Enabled(true);
    }

    @Override
    @Test
    public void testConnectReadRequestClose()
    {
        throw new SkipException("Would need to extend FakeServer to process more HTTP/2 protocol");
    }

}
