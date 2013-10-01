package com.proofpoint.stats;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Objects;
import com.google.common.base.Ticker;
import org.weakref.jmx.Managed;

import java.util.concurrent.TimeUnit;

import static java.util.concurrent.TimeUnit.SECONDS;

import static com.google.common.base.Preconditions.checkArgument;

/*
 * A counter that decays exponentially. Values are weighted according to the formula
 *     w(t, α) = e^(-α * t), where α is the decay factor and t is the age in seconds
 *
 * The implementation is based on the ideas from
 * http://www.research.att.com/people/Cormode_Graham/library/publications/CormodeShkapenyukSrivastavaXu09.pdf
 * to not have to rely on a timer that decays the value periodically
 */
public final class DecayCounter implements RateCounter
{
    // needs to be such that Math.exp(alpha * seconds) does not grow too big
    static final long NANOSECONDS_PER_SECOND = SECONDS.toNanos(1);
    static final double RESCALE_THRESHOLD_NANOSECONDS = SECONDS.toNanos(50);
    static final double DECAY_THRESHOLD_NANOSECONDS = SECONDS.toNanos(30);
    static final long NEVER = Long.MIN_VALUE; // flag for lastDivisorAdjustment, known to be most negative

    private final double alpha;
    private final Ticker ticker;        // ticks in nanoseconds, like systemTicker
    private final long epoch;           // in nanoseconds, but rounded down to a whole second
    private long landmark;              // in nanoseconds relative to epoch
    private double rateNumerator;       // dividend of decay counter
    private double rateDenominator;     // divisor of decay counter
    private long lastDivisorAdjustment; // in nanoseconds relative to epoch

    public DecayCounter(double alpha)
    {
        this(alpha, null);
    }

    public DecayCounter(double alpha, Ticker ticker)
    {
        this(alpha, ticker, 0, SECONDS);
    }

    public DecayCounter(double alpha, long assumedHistory, TimeUnit assumedHistoryTimeUnit)
    {
        this(alpha, null, assumedHistory, assumedHistoryTimeUnit);
    }

    public DecayCounter(double alpha, Ticker ticker, long assumedHistory, TimeUnit assumedHistoryTimeUnit)
    {
        checkArgument(alpha > 0.0, "alpha is non-positive");
        this.alpha = alpha;
        this.ticker = ticker != null ? ticker : Ticker.systemTicker();
        long time = getTick(); // should be the only call to getTick() other than the one in getNow()
        this.epoch = roundDownToWholeSecond(time);
        reset(time - epoch, assumedHistory, assumedHistoryTimeUnit);
    }

    public synchronized void reset(long assumedHistory, TimeUnit assumedHistoryTimeUnit)
    {
        reset(getNow(), assumedHistory, assumedHistoryTimeUnit);
    }

    public synchronized void reset()
    {
        reset(0, SECONDS);
    }

    public synchronized String toString()
    {
        return Objects.toStringHelper(this)
                .add("rate", calcRate())
                .add("rateNumerator", rateNumerator)
                .add("rateDenominator", rateDenominator)
                .add("landmark", landmark / (double) NANOSECONDS_PER_SECOND)
                .add("alpha", alpha)
                .toString();
    }

    public synchronized void add(double value)
    {
        long now = getNow();
        rescale(now);
        rateNumerator += value * weight(now);
    }

    public void add(long value)
    {
        add((double) value);
    }

    private long getTick()
    {
        return ticker.read();
    }

    private long getNow()
    {
        return getTick() - epoch;
    }

    private double calcRate()
    {
        // calculate the decayed average: A = S / C = decayed sum / decayed count
        return rateDenominator != 0.0 ? rateNumerator / rateDenominator : 0.0;
    }

    private void reset(long now, long assumedHistory, TimeUnit assumedHistoryTimeUnit)
    {
        checkArgument(assumedHistory >= 0, "assumedHistory is negative");
        long assumedHistoryInNanoseconds = assumedHistoryTimeUnit.toNanos(assumedHistory);
        checkArgument(NEVER + assumedHistoryInNanoseconds < now, "assumedHistory is too large");
        landmark = now - assumedHistoryInNanoseconds; // ok if this is negative; just not NEVER or underflow;
        lastDivisorAdjustment = assumedHistory > 0 ? landmark : NEVER;
        rateNumerator = rateDenominator = 0.0;
    }

    private double weight(long now)
    {
        return Math.exp(alpha * (double) (now - landmark) / NANOSECONDS_PER_SECOND);
    }

    private void rescale(long now)
    {
        if (now - landmark > RESCALE_THRESHOLD_NANOSECONDS) {
            // rescale the count based on a new landmark to avoid numerical overflow issues.
            double currentWeight = weight(now);
            rateNumerator /= currentWeight;
            rateDenominator /= currentWeight;
            landmark = now;
        }
    }

    private void adjustDivisor(long now)
    {
        if (lastDivisorAdjustment == now) {
            return;
        }
        if (lastDivisorAdjustment == NEVER) {
            rateDenominator = weight(now);
            lastDivisorAdjustment = now;
        }
        else {
            // If the last event is too far in the past, the weights being
            // added are negligible. There is no point in considering them.
            long farthestBack = (long) (DECAY_THRESHOLD_NANOSECONDS / alpha);
            long time = Math.max(lastDivisorAdjustment, now - farthestBack);

            while (time + NANOSECONDS_PER_SECOND <= now) {
                time += NANOSECONDS_PER_SECOND;
                rateDenominator += weight(time);
            }
            lastDivisorAdjustment = time;
        }
    }

    static private long roundDownToWholeSecond(long time)
    {
        // won't work when time < 0 since TimeUnit.convert() truncate towards zero:
        //  return SECONDS.toNanos(NANOSECONDS.toSeconds(time));
        time = Math.max(time, NEVER + NANOSECONDS_PER_SECOND); // pretty degenerate case; rounds up
        long remainder = time % NANOSECONDS_PER_SECOND; // != mod(time, NANOSECONDS_PER_SECOND) when time < 0
        long mod = remainder + (remainder < 0 ? NANOSECONDS_PER_SECOND : 0); // now we've got a proper mod
        return time - mod;
    }

    @Managed
    public synchronized double getCount()
    {
        long now = getNow();
        return rateNumerator / weight(now);
    }

    @Managed
    public synchronized double getRate()
    {
        long now = getNow();
        rescale(now);  // The rescaling...
        adjustDivisor(now);  // ...and adjusting of the divisor does not "change" the value of the counter.
        return calcRate();
    }

    public DecayCounterSnapshot snapshot()
    {
        return new DecayCounterSnapshot(getCount(), getRate());
    }

    public static class DecayCounterSnapshot
    {
        private final double count;
        private final double rate;

        @JsonCreator
        public DecayCounterSnapshot(@JsonProperty("count") double count, @JsonProperty("rate") double rate)
        {
            this.count = count;
            this.rate = rate;
        }

        @JsonProperty
        public double getCount()
        {
            return count;
        }

        @JsonProperty
        public double getRate()
        {
            return rate;
        }

        @Override
        public String toString()
        {
            return Objects.toStringHelper(this)
                    .add("count", count)
                    .add("rate", rate)
                    .toString();
        }
    }
}
