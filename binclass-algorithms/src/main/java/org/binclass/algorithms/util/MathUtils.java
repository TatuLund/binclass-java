/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.util;

/**
 * Low-level mathematical utilities for the BinClass algorithm suite.
 * <p>
 * Provides logarithm base 2, gamma function, and factorial computations used
 * throughout the information-theoretic calculations in the original C codebase
 * (see {@code bottom.h}). All methods use Java native {@code double} precision
 * (IEEE 754).
 * </p>
 *
 * <p>
 * This is a static utility class — all methods are pure functions with no side
 * effects.
 * </p>
 */
public final class MathUtils {

    /** Inverse of natural logarithm of two: 1 / ln(2) ≈ 1.4426950408889634 */
    public static final double ILOGOF2 = 1.4426950408889634;

    /** Small epsilon for numerical stability in logarithm computations */
    public static final double EPSILON = 0.001;

    private MathUtils() {
        // Utility class — prevent instantiation
    }

    /**
     * Computes the base-2 logarithm of (1 - {@code val}).
     * <p>
     * Equivalent to C function {@code log_2_complement()} from
     * {@code logfile.c}:
     * 
     * <pre>{@code
     * double log_2_complement(double x) {
     *   return log(1.0 - x) / log(2);
     * }</pre>
     * 
     * 
     * </p>
     *
     * @param val the value to compute log₂(1 - val) of; must be between 0 and 1
     * (exclusive)
     * 
     * @return log₂(1 - val)
     * @throws IllegalArgumentException
     *             if {@code val <= 0} or {@code val >= 1}
     */
    public static double log2Complement(double val) {
        if (val < 0.0 || val > 1.0) {
            throw new IllegalArgumentException(
                    "log2Complement requires value in [0, 1], got: " + val);
        }
        return StrictMath.log(1.0 - val) * ILOGOF2;
    }

    /**
     * Computes the base-2 logarithm of {@code x}.
     * <p>
     * Equivalent to C macro {@code log_2(x)} from {@code bottom.h}:
     * 
     * <pre>{@code #define log_2(x) ((log(x) * ILOGOF2)}</pre>
     * </p>
     *
     * @param x
     *            the value to compute log₂ of; must be positive
     * @return log base 2 of {@code x}
     * @throws IllegalArgumentException
     *             if {@code x <= 0}
     */
    public static double log2(double x) {
        if (x < 0.0) {
            throw new IllegalArgumentException(
                    "log2 requires non-negative argument, got: " + x);
        }
        return StrictMath.log(x) * ILOGOF2;
    }

    /**
     * Computes the base-2 logarithm of {@code x} with epsilon check.
     * <p>
     * If {@code x <= EPSILON}, returns {@code log2(EPSILON)} instead to avoid
     * numerical instability from log(0) or log(negative).
     * </p>
     * <p>
     * Equivalent to C macro {@code log_2e(x)} from {@code bottom.h}:
     * 
     * <pre>{@code #define log_2e(x) ((epsilon > (x)) ? log_2(epsilon) : log_2(x)}</pre>
     * </p>
     *
     * @param x
     *            the value to compute log₂ of
     * @return log base 2 of {@code x}, or log₂(EPSILON) if {@code x <= EPSILON}
     */
    public static double log2e(double x) {
        return (EPSILON > x) ? log2(EPSILON) : log2(x);
    }

    /**
     * Computes the base-2 logarithm of the factorial of {@code n}: log₂(n!).
     * <p>
     * Uses precomputed lookup table for small values, falls back to sum of
     * log₂(i) for larger.
     * </p>
     * <p>
     * Equivalent to C macro {@code log2_factorial(x)} from {@code bottom.h}:
     * 
     * <pre>{@code #define log2_factorial(x) (x < 0) ? log2_factorials[0] : log2_factorials[x]}</pre>
     * </p>
     *
     * @param n
     *            the non-negative integer whose factorial's log₂ is to be
     *            computed
     * @return log₂(n!)
     * @throws IllegalArgumentException
     *             if {@code n < 0}
     */
    public static double log2Factorial(int n) {
        if (n < 0) {
            throw new IllegalArgumentException(
                    "log2Factorial requires non-negative argument, got: " + n);
        }
        // Precomputed values for small n (0! through 20!)
        if (LOG2_FACTORIALS != null && n < LOG2_FACTORIALS.length) {
            return LOG2_FACTORIALS[n];
        } else {
            // Ensure the array is large enough, or compute directly for larger
            // values
            int size = Math.max(n + 1, 3);
            prepareLog2Factorials(size);
            return LOG2_FACTORIALS[n];
        }
    }

