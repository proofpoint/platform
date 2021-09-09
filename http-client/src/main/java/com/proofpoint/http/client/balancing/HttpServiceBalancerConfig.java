/*
 * Copyright 2017 Proofpoint, Inc.
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
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.Min;

import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

public class HttpServiceBalancerConfig
{
    private int consecutiveFailures = 5;
    private Duration minBackoff = new Duration(5, SECONDS);
    private Duration maxBackoff = new Duration(2, MINUTES);

    @Min(1)
    public int getConsecutiveFailures()
    {
        return consecutiveFailures;
    }

    @Config("consecutive-failures")
    @ConfigDescription("Number of consecutive failures before a URI is marked dead")
    public HttpServiceBalancerConfig setConsecutiveFailures(int consecutiveFailures)
    {
        this.consecutiveFailures = consecutiveFailures;
        return this;
    }

    public Duration getMinBackoff()
    {
        return minBackoff;
    }

    @Config("min-backoff")
    @ConfigDescription("Minimum backoff delay before probing a dead URI")
    public HttpServiceBalancerConfig setMinBackoff(Duration minBackoff)
    {
        this.minBackoff = minBackoff;
        return this;
    }

    public Duration getMaxBackoff()
    {
        return maxBackoff;
    }

    @Config("max-backoff")
    @ConfigDescription("Maximum backoff delay before probing a dead URI")
    public HttpServiceBalancerConfig setMaxBackoff(Duration maxBackoff)
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
