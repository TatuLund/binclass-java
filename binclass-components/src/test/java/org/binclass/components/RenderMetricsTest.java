/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RenderMetrics}.
 */
class RenderMetricsTest {

    @Test
    void sideLengthClampsToMinimum() {
        assertEquals(2, RenderMetrics.sideLength(1));
        assertEquals(2, RenderMetrics.sideLength(4));
    }

    @Test
    void sideLengthScalesWithVectorLength() {
        assertEquals(3, RenderMetrics.sideLength(9));
        assertEquals(4, RenderMetrics.sideLength(10));
        assertEquals(4, RenderMetrics.sideLength(16));
        assertEquals(5, RenderMetrics.sideLength(25));
    }

    @Test
    void sideLengthClampsToMaximum() {
        assertEquals(12, RenderMetrics.sideLength(144));
        assertEquals(12, RenderMetrics.sideLength(200));
        assertEquals(12, RenderMetrics.sideLength(10_000));
    }

    @Test
    void sideLengthThrowsForNonPositive() {
        assertThrows(IllegalArgumentException.class,
                () -> RenderMetrics.sideLength(0));
        assertThrows(IllegalArgumentException.class,
                () -> RenderMetrics.sideLength(-5));
    }

    @Test
    void showNameInlineSwitchesAtNine() {
        assertFalse(RenderMetrics.showNameInline(64),
                "8x8 grid should not show name inline");
        assertTrue(RenderMetrics.showNameInline(81),
                "9x9 grid should show name inline");
    }
}
