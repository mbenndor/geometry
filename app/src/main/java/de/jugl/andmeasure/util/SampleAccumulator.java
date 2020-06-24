package de.jugl.andmeasure.util;

import java.util.Arrays;

public class SampleAccumulator {

    /**
     * Default size of sample accumulator.
     */
    public static final int DEFAULT_SAMPLE_SIZE = 50;

    /**
     * Amount of samples to collect.
     */
    private final int mSampleSize;

    /**
     * Amount of collected samples.
     */
    private int mSampleCount;

    /**
     * Array of samples.
     */
    private double[] mSamples;

    /**
     * Creates a new sample accumulator with space for 50 samples.
     */
    public SampleAccumulator() {
        this(DEFAULT_SAMPLE_SIZE);
    }

    /**
     * Creates a new sample accumulator.
     *
     * @param sampleSize Amount of samples to collect
     */
    public SampleAccumulator(int sampleSize) {
        this.mSampleSize = sampleSize;
        this.mSamples = new double[sampleSize];
        this.clear();
    }

    /**
     * Resets the sample accumulator.
     */
    public void clear() {
        this.mSampleCount = 0;
        Arrays.fill(this.mSamples, 0d);
    }

    /**
     * Appends a sample to the sample accumulator.
     *
     * @param val Sample to add
     * @return <code>false</code> if the maximum amount of samples was reached after adding the provided sample,
     * or if the sample accumulator is already full, <code>false</code> otherwise
     */
    public boolean push(double val) {
        if (this.isFull()) {
            return false;
        }

        this.mSamples[this.mSampleCount++] = val;

        return !this.isFull();
    }

    /**
     * Calculates the average over all samples.
     *
     * @return Average of samples
     */
    public double getAverage() {
        // Check trivial cases.
        if (this.mSampleCount == 0) {
            return 0d;
        } else if (this.mSampleCount == 1) {
            return this.mSamples[0];
        }

        double avg = 0d;

        for (int i = 0; i < this.mSampleCount; i++) {
            avg += this.mSamples[i];
        }

        return avg / this.mSampleCount;
    }

    /**
     * Checks if the sample accumulator is full.
     *
     * @return <code>true</code> if the sample accumulator is full, <code>false</code> otherwise
     */
    public boolean isFull() {
        return this.mSampleCount == this.mSampleSize;
    }

    /**
     * @return Amount of samples to collect
     */
    public int getSampleSize() {
        return this.mSampleSize;
    }

    /**
     * @return Amount of collected samples
     */
    public int getSampleCount() {
        return this.mSampleCount;
    }

    /**
     * @return Collected samples
     */
    public double[] getSamples() {
        return this.mSamples;
    }

}
