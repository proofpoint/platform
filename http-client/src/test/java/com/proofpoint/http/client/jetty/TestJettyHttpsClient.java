package com.proofpoint.http.client.jetty;

import com.google.common.collect.ImmutableList;
import com.proofpoint.http.client.AbstractHttpClientTest;
import com.proofpoint.http.client.HttpClientConfig;
import com.proofpoint.http.client.HttpRequestFilter;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.http.client.TestingRequestFilter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.net.ssl.SSLHandshakeException;
import java.io.EOFException;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeoutException;

import static com.google.common.io.Resources.getResource;
import static com.proofpoint.http.client.Request.Builder.prepareGet;
import static com.proofpoint.testing.Closeables.closeQuietly;

public class TestJettyHttpsClient
        extends AbstractHttpClientTest
{
    private JettyHttpClient httpClient;
    private JettyIoPool jettyIoPool;

    TestJettyHttpsClient()
    {
        super("localhost", getResource("localhost.keystore").toString());
    }

    @BeforeMethod
    public void setUpHttpClient()
    {
        jettyIoPool = new JettyIoPool("test-shared", new JettyIoPoolConfig());
        httpClient = new JettyHttpClient(createClientConfig(), jettyIoPool, ImmutableList.<HttpRequestFilter>of(new TestingRequestFilter()));
        stats = httpClient.getStats();
    }

    @AfterMethod
    public void tearDownHttpClient()
            throws Exception
    {
        closeQuietly(httpClient);
        closeQuietly(jettyIoPool);
    }

    @Override
    protected HttpClientConfig createClientConfig()
    {
        return new HttpClientConfig()
                .setKeyStorePath(getResource("localhost.keystore").getPath())
                .setKeyStorePassword("changeit")
                .setTrustStorePath(getResource("localhost.truststore").getPath())
                .setTrustStorePassword("changeit");
    }

    @Override
    public <T, E extends Exception> T executeRequest(Request request, ResponseHandler<T, E> responseHandler)
            throws Exception
    {
        return httpClient.execute(request, responseHandler);
    }

    @Override
    public ClientTester clientTester(final HttpClientConfig config)
    {
        config.setKeyStorePath(getResource("localhost.keystore").getPath())
                .setKeyStorePassword("changeit")
                .setTrustStorePath(getResource("localhost.truststore").getPath())
                .setTrustStorePassword("changeit");

        return new ClientTester()
        {
            JettyIoPool jettyIoPool = new JettyIoPool("test-private", new JettyIoPoolConfig());
            JettyHttpClient client = new JettyHttpClient(config, jettyIoPool, ImmutableList.<HttpRequestFilter>of(new TestingRequestFilter()));

            @Override
            public <T, E extends Exception> T executeRequest(Request request, ResponseHandler<T, E> responseHandler)
                    throws Exception
            {
                return client.execute(request, responseHandler);
            }

            @Override
            public void close()
            {
                client.close();
                jettyIoPool.close();
            }
        };
    }

    // https://github.com/eclipse/jetty.project/issues/1199
    @Test(expectedExceptions = {SSLHandshakeException.class, EOFException.class}, enabled = false)
    public void testCertHostnameMismatch()
            throws Exception
    {
        URI uri = new URI("https", null, "127.0.0.1", baseURI.getPort(), "/", null, null);
        Request request = prepareGet()
                .setUri(uri)
                .build();

        executeRequest(request, new ExceptionResponseHandler());
    }

    @Override
    @Test(expectedExceptions = {IOException.class,  IllegalStateException.class})
    public void testConnectReadRequestClose()
            throws Exception
    {
        super.testConnectReadRequestClose();
    }

    @Override
    @Test(expectedExceptions = {IOException.class,  IllegalStateException.class})
    public void testConnectNoReadClose()
            throws Exception
    {
        super.testConnectNoReadClose();
    }

    @Override
    @Test(expectedExceptions = {IOException.class, TimeoutException.class, IllegalStateException.class})
    public void testConnectReadIncompleteClose()
            throws Exception
    {
        super.testConnectReadIncompleteClose();
    }
}
