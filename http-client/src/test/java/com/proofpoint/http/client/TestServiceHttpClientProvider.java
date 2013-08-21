package com.proofpoint.http.client;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

import java.lang.reflect.Field;

import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class TestServiceHttpClientProvider
{
    
    private ServiceHttpClientProvider provider;
    
    @BeforeMethod
    public void setup()
    {
        provider = new ServiceHttpClientProvider();
    }
    
    @Test
    public void testNullsNotAllowed()
    {
        try {
            provider.config(null);
            fail("should have thrown an exception");
        }
        catch(NullPointerException expected) { }
        try {
            provider.requestFilters(null);
            fail("should have thrown an exception");
        }
        catch(NullPointerException expected) { }
    }
    
    @Test
    public void testServiceNameCanBeNull()
    {
        assertNull(provider.getServiceName());
        provider.serviceName("foo-service");
        assertEquals(provider.getServiceName(), "foo-service");
        provider.serviceName(null);
        assertNull(provider.getServiceName());
    }
    
    @Test
    public void testBuild() throws Exception 
    {
        provider.serviceName("wrong");
        ApacheHttpClient client = (ApacheHttpClient)provider.get();
        assertNotNull(client);
        Field field = ApacheHttpClient.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        org.apache.http.impl.client.DefaultHttpClient apacheClient = (org.apache.http.impl.client.DefaultHttpClient)field.get(client);
        Scheme scheme = apacheClient.getConnectionManager().getSchemeRegistry().get("https");
        SSLSocketFactory socketFactory = (SSLSocketFactory)scheme.getSchemeSocketFactory();
        assertNotNull(socketFactory);
    }

}
