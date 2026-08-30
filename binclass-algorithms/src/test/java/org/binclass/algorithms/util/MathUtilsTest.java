/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MathUtils}.
 */
class MathUtilsTest {

    @BeforeEach
    void resetLog2Factorials() throws Exception {
        // Reset LOG2_FACTORIALS to ensure clean state (static field)
        java.lang.reflect.Field field = MathUtils.class
                .getDeclaredField("LOG2_FACTORIALS");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test
    void testLog2() {
        assertEquals(3.0, MathUtils.log2(8), 1e-10);
        assertEquals(0.0, MathUtils.log2(1), 1e-10);
        assertEquals(1.0, MathUtils.log2(2), 1e-10);
    }

    @Test
    void testLog2Negative() {
        assertThrows(IllegalArgumentException.class, () -> MathUtils.log2(-1));
    }

    @Test
    void testLog2e() {
        assertEquals(MathUtils.log2(MathUtils.EPSILON), MathUtils.log2e(0.0001),
                1e-10);
        assertEquals(MathUtils.log2(8), MathUtils.log2e(8), 1e-10);
    }

    @Test
    void testLog2Factorial() {
        assertEquals(0.0, MathUtils.log2Factorial(0), 1e-10);
        assertEquals(0.0, MathUtils.log2Factorial(1), 1e-10);
        assertEquals(1.0, MathUtils.log2Factorial(2), 1e-10);
        assertEquals(MathUtils.log2(6), MathUtils.log2Factorial(3), 1e-10);
    }

    @Test
    void testLog2FactorialNegative() {
        // Negative arguments clamp to index 0 like the C macro
        // log2_factorial(x):
        // (x < 0) ? log2_factorials[0] : ..., which is log₂(0!) = 0.
        assertEquals(0.0, MathUtils.log2Factorial(-1), 1e-10);
        assertEquals(0.0, MathUtils.log2Factorial(-5), 1e-10);
    }

    @Test
    void testLog2Gamma() {
        assertEquals(MathUtils.log2(24), MathUtils.log2Gamma(5), 1e-6);
        assertEquals(MathUtils.log2(1), MathUtils.log2Gamma(1), 1e-10);
    }

    @Test
    void testLog2GammaNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> MathUtils.log2Gamma(-1));
        assertThrows(IllegalArgumentException.class,
                () -> MathUtils.log2Gamma(0));
    }

    @Test
    void testPrepareLog2Factorials() throws Exception {
        // Reset LOG2_FACTORIALS to ensure clean state (static field)
        java.lang.reflect.Field field = MathUtils.class
                .getDeclaredField("LOG2_FACTORIALS");
        field.setAccessible(true);
        field.set(null, null);

        double[] result = MathUtils.prepareLog2Factorials(
                LOG2_FACTORIALS_REFERENCE.length);
        assertEquals(LOG2_FACTORIALS_REFERENCE.length, result.length,
                "Array size should match requested size");
        for (int i = 0; i < LOG2_FACTORIALS_REFERENCE.length; i++) {
            assertEquals(LOG2_FACTORIALS_REFERENCE[i], result[i], 1e-10,
                    "Mismatch at index " + i);
        }
    }

    @Test
    void testPrepareLog2FactorialsSmall() throws Exception {
        // Reset LOG2_FACTORIALS to ensure clean state (static field)
        java.lang.reflect.Field field = MathUtils.class
                .getDeclaredField("LOG2_FACTORIALS");
        field.setAccessible(true);
        field.set(null, null);

        double[] result = MathUtils.prepareLog2Factorials(3);
        assertEquals(3, result.length);
        assertEquals(0.0, result[0], 1e-10); // log₂(0!) = 0
        assertEquals(0.0, result[1], 1e-10); // log₂(1!) = 0
        assertEquals(1.0, result[2], 1e-10); // log₂(2!) = 1
    }

    @Test
    void testPrepareLog2FactorialsNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> MathUtils.prepareLog2Factorials(-1));
    }

    @Test
    void testPrepareLog2FactorialsIdempotent() {
        // Verify that calling prepareLog2Factorials multiple times produces
        // consistent results regardless of previous calls (static field reset)
        double[] small = MathUtils.prepareLog2Factorials(3);
        assertEquals(3, small.length);

        double[] large = MathUtils.prepareLog2Factorials(5);
        assertEquals(5, large.length);
        assertEquals(0.0, large[0], 1e-10); // log₂(0!) = 0
        assertEquals(0.0, large[1], 1e-10); // log₂(1!) = 0
        assertEquals(1.0, large[2], 1e-10); // log₂(2!) = 1
        assertEquals(MathUtils.log2(6), large[3], 1e-10); // log₂(3!) ≈ 2.585
        assertEquals(MathUtils.log2(24), large[4], 1e-10); // log₂(4!) ≈ 4.922

        // Verify small array is still valid after re-creating larger one
        double[] smallAgain = MathUtils.prepareLog2Factorials(3);
        assertEquals(3, smallAgain.length);
        assertEquals(0.0, smallAgain[0], 1e-10);
        assertEquals(0.0, smallAgain[1], 1e-10);
        assertEquals(1.0, smallAgain[2], 1e-10);
    }

    /**
     * Precomputed log₂(n!) values for n = 0 to 20.
     * <p>
     * Computed as: sum of log₂(i) for i = 1 to n.
     * </p>
     */
    private static final double[] LOG2_FACTORIALS_REFERENCE = {
        // @formatter:off
            0.0,                          // 0! = 1, log₂(1) = 0
            0.0,                          // 1! = 1, log₂(1) = 0
            1.0,                          // 2! = 2, log₂(2) = 1
            2.5849625007211561,           // 3! = 6, log₂(6) ≈ 2.585
            4.5849625007211561,           // 4! = 24, log₂(24) ≈ 4.585
            6.9068905956085178,           // 5! = 120, log₂(120) ≈ 6.907
            9.4918530963296739,           // 6! = 720, log₂(720) ≈ 9.492
            12.2992080183872776,          // 7! = 5040, log₂(5040) ≈ 12.299
            15.2992080183872776,          // 8! = 40320, log₂(40320) ≈ 15.299
            18.4691330198295915,          // 9! = 362880, log₂(362880) ≈ 18.469
            21.7910611147169533,          // 10! = 3628800, log₂(3628800) ≈ 21.791
            25.2504927333542497,          // 11! = 39916800, log₂(39916800) ≈ 25.250
            28.8354552340754040,          // 12! = 479001600, log₂(479001600) ≈ 28.835
            32.5358949522164949,          // 13! = 6227020800, log₂(6227020800) ≈ 32.536
            36.3432498742741004,          // 14! = 87178291200, log₂(87178291200) ≈ 36.343
            40.2501404698826164,          // 15! = 1307674368000, log₂(1307674368000) ≈ 40.250
            44.2501404698826164,          // 16! = 20922789888000, log₂(20922789888000) ≈ 44.250
            48.3376033111329519,          // 17! = 355687428096000, log₂(355687428096000) ≈ 48.338
            52.5075283125752605,          // 18! = 6402373705728000, log₂(6402373705728000) ≈ 52.508
            56.7554558260188458,          // 19! = 121645100408832000, log₂(121645100408832000) ≈ 56.755
            61.0773839209062075           // 20! = 2432902008176640000, log₂(2432902008176640000) ≈ 61.077
        // @formatter:on
    };

}