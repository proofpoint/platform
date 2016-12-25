package com.proofpoint.reporting.testing;

import com.proofpoint.reporting.BucketIdProvider;

import java.util.concurrent.atomic.AtomicInteger;

class TestingBucketIdProvider
    implements BucketIdProvider
{
    private AtomicInteger bucket = new AtomicInteger();

    @Override
    public int get()
    {
        return bucket.get();
    }

    void incrementBucket()
    {
        bucket.incrementAndGet();
    }
}
