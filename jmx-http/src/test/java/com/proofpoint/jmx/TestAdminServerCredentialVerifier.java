/*
 * Copyright 2015 Proofpoint, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.proofpoint.jmx;

import com.proofpoint.http.server.HttpServerConfig;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.SecurityContext;
import org.testng.annotations.Test;

import java.security.Principal;
import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

public class TestAdminServerCredentialVerifier
{
    private static final AdminServerConfig ADMIN_SERVER_CONFIG = new AdminServerConfig().setUsername("foo").setPassword("bar");
    private static final HttpServerConfig HTTP_SERVER_CONFIG = new HttpServerConfig().setHttpsEnabled(true);
    private static final SecurityContext SECURITY_CONTEXT_DENY = new SecurityContext()
    {
        @Override
        public Principal getUserPrincipal()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isUserInRole(String role)
        {
            if ("server.admin".equals(role)) {
                return false;
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSecure()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getAuthenticationScheme()
        {
            throw new UnsupportedOperationException();
        }
    };
    private static final SecurityContext SECURITY_CONTEXT_OPTIONAL = new SecurityContext()
    {
        @Override
        public Principal getUserPrincipal()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isUserInRole(String role)
        {
            if ("server.admin".equals(role)) {
                return true;
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSecure()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getAuthenticationScheme()
        {
            return "none";
        }
    };
    private static final SecurityContext SECURITY_CONTEXT_ALLOW = new SecurityContext()
    {
        @Override
        public Principal getUserPrincipal()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isUserInRole(String role)
        {
            if ("server.admin".equals(role)) {
                return true;
            }
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isSecure()
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public String getAuthenticationScheme()
        {
            return "testing";
        }
    };

    @Test
    public void testHttpsDisabled()
    {
        try {
            new AdminServerCredentialVerifier(ADMIN_SERVER_CONFIG, new HttpServerConfig().setHttpsEnabled(false))
                    .authenticate(SECURITY_CONTEXT_ALLOW, null);
            fail("Expected WebApplicationException");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), 403);
        }
    }

    @Test
    public void testNoPasswordConfigured()
    {
        try {
            new AdminServerCredentialVerifier(new AdminServerConfig(), HTTP_SERVER_CONFIG)
                    .authenticate(SECURITY_CONTEXT_DENY, null);
            fail("Expected WebApplicationException");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), 403);
        }
    }

    @Test
    public void testNoPasswordConfiguredContextAllows()
    {
        new AdminServerCredentialVerifier(new AdminServerConfig(), HTTP_SERVER_CONFIG)
                .authenticate(SECURITY_CONTEXT_ALLOW, null);
    }

    @Test
    public void testNoAuthentication()
    {
        try {
            new AdminServerCredentialVerifier(ADMIN_SERVER_CONFIG, HTTP_SERVER_CONFIG)
                    .authenticate(SECURITY_CONTEXT_DENY, null);
            fail("Expected WebApplicationException");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), 401);
        }
    }

    @Test
    public void testOptionalAuthenticationContextIgnored()
    {
        try {
            new AdminServerCredentialVerifier(ADMIN_SERVER_CONFIG, HTTP_SERVER_CONFIG)
                    .authenticate(SECURITY_CONTEXT_OPTIONAL, null);
            fail("Expected WebApplicationException");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), 401);
        }
    }

    @Test
    public void testContextAuthentication()
    {
        new AdminServerCredentialVerifier(ADMIN_SERVER_CONFIG, HTTP_SERVER_CONFIG)
                .authenticate(SECURITY_CONTEXT_ALLOW, null);
    }

    @Test
    public void testPasswordAuthentication()
    {
        new AdminServerCredentialVerifier(ADMIN_SERVER_CONFIG, HTTP_SERVER_CONFIG)
                .authenticate(SECURITY_CONTEXT_DENY, "Basic " + Base64.getEncoder().encodeToString("foo:bar".getBytes(UTF_8)));
    }

    @Test
    public void testPasswordAuthenticationWithOptionalAuthenticationContext()
    {
        new AdminServerCredentialVerifier(ADMIN_SERVER_CONFIG, HTTP_SERVER_CONFIG)
                .authenticate(SECURITY_CONTEXT_OPTIONAL, "Basic " + Base64.getEncoder().encodeToString("foo:bar".getBytes(UTF_8)));
    }

    @Test
    public void testBadUsername()
    {
        try {
            new AdminServerCredentialVerifier(ADMIN_SERVER_CONFIG, HTTP_SERVER_CONFIG)
                    .authenticate(SECURITY_CONTEXT_DENY, "Basic " + Base64.getEncoder().encodeToString("bad:bar".getBytes(UTF_8)));
            fail("Expected WebApplicationException");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), 401);
        }
    }

    @Test
    public void testBadPassword()
    {
        try {
            new AdminServerCredentialVerifier(ADMIN_SERVER_CONFIG, HTTP_SERVER_CONFIG)
                    .authenticate(SECURITY_CONTEXT_DENY, "Basic " + Base64.getEncoder().encodeToString("foo:bad".getBytes(UTF_8)));
            fail("Expected WebApplicationException");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), 401);
        }
    }

    @Test
    public void testWrongScheme()
    {
        try {
            new AdminServerCredentialVerifier(ADMIN_SERVER_CONFIG, HTTP_SERVER_CONFIG)
                    .authenticate(SECURITY_CONTEXT_DENY, "Digest " + Base64.getEncoder().encodeToString("foo:bar".getBytes(UTF_8)));
            fail("Expected WebApplicationException");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), 401);
        }
    }

    @Test
    public void testNoPassword()
    {
        try {
            new AdminServerCredentialVerifier(ADMIN_SERVER_CONFIG, HTTP_SERVER_CONFIG)
                    .authenticate(SECURITY_CONTEXT_DENY, "Digest " + Base64.getEncoder().encodeToString("foo".getBytes(UTF_8)));
            fail("Expected WebApplicationException");
        }
        catch (WebApplicationException e) {
            assertEquals(e.getResponse().getStatus(), 401);
        }
    }
}
