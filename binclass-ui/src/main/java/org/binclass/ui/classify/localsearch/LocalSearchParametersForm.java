/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.localsearch;

import org.jspecify.annotations.NullMarked;
import org.binclass.algorithms.gla.GLAConfig;

/**
 * Immutable snapshot of the raw values the Local Search form exposes, plus the
 * logic that turns it into a {@link GLAConfig}. Mirrors the flag-to-control
 * mapping in {@code docs/WebUI.PRD.md} §4.2 — focused on heuristic-related
 * flags (heuristic 1..8, {@code -j} count, epsilon, {@code -J}) and reuses the
 * shared GLA scalar fields so a valid config can be built without the
 * search-type switches that Local Search drops.
 * <p>
 * The two meta-heuristics are booleans on {@link GLAConfig}, not extra
 * heuristic codes: code 7 sets {@code lsCycler} and code 8 sets
 * {@code lsAdaptive}. This record resolves them in {@link #toConfig(int)} so
 * the view's translation layer stays untouched. The form carries no
 * {@code kstop}/{@code kstopwhen}, so there is no mutual-exclusion branch —
 * {@link #isValid()} only checks epsilon.
 * </p>
 */
@NullMarked
public record LocalSearchParametersForm(
        /** Filebase (or generated name) of the uploaded data files. */
        String filebase,
        /** Heuristic type ({@code -r}); 1..8, where 7=cycler and 8=adaptive. */
        int heuristic,
        /** Heuristic count parameter ({@code -j}). */
        int heuristicCount,
        /**
         * Convergence threshold ({@code -E}); validated in {@link #toConfig()}.
         */
        double epsilon,
        /** Bare {@code -E}: decreasing-epsilon mode toggles the threshold. */
        boolean decreasingEpsilon,
        /**
         * Alternate worst-match/empty-cell-fix combination ({@code -eX}, 1-4).
         */
        int alternateMode,
        /** Centroid type strategy ({@code -c}). */
        int centroidType,
        /** Distance type ({@code -f}). */
        int distanceType,
        /** Use Jeffreys prior for stochastic complexity ({@code -J}). */
        boolean jeffreysPrior) {

    /**
     * Returns the default form values matching {@link GLAConfig#DEFAULT}.
     *
     * @return a fresh defaults instance with an empty filebase
     */
    public static LocalSearchParametersForm defaults() {
        GLAConfig d = GLAConfig.DEFAULT;
        return new LocalSearchParametersForm(
                "",
                d.heuristic(),
                d.heuristicCount(),
                d.epsilon(),
                false,
                d.alternateMode(),
                d.centroidType(),
                d.distanceType(),
                false);
    }

    /**
     * Translates the form values into a {@link GLAConfig}. The vector count is
     * supplied by the caller once data is loaded and stored in {@code n} for
     * weighted codelength calculations. Code 7 sets {@code lsCycler} and code 8
     * sets {@code lsAdaptive}; every other code keeps a single operator.
     *
     * @param vectorCount
     *            total number of vectors in the loaded set (sets {@code n})
     * @return a validated {@link GLAConfig}
     */
    public GLAConfig toConfig(int vectorCount) {
        double eps = decreasingEpsilon ? epsilon : validateEpsilon(epsilon);
        boolean worstMatch = alternateMode == 2 || alternateMode == 4;
        boolean emptyCellFix = alternateMode == 3 || alternateMode == 4;
        return new GLAConfig(
                eps,
                1.8,
                heuristic,
                alternateMode,
                centroidType,
                0,
                GLAConfig.DEFAULT.safetyLimit(),
                GLAConfig.DEFAULT.iterBase(),
                vectorCount,
                GLAConfig.DEFAULT.kstopwhen(),
                GLAConfig.DEFAULT.kcStopWhen(),
                jeffreysPrior,
                false,
                jeffreysPrior,
                false,
                false,
                false,
                0.0,
                false,
                distanceType,
                heuristicCount,
                false,
                false,
                heuristic == 7,
                heuristic == 8,
                decreasingEpsilon,
                worstMatch,
                emptyCellFix);
    }

    /**
     * Convenience overload that leaves {@code n} unset. Intended for tests and
     * callers that do not need the vector count populated.
     *
     * @return a config with {@code n = 0}
     */
    public GLAConfig toConfig() {
        return toConfig(0);
    }

    /**
     * Validates the convergence threshold, allowing a bare decreasing-epsilon
     * value through unchanged.
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
     * Whether the convergence threshold is valid for the current
     * decreasing-epsilon mode: a bare value passes through unchanged when
     * {@code -E} toggling is on, otherwise it must lie in the open interval
     * {@code (0, 0.5)}.
     *
     * @return {@code true} when epsilon may be used as-is
     */
    public boolean isValidEpsilon() {
        return decreasingEpsilon || (epsilon > 0.0 && epsilon < 0.5);
    }

    /**
     * Whether every exposed field holds a valid value. Local Search drops the
     * search-type switches, so validity is just the epsilon range check.
     *
     * @return {@code true} when the form is fully valid
     */
    public boolean isValid() {
        return isValidEpsilon();
    }
}
