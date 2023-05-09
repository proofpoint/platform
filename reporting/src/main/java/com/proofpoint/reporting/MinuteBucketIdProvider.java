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

import com.google.common.base.Ticker;
import jakarta.inject.Inject;

import java.util.concurrent.TimeUnit;

import static com.proofpoint.reporting.BucketIdProvider.BucketId.bucketId;
import static java.lang.System.currentTimeMillis;

public class MinuteBucketIdProvider
    implements BucketIdProvider
{
    private static final long ONE_MINUTE_IN_NANOS = 60_000_000_000L;
    private final Ticker ticker;
    private final long initialValue;

    @Inject
    public MinuteBucketIdProvider()
    {
        this(Ticker.systemTicker());
    }

    public MinuteBucketIdProvider(Ticker ticker)
    {
        this.ticker = ticker;
        this.initialValue = ticker.read();
    }

    @Override
    public BucketId get()
    {
        long nanosSinceInitial = ticker.read() - initialValue;
        int id = (int) (nanosSinceInitial / ONE_MINUTE_IN_NANOS);
        long nanosSinceBoundary = nanosSinceInitial % ONE_MINUTE_IN_NANOS;
        long timeAtBoundary = TimeUnit.MILLISECONDS.toNanos(currentTimeMillis()) - nanosSinceBoundary;
        return bucketId(id, timeAtBoundary);
    }

    public long getLastSystemTimeMillis()
    {
        long nanosSinceBoundary = (ticker.read() - initialValue) % ONE_MINUTE_IN_NANOS;
        return currentTimeMillis() - TimeUnit.NANOSECONDS.toMillis(nanosSinceBoundary);
    }
}