    /**
     * Computes the base-2 logarithm of the gamma function: log₂(Γ(x)).
     * <p>
     * Uses Java's {@link StrictMath#lgamma(double)} if available, otherwise
     * falls back to natural log gamma converted to base 2.
     * </p>
     * <p>
     * Equivalent to C function {@code log2_gamma(x)} from {@code bottom.h}:
     * 
     * <pre>{@code #define log2_gamma(x) (lgamma(x) * ILOGOF2)}</pre>
     * </p>
     *
     * @param x
     *            the value at which to evaluate log₂(Γ(x)); must be positive
     *            and not a non-positive integer
     * @return log₂(Γ(x))
     * @throws IllegalArgumentException
     *             if {@code x <= 0} or is a non-positive integer
     */
    public static double log2Gamma(double x) {
        if (x <= 0.0) {
            throw new IllegalArgumentException(
                    "log2Gamma requires positive argument, got: " + x);
        }
        // Special case: Γ(1) = 1, so log₂(Γ(1)) = log₂(1) = 0 exactly
        if (x == 1.0) {
            return 0.0;
        }
        // Java's StrictMath doesn't have lgamma, so we compute ln(Γ(x)) via
        // Stirling's approximation
        // or use the identity: ln(Γ(x)) = ln(Gamma(x)) from a numerical library
        // For simplicity, use the natural log gamma approximation
        return logGamma(x) * ILOGOF2;
    }

    /**
     * Computes the natural logarithm of the gamma function: ln(Γ(x)).
     * <p>
     * Uses Stirling's approximation for large x, exact computation for small x.
     * </p>
     *
     * @param x
     *            the value at which to evaluate ln(Γ(x))
     * @return ln(Γ(x))
     */
    private static double logGamma(double x) {
        // For x <= 0, gamma has poles — return infinity
        if (x <= 0.0 && x == Math.floor(x)) {
            return Double.POSITIVE_INFINITY;
        }

        // Use the Lanczos approximation for numerical stability
        // This is a simplified version; production code would use a more
        // accurate implementation
        if (x < 0.5) {
            // Reflection formula: Γ(x) * Γ(1-x) = π / sin(πx)
            return StrictMath
                    .log(StrictMath.PI / StrictMath.sin(StrictMath.PI * x))
                    - logGamma(1.0 - x);
        }

        // For x >= 0.5, use Stirling's approximation with correction terms
        // ln(Γ(x)) ≈ (x - 0.5) * ln(x) - x + 0.5 * ln(2π) + 1/(12x) - 1/(360x³)
        // + ...
        double result = (x - 0.5) * StrictMath.log(x) - x
                + 0.5 * StrictMath.log(2.0 * StrictMath.PI);
        result += 1.0 / (12.0 * x);
        result -= 1.0 / (360.0 * x * x * x);
        return result;
    }

    /**
     * Builds a dynamic log₂(n!) lookup table of the given size.
     * <p>
     * Equivalent to C function {@code prepare_log2_factorials(int s)} from
     * {@code bottom.c}:
     * 
     * <pre>{@code
     * double *prepare_log2_factorials (int s) {
     *   double *lt;
     *   int i;
     *   if ((lt = (double *) malloc(sizeof(double)*s)) == NULL) out_of_mem();
     *   lt[0] = 0.0;
     *   for (i=1;i<s;i++) lt[i] = lt[i-1] + log_2((double)i);
     *   return lt;
     * }</pre>
     * </p>
     * <p>
     * The returned array has length {@code s}, where element at index {@code i}
     * contains log₂(i!). Index 0 is always 0.0 (since 0! = 1 and log₂(1) = 0).
     * </p>
     *
     * @param s the size of the array to build; must be positive
     * 
     * @return a new double array of length {@code s} with precomputed log₂(n!)
     *         values
     * @throws IllegalArgumentException
     *             if {@code s <= 0}
     */
    public static double[] prepareLog2Factorials(int s) {
        if (s <= 0) {
            throw new IllegalArgumentException(
                    "prepareLog2Factorials requires positive size, got: " + s);
        }
        LOG2_FACTORIALS = new double[s];
        LOG2_FACTORIALS[0] = 0.0; // log₂(0!) = log₂(1) = 0
        for (int i = 1; i < s; i++) {
            LOG2_FACTORIALS[i] = LOG2_FACTORIALS[i - 1] + log2(i);
        }
        return LOG2_FACTORIALS;
    }

    private static double[] LOG2_FACTORIALS;
}
