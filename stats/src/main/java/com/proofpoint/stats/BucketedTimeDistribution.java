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
package com.proofpoint.stats;

import com.google.common.base.Function;
import com.proofpoint.reporting.Prometheus;
import com.proofpoint.reporting.PrometheusSummary;
import com.proofpoint.reporting.Reported;

import javax.annotation.concurrent.GuardedBy;

import static com.proofpoint.reporting.PrometheusType.COUNTER;
import static com.proofpoint.reporting.PrometheusType.SUPPRESSED;

public final class BucketedTimeDistribution
    extends PrometheusSummary<BucketedTimeDistribution.Distribution>
{
    public void add(final long value)
    {
        applyToCurrentBucket((Function<Distribution, Void>) input -> {
            synchronized (input) {
                input.allTimeCount++;
                input.allTimeTotal += value;
                input.digest.add(value);
                input.total += value;
            }
            return null;
        });
    }

    @Override
    protected Distribution createBucket(Distribution previousBucket)
    {
        return new Distribution(previousBucket);
    }

    protected static class Distribution
    {
        private static final double MAX_ERROR = 0.01;

        @GuardedBy("this")
        private long allTimeTotal = 0;

        @GuardedBy("this")
        private long allTimeCount = 0;

        @GuardedBy("this")
        private final QuantileDigest digest = new QuantileDigest(MAX_ERROR);

        @GuardedBy("this")
        private long total = 0;
    
        public Distribution(Distribution previousDistribution)
        {
            if (previousDistribution != null) {
                allTimeTotal = previousDistribution.allTimeTotal;
                allTimeCount = previousDistribution.allTimeCount;
            }
        }

        @Prometheus(name = "Sum", type = COUNTER)
        public synchronized double getAllTimeTotal()
        {
            return convertToSeconds(allTimeTotal);
        }

        @Prometheus(name = "Count", type = COUNTER)
        public synchronized long getAllTimeCount()
        {
            return allTimeCount;
        }

        @Reported
        @Prometheus(type = SUPPRESSED)
        public synchronized double getCount()
        {
            return digest.getCount();
        }

        @Reported
        @Prometheus(type = SUPPRESSED)
        public synchronized double getTotal() {
            return convertToSeconds(total);
        }
    
        @Reported
        public synchronized double getP50()
        {
            return convertToSeconds(digest.getQuantile(0.5));
        }
    
        @Reported
        public synchronized double getP75()
        {
            return convertToSeconds(digest.getQuantile(0.75));
        }
    
        @Reported
        public synchronized double getP90()
        {
            return convertToSeconds(digest.getQuantile(0.90));
        }
    
        @Reported
        public synchronized double getP95()
        {
            return convertToSeconds(digest.getQuantile(0.95));
        }
    
        @Reported
        public synchronized double getP99()
        {
            return convertToSeconds(digest.getQuantile(0.99));
        }
    
        @Reported
        public synchronized double getMin()
        {
            return convertToSeconds(digest.getMin());
        }
    
        @Reported
        public synchronized double getMax()
        {
            return convertToSeconds(digest.getMax());
        }

        private static double convertToSeconds(long nanos)
        {
            if (nanos == Long.MAX_VALUE || nanos == Long.MIN_VALUE) {
                return Double.NaN;
            }
            return nanos * 0.000_000_001;
        }
    }
}
