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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.proofpoint.reporting.BucketIdProvider.BucketId;
import jakarta.annotation.Nullable;

import static com.proofpoint.reporting.BucketIdProvider.BucketId.bucketId;
import static java.util.Objects.requireNonNull;

public abstract class Bucketed<T>
{
    private static final BucketIdProvider INITIAL_BUCKET_ID_PROVIDER = () -> bucketId(-5, 0);
    private BucketIdProvider bucketIdProvider = INITIAL_BUCKET_ID_PROVIDER;
    private BucketId currentBucketId = bucketId(-10, 0);
    private T previousBucket = null;
    private T currentBucket = null;

    protected abstract T createBucket(@Nullable T previousBucket);

    protected final synchronized <R> R applyToCurrentBucket(Function<T, R> function)
    {
        rotateBucketIfNeeded();
        return function.apply(currentBucket);
    }

    @SuppressWarnings("UnusedDeclaration") // Called via reflection
    private synchronized BucketInfo getPreviousBucket()
    {
        rotateBucketIfNeeded();
        return new BucketInfo(previousBucket, currentBucketId);
    }

    @VisibleForTesting
    public synchronized void setBucketIdProvider(BucketIdProvider bucketIdProvider)
    {
        this.bucketIdProvider = bucketIdProvider;
        currentBucketId = bucketIdProvider.get();
        previousBucket = createBucket(null);
        currentBucket = createBucket(previousBucket);
    }

    private void rotateBucketIfNeeded()
    {
        BucketId bucketId = bucketIdProvider.get();
        if (bucketId.getId() != currentBucketId.getId()) {
            if (currentBucketId.getId() + 1 == bucketId.getId()) {
                previousBucket = currentBucket;
            }
            else {
                previousBucket = createBucket(currentBucket);
            }
            currentBucketId = bucketId;
            currentBucket = createBucket(previousBucket);
        }
    }

    public record BucketInfo(Object getBucket, BucketId getBucketId)
    {
        public BucketInfo {
            requireNonNull(getBucket, "bucket is null");
            requireNonNull(getBucketId, "bucketId is null");
        }
    }
}
