/*
 * Copyright 2010 Proofpoint, Inc.
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
package com.proofpoint.http.server;

import com.proofpoint.discovery.client.announce.AnnouncementHttpServerInfo;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;

import java.net.URI;

public class LocalAnnouncementHttpServerInfo
        implements AnnouncementHttpServerInfo
{
    private final HttpServerInfo httpServerInfo;

    @Inject
    public LocalAnnouncementHttpServerInfo(HttpServerInfo httpServerInfo)
    {
        this.httpServerInfo = httpServerInfo;
    }

    @Override
    @Nullable
    public URI getHttpUri()
    {
        return httpServerInfo.getHttpUri();
    }

    @Override
    @Nullable
    public URI getHttpExternalUri()
    {
        return httpServerInfo.getHttpExternalUri();
    }

    @Override
    @Nullable
    public URI getHttpsUri()
    {
        return httpServerInfo.getHttpsUri();
    }

    @Override
    @Nullable
    public URI getAdminUri()
    {
        return httpServerInfo.getAdminUri();
    }
}
