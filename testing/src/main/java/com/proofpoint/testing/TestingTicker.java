package com.proofpoint.testing;

import com.google.common.base.Ticker;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.google.common.base.Preconditions.checkArgument;

public class TestingTicker
        extends Ticker
{
    private final AtomicLong time = new AtomicLong();

    @Override
    public long read()
    {
        return time.get();
    }

    /**
     * @deprecated Use {@link #elapseTime(long, TimeUnit)}
     */
    @Deprecated
    public void increment(long delta, TimeUnit unit)
    {
        checkArgument(delta >= 0, "delta is negative");
        time.getAndAdd(unit.toNanos(delta));
    }

    /**
     * Advance time by the given quantum.
     *
     * @param quantum the amount of time to advance
     * @param timeUnit the unit of the quantum amount
     */
    public void elapseTime(long quantum, TimeUnit timeUnit)
    {
        checkArgument(quantum >= 0, "quantum is negative");
        time.getAndAdd(timeUnit.toNanos(quantum));
    }

    public void elapseTimeNanosecondBefore(long quantum, TimeUnit timeUnit)
    {
        checkArgument(quantum > 0, "quantum is non-positive");
        time.getAndAdd(timeUnit.toNanos(quantum) - 1);
    }
}
