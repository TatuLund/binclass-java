/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.gla;

import org.jspecify.annotations.NullMarked;
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.SearchType;

/**
 * Immutable snapshot of every raw value the GLA Search form exposes, plus the
 * logic that turns it into a {@link GLAConfig} and resolves the search type.
 * <p>
 * Mirrors the flag-to-control mapping in {@code docs/WebUI.PRD.md} §3.2 and the
 * reference parsing in {@code ClassifyCommand}. The record holds only plain
 * field values; it knows nothing about Vaadin components so it can be unit
 * tested in isolation and reused by Local Search, which shares the same shape.
 * </p>
 */
@NullMarked
public record GlaParametersForm(
        /** Filebase (or generated name) of the uploaded data files. */
        String filebase,
        /**
         * Lowest cluster count to try ({@code -b}); 1 means automatic start.
         */
        int kStart,
        /** Highest cluster count to try ({@code -s}); 0 means auto-scan. */
        int kStop,
        /** Maximum clusters to search for Split-GLA ({@code -S}). */
        int kStopWhen,
        /** Steps without SC improvement before stopping ({@code -W}). */
        int kcStopWhen,
        /**
         * Convergence threshold ({@code -E}); validated in {@link #toConfig()}.
         */
        double epsilon,
        /** Bare {@code -E}: decreasing-epsilon mode toggles the threshold. */
        boolean decreasingEpsilon,
        /** Heuristic type ({@code -r}). */
        int heuristic,
        /**
         * Alternate worst-match/empty-cell-fix combination ({@code -eX}, 1-4).
         */
        int alternateMode,
        /** Analyse missing bits in vectors ({@code -m}). */
        boolean analyseMissing,
        /** Centroid type strategy ({@code -c}). */
        int centroidType,
        /** Log centroid information to file ({@code -l}). */
        boolean logCentroids,
        /** Use Jeffreys prior for stochastic complexity ({@code -J}). */
        boolean jeffreysPrior,
        /** Use class weights instead of uniform weights ({@code -w}). */
        boolean classWeights,
        /** Enable trashcan (outlier) class mode ({@code -t}). */
        boolean trashcan,
        /** Require improving distance in trials ({@code -B}). */
        boolean requireBetter,
        /** First-distance value for initialization ({@code -B} double). */
        double firstD,
        /** Use best code length criterion ({@code -C}). */
        boolean bestCodeLength,
        /** Round centroids to binary values ({@code -R}). */
        boolean rounded,
        /** Distance type ({@code -f}). */
        int distanceType,
        /** Maximum iterations ({@code -n}); non-zero forces range search. */
        int maxIter,
        /** Heuristic count parameter ({@code -j}). */
        int heuristicCount,
        /** Safety limit for iterations ({@code -F}). */
        int safetyLimit,
        /** Iteration base parameter ({@code -a}). */
        int iterBase,
        /** Predefined centroids file to evaluate ({@code -L}; LCENT search). */
        String centroidFile) {

    /**
     * Returns the default form values matching {@link GLAConfig#DEFAULT}.
     *
     * @return a fresh defaults instance with an empty filebase
     */
    public static GlaParametersForm defaults() {
        GLAConfig d = GLAConfig.DEFAULT;
        return new GlaParametersForm(
                "",
                1,
                0,
                d.kstopwhen(),
                d.kcStopWhen(),
                d.epsilon(),
                false,
                d.heuristic(),
                d.alternateMode(),
                false,
                d.centroidType(),
                false,
                false,
                false,
                false,
                false,
                0.0,
                false,
                false,
                d.distanceType(),
                d.maxIter(),
                d.heuristicCount(),
                d.safetyLimit(),
                d.iterBase(),
                "");
    }

    /**
     * Resolves the search type from which flags are set, mirroring
     * {@code ClassifyCommand.determineSearchType()}. A fixed range ({@code -b}
     * or {@code -s}) or an explicit iteration cap ({@code -n}) selects range
     * search; a centroids file selects LCENT; otherwise the automatic
     * SC-minimizer runs.
     *
     * @return the resolved {@link SearchType}
     */
    public SearchType determineSearchType() {
        if (maxIter > 0) {
            return SearchType.NAUTO;
        }
        if (centroidFile != null && !centroidFile.isBlank()) {
            return SearchType.LCENT;
        }
        if (kStart > 1 || kStop > 0) {
            return SearchType.NAUTO;
        }
        return SearchType.AUTO;
    }

    /**
     * Translates the form values into a {@link GLAConfig}. The vector count is
     * supplied by the caller once data is loaded and stored in {@code n} for
     * weighted codelength calculations.
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
                maxIter,
                safetyLimit,
                iterBase,
                vectorCount,
                kStopWhen,
                kcStopWhen,
                classWeights,
                rounded,
                jeffreysPrior,
                trashcan,
                analyseMissing,
                logCentroids,
                firstD,
                bestCodeLength,
                distanceType,
                heuristicCount,
                false,
                requireBetter,
                false,
                false,
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
     * Whether the search-range switches are mutually exclusive: {@code -s}
     * (kstop) and {@code -S} (kstopwhen) must not both be set to a positive
     * value, since each independently bounds the range search.
     *
     * @return {@code true} when at most one of kstop/kstopwhen is positive
     */
    public boolean kRangesCompatible() {
        return !(kStop > 0 && kStopWhen > 0);
    }

    /**
     * Whether every exposed field holds a valid value. Combines the epsilon
     * range check with the kstop/kstopwhen mutual-exclusion rule so callers can
     * gate the Run button on a single predicate.
     *
     * @return {@code true} when the form is fully valid
     */
    public boolean isValid() {
        return isValidEpsilon() && kRangesCompatible();
    }
}
