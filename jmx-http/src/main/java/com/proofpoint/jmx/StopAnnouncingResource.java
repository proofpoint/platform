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

import com.google.inject.Inject;
import com.proofpoint.discovery.client.announce.Announcer;
import com.proofpoint.jaxrs.AccessDoesNotRequireAuthentication;
import com.proofpoint.log.Logger;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;

import static java.util.Objects.requireNonNull;

@Path("/admin/stop-announcing")
@AccessDoesNotRequireAuthentication
public class StopAnnouncingResource
{
    private static final Logger log = Logger.get(StopAnnouncingResource.class);
    private Announcer announcer;
    private final AdminServerCredentialVerifier adminServerCredentialVerifier;

    @Inject
    public StopAnnouncingResource(AdminServerCredentialVerifier adminServerCredentialVerifier)
    {
        this.adminServerCredentialVerifier = requireNonNull(adminServerCredentialVerifier, "adminServerCredentialVerifier is null");
    }

    @Inject(optional = true)
    public void setAnnouncer(Announcer announcer)
    {
        this.announcer = announcer;
    }

    @PUT
    public void stopAnnouncing(@Context SecurityContext securityContext, @HeaderParam("Authorization") String authHeader)
    {
        adminServerCredentialVerifier.authenticate(securityContext, authHeader);

        if (announcer != null) {
            log.info("Received shutdown request. Stopping discovery announcer.");
            announcer.destroy();
        }
    }
}
