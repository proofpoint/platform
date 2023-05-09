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
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.SecurityContext;

import java.util.Base64;

import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

public class AdminServerCredentialVerifier
{
    private static final Base64.Decoder decoder = Base64.getDecoder();

    private final String username;
    private final String password;
    private final boolean httpsEnabled;

    @Inject
    public AdminServerCredentialVerifier(AdminServerConfig adminServerConfig, HttpServerConfig httpServerConfig)
    {
        this.username = requireNonNull(adminServerConfig, "adminServerConfig is null").getUsername();
        this.password = adminServerConfig.getPassword();
        httpsEnabled = requireNonNull(httpServerConfig, "httpServerConfig is null").isHttpsEnabled();
    }

    public void authenticate(SecurityContext securityContext, String authHeader)
    {
        if (!httpsEnabled) {
            throw new WebApplicationException(Response.status(FORBIDDEN)
                    .header("Content-Type", "text/plain")
                    .entity("HTTPS not enabled")
                    .build());
        }

        if (securityContext.isUserInRole("server.admin") && !"none".equals(securityContext.getAuthenticationScheme())) {
            return;
        }

        if (username == null || password == null) {
            throw new WebApplicationException(Response.status(FORBIDDEN)
                    .header("Content-Type", "text/plain")
                    .entity("Administrator password not configured")
                    .build());
        }

        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            unauthorized();
        }

        String credentials = new String(decoder.decode(authHeader.substring("Basic ".length())), UTF_8);
        int index = credentials.indexOf(':');
        if (index < 0 || !username.equals(credentials.substring(0, index)) || !password.equals(credentials.substring(index + 1))) {
            unauthorized();
        }
    }

    private static void unauthorized()
    {
        throw new WebApplicationException(Response.status(UNAUTHORIZED)
                .header("WWW-Authenticate", "Basic realm=\"Administration port\"")
                .header("Content-Type", "text/plain")
                .entity("Incorrect username or password")
                .build()
        );
    }
}
