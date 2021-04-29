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
import com.proofpoint.units.Duration;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Math.toIntExact;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

class LeakyTokenBucket
{
    private final WindowedAdder windowedAdder;
    private final int reserve;

    LeakyTokenBucket(Duration ttl, int reserve, Ticker ticker)
    {
        windowedAdder = new WindowedAdder(ttl.roundTo(NANOSECONDS), 10, ticker);
        this.reserve = reserve;
    }

    void put(int n)
    {
        checkArgument(n >= 0, "n must be non-negative");
        windowedAdder.add(n);
    }

    synchronized boolean tryGet(int n)
    {
        checkArgument(n >= 0, "n must be non-negative");
        if (count() < n) {
            return false;
        }
        windowedAdder.add(-n);
        return true;
    }

    private int count()
    {
        return toIntExact(windowedAdder.sum() + reserve);
    }

    private static class WindowedAdder
    {
        private final int window;
        private final int buckets;
        private final Ticker ticker;
        private final LongAdder writer = new LongAdder();
        private volatile int gen = 0;
        private final AtomicInteger expiredGen = new AtomicInteger(gen);
        // Since we only write into the head bucket, we simply maintain
        // counts in an array; these are written to rarely, but are read
        // often.
        private final long buf[];
        private volatile int i = 0;
        private volatile long old;

        WindowedAdder(long range, int slices, Ticker ticker)
        {
            checkArgument(slices > 1, "slices must be greater than one");
            window = toIntExact(range / slices);
            buckets = slices - 1;
            this.ticker = ticker;
            buf = new long[buckets];
            old = ticker.read();
        }

        public void add(int n)
        {
            if (ticker.read() - old >= window) {
                expired();
            }
            writer.add(n);
        }

        public long sum()
        {
            if ((ticker.read() - old) >= window) {
                expired();
            }
            int barrier = gen;  // Barrier.
            long sum = writer.sum();
            for (int i = 0; i < buckets; i++) {
                sum += buf[i];
            }
            return sum;
        }

        @SuppressFBWarnings(value = "VO_VOLATILE_INCREMENT", justification = "The expiredGen.compareAndSet() ensures only one writer to gen at a time")
        private void expired()
        {
            if (!expiredGen.compareAndSet(gen, gen + 1)) {
                return;
            }
            // At the time of add, we were likely up to date,
            // so we credit it to the current slice.
            buf[i] = writer.sumThenReset();
            i = (i + 1) % buckets;


            // If it turns out we've skipped a number of
            // slices, we adjust for that here.
            int nSkip = Math.min((toIntExact((ticker.read() - old) / window - 1)), buckets);
            if (nSkip > 0) {
                int r = Math.min(nSkip, buckets - i);
                Arrays.fill(buf, i, i + r, 0L);
                Arrays.fill(buf, 0, nSkip - r, 0L);
                i = (i + nSkip) % buckets;
            }

            old = ticker.read();
            gen += 1;
        }
    }
}
