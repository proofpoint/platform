package com.proofpoint.http.client;

import org.apache.http.conn.ssl.TrustStrategy;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.X509TrustManager;

class ServiceTrustStrategy implements TrustStrategy {

    private final X509TrustManager trustManager;
    
    ServiceTrustStrategy(X509TrustManager trustManager)
    {
        this.trustManager = trustManager;
    }

    @Override
    public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException
    {
        trustManager.checkClientTrusted(chain, authType);
        return true;
    }
    
}