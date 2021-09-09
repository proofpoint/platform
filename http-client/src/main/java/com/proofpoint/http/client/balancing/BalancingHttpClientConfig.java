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
import com.proofpoint.units.MaxDuration;
import com.proofpoint.units.MinDuration;
import jakarta.validation.constraints.AssertFalse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public class BalancingHttpClientConfig
{
    private int maxAttempts = 3;
    private Duration minBackoff = new Duration(10, MILLISECONDS);
    private Duration maxBackoff = new Duration(10, SECONDS);
    private BigDecimal retryBudgetRatio = new BigDecimal(2).movePointLeft(1);
    private Duration retryBudgetRatioPeriod = new Duration(10, SECONDS);
    private int retryBudgetMinPerSecond = 10;

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

    @Min(0)
    @Max(1000)
    public BigDecimal getRetryBudgetRatio()
    {
        return retryBudgetRatio;
    }

    @Config("http-client.retry-budget.ratio")
    @ConfigDescription("The ratio of permitted retries to initial requests")
    public BalancingHttpClientConfig setRetryBudgetRatio(BigDecimal retryBudgetRatio)
    {
        this.retryBudgetRatio = retryBudgetRatio;
        return this;
    }

    @MinDuration("1s")
    @MaxDuration("60s")
    public Duration getRetryBudgetRatioPeriod()
    {
        return retryBudgetRatioPeriod;
    }

    @Config("http-client.retry-budget.ratio-period")
    @ConfigDescription("The time over which initial requests are considered when calculating retry budgets")
    public BalancingHttpClientConfig setRetryBudgetRatioPeriod(Duration retryBudgetRatioPeriod)
    {
        this.retryBudgetRatioPeriod = retryBudgetRatioPeriod;
        return this;
    }

    @Min(1)
    public int getRetryBudgetMinPerSecond()
    {
        return retryBudgetMinPerSecond;
    }

    @Config("http-client.retry-budget.min-per-second")
    @ConfigDescription("Additional number of retries permitted per second")
    public BalancingHttpClientConfig setRetryBudgetMinPerSecond(int retryBudgetMinPerSecond)
    {
        this.retryBudgetMinPerSecond = retryBudgetMinPerSecond;
        return this;
    }

    @AssertFalse
    public boolean isMaxBackoffLessThanMinBackoff()
    {
        return maxBackoff.compareTo(minBackoff) < 0;
    }
}
