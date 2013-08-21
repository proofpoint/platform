package com.proofpoint.http.client;

import com.proofpoint.http.client.TestServiceTrustManager.TestX509Certificate;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class TestServiceTrustStrategy
{
    
    private X509TrustManager trustManager;
    private ServiceTrustStrategy strategy;
    
    @BeforeMethod
    public void setup()
    {
        trustManager = mock(X509TrustManager.class);
        strategy = new ServiceTrustStrategy(trustManager);
    }

    @Test
    public void testTrusted() throws CertificateException 
    {
        X509Certificate[] certs = {new TestX509Certificate("C=US, ST=California, L=Sunnyvale, O=Proofpoint Inc, OU=Operations, OU=foo-service, CN=m0044130.lab.ppops.net"), 
            new TestX509Certificate("C=US, ST=California, L=Sunnyvale, O=Proofpoint Inc, OU=Operations, CN=*.lab.ppops.net"), new TestX509Certificate("C=US, O=Thawte Inc., CN=Thawte SSL CA")};
        String authType = "whatever";
        strategy.isTrusted(certs, authType);
        verify(trustManager).checkClientTrusted(certs, authType);
    }
    
    @Test
    public void testNotTrusted() throws CertificateException 
    {
        X509Certificate[] certs = {new TestX509Certificate("C=US, ST=California, L=Sunnyvale, O=Proofpoint Inc, OU=Operations, OU=foo-service, CN=m0044130.lab.ppops.net"), 
            new TestX509Certificate("C=US, ST=California, L=Sunnyvale, O=Proofpoint Inc, OU=Operations, CN=*.lab.ppops.net"), new TestX509Certificate("C=US, O=Thawte Inc., CN=Thawte SSL CA")};
        String authType = "whatever";
        doThrow(new CertificateException("")).when(trustManager).checkClientTrusted(certs, authType);
        try {
            strategy.isTrusted(certs, authType);
        }
        catch(CertificateException expected) { }
        verify(trustManager).checkClientTrusted(certs, authType);
    }
    
}
