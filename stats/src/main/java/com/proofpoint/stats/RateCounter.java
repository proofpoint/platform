package com.proofpoint.stats;

public interface RateCounter
{
    public void add(long value);

    public void add(double value);

    public double getCount();

    public double getRate();
}
