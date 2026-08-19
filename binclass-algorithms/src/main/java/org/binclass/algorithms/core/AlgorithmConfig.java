/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.core;

/**
 * Central configuration for algorithm parameters, particularly epsilon values
 * used across different algorithms for numerical stability and convergence
 * checking.
 * <p>
 * This class provides a single source of truth for all epsilon constants used
 * in the binclass-algorithms package, ensuring consistency across GLA variants,
 * classifiers, distance calculations, and information-theoretic functions.
 * </p>
 */
public final class AlgorithmConfig {

    /** Epsilon for convergence checking in GLA algorithms (1e-6) */
    public static final double CONVERGENCE_EPSILON = 1e-6;

    /** Epsilon for numerical stability in probability calculations (1e-10) */
    public static final double NUMERICAL_STABILITY_EPSILON = 1e-10;

    /** Epsilon for logarithmic computations to avoid log(0) (0.001) */
    public static final double LOG_EPSILON = 0.001;

    private AlgorithmConfig() {
        // Utility class — prevent instantiation
    }
}
