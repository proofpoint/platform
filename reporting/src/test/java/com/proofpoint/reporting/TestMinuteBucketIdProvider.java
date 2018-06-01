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
package com.proofpoint.reporting;

import com.proofpoint.reporting.BucketIdProvider.BucketId;
import com.proofpoint.testing.TestingTicker;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static com.proofpoint.testing.Assertions.assertGreaterThanOrEqual;
import static com.proofpoint.testing.Assertions.assertLessThanOrEqual;
import static org.testng.Assert.assertEquals;

public class TestMinuteBucketIdProvider
{
    private TestingTicker ticker;

    @BeforeMethod
    public void setup()
    {
        ticker = new TestingTicker();
    }

    @Test
    public void testInitialState()
    {
        long startTime = getCurrentTimeNanos();
        BucketId bucketId = new MinuteBucketIdProvider(ticker).get();
        assertEquals(bucketId.getId(), 0);
        assertGreaterThanOrEqual(bucketId.getTimestamp(), startTime);
        assertLessThanOrEqual(bucketId.getTimestamp(), getCurrentTimeNanos());

        ticker.elapseTime(27, TimeUnit.HOURS);
        ticker.elapseTime(977_777, TimeUnit.NANOSECONDS);
        startTime = getCurrentTimeNanos();
        bucketId = new MinuteBucketIdProvider(ticker).get();
        assertEquals(bucketId.getId(), 0);
        assertGreaterThanOrEqual(bucketId.getTimestamp(), startTime);
        assertLessThanOrEqual(bucketId.getTimestamp(), getCurrentTimeNanos());
    }

    @Test
    public void testMinuteBoundary()
    {
        ticker.elapseTime(27, TimeUnit.HOURS);
        ticker.elapseTime(977_777, TimeUnit.NANOSECONDS);
        long startTime = getCurrentTimeNanos();
        BucketIdProvider idProvider = new MinuteBucketIdProvider(ticker);
        BucketId bucketId = idProvider.get();
        assertEquals(bucketId.getId(), 0, "initial state");
        assertGreaterThanOrEqual(bucketId.getTimestamp(), startTime);
        assertLessThanOrEqual(bucketId.getTimestamp(), getCurrentTimeNanos());

        ticker.elapseTime(59_999_999_999L, TimeUnit.NANOSECONDS);
        startTime = getCurrentTimeNanos();
        bucketId = idProvider.get();
        assertEquals(bucketId.getId(), 0, "before minute boundary");
        assertGreaterThanOrEqual(bucketId.getTimestamp(), startTime - 59_999_999_999L);
        assertLessThanOrEqual(bucketId.getTimestamp(), getCurrentTimeNanos() - 59_999_999_999L);

        ticker.elapseTime(1, TimeUnit.NANOSECONDS);
        startTime = getCurrentTimeNanos();
        bucketId = idProvider.get();
        assertEquals(bucketId.getId(), 1, "on minute boundary");
        assertGreaterThanOrEqual(bucketId.getTimestamp(), startTime);
        assertLessThanOrEqual(bucketId.getTimestamp(), getCurrentTimeNanos());
    }

    private static long getCurrentTimeNanos()
    {
        return TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis());
    }
}
