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

import com.proofpoint.units.Duration;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

class DecorrelatedJitteredBackoffPolicy
    implements BackoffPolicy
{
    private final long min;
    private final long max;

    public DecorrelatedJitteredBackoffPolicy(Duration min, Duration max)
    {
        this.min = min.roundTo(TimeUnit.NANOSECONDS);
        this.max = max.roundTo(TimeUnit.NANOSECONDS);
        checkArgument(this.min <= this.max, "min is greater than max");
    }

    @Override
    public BackoffPolicy nextAttempt()
    {
        return this;
    }

    @Override
    public Duration backoff(Duration previousBackoff, Duration suggestedBackoff)
    {
        long prev = previousBackoff.roundTo(TimeUnit.NANOSECONDS);
        long range = Math.abs(prev * 3 - min);
        long randBackoff;
        if (range == 0) {
            randBackoff = min;
        } else {
            randBackoff = min + ThreadLocalRandom.current().nextLong(range);
        }
        long backoff = Math.min(max, Math.max(randBackoff, suggestedBackoff.roundTo(TimeUnit.NANOSECONDS)));

        return new Duration(backoff, TimeUnit.NANOSECONDS);
    }
}
