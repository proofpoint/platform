/*
 * Copyright 2022 Proofpoint, Inc.
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
package com.proofpoint.reporting;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigSecuritySensitive;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public class SplunkObservabilityClientConfig
{
    private boolean enabled = true;
    private boolean includeHostTag = true;
    private String authToken;

    public boolean isEnabled()
    {
        return enabled;
    }

    @Config("splunk-observability.enabled")
    public SplunkObservabilityClientConfig setEnabled(boolean enabled)
    {
        this.enabled = enabled;
        return this;
    }

    public boolean isIncludeHostTag()
    {
        return includeHostTag;
    }

    @Config("splunk-observability.include-host-tag")
    public SplunkObservabilityClientConfig setIncludeHostTag(boolean includeHostTag)
    {
        this.includeHostTag = includeHostTag;
        return this;
    }

    @NotNull
    @NotEmpty
    public String getAuthToken()
    {
        return authToken;
    }

    @ConfigSecuritySensitive
    @Config("splunk-observability.auth-token")
    public SplunkObservabilityClientConfig setAuthToken(String authToken)
    {
        this.authToken = authToken;
        return this;
    }
}
