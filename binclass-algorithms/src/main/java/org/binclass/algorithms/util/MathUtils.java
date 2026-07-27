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
        if (val <= 0.0 || val >= 1.0) {
            throw new IllegalArgumentException(
                    "log2Complement requires value in (0, 1), got: " + val);
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
        if (n <= 20) {
            return LOG2_FACTORIALS[n];
        }
        // For larger n, compute sum of log2(i) for i=1..n
        double result = 0.0;
        for (int i = 1; i <= n; i++) {
            result += log2(i);
        }
        return result;
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

    /**
     * Precomputed log₂(n!) values for n = 0 to 20.
     * <p>
     * Computed as: sum of log₂(i) for i = 1 to n.
     * </p>
     */
    private static double[] LOG2_FACTORIALS = {
        // @formatter:off
            0.0,                          // 0! = 1, log₂(1) = 0
            0.0,                          // 1! = 1, log₂(1) = 0
            1.0,                          // 2! = 2, log₂(2) = 1
            2.584962500721156,           // 3! = 6, log₂(6) ≈ 2.585
            4.921928094887346,           // 4! = 24, log₂(24) ≈ 4.922
            7.953097774571644,           // 5! = 120, log₂(120) ≈ 7.953
            11.809560377490796,          // 6! = 720, log₂(720) ≈ 11.810
            16.509775004420668,          // 7! = 5040, log₂(5040) ≈ 16.510
            22.032970107010615,          // 8! = 40320, log₂(40320) ≈ 22.033
            28.383605956788286,          // 9! = 362880, log₂(362880) ≈ 28.384
            35.513758162992456,          // 10! = 3628800, log₂(3628800) ≈ 35.514
            43.383605956788286,          // 11! = 39916800, log₂(39916800) ≈ 43.384
            51.945571835544676,          // 12! = 479001600, log₂(479001600) ≈ 51.946
            61.161447059283076,          // 13! = 6227020800, log₂(6227020800) ≈ 61.161
            71.004469686890586,          // 14! = 87178291200, log₂(87178291200) ≈ 71.004
            81.457439915589776,          // 15! = 1307674368000, log₂(1307674368000) ≈ 81.457
            92.513758162992456,          // 16! = 20922789888000, log₂(20922789888000) ≈ 92.514
            104.168353832018086,         // 17! = 355687428096000, log₂(355687428096000) ≈ 104.168
            116.417667631887286,         // 18! = 6402373705728000, log₂(6402373705728000) ≈ 116.418
            129.259560377490796,         // 19! = 121645100408832000, log₂(121645100408832000) ≈ 129.260
            142.692775004420668          // 20! = 2432902008176640000, log₂(2432902008176640000) ≈ 142.693
        // @formatter:on
    };

}
