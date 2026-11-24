/*
* Copyright (c) 2024 BinClass Contributors
* SPDX-License-Identifier: Apache-2.0
*/
package org.binclass.components.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link AffinityMatrix} helpers that are extracted so they
 * can be exercised directly: {@code matrixRange} (the observed distance span)
 * and {@code colorFor} (the interpolated background color). Both live on
 * {@link AffinityMatrix} behind package-private / protected access so this test
 * in the same package can call them.
 */
class AffinityMatrixTest {

    /** Parses an {@code #rrggbb} string into its three channel values. */
    private static int[] parseColor(String color) {
        assertEquals(7, color.length(), "expected an #rrggbb hex color");
        return new int[] {
                Integer.parseInt(color.substring(1, 3), 16),
                Integer.parseInt(color.substring(3, 5), 16),
                Integer.parseInt(color.substring(5, 7), 16),
        };
    }

    /** Asserts every channel of {@code color} lies within the valid range. */
    private static void assertValidColor(String color) {
        for (int channel : parseColor(color)) {
            assertTrue(channel >= 0 && channel <= 255,
                    "channel out of range: " + color);
        }
    }

    @Test
    void matrixRangeFindsMinAndMaxAcrossDataRegion() {
        double[][] matrix = new double[3][3];
        // 1-based data region; diagonal holds its default self-distance.
        matrix[1][1] = 5;
        matrix[2][1] = 9;
        matrix[1][2] = 9;
        matrix[2][2] = 2;

        AffinityMatrix.MatrixRange range = AffinityMatrix.matrixRange(matrix,
                2);

        assertEquals(2.0, range.min());
        assertEquals(9.0, range.max());
    }

    @Test
    void matrixRangeHandlesSingleCluster() {
        double[][] matrix = new double[2][2];
        matrix[1][1] = 7;

        AffinityMatrix.MatrixRange range = AffinityMatrix.matrixRange(matrix,
                1);

        assertEquals(7.0, range.min());
        assertEquals(7.0, range.max());
    }

    @Test
    void matrixRangeExcludesZeroDistances() {
        double[][] matrix = new double[3][3];
        // Diagonal holds zeros; off-diagonals are the real separations.
        matrix[1][2] = 6;
        matrix[2][1] = 6;

        AffinityMatrix.MatrixRange range = AffinityMatrix.matrixRange(matrix,
                2);

        assertEquals(6.0, range.min());
        assertEquals(6.0, range.max());
    }

    @Test
    void matrixRangeExcludesExplicitZeroCells() {
        double[][] matrix = new double[3][3];
        // A non-diagonal cell may also be exactly zero; it must be skipped.
        matrix[1][2] = 6;
        matrix[2][1] = 6;
        matrix[2][2] = 0.0;

        AffinityMatrix.MatrixRange range = AffinityMatrix.matrixRange(matrix,
                2);

        assertEquals(6.0, range.min());
        assertEquals(6.0, range.max());
    }

    @Test
    void matrixRangeHandlesNegativeAffinityValues() {
        double[][] matrix = new double[3][3];
        // Affinity transform can produce negative similarities.
        matrix[1][2] = -3;
        matrix[2][1] = -3;
        matrix[2][2] = 4;

        AffinityMatrix.MatrixRange range = AffinityMatrix.matrixRange(matrix,
                2);

        assertEquals(-3.0, range.min());
        assertEquals(4.0, range.max());
    }

    @Test
    void colorForMapsMinToGreen() {
        String color = AffinityMatrix.colorFor(2.0, 2.0, 9.0);
        assertValidColor(color);
        int[] rgb = parseColor(color);
        // Green anchor is #a0f0a0.
        assertEquals(0xa0, rgb[0]);
        assertEquals(0xf0, rgb[1]);
        assertEquals(0xa0, rgb[2]);
    }

    @Test
    void colorForMapsMaxToRed() {
        String color = AffinityMatrix.colorFor(9.0, 2.0, 9.0);
        assertValidColor(color);
        int[] rgb = parseColor(color);
        // Red anchor is #f0a0a0.
        assertEquals(0xf0, rgb[0]);
        assertEquals(0xa0, rgb[1]);
        assertEquals(0xa0, rgb[2]);
    }

    @Test
    void colorForMapsMidpointToOrange() {
        String color = AffinityMatrix.colorFor(5.5, 2.0, 9.0);
        assertValidColor(color);
        int[] rgb = parseColor(color);
        // Orange anchor is #f0f0a0.
        assertEquals(0xf0, rgb[0]);
        assertEquals(0xf0, rgb[1]);
        assertEquals(0xa0, rgb[2]);
    }

    @Test
    void colorForInterpolatesChannelsMonotonically() {
        double min = 2.0;
        double max = 9.0;
        int[] green = parseColor(AffinityMatrix.colorFor(min, min, max));
        int[] orange = parseColor(
                AffinityMatrix.colorFor((min + max) / 2, min, max));
        int[] red = parseColor(AffinityMatrix.colorFor(max, min, max));

        // Red channel rises from green to red.
        assertTrue(red[0] >= orange[0] && orange[0] >= green[0],
                "red channel should increase toward the red anchor");
        // Blue channel falls from green to red.
        assertTrue(green[2] >= orange[2] && orange[2] >= red[2],
                "blue channel should decrease toward the red anchor");
    }

    @Test
    void colorForClampsOutOfRangeValues() {
        double min = 2.0;
        double max = 9.0;
        // Values below the minimum or above it still yield valid hex colors.
        assertValidColor(AffinityMatrix.colorFor(min - 10, min, max));
        assertValidColor(AffinityMatrix.colorFor(max + 10, min, max));
    }

    @Test
    void colorForUsesMidpointWhenRangeCollapses() {
        String color = AffinityMatrix.colorFor(4.0, 4.0, 4.0);
        int[] rgb = parseColor(color);
        // A collapsed range maps every value to the midpoint (orange) anchor.
        assertEquals(0xf0, rgb[0]);
        assertEquals(0xf0, rgb[1]);
        assertEquals(0xa0, rgb[2]);
    }

    @Test
    void colorForProducesLowercaseHex() {
        String color = AffinityMatrix.colorFor(5.0, 0.0, 10.0);
        assertTrue(color.equals(color.toLowerCase(Locale.ROOT)),
                "hex color should be lowercase: " + color);
    }
}
