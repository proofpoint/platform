package com.proofpoint.http.server;

import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jetty.plus.jaas.JAASLoginService;
import org.eclipse.jetty.security.DefaultIdentityService;

public class JaasLoginServiceProvider
        implements Provider<JAASLoginService>
{
    private final String loginModuleName;

    @Inject
    public JaasLoginServiceProvider(HttpServerConfig config)
    {
        if (config.getJaasConfigFile() != null) {
            this.loginModuleName = config.getJaasLoginModuleName();
            System.setProperty("java.security.auth.login.config", config.getJaasConfigFile());
        }
        else {
            this.loginModuleName = null;
        }
    }

    @Override
    public JAASLoginService get()
    {
        if (this.loginModuleName == null) {
            return null;
        }

        JAASLoginService service = new JAASLoginService();

        service.setName(HttpServerModule.REALM_NAME);
        service.setLoginModuleName(this.loginModuleName);
        service.setIdentityService(new DefaultIdentityService());

        return service;
    }
}
