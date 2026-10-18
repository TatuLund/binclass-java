/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.partition;

import java.util.Arrays;

import org.jspecify.annotations.NullMarked;

/**
 * Immutable presentation data for a probability centroid rendered as
 * grey-shaded cells.
 * <p>
 * Each cell carries a probability in {@code [0, 1]} that maps to a shade
 * between white (0) and black (1). The record also carries the cluster-level
 * metadata shown in the tooltip: name, distortion, weight, and vector count.
 * Independent of {@code binclass-algorithms}.
 * </p>
 */
@NullMarked
public record CentroidData(
        /**
         * Probability per bit, each value in the closed interval
         * {@code [0, 1]}.
         */
        double[] probabilities,
        /** Cluster name: most frequent strain label within the cluster. */
        String name,
        /** Class distortion (average Hamming distance to centroid). */
        double classDistortion,
        /** Weight of the cluster (frequency ratio or raw count). */
        double weight,
        /** Number of vectors assigned to the cluster. */
        int vectorCount) {

    /**
     * Creates a centroid record with default metadata values.
     *
     * @param probabilities
     *            the per-bit probabilities in {@code [0, 1]}
     */
    public CentroidData(double[] probabilities) {
        this(probabilities, "", Double.NaN, Double.NaN, 0);
        if (probabilities == null) {
            throw new IllegalArgumentException(
                    "probabilities must not be null");
        }
    }

    /**
     * Returns a copy of the probability array so callers cannot mutate internal
     * state.
     *
     * @return a defensive copy of {@link #probabilities()}
     */
    public double[] probabilities() {
        return Arrays.copyOf(probabilities, probabilities.length);
    }

    /**
     * Clamps a probability into the closed interval {@code [0, 1]}.
     *
     * @param value
     *            an unbounded numeric value
     * @return the value clamped to {@code 0} or {@code 1} at the extremes
     */
    public static double clampProbability(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * Returns a copy of the probability array with each value clamped into
     * {@code [0, 1]}.
     *
     * @return a defensive copy of {@link #probabilities()} with values clamped
     */
    public double[] clampedProbabilities() {
        double[] result = Arrays.copyOf(probabilities, probabilities.length);
        for (int i = 0; i < result.length; i++) {
            result[i] = clampProbability(result[i]);
        }
        return result;
    }
}
