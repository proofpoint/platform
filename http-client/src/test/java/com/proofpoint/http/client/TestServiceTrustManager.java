package com.proofpoint.http.client;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.testng.annotations.Test;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Principal;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Set;

import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestServiceTrustManager
{
    
    private ServiceTrustManager trustManager;
    
    @Test
    public void testConstructor()
    {
        trustManager = new ServiceTrustManager("foo");
        trustManager = new ServiceTrustManager("");
        try {
            trustManager = new ServiceTrustManager(null);
            fail("should have thrown an exception");
        }
        catch(NullPointerException expected) { }
    }
    
    @Test
    public void testGetAcceptedIssuers()
    {
        X509TrustManager first = mock(X509TrustManager.class);
        X509TrustManager second = mock(X509TrustManager.class);
        when(first.getAcceptedIssuers()).thenReturn(new X509Certificate[]{new TestX509Certificate("C=US, ST=California, L=Sunnyvale, O=Proofpoint Inc, OU=Operations, OU=foo-service, CN=m0044130.lab.ppops.net")});
        when(second.getAcceptedIssuers()).thenReturn(new X509Certificate[]{new TestX509Certificate("C=a, ST=b, L=c, O=d, OU=e, CN=f"), new TestX509Certificate("C=g, ST=h, L=i, O=j, OU=k, CN=m")});
        ServiceTrustManager trustManagerSpy = spy(trustManager);
        when(trustManagerSpy.getDefaultTrustManagers()).thenReturn(new X509TrustManager[]{first, second});
        X509Certificate[] certificates = trustManagerSpy.getAcceptedIssuers();
        verify(trustManagerSpy).getDefaultTrustManagers();
        verify(first).getAcceptedIssuers();
        verify(second).getAcceptedIssuers();
        assertEquals(certificates.length, 3);
    }
    
    @Test
    public void testCheckServerTrusted() throws CertificateException
    {
        X509Certificate[] certs = {new TestX509Certificate("C=US, ST=California, L=Sunnyvale, O=Proofpoint Inc, OU=Operations, OU=foo-service, CN=m0044130.lab.ppops.net"), 
            new TestX509Certificate("C=US, ST=California, L=Sunnyvale, O=Proofpoint Inc, OU=Operations, CN=*.lab.ppops.net"), new TestX509Certificate("C=US, O=Thawte Inc., CN=Thawte SSL CA")};
        try {
            trustManager.checkServerTrusted(certs, "whatever");
            fail("should have thrown an exception - checkClientTrusted is not supported");
        }
        catch(IllegalStateException expected) { }
    }
    
    @Test
    public void testCheckClientTrusted() throws CertificateException
    {
        X509TrustManager jreTrustManager = mock(X509TrustManager.class);
        X509TrustManager[] managers = {jreTrustManager};
        X509Certificate[] certs = {new TestX509Certificate("C=US, ST=California, L=Sunnyvale, O=Proofpoint Inc, OU=Operations, OU=foo-service, CN=m0044130.lab.ppops.net"), 
            new TestX509Certificate("C=US, ST=California, L=Sunnyvale, O=Proofpoint Inc, OU=Operations, CN=*.lab.ppops.net"), new TestX509Certificate("C=US, O=Thawte Inc., CN=Thawte SSL CA")};
        ServiceTrustManager trustManagerSpy = spy(trustManager);
        when(trustManagerSpy.getDefaultTrustManagers()).thenReturn(managers);
        trustManagerSpy.checkClientTrusted(certs, "whatever");
        verify(trustManagerSpy).getDefaultTrustManagers();
    }
    
    @Test
    public void testCheckClientTrustedFalse() throws CertificateException
    {
        X509TrustManager jreTrustManager = mock(X509TrustManager.class);
        X509TrustManager[] managers = {jreTrustManager};
        X509Certificate[] certs = {new TestX509Certificate("C=US, ST=California, L=Sunnyvale, O=Proofpoint Inc, OU=Operations, OU=foo-service, CN=m0044130.lab.ppops.net"), 
            new TestX509Certificate("C=US, ST=California, L=Sunnyvale, O=Proofpoint Inc, OU=Operations, CN=*.lab.ppops.net"), new TestX509Certificate("C=US, O=Thawte Inc., CN=Thawte SSL CA")};
        ServiceTrustManager trustManagerSpy = spy(trustManager);
        when(trustManagerSpy.getDefaultTrustManagers()).thenReturn(managers);
        Answer<Void> answer = new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable
            {
                throw new CertificateException("");
            }
            
        };
        doAnswer(answer).when(jreTrustManager).checkServerTrusted(certs, "whatever");
        try {
            trustManagerSpy.checkClientTrusted(certs, "whatever");
        }
        catch(CertificateException expected) { }
        verify(trustManagerSpy).getDefaultTrustManagers();
    }
    
    @Test
    public void testCheckclientNotTrustedWrongServiceName() throws CertificateException
    {
        X509Certificate[] certs = {new TestX509Certificate("C=US, ST=California, L=Sunnyvale, O=Proofpoint Inc, OU=Operations, OU=wrong-service, CN=m0044130.lab.ppops.net"), 
            new TestX509Certificate("C=US, ST=California, L=Sunnyvale, O=Proofpoint Inc, OU=Operations, CN=*.lab.ppops.net"), new TestX509Certificate("C=US, O=Thawte Inc., CN=Thawte SSL CA")};
        try {
            trustManager.checkClientTrusted(certs, "whatever");
            fail("should have thrown an exception");
        }
        catch(CertificateException expected) { }
    }
    
    @Test
    public void testCheckCertificateMatchesServiceName() throws CertificateException
    {
        trustManager = new ServiceTrustManager("foo-service");
        X509Certificate[] certs = {new TestX509Certificate("C=US, ST=California, L=Sunnyvale, O=Proofpoint Inc, OU=Operations, OU=foo-service, CN=m0044130.lab.ppops.net"), 
            new TestX509Certificate("C=US, ST=California, L=Sunnyvale, O=Proofpoint Inc, OU=Operations, CN=*.lab.ppops.net"), new TestX509Certificate("C=US, O=Thawte Inc., CN=Thawte SSL CA")};
        trustManager.checkCertificateMatchesServiceName(certs);
    }
    
    @Test
    public void testCheckCertificateMatchesServiceNameFalse() throws CertificateException
    {
        trustManager = new ServiceTrustManager("foo-service");
        X509Certificate[] certs = {new TestX509Certificate("C=US, ST=California, L=Sunnyvale, O=Proofpoint Inc, OU=Operations, OU=wrong-service, CN=m0044130.lab.ppops.net"), 
            new TestX509Certificate("C=US, ST=California, L=Sunnyvale, O=Proofpoint Inc, OU=Operations, CN=*.lab.ppops.net"), new TestX509Certificate("C=US, O=Thawte Inc., CN=Thawte SSL CA")};
        try {
            trustManager.checkCertificateMatchesServiceName(certs);
            fail("should have thrown an exception");
        }
        catch(CertificateException expected) { }
    }
    
    @Test
    public void testCheckCertificateMatchesServiceNameNoCertificatesAvailable()
    {
        try {
            trustManager.checkCertificateMatchesServiceName(new X509Certificate[]{});
            fail("should have thrown an exception");
        }
        catch(CertificateException expected) { }
        try {
            trustManager.checkCertificateMatchesServiceName(null);
            fail("should have thrown an exception");
        }
        catch(CertificateException expected) { }
    }
    
    static class TestX509Certificate extends X509Certificate {

        private final X500Principal principal;
        
        TestX509Certificate(String dn)
        {
            principal = new X500Principal(dn);
        }
        
        @Override
        public X500Principal getSubjectX500Principal()
        {
            return principal;
        }

        @Override
        public boolean hasUnsupportedCriticalExtension()
        {
            return false;
        }

        @Override
        public Set<String> getCriticalExtensionOIDs()
        {
            return null;
        }

        @Override
        public Set<String> getNonCriticalExtensionOIDs()
        {
            return null;
        }

        @Override
        public byte[] getExtensionValue(String oid)
        {
            return null;
        }

        @Override
        public void checkValidity() throws CertificateExpiredException, CertificateNotYetValidException
        {
            ;
        }

        @Override
        public void checkValidity(Date date) throws CertificateExpiredException, CertificateNotYetValidException
        {
            ;
        }

        @Override
        public int getVersion()
        {
            return 0;
        }

        @Override
        public BigInteger getSerialNumber()
        {
            return null;
        }

        @Override
        public Principal getIssuerDN()
        {
            return null;
        }

        @Override
        public Principal getSubjectDN()
        {
            return null;
        }

        @Override
        public Date getNotBefore()
        {
            return null;
        }

        @Override
        public Date getNotAfter()
        {
            return null;
        }

        @Override
        public byte[] getTBSCertificate() throws CertificateEncodingException
        {
            return null;
        }

        @Override
        public byte[] getSignature()
        {
            return null;
        }

        @Override
        public String getSigAlgName()
        {
            return null;
        }

        @Override
        public String getSigAlgOID()
        {
            return null;
        }

        @Override
        public byte[] getSigAlgParams()
        {
            return null;
        }

        @Override
        public boolean[] getIssuerUniqueID()
        {
            return null;
        }

        @Override
        public boolean[] getSubjectUniqueID()
        {
            return null;
        }

        @Override
        public boolean[] getKeyUsage()
        {
            return null;
        }

        @Override
        public int getBasicConstraints()
        {
            return 0;
        }

        @Override
        public byte[] getEncoded() throws CertificateEncodingException
        {
            return null;
        }

        @Override
        public void verify(PublicKey key) throws CertificateException, NoSuchAlgorithmException, InvalidKeyException,
                NoSuchProviderException, SignatureException
        {
            ;
        }

        @Override
        public void verify(PublicKey key, String sigProvider) throws CertificateException, NoSuchAlgorithmException,
                InvalidKeyException, NoSuchProviderException, SignatureException
        {
            ;
        }

        @Override
        public String toString()
        {
            return null;
        }

        @Override
        public PublicKey getPublicKey()
        {
            return null;
        }
        
    }

}
