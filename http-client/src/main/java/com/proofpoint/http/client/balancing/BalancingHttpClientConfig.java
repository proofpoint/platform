/*
 * Copyright 2013 Proofpoint, Inc.
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
package com.proofpoint.http.client.balancing;

import com.proofpoint.configuration.Config;
import com.proofpoint.configuration.ConfigDescription;
import com.proofpoint.units.Duration;

import javax.validation.constraints.AssertFalse;
import javax.validation.constraints.Min;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class BalancingHttpClientConfig
{
    private int maxAttempts = 3;
    private Duration minBackoff = new Duration(10, MILLISECONDS);
    private Duration maxBackoff = new Duration(10, SECONDS);

    @Min(1)
    public int getMaxAttempts()
    {
        return maxAttempts;
    }

    @Config("http-client.max-attempts")
    public BalancingHttpClientConfig setMaxAttempts(int maxAttempts)
    {
        this.maxAttempts = maxAttempts;
        return this;
    }

    public Duration getMinBackoff()
    {
        return minBackoff;
    }

    @Config("http-client.min-backoff")
    @ConfigDescription("Minimum backoff delay before a retry")
    public BalancingHttpClientConfig setMinBackoff(Duration minBackoff)
    {
        this.minBackoff = minBackoff;
        return this;
    }

    public Duration getMaxBackoff()
    {
        return maxBackoff;
    }

    @Config("http-client.max-backoff")
    @ConfigDescription("Maximum backoff delay before a retry")
    public BalancingHttpClientConfig setMaxBackoff(Duration maxBackoff)
    {
        this.maxBackoff = maxBackoff;
        return this;
    }

    @AssertFalse
    public boolean isMaxBackoffLessThanMinBackoff()
    {
        return maxBackoff.compareTo(minBackoff) < 0;
    }
}
