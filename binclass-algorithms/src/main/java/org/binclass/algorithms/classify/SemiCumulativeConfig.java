/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.classify;

/**
 * Configuration record for semi-cumulative classification algorithm.
 * <p>
 * Extends cumulative configuration with parameters specific to the
 * semi-cumulative (sclassify) mode, including absolute matching,
 * Jeffreys prior, join target, and GLA threshold.
 * </p>
 */
public record SemiCumulativeConfig(
        /** Whether to use absolute match (-A flag) */
        boolean useAbsMatch,

        /** Epsilon convergence threshold (-E flag, default 0.001) */
        double epsilon,

        /** Use Jeffreys prior for Bayesian calculation (-J flag) */
        boolean jeffreysPrior,

        /** Join target parameter (-j flag, default 2) */
        int joinTarget,

        /** GLA threshold parameter (-T flag, default 1.0) */
        double glaThreshold) {

    /**
     * Creates a SemiCumulativeConfig with default values.
     * <p>
     * Default configuration: useAbsMatch=false, epsilon=0.001,
     * jeffreysPrior=false, joinTarget=2, glaThreshold=1.0.
     * </p>
     *
     * @return a new SemiCumulativeConfig with sensible defaults
     */
    public static SemiCumulativeConfig defaults() {
        return new SemiCumulativeConfig(
                false, // useAbsMatch disabled by default
                0.001, // epsilon convergence threshold
                false, // jeffreysPrior disabled by default
                2, // joinTarget default value
                1.0 // glaThreshold default value
        );
    }

    /**
     * Creates a SemiCumulativeConfig with all parameters specified.
     * <p>
     * Use this when constructing from parsed command line arguments.
     * </p>
     */
    public SemiCumulativeConfig(boolean useAbsMatch, double epsilon, boolean jeffreysPrior, int joinTarget, double glaThreshold) {
        this.useAbsMatch = useAbsMatch;
        this.epsilon = epsilon;
        this.jeffreysPrior = jeffreysPrior;
        this.joinTarget = joinTarget;
        this.glaThreshold = glaThreshold;
    }
}
