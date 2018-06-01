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

public class SparseDistributionStat
    extends PrometheusSummary<SparseDistributionStat.Distribution>
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
    protected final Distribution createBucket(Distribution previousBucket)
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
        public synchronized long getAllTimeTotal()
        {
            return allTimeTotal;
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
            double count = digest.getCount();
            if (count == 0.0) {
                return Double.NaN;
            }
            return count;
        }

        @Reported
        @Prometheus(type = SUPPRESSED)
        public synchronized long getTotal()
        {
            if (digest.getCount() == 0.0) {
                return Long.MIN_VALUE;
            }
            return total;
        }
    
        @Reported
        public synchronized long getP50()
        {
            return digest.getQuantile(0.5);
        }
    
        @Reported
        public synchronized long getP75()
        {
            return digest.getQuantile(0.75);
        }
    
        @Reported
        public synchronized long getP90()
        {
            return digest.getQuantile(0.90);
        }
    
        @Reported
        public synchronized long getP95()
        {
            return digest.getQuantile(0.95);
        }
    
        @Reported
        public synchronized long getP99()
        {
            return digest.getQuantile(0.99);
        }
    
        @Reported
        public synchronized long getMin()
        {
            return digest.getMin();
        }
    
        @Reported
        public synchronized long getMax()
        {
            return digest.getMax();
        }
    }
}
