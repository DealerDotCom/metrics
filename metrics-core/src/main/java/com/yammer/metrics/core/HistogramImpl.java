package com.yammer.metrics.core;

import com.yammer.metrics.stats.Sample;
import com.yammer.metrics.stats.Snapshot;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.Math.sqrt;

/**
 * The default implementation of {@link Histogram}.
 */
public class HistogramImpl implements Histogram {
    private final Sample sample;
    private final AtomicLong min = new AtomicLong();
    private final AtomicLong max = new AtomicLong();
    private final AtomicLong sum = new AtomicLong();
    // These are for the Welford algorithm for calculating running variance
    // without floating-point doom.
    private final AtomicReference<double[]> variance =
            new AtomicReference<double[]>(new double[]{-1, 0}); // M, S
    private final AtomicLong count = new AtomicLong();

    /**
     * Creates a new {@link HistogramImpl} with the given sample type.
     *
     * @param type the type of sample to use
     */
    public HistogramImpl(SampleType type) {
        this(type.newSample());
    }

    /**
     * Creates a new {@link HistogramImpl} with the given sample.
     *
     * @param sample the sample to create a histogram from
     */
    public HistogramImpl(Sample sample) {
        this.sample = sample;
        clear();
    }

    @Override
    public void clear() {
        sample.clear();
        count.set(0);
        max.set(Long.MIN_VALUE);
        min.set(Long.MAX_VALUE);
        sum.set(0);
        variance.set(new double[]{ -1, 0 });
    }

    @Override
    public void update(int value) {
        update((long) value);
    }

    @Override
    public void update(long value) {
        count.incrementAndGet();
        sample.update(value);
        setMax(value);
        setMin(value);
        sum.getAndAdd(value);
        updateVariance(value);
    }

    @Override
    public long getCount() {
        return count.get();
    }

    /* (non-Javadoc)
     * @see com.yammer.metrics.core.Summarizable#max()
     */
    @Override
    public double getMax() {
        if (getCount() > 0) {
            return max.get();
        }
        return 0.0;
    }

    /* (non-Javadoc)
     * @see com.yammer.metrics.core.Summarizable#min()
     */
    @Override
    public double getMin() {
        if (getCount() > 0) {
            return min.get();
        }
        return 0.0;
    }

    /* (non-Javadoc)
     * @see com.yammer.metrics.core.Summarizable#mean()
     */
    @Override
    public double getMean() {
        if (getCount() > 0) {
            return sum.get() / (double) getCount();
        }
        return 0.0;
    }

    /* (non-Javadoc)
     * @see com.yammer.metrics.core.Summarizable#stdDev()
     */
    @Override
    public double getStdDev() {
        if (getCount() > 0) {
            return sqrt(getVariance());
        }
        return 0.0;
    }

    /* (non-Javadoc)
     * @see com.yammer.metrics.core.Summarizable#sum()
     */
    @Override
    public double getSum() {
        return (double) sum.get();
    }

    @Override
    public Snapshot getSnapshot() {
        return sample.getSnapshot();
    }

    private double getVariance() {
        if (getCount() <= 1) {
            return 0.0;
        }
        return variance.get()[1] / (getCount() - 1);
    }

    private void setMax(long potentialMax) {
        boolean done = false;
        while (!done) {
            final long currentMax = max.get();
            done = currentMax >= potentialMax || max.compareAndSet(currentMax, potentialMax);
        }
    }

    private void setMin(long potentialMin) {
        boolean done = false;
        while (!done) {
            final long currentMin = min.get();
            done = currentMin <= potentialMin || min.compareAndSet(currentMin, potentialMin);
        }
    }

    private void updateVariance(long value) {
        while (true) {
            final double[] oldValues = variance.get();
            final double[] newValues = new double[2];
            if (oldValues[0] == -1) {
                newValues[0] = value;
                newValues[1] = 0;
            } else {
                final double oldM = oldValues[0];
                final double oldS = oldValues[1];

                final double newM = oldM + ((value - oldM) / getCount());
                final double newS = oldS + ((value - oldM) * (value - newM));

                newValues[0] = newM;
                newValues[1] = newS;
            }
            if (variance.compareAndSet(oldValues, newValues)) {
                return;
            }
        }
    }
}