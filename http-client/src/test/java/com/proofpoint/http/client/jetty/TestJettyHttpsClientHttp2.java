package com.proofpoint.http.client.jetty;

import com.proofpoint.http.client.HttpClientConfig;
import org.testng.SkipException;

public class TestJettyHttpsClientHttp2
        extends TestJettyHttpsClient
{
    @Override
    protected HttpClientConfig createClientConfig()
    {
        if (System.getProperty("java.version").startsWith("1.8.")) {
            throw new SkipException("Not implemented for Java 8");
        }
        return super.createClientConfig()
                .setHttp2Enabled(true);
    }
}
