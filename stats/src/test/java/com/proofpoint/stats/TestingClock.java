package com.proofpoint.stats;

import com.google.common.base.Ticker;

import java.util.concurrent.TimeUnit;

public class TestingClock
        extends Ticker
{
    private long time;

    @Override
    public long read()
    {
        return time;
    }

    public void increment(long delta, TimeUnit unit)
    {
        time += unit.toNanos(delta);
    }
}
