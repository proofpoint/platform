package com.proofpoint.http.client;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.List;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayList;

public class ServiceTrustManager implements X509TrustManager
{

    private final String serviceName;
    
    
    public ServiceTrustManager(String serviceName)
    {
        this.serviceName = checkNotNull(serviceName);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException
    {
        checkCertificateMatchesServiceName(chain);
        X509TrustManager[] trustManagers = getDefaultTrustManagers();
        for(X509TrustManager trustManager: trustManagers) {
            trustManager.checkServerTrusted(chain, authType);
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException
    {
        throw new IllegalStateException("not supported");
    }
    
    protected void checkCertificateMatchesServiceName(X509Certificate[] chain) throws CertificateException
    {
        if(chain==null || chain.length<1) {
            throw new CertificateException("no certificates available");
        }
        X509Certificate certificate = chain[0];
        X500Principal principal = certificate.getSubjectX500Principal();
        boolean matched = false;
        try {
            List<Rdn> rdns = new LdapName(principal.getName()).getRdns();
            Iterator<Rdn> iter = rdns.iterator();
            while(iter.hasNext() && !matched) {
                Rdn rdn = iter.next();
                if("OU".equalsIgnoreCase(rdn.getType()) && serviceName.equals(rdn.getValue())) {
                    matched = true;
                }
            }
        }
        catch (InvalidNameException e) {
            ;
        }
        if(!matched) {
            throw new CertificateException("no certificate matched the specified service name");
        }
    }
    
    protected X509TrustManager[] getDefaultTrustManagers()
    { 
        List<X509TrustManager> results = newArrayList();
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init((KeyStore)null);
            TrustManager[] managers = trustManagerFactory.getTrustManagers();
            for(TrustManager trustManager: managers) {
                if(trustManager instanceof X509TrustManager) {
                    results.add((X509TrustManager)trustManager);
                }
            }
        }
        catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        catch (KeyStoreException e) {
            e.printStackTrace();
        }
        X509TrustManager[] trustManagers = new X509TrustManager[results.size()];
        return results.toArray(trustManagers);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers()
    {
        List<X509Certificate> list = newArrayList();
        X509TrustManager[] trustManagers = getDefaultTrustManagers();
        for(X509TrustManager trustManager: trustManagers) {
            X509Certificate[] certificates = trustManager.getAcceptedIssuers();
            for(X509Certificate certificate: certificates) {
                list.add(certificate);
            }
        }
        X509Certificate[] result = new X509Certificate[list.size()];
        return list.toArray(result);
    }

    String getServiceName()
    {
        return serviceName;
    }
    
}
