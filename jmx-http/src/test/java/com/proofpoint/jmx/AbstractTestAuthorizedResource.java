/*
 * Copyright 2018 Proofpoint, Inc.
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

import com.google.inject.Module;
import com.proofpoint.bootstrap.LifeCycleManager;
import com.proofpoint.http.client.HttpClient;
import com.proofpoint.http.client.Request;
import com.proofpoint.http.client.Request.Builder;
import com.proofpoint.http.client.Response;
import com.proofpoint.http.client.ResponseHandler;
import com.proofpoint.http.client.StatusResponseHandler.StatusResponse;
import com.proofpoint.http.client.jetty.JettyHttpClient;
import com.proofpoint.http.server.testing.TestingAdminHttpServer;
import com.proofpoint.testing.Closeables;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.net.URI;
import java.security.Principal;

import static com.google.inject.util.Modules.override;
import static com.proofpoint.http.client.StatusResponseHandler.createStatusResponseHandler;
import static com.proofpoint.jaxrs.JaxrsBinder.jaxrsBinder;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.testng.Assert.assertEquals;

public abstract class AbstractTestAuthorizedResource
{
    private static final SecurityContext SECURITY_CONTEXT = new SecurityContext()
    {
        @Override
        public Principal getUserPrincipal()
        {
            return null;
        }

        @Override
        public boolean isUserInRole(String role)
        {
            return false;
        }

        @Override
        public boolean isSecure()
        {
            return false;
        }

        @Override
        public String getAuthenticationScheme()
        {
            return "testing scheme";
        }
    };
    protected final HttpClient client = new JettyHttpClient();
    protected Module jmxHttpModule;
    protected LifeCycleManager lifeCycleManager;
    protected TestingAdminHttpServer server;

    private AdminServerCredentialVerifier verifier;

    protected abstract void createServer()
            throws Exception;

    protected abstract Builder createRequestBuilder();

    protected abstract void assertActionTaken(Response response);

    protected abstract void assertActionNotTaken();

    @BeforeMethod
    public final void setupAuthorization()
    {
        verifier = mock(AdminServerCredentialVerifier.class);
        jmxHttpModule = binder -> {
            binder.install(override(new JmxHttpModule())
                    .with(b -> b.bind(AdminServerCredentialVerifier.class).toInstance(verifier)));
            jaxrsBinder(binder).bindAdmin(SecurityContextRequestFilter.class);
        };
        lifeCycleManager = null;
        server = null;
    }

    @AfterMethod(alwaysRun = true)
    public final void teardownAuthorization()
            throws Exception
    {
        if (lifeCycleManager != null) {
            lifeCycleManager.stop();
        }
    }

    @AfterClass(alwaysRun = true)
    public final void teardownAuthorizationClass()
    {
        Closeables.closeQuietly(client);
    }

    @Test
    public void testSuccess()
            throws Exception
    {
        createServer();

        Response response = client.execute(
                createRequestBuilder()
                        .addHeader("Authorization", "authHeader")
                        .build(),
                new ResponseHandler<Response, Exception>()
                {
                    @Override
                    public Response handleException(Request request, Exception exception)
                    {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Response handle(Request request, Response response)
                    {
                        return response;
                    }
                });


        ArgumentCaptor<SecurityContext> captor = ArgumentCaptor.forClass(SecurityContext.class);
        verify(verifier).authenticate(captor.capture(), eq("authHeader"));
        verifyNoMoreInteractions(verifier);
        assertEquals(captor.getValue().getAuthenticationScheme(), "testing scheme");
        assertActionTaken(response);
    }

    @Test
    public void testFailAuthentication()
            throws Exception
    {
        createServer();
        doThrow(new WebApplicationException(FORBIDDEN.getStatusCode())).when(verifier).authenticate(any(), anyString());

        StatusResponse response = client.execute(
                createRequestBuilder()
                        .addHeader("Authorization", "authHeader")
                        .build(),
                createStatusResponseHandler());

        assertEquals(response.getStatusCode(), FORBIDDEN.getStatusCode());

        ArgumentCaptor<SecurityContext> captor = ArgumentCaptor.forClass(SecurityContext.class);
        verify(verifier).authenticate(captor.capture(), eq("authHeader"));
        verifyNoMoreInteractions(verifier);
        assertEquals(captor.getValue().getAuthenticationScheme(), "testing scheme");
        assertActionNotTaken();
    }


    protected URI uriFor(String path)
    {
        return server.getBaseUrl().resolve(path);
    }

    @Provider
    @Priority(Priorities.AUTHENTICATION)
    public static class SecurityContextRequestFilter
            implements ContainerRequestFilter
    {
        @Override
        public void filter(ContainerRequestContext requestContext)
        {
            requestContext.setSecurityContext(SECURITY_CONTEXT);
        }
    }
}
