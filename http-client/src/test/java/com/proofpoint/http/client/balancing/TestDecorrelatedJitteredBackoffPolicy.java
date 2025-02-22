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
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static com.proofpoint.http.client.balancing.RetryException.NO_SUGGESTED_BACKOFF;
import static com.proofpoint.testing.Assertions.assertGreaterThanOrEqual;
import static com.proofpoint.testing.Assertions.assertLessThanOrEqual;
import static org.testng.Assert.assertEquals;

public class TestDecorrelatedJitteredBackoffPolicy
{
    @Test
    public void testBackoff()
    {
        Duration min = new Duration(1, TimeUnit.MILLISECONDS);
        Duration max = new Duration(5, TimeUnit.SECONDS);
        BackoffPolicy policy = new DecorrelatedJitteredBackoffPolicy(min, max);
        for (int i = 0; i < 1000; i++) {
            Duration last = new Duration(0, TimeUnit.SECONDS);
            Duration prev = min; // Special-case constraint for the first backoff
            for (int j = 0; j < 1000; j++) {
                Duration next = policy.backoff(last, NO_SUGGESTED_BACKOFF);
                assertGreaterThanOrEqual(next, min);
                assertLessThanOrEqual(next, new Duration(prev.roundTo(TimeUnit.NANOSECONDS) * 3, TimeUnit.NANOSECONDS));
                assertLessThanOrEqual(next, max);
                prev = next;
                last = next;
            }
        }
    }

    @Test
    public void testSuggestedBackoff()
    {
        Duration min = new Duration(1, TimeUnit.MILLISECONDS);
        Duration suggested = new Duration(1, TimeUnit.SECONDS);
        Duration max = new Duration(5, TimeUnit.SECONDS);
        BackoffPolicy policy = new DecorrelatedJitteredBackoffPolicy(min, max);
        for (int i = 0; i < 1000; i++) {
            Duration last = new Duration(0, TimeUnit.SECONDS);
            Duration prev = min; // Special-case constraint for the first backoff
            for (int j = 0; j < 1000; j++) {
                Duration next = policy.backoff(last, suggested);
                assertGreaterThanOrEqual(next, suggested);
                if (next.getValue(TimeUnit.NANOSECONDS) > suggested.getValue(TimeUnit.NANOSECONDS)) {
                    assertLessThanOrEqual(next, new Duration(prev.roundTo(TimeUnit.NANOSECONDS) * 3, TimeUnit.NANOSECONDS));
                }
                assertLessThanOrEqual(next, max);
                prev = next;
                last = next;
            }
        }
    }

    @Test
    public void testSuggestedBackoffGreaterThanMax()
    {
        Duration min = new Duration(1, TimeUnit.MILLISECONDS);
        Duration suggested = new Duration(10, TimeUnit.SECONDS);
        Duration max = new Duration(5, TimeUnit.SECONDS);
        BackoffPolicy policy = new DecorrelatedJitteredBackoffPolicy(min, max);
        Duration last = new Duration(0, TimeUnit.SECONDS);
        Duration next = policy.backoff(last, suggested);
        assertEquals(next, max);
    }
}