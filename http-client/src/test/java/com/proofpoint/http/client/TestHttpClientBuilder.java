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

public class TestHttpClientBuilder
{
    
    private HttpClientBuilder builder;
    
    @BeforeMethod
    public void setup()
    {
        builder = new HttpClientBuilder();
    }
    
    @Test
    public void testNullsNotAllowed()
    {
        try {
            builder.config(null);
            fail("should have thrown an exception");
        }
        catch(NullPointerException expected) { }
        try {
            builder.requestFilters(null);
            fail("should have thrown an exception");
        }
        catch(NullPointerException expected) { }
    }
    
    @Test
    public void testServiceNameCanBeNull()
    {
        assertNull(builder.getServiceName());
        builder.serviceName("foo-service");
        assertEquals(builder.getServiceName(), "foo-service");
        builder.serviceName(null);
        assertNull(builder.getServiceName());
    }
    
    @Test
    public void testBuild() throws Exception 
    {
        builder.serviceName("wrong");
        ApacheHttpClient client = (ApacheHttpClient)builder.build();
        assertNotNull(client);
        Field field = ApacheHttpClient.class.getDeclaredField("httpClient");
        field.setAccessible(true);
        org.apache.http.impl.client.DefaultHttpClient apacheClient = (org.apache.http.impl.client.DefaultHttpClient)field.get(client);
        Scheme scheme = apacheClient.getConnectionManager().getSchemeRegistry().get("https");
        SSLSocketFactory socketFactory = (SSLSocketFactory)scheme.getSchemeSocketFactory();
        assertNotNull(socketFactory);
    }

}
