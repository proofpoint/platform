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

import com.proofpoint.testing.TestingTicker;
import com.proofpoint.units.Duration;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

public class TestLeakyTokenBucket
{
    private TestingTicker ticker;

    @BeforeMethod
    public void setup()
    {
        ticker = new TestingTicker();
    }

    @Test
    public void TestIsLeaky()
    {
        LeakyTokenBucket bucket = new LeakyTokenBucket(new Duration(3, SECONDS), 0, ticker);
        bucket.put(100);
        assertTrue(bucket.tryGet(1));

        ticker.elapseTime(3, SECONDS);
        assertFalse(bucket.tryGet(1));
    }

    @Test
    public void TestFailsWhenEmpty()
    {
        LeakyTokenBucket bucket = new LeakyTokenBucket(new Duration(3, SECONDS), 0, ticker);
        bucket.put(100);
        assertTrue(bucket.tryGet(50));
        assertTrue(bucket.tryGet(49));
        assertTrue(bucket.tryGet(1));
        assertFalse(bucket.tryGet(1));
        assertFalse(bucket.tryGet(50));
        bucket.put(1);
        assertFalse(bucket.tryGet(2));
        assertTrue(bucket.tryGet(1));
        assertFalse(bucket.tryGet(1));
    }

    @Test
    public void TestProvisionsReserves()
    {
        LeakyTokenBucket bucket = new LeakyTokenBucket(new Duration(3, SECONDS), 100, ticker);
        // start at 0, though with 100 in reserve
        assertTrue(bucket.tryGet(50)); // -50 + 100 = 0
        assertTrue(bucket.tryGet(50)); // -100 + 100 = 0
        assertFalse(bucket.tryGet(1)); // nope, at 0
        bucket.put(1); // now at -99 + 100 = 1
        assertTrue(bucket.tryGet(1)); // back to 0

        ticker.elapseTime(1, SECONDS);
        assertFalse(bucket.tryGet(1)); // still at -100 + 100 = 0

        ticker.elapseTime(1, SECONDS);
        assertFalse(bucket.tryGet(1)); // still at -100 + 100 = 0

        ticker.elapseTime(1, SECONDS);
        assertFalse(bucket.tryGet(1)); // still at -100 + 100 = 0

        ticker.elapseTime(1, SECONDS);
        assertTrue(bucket.tryGet(50)); // the -100 expired, so -50 + 100 = 50

        ticker.elapseTime(3, SECONDS); // the -50 expired, so -100 + 100 = 0
        assertTrue(bucket.tryGet(100));
        assertFalse(bucket.tryGet(1));
    }
}
