/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.classify;

import org.binclass.algorithms.util.MathUtils;

/**
 * Configuration record for cumulative classification algorithm.
 * <p>
 * Bundles all parameters needed by {@link CumulativeClassifier} including the
 * delta value, analysis mode flags, and processing options.
 * </p>
 */
public record CumulativeConfig(
        /**
         * Delta value for predictive fit calculation (controls sensitivity to
         * new class creation)
         */
        int delta,

        /** Whether cumulative analysis is enabled (-N flag) */
        boolean cumulativeAnalysis,

        /** Number of cumulative samples (-s flag) */
        int cumulativeSamples,

        /**
         * Whether fixed delta was explicitly set via -D vs -d (-D enables
         * fixedDelta mode)
         */
        boolean fixedDelta,

        /**
         * Bayesian predictive identification enabled (default true, disabled by
         * -S)
         */
        boolean bayesianPredictive,

        /** Test feature significance enabled (-F flag) */
        boolean testFeatureSignificance,

        /** Save by probability factor enabled (default true, disabled by -c) */
        boolean cumSaveByPf,

        /** No new classes mode enabled (-n flag) */
        boolean cumNoNewClasses,

        /** Process vectors in input order instead of shuffled (-O flag) */
        boolean inOrder,

        /** Epsilon convergence threshold for cumulative analysis (-E flag) */
        double epsilon) {

    /**
     * Creates a CumulativeConfig with default values.
     * <p>
     * Default configuration: delta=1, all flags disabled except
     * bayesianPredictive=true.
     * </p>
     *
     * @return a new CumulativeConfig with sensible defaults
     */
    public static CumulativeConfig defaults() {
        return new CumulativeConfig(
                1, // default delta
                false, // cumulativeAnalysis disabled by default
                0, // no samples limit
                false, // fixedDelta not set
                true, // bayesianPredictive enabled by default
                false, // testFeatureSignificance disabled
                true, // cumSaveByPf enabled by default
                false, // cumNoNewClasses disabled
                false, // inOrder disabled (shuffle vectors)
                MathUtils.EPSILON // epsilon convergence threshold
        );
    }

    /**
     * Creates a CumulativeConfig with the specified delta value and all other
     * defaults.
     *
     * @param delta
     *            the delta value for predictive fit calculation
     * @return a new CumulativeConfig with the given delta
     */
    public static CumulativeConfig ofDelta(int delta) {
        return new CumulativeConfig(
                delta,
                false, // cumulativeAnalysis disabled by default
                0, // no samples limit
                false, // fixedDelta not set
                true, // bayesianPredictive enabled by default
                false, // testFeatureSignificance disabled
                true, // cumSaveByPf enabled by default
                false, // cumNoNewClasses disabled
                false, // inOrder disabled (shuffle vectors)
                0.001 // epsilon convergence threshold
        );
    }

    /**
     * Creates a CumulativeConfig with the specified delta value and fixedDelta
     * mode.
     * <p>
     * This is used when -D flag is provided (explicitly set delta).
     * </p>
     *
     * @param delta
     *            the delta value for predictive fit calculation
     * @return a new CumulativeConfig with the given delta and fixedDelta=true
     */
    public static CumulativeConfig ofFixedDelta(int delta) {
        return new CumulativeConfig(
                delta,
                false, // cumulativeAnalysis disabled by default
                0, // no samples limit
                true, // fixedDelta enabled (from -D flag)
                true, // bayesianPredictive enabled by default
                false, // testFeatureSignificance disabled
                true, // cumSaveByPf enabled by default
                false, // cumNoNewClasses disabled
                false, // inOrder disabled (shuffle vectors)
                0.001 // epsilon convergence threshold
        );
    }

    /**
     * Creates a CumulativeConfig with all parameters specified.
     * <p>
     * Use this when constructing from parsed command line arguments.
     * </p>
     *
     * @param delta
     *            the delta value for predictive fit calculation
     * @param cumulativeAnalysis
     *            whether cumulative analysis is enabled (-N flag)
     * @param cumulativeSamples
     *            number of cumulative samples (-s flag)
     * @param fixedDelta
     *            whether fixed delta was explicitly set via -D vs -d
     * @param bayesianPredictive
     *            Bayesian predictive identification enabled (default true,
     *            disabled by -S)
     * @param testFeatureSignificance
     *            test feature significance enabled (-F flag)
     * @param cumSaveByPf
     *            save by probability factor enabled (default true, disabled by
     *            -c)
     * @param cumNoNewClasses
     *            no new classes mode enabled (-n flag)
     * @param inOrder
     *            process vectors in input order instead of shuffled (-O flag)
     * @param epsilon
     *            convergence threshold for cumulative analysis (-E flag)
     */
    public CumulativeConfig(
            int delta,
            boolean cumulativeAnalysis,
            int cumulativeSamples,
            boolean fixedDelta,
            boolean bayesianPredictive,
            boolean testFeatureSignificance,
            boolean cumSaveByPf,
            boolean cumNoNewClasses,
            boolean inOrder,
            double epsilon) {

        if (delta < 0) {
            throw new IllegalArgumentException("Delta must be >= 0");
        }

        this.delta = delta;
        this.cumulativeAnalysis = cumulativeAnalysis;
        this.cumulativeSamples = cumulativeSamples;
        this.fixedDelta = fixedDelta;
        this.bayesianPredictive = bayesianPredictive;
        this.testFeatureSignificance = testFeatureSignificance;
        this.cumSaveByPf = cumSaveByPf;
        this.cumNoNewClasses = cumNoNewClasses;
        this.inOrder = inOrder;
        this.epsilon = epsilon;
    }

    /**
     * Returns a new CumulativeConfig with the specified delta value.
     * <p>
     * This is a convenience method for creating configs with different delta
     * values while preserving other settings.
     * </p>
     *
     * @param delta
     *            the new delta value
     * @return a new CumulativeConfig with the given delta and all other fields
     *         unchanged
     */
    public CumulativeConfig withDelta(int delta) {
        return new CumulativeConfig(
                delta,
                this.cumulativeAnalysis,
                this.cumulativeSamples,
                this.fixedDelta,
                this.bayesianPredictive,
                this.testFeatureSignificance,
                this.cumSaveByPf,
                this.cumNoNewClasses,
                this.inOrder,
                this.epsilon);
    }

    /**
     * Returns a new CumulativeConfig with the specified bayesianPredictive
     * flag.
     * <p>
     * This is a convenience method for creating configs with different bayesian
     * predictive settings.
     * </p>
     *
     * @param bayesianPredictive
     *            the new bayesian predictive setting
     * @return a new CumulativeConfig with the given bayesianPredictive and all
     *         other fields unchanged
     */
    public CumulativeConfig withBayesianPredictive(boolean bayesianPredictive) {
        return new CumulativeConfig(
                this.delta,
                this.cumulativeAnalysis,
                this.cumulativeSamples,
                this.fixedDelta,
                bayesianPredictive,
                this.testFeatureSignificance,
                this.cumSaveByPf,
                this.cumNoNewClasses,
                this.inOrder,
                this.epsilon);
    }

    /**
     * Returns a new CumulativeConfig with the specified testFeatureSignificance
     * flag.
     * <p>
     * This is a convenience method for creating configs with different feature
     * significance settings.
     * </p>
     *
     * @param testFeatureSignificance
     *            the new test feature significance setting
     * @return a new CumulativeConfig with the given testFeatureSignificance and
     *         all other fields unchanged
     */
    public CumulativeConfig withTestFeatureSignificance(
            boolean testFeatureSignificance) {
        return new CumulativeConfig(
                this.delta,
                this.cumulativeAnalysis,
                this.cumulativeSamples,
                this.fixedDelta,
                this.bayesianPredictive,
                testFeatureSignificance,
                this.cumSaveByPf,
                this.cumNoNewClasses,
                this.inOrder,
                this.epsilon);
    }

    /**
     * Returns a new CumulativeConfig with the specified cumSaveByPf flag.
     * <p>
     * This is a convenience method for creating configs with different save by
     * probability factor settings.
     * </p>
     *
     * @param cumSaveByPf
     *            the new cumSaveByPf setting
     * @return a new CumulativeConfig with the given cumSaveByPf and all other
     *         fields unchanged
     */
    public CumulativeConfig withCumSaveByPf(boolean cumSaveByPf) {
        return new CumulativeConfig(
                this.delta,
                this.cumulativeAnalysis,
                this.cumulativeSamples,
                this.fixedDelta,
                this.bayesianPredictive,
                this.testFeatureSignificance,
                cumSaveByPf,
                this.cumNoNewClasses,
                this.inOrder,
                this.epsilon);
    }

    /**
     * Returns a new CumulativeConfig with the specified cumNoNewClasses flag.
     * <p>
     * This is a convenience method for creating configs with different no new
     * classes settings.
     * </p>
     *
     * @param cumNoNewClasses
     *            the new cumNoNewClasses setting
     * @return a new CumulativeConfig with the given cumNoNewClasses and all
     *         other fields unchanged
     */
    public CumulativeConfig withCumNoNewClasses(boolean cumNoNewClasses) {
        return new CumulativeConfig(
                this.delta,
                this.cumulativeAnalysis,
                this.cumulativeSamples,
                this.fixedDelta,
                this.bayesianPredictive,
                this.testFeatureSignificance,
                this.cumSaveByPf,
                cumNoNewClasses,
                this.inOrder,
                this.epsilon);
    }

    /**
     * Returns a new CumulativeConfig with the specified cumulativeAnalysis
     * flag.
     * <p>
     * This is a convenience method for creating configs with different
     * cumulative analysis settings.
     * </p>
     *
     * @param cumulativeAnalysis
     *            the new cumulativeAnalysis setting
     * @return a new CumulativeConfig with the given cumulativeAnalysis and all
     *         other fields unchanged
     */
    public CumulativeConfig withCumulativeAnalysis(boolean cumulativeAnalysis) {
        return new CumulativeConfig(
                this.delta,
                cumulativeAnalysis,
                this.cumulativeSamples,
                this.fixedDelta,
                this.bayesianPredictive,
                this.testFeatureSignificance,
                this.cumSaveByPf,
                this.cumNoNewClasses,
                this.inOrder,
                this.epsilon);
    }

    /**
     * Returns a new CumulativeConfig with the specified cumulativeSamples
     * value.
     * <p>
     * This is a convenience method for creating configs with different sample
     * limits.
     * </p>
     *
     * @param cumulativeSamples
     *            the new cumulativeSamples value
     * @return a new CumulativeConfig with the given cumulativeSamples and all
     *         other fields unchanged
     */
    public CumulativeConfig withCumulativeSamples(int cumulativeSamples) {
        return new CumulativeConfig(
                this.delta,
                this.cumulativeAnalysis,
                cumulativeSamples,
                this.fixedDelta,
                this.bayesianPredictive,
                this.testFeatureSignificance,
                this.cumSaveByPf,
                this.cumNoNewClasses,
                this.inOrder,
                this.epsilon);
    }

    /**
     * Returns a new CumulativeConfig with the specified fixedDelta flag.
     * <p>
     * This is a convenience method for creating configs with different fixed
     * delta settings.
     * </p>
     *
     * @param fixedDelta
     *            the new fixedDelta setting
     * @return a new CumulativeConfig with the given fixedDelta and all other
     *         fields unchanged
     */
    public CumulativeConfig withFixedDelta(boolean fixedDelta) {
        return new CumulativeConfig(
                this.delta,
                this.cumulativeAnalysis,
                this.cumulativeSamples,
                fixedDelta,
                this.bayesianPredictive,
                this.testFeatureSignificance,
                this.cumSaveByPf,
                this.cumNoNewClasses,
                this.inOrder,
                this.epsilon);
    }

    @Override
    public String toString() {
        return "CumulativeConfig[" +
                "delta=" + delta + ", " +
                "cumulativeAnalysis=" + cumulativeAnalysis + ", " +
                "cumulativeSamples=" + cumulativeSamples + ", " +
                "fixedDelta=" + fixedDelta + ", " +
                "bayesianPredictive=" + bayesianPredictive + ", " +
                "testFeatureSignificance=" + testFeatureSignificance + ", " +
                "cumSaveByPf=" + cumSaveByPf + ", " +
                "cumNoNewClasses=" + cumNoNewClasses + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof CumulativeConfig other))
            return false;
        return delta == other.delta &&
                cumulativeAnalysis == other.cumulativeAnalysis &&
                cumulativeSamples == other.cumulativeSamples &&
                fixedDelta == other.fixedDelta &&
                bayesianPredictive == other.bayesianPredictive &&
                testFeatureSignificance == other.testFeatureSignificance &&
                cumSaveByPf == other.cumSaveByPf &&
                cumNoNewClasses == other.cumNoNewClasses;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31
                * (31 * (31 * (31 * (31 * delta + (cumulativeAnalysis ? 1 : 0))
                        + cumulativeSamples) + (fixedDelta ? 1 : 0))
                        + (bayesianPredictive ? 1 : 0))
                + (testFeatureSignificance ? 1 : 0)) + (cumSaveByPf ? 1 : 0))
                + (cumNoNewClasses ? 1 : 0);
    }
}
