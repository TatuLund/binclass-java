/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.cumulative;

import org.jspecify.annotations.NullMarked;
import org.binclass.algorithms.classify.CumulativeConfig;
import org.binclass.algorithms.util.MathUtils;

/**
 * Immutable snapshot of every raw value the Cumulative Classification form
 * exposes, plus the logic that turns it into a {@link CumulativeConfig}.
 * <p>
 * Mirrors the flag-to-control mapping in {@code docs/WebUI.PRD.md} §5.2 and the
 * reference parsing in {@code CumulativeCommand}. The record holds only plain
 * field values; it knows nothing about Vaadin components so it can be unit
 * tested in isolation.
 * </p>
 */
@NullMarked
public record CumulativeParametersForm(
        /** Filebase (or generated name) of the uploaded data files. */
        String filebase,
        /** Predictive-fit delta ({@code -D}/{@code -d}). */
        int delta,
        /** Whether fixed delta was explicitly set via {@code -D}. */
        boolean fixedDelta,
        /** Cumulative analysis enabled ({@code -N}). */
        boolean cumulativeAnalysis,
        /** Number of cumulative samples ({@code -s}). */
        int cumulativeSamples,
        /**
         * Bayesian predictive identification enabled (default on, toggle off).
         */
        boolean bayesianPredictive,
        /** Test feature significance enabled ({@code -F}). */
        boolean testFeatureSignificance,
        /** Save by probability factor enabled (default on, toggle off). */
        boolean cumSaveByPf,
        /** No new classes mode enabled ({@code -n}). */
        boolean cumNoNewClasses,
        /** Process vectors in input order ({@code -O}/{@code -I}). */
        boolean inOrder,
        /**
         * Convergence threshold ({@code -E}); validated in {@link #isValid()}.
         */
        double epsilon) {

    /**
     * Returns the default form values matching
     * {@link CumulativeConfig#defaults()}. Both {@code -d} and "no delta flag"
     * start at {@code fixedDelta=false}, so the checkbox is unchecked.
     *
     * @return a fresh defaults instance with an empty filebase
     */
    public static CumulativeParametersForm defaults() {
        CumulativeConfig d = CumulativeConfig.defaults();
        return new CumulativeParametersForm(
                "",
                d.delta(),
                d.fixedDelta(),
                d.cumulativeAnalysis(),
                d.cumulativeSamples(),
                d.bayesianPredictive(),
                d.testFeatureSignificance(),
                d.cumSaveByPf(),
                d.cumNoNewClasses(),
                d.inOrder(),
                d.epsilon());
    }

    /**
     * Translates the form values into a {@link CumulativeConfig}. The vector
     * count is supplied by the caller once data is loaded and stored as
     * {@code n} for the algorithm, even though cumulative classification does
     * not use it.
     *
     * @param vectorCount
     *            total number of vectors in the loaded set (stored as
     *            {@code n})
     * @return a validated {@link CumulativeConfig}
     */
    public CumulativeConfig toConfig(int vectorCount) {
        return new CumulativeConfig(
                delta,
                cumulativeAnalysis,
                cumulativeSamples,
                fixedDelta,
                bayesianPredictive,
                testFeatureSignificance,
                cumSaveByPf,
                cumNoNewClasses,
                inOrder,
                validateEpsilon(epsilon),
                vectorCount);
    }

    /**
     * Convenience overload that leaves {@code n} unset. Intended for tests and
     * callers that do not need the vector count populated.
     *
     * @return a config with {@code n = 0}
     */
    public CumulativeConfig toConfig() {
        return toConfig(0);
    }

    /**
     * Validates the convergence threshold in the open interval
     * {@code (0, 0.5)}. Cumulative has no decreasing-epsilon mode, so a bare
     * value is always range-checked here.
     *
     * @param candidate
     *            the epsilon value to check
     * @return the validated threshold in the open interval {@code (0, 0.5)}
     */
    private static double validateEpsilon(double candidate) {
        if (candidate <= 0.0 || candidate >= 0.5) {
            throw new IllegalArgumentException(
                    "epsilon must be in (0, 0.5): " + candidate);
        }
        return candidate;
    }

    /**
     * Whether the convergence threshold is positive: a bare value passes
     * through unchanged. Cumulative has no decreasing-epsilon mode, so this
     * only guards against non-positive input; the full {@code (0, 0.5)} range
     * gate lives in {@link #isValid()}.
     *
     * @return {@code true} when epsilon is greater than zero
     */
    public boolean isValidEpsilon() {
        return epsilon > 0.0;
    }

    /**
     * Whether every exposed field holds a valid value: delta non-negative and
     * epsilon in the open interval {@code (0, 0.5)}. Gates the Run button.
     *
     * @return {@code true} when the form is fully valid
     */
    public boolean isValid() {
        return delta >= 0 && epsilon > 0.0 && epsilon < 0.5;
    }

    /**
     * Returns the epsilon constant used by the algorithm, exposed so tests and
     * callers can seed fields without importing the algorithms module.
     *
     * @return the shared epsilon value
     */
    public static double epsilonConstant() {
        return MathUtils.EPSILON;
    }
}
