package com.proofpoint.stats;

import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertTrue;

public class TestDecayCounter
{
    @Test
    public void testCountDecays()
    {
        TestingTicker ticker = new TestingTicker();

        DecayCounter counter = new DecayCounter(ExponentialDecay.oneMinute(), ticker);
        counter.add(1);
        ticker.increment(1, TimeUnit.MINUTES);

        assertTrue(Math.abs(counter.getCount() - 1 / Math.E) < 1e-9);
    }

    @Test
    public void testAddAfterRescale()
    {
        TestingTicker ticker = new TestingTicker();

        DecayCounter counter = new DecayCounter(ExponentialDecay.oneMinute(), ticker);
        counter.add(1);
        ticker.increment(1, TimeUnit.MINUTES);
        counter.add(2);

        double expected = 2 + 1 / Math.E;
        assertTrue(Math.abs(counter.getCount() - expected) < 1e-9);
    }

    @Test
    public void testAssumedHistory()
    {
        TestingTicker ticker = new TestingTicker();

        DecayCounter counter = new DecayCounter(1, ticker, 1, TimeUnit.SECONDS);
        counter.add(1);

        double expected = 1.0;
        assertTrue(Math.abs(counter.getCount() - expected) < 1e-9);
    }

}
