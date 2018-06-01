package com.proofpoint.reporting.testing;

import com.proofpoint.reporting.BucketIdProvider;

import java.util.concurrent.atomic.AtomicInteger;

import static com.proofpoint.reporting.BucketIdProvider.BucketId.bucketId;

class TestingBucketIdProvider
    implements BucketIdProvider
{
    private AtomicInteger bucket = new AtomicInteger();

    @Override
    public BucketId get()
    {
        int id = bucket.get();
        return bucketId(id, id * 10);
    }

    void incrementBucket()
    {
        bucket.incrementAndGet();
    }
}
