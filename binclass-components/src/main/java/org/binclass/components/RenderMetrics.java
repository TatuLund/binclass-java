/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components;

import org.jspecify.annotations.NullMarked;

/**
 * Pure functions describing how a binary vector is rendered as a horizontal
 * strip of cells, one per bit.
 * <p>
 * The side length (cells per row) scales from {@code 2x2} up to {@code 12x12}.
 * Names are shown inline for grids larger than {@code 8x8}, otherwise they live
 * in a tooltip.
 * </p>
 */
@NullMarked
public final class RenderMetrics {

    /** Smallest allowed side length for a rendered grid. */
    public static final int MIN_SIDE = 2;

    /** Largest allowed side length for a rendered grid. */
    public static final int MAX_SIDE = 12;

    /**
     * Side length at which the vector name is shown inline instead of in a
     * tooltip.
     */
    public static final int NAME_INLINE_THRESHOLD = 9;

    private RenderMetrics() {
        // Utility class - prevent instantiation
    }

    /**
     * Computes the grid side length for a vector of the given length.
     * <p>
     * Equivalent to {@code clamp(ceil(sqrt(length)), MIN_SIDE, MAX_SIDE)}.
     * </p>
     *
     * @param length
     *            the number of cells (bits) in the vector, must be positive
     * @return the grid side length clamped between {@link #MIN_SIDE} and
     *         {@link #MAX_SIDE}
     */
    public static int sideLength(int length) {
        if (length <= 0) {
            throw new IllegalArgumentException(
                    "Vector length must be positive, got: " + length);
        }
        int side = (int) Math.ceil(Math.sqrt(length));
        return Math.max(MIN_SIDE, Math.min(MAX_SIDE, side));
    }

    /**
     * Returns whether the vector name should be shown inline for a vector of
     * the given length.
     * <p>
     * Names are shown inline when the grid is larger than {@code 8x8}, i.e. the
     * computed side length is at least {@link #NAME_INLINE_THRESHOLD}.
     * </p>
     *
     * @param length
     *            the number of cells (bits) in the vector, must be positive
     * @return {@code true} when the name should be rendered inline next to the
     *         grid
     */
    public static boolean showNameInline(int length) {
        return sideLength(length) >= NAME_INLINE_THRESHOLD;
    }
}
