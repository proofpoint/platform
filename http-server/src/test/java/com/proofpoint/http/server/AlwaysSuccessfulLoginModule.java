package com.proofpoint.http.server;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import java.security.Principal;
import java.util.Map;

public class AlwaysSuccessfulLoginModule
    implements LoginModule
{
    private Subject subject;
    private CallbackHandler callbackHandler;
    private Principal user;

    public static class DummyPrincipal
        implements Principal
    {
        private final String name;

        public DummyPrincipal(String name)
        {
            this.name = name;
        }

        @Override
        public String getName()
        {
            return name;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            DummyPrincipal that = (DummyPrincipal) o;

            if (name != null ? !name.equals(that.name) : that.name != null) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            return name != null ? name.hashCode() : 0;
        }
    }

    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> stringMap, Map<String, ?> stringMap1)
    {
        this.subject = subject;
        this.callbackHandler = callbackHandler;
    }

    @Override
    public boolean login()
            throws LoginException
    {
        NameCallback nameCallback = new NameCallback("user name: ");
        PasswordCallback passwordCallback = new PasswordCallback("password: ", false);

        Callback[] callbacks = new Callback[2];
        callbacks[0] = nameCallback;
        callbacks[1] = passwordCallback;

        try {
            callbackHandler.handle(callbacks);
        }
        catch (Exception e) {
            throw new LoginException(String.format("Callback handling bombed out [%s]", e.getMessage()));
        }

        user = new DummyPrincipal(nameCallback.getName());
        return true;
    }

    @Override
    public boolean commit()
            throws LoginException
    {
        subject.getPrincipals().add(user);
        return true;
    }

    @Override
    public boolean abort()
            throws LoginException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean logout()
            throws LoginException
    {
        subject.getPrincipals().remove(user);
        return true;
    }
}
