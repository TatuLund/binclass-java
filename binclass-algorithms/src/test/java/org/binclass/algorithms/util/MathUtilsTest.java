/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MathUtils}.
 */
public class MathUtilsTest {

    @Test
    public void testLog2() {
        assertEquals(3.0, MathUtils.log2(8), 1e-10);
        assertEquals(0.0, MathUtils.log2(1), 1e-10);
        assertEquals(1.0, MathUtils.log2(2), 1e-10);
    }

    @Test
    public void testLog2Negative() {
        assertThrows(IllegalArgumentException.class, () -> MathUtils.log2(-1));
    }

    @Test
    public void testLog2e() {
        assertEquals(MathUtils.log2(MathUtils.EPSILON), MathUtils.log2e(0.0001),
                1e-10);
        assertEquals(MathUtils.log2(8), MathUtils.log2e(8), 1e-10);
    }

    @Test
    public void testLog2Factorial() {
        assertEquals(0.0, MathUtils.log2Factorial(0), 1e-10);
        assertEquals(0.0, MathUtils.log2Factorial(1), 1e-10);
        assertEquals(1.0, MathUtils.log2Factorial(2), 1e-10);
        assertEquals(MathUtils.log2(6), MathUtils.log2Factorial(3), 1e-10);
    }

    @Test
    public void testLog2FactorialNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> MathUtils.log2Factorial(-1));
    }

    @Test
    public void testLog2Gamma() {
        assertEquals(MathUtils.log2(24), MathUtils.log2Gamma(5), 1e-6);
        assertEquals(MathUtils.log2(1), MathUtils.log2Gamma(1), 1e-10);
    }

    @Test
    public void testLog2GammaNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> MathUtils.log2Gamma(-1));
        assertThrows(IllegalArgumentException.class,
                () -> MathUtils.log2Gamma(0));
    }
}