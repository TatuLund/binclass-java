/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.gla;

import org.binclass.algorithms.util.MathUtils;

/**
 * Configuration record for GLA algorithm parameters.
 * <p>
 * Bundles all command-line parameters that affect GLA behavior into an
 * immutable configuration object, simplifying method signatures and ensuring
 * consistent parameter passing across all GLA variants.
 * </p>
 */
public record GLAConfig(
        /** Convergence epsilon threshold */
        double epsilon,

        /** PNN merging threshold (squared L2 distance) */
        double pnnThreshold,

        /**
         * Heuristic type: 1=standard, 2=stochastic, 3=simulated annealing, etc.
         */
        int heuristic,

        /** Alternate mode parameter */
        int alternateMode,

        /** Centroid type: 1=CLASSIC */
        int centroidType,

        /** Maximum iterations (0 means use default) */
        int maxIter,

        /** Safety limit for iterations */
        int safetyLimit,

        /** Iteration base parameter */
        int iterBase,

        /** Total number of vectors in the dataset */
        int n,

        /** Maximum clusters to search for Split-GLA (-S flag) */
        int kstopwhen,

        /** Steps without SC improvement before stopping Split-GLA (-W flag) */
        int kcStopWhen,

        /** Use class weights (true) or uniform weights (false) */
        boolean weights,

        /**
         * Round centroids to binary values (true) or keep fractional (false)
         */
        boolean rounded,

        /**
         * Use Jeffreys prior for stochastic complexity (true) or uniform prior
         * (false)
         */
        boolean jeffreysPrior,

        /** Enable trashcan class mode (-t flag) */
        boolean trashcan,

        /** Analyse missing bits in vectors (-m flag) */
        boolean analyseMissing,

        /** Log centroid information to file (-l flag) */
        boolean logCentroids,

        /** First distance value for initialization (-B flag) */
        double firstD,

        /** Use best code length criterion (-C flag) */
        boolean bestCodeLength,

        /** Distance type: 1=HAM, others (int) */
        int distanceType,

        /** Heuristic count parameter (-j flag) */
        int heuristicCount,

        /** filter_exact_k flag: keep only partitions with exactly k clusters */
        boolean filterExactK,

        /** require_better flag: require improving distance in trials */
        boolean requireBetter,

        /**
         * Local search cycler mode (-r7): cycle through all operators instead
         * of using a single fixed one. Mirrors C {@code ls_heuristic_cycler}.
         */
        boolean lsCycler,

        /**
         * Local search adaptive mode (-r8): adaptively select operators based
         * on success probabilities. Mirrors C {@code ls_adaptive_heuristic}.
         */
        boolean lsAdaptive,

        /**
         * decreasing_epsilon (-E two-char form): reset and halve the epsilon
         * threshold each GLA iteration. Mirrors C {@code decreasing_epsilon}.
         */
        boolean decreasingEpsilon,

        /**
         * alternate_worst_match (-eX): fill an empty cell with the absolute
         * worst-match vector instead of the class-distance worst match.
         * Mirrors C {@code alternate_worst_match}.
         */
        boolean alternateWorstMatch,

        /**
         * alternate_empty_cell_fix (-eX): run a local repartition after filling
         * an empty cell. Mirrors C {@code alternate_empty_cell_fix}.
         */
        boolean alternateEmptyCellFix) {

    /** Default configuration with standard parameters */
    public static final GLAConfig DEFAULT = new GLAConfig(
            MathUtils.EPSILON, // epsilon
            1.8, // pnnThreshold
            1, // heuristic (standard)
            1, // alternateMode
            1, // centroidType (CLASSIC)
            0, // maxIter (use default)
            1000, // safetyLimit
            0, // iterBase
            0, // n (to be set by caller)
            20, // kstopwhen (-S flag)
            5, // kcStopWhen (-W flag)
            false, // weights (uniform by default)
            false, // rounded (fractional centroids by default)
            false, // jeffreysPrior (uniform prior by default)
            false, // trashcan (disabled by default)
            false, // analyseMissing (disabled by default)
            false, // logCentroids (disabled by default)
            0.0, // firstD (no initial distance)
            false, // bestCodeLength (false by default)
            1, // distanceType (HAM by default)
            1, // heuristicCount (default)
            false, // filterExactK (disabled by default)
            false, // requireBetter (disabled by default)
            false, // lsCycler (disabled by default)
            false, // lsAdaptive (disabled by default)
            false, // decreasingEpsilon (-E two-char form)
            true, // alternateWorstMatch (TRUE by default per C vars.c)
            false // alternateEmptyCellFix (FALSE by default per C vars.c)
    );

    /** Effective maximum iterations based on config */
    public int effectiveMaxIter() {
        return maxIter > 0 ? maxIter : safetyLimit;
    }
}
