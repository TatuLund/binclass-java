/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components;

import java.util.Arrays;

import org.jspecify.annotations.NullMarked;

/**
 * Immutable presentation data for a single binary vector rendered as a grid of
 * cells.
 * <p>
 * Carries the bits, identifying metadata used by tooltips and inline names, the
 * rounded centroid values used to highlight mismatching cells on hover, and the
 * pre-computed Hamming distance to the cluster centroid. This is independent of
 * {@code binclass-algorithms}; the translation layer in {@code binclass-ui}
 * maps a {@link org.binclass.algorithms.core.BinaryVector} into this record.
 * </p>
 */
@NullMarked
public record VectorData(
        /** The bit values, one per cell ({@code 0} or {@code 1}). */
        int[] bits,
        /**
         * Human-readable name shown as a prefix (large grids) or in the
         * tooltip.
         */
        String name,
        /** Optional identifier shown alongside the name in tooltips. */
        @org.jspecify.annotations.Nullable String identifier,
        /** Hamming distance from this vector to its cluster centroid. */
        int distanceToCentroid,
        /**
         * The rounded centroid values ({@code 0}/{@code 1}) used for hover
         * highlight.
         */
        int[] roundedCentroid) {

    /**
     * Creates a vector record without centroid highlight data. Every cell is
     * treated as matching the (absent) centroid, so no cell highlights on
     * hover.
     *
     * @param bits
     *            the bit values
     * @param name
     *            the display name
     */
    public VectorData(int[] bits, String name) {
        this(bits, name, null, 0, new int[bits.length]);
        if (bits == null || roundedCentroid == null) {
            throw new IllegalArgumentException(
                    "bits and roundedCentroid must not be null");
        }
        if (bits.length != roundedCentroid.length) {
            throw new IllegalArgumentException(
                    "bits length (" + bits.length
                            + ") must equal roundedCentroid length ("
                            + roundedCentroid.length + ")");
        }
    }

    /**
     * Returns a copy of the bit array so callers cannot mutate internal state.
     *
     * @return a defensive copy of {@link #bits()}
     */
    public int[] bits() {
        return Arrays.copyOf(bits, bits.length);
    }

    /**
     * Returns a copy of the rounded centroid array used for highlight
     * comparison.
     *
     * @return a defensive copy of {@link #roundedCentroid()}
     */
    public int[] roundedCentroid() {
        return Arrays.copyOf(roundedCentroid, roundedCentroid.length);
    }

    /**
     * Returns whether the cell at the given index should highlight on hover
     * because its bit differs from the rounded centroid value.
     *
     * @param index
     *            the cell position
     * @return {@code true} when the bit differs from the rounded centroid (or
     *         no centroid is set)
     */
    public boolean mismatchAt(int index) {
        if (index < 0 || index >= bits.length) {
            return false;
        }
        int expected = roundedCentroid[index];
        return bits[index] != expected;
    }
}
