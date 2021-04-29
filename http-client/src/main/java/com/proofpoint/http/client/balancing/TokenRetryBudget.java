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

import com.google.common.base.Ticker;
import com.google.common.primitives.Ints;
import com.proofpoint.stats.SparseCounterStat;
import com.proofpoint.units.Duration;
import org.weakref.jmx.Nested;

import java.math.BigDecimal;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static java.math.BigDecimal.ROUND_HALF_UP;
import static java.math.BigDecimal.ZERO;
import static java.util.concurrent.TimeUnit.SECONDS;

class TokenRetryBudget
        implements RetryBudget
{
    private static final BigDecimal SCALE_FACTOR = new BigDecimal(1000);
    private final int depositAmount;
    private final int withdrawalAmount;
    private final LeakyTokenBucket tokenBucket;
    private final SparseCounterStat retryBudgetExhausted = new SparseCounterStat();

    private TokenRetryBudget(BigDecimal retryBudgetRatio, Duration retryBudgetRatioPeriod, int retryBudgetMinPerSecond, Ticker ticker)
    {
        // if you only have minRetries, everything costs 1 but you
        // get no credit for requests. all credits come via time.
        if (retryBudgetRatio.equals(ZERO)) {
            depositAmount = 0;
            withdrawalAmount = 1;
        }
        else {
            depositAmount = SCALE_FACTOR.intValue();
            withdrawalAmount = SCALE_FACTOR.divide(retryBudgetRatio, ROUND_HALF_UP).intValue();
        }

        // compute the reserve by scaling retryBudgetMinPerSecond by retryBudgetRatioPeriod and retry cost
        // to allow for clients that've just started or have low rps
        int reserve = retryBudgetMinPerSecond * toIntExact(retryBudgetRatioPeriod.roundTo(SECONDS)) * withdrawalAmount;
        tokenBucket = new LeakyTokenBucket(retryBudgetRatioPeriod, reserve, ticker);
    }

    static RetryBudget tokenRetryBudget(BigDecimal retryBudgetRatio, Duration retryBudgetRatioPeriod, int retryBudgetMinPerSecond, Ticker ticker)
    {
        checkArgument(retryBudgetRatio.compareTo(ZERO) >= 0, "retryBudgetRatio must be non-negative");
        checkArgument(retryBudgetRatio.compareTo(SCALE_FACTOR) <= 0, "retryBudgetRatio must be no greater than " + SCALE_FACTOR);
        checkArgument(retryBudgetRatioPeriod.compareTo(new Duration(1, SECONDS)) >= 0, " retryBudgetRatioPeriod must be at least 1s");
        checkArgument(retryBudgetRatioPeriod.compareTo(new Duration(60, SECONDS)) <= 0, " retryBudgetRatioPeriod must be at most 60s");
        checkArgument(retryBudgetMinPerSecond >= 0, "retryBudgetMinPerSecond must be non-negative");

        if (retryBudgetRatio.equals(ZERO) && retryBudgetMinPerSecond == 0) {
            return NoRetryBudget.INSTANCE;
        }
        return new TokenRetryBudget(retryBudgetRatio, retryBudgetRatioPeriod, retryBudgetMinPerSecond, ticker);
    }

    @Override
    public void initialAttempt()
    {
        tokenBucket.put(depositAmount);
    }

    @Override
    public boolean canRetry()
    {
        if (tokenBucket.tryGet(withdrawalAmount)) {
            return true;
        }
        retryBudgetExhausted.add(1);
        return false;
    }

    @Nested
    public SparseCounterStat getRetryBudgetExhausted()
    {
        return retryBudgetExhausted;
    }
}
