package com.proofpoint.http.client;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Set;

import javax.net.ssl.X509TrustManager;

public class HttpClientBuilder
{

    private HttpClientConfig config = new HttpClientConfig();
    private Set<? extends HttpRequestFilter> requestFilters = Collections.<HttpRequestFilter>emptySet();
    private String serviceName = null;
 
    public HttpClientBuilder config(HttpClientConfig config)
    {
        this.config = checkNotNull(config);
        return this;
    }
    
    public HttpClientBuilder requestFilters(Set<? extends HttpRequestFilter> requestFilters)
    {
        this.requestFilters = checkNotNull(requestFilters);
        return this;
    }
    
    public HttpClientBuilder serviceName(String serviceName)
    {
        // this is ok to be null, no need to check
        this.serviceName = serviceName;
        return this;
    }
    
    String getServiceName()
    {
        return serviceName;
    }
    
    public HttpClient build()
    {
        X509TrustManager trustManager = null;
        if(serviceName!=null) {
            trustManager = new ServiceTrustManager(serviceName);
        }
        return new ApacheHttpClient(config, requestFilters, trustManager);
    }
    
}
