/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.partition;

import java.util.List;

import org.jspecify.annotations.NullMarked;

/**
 * Immutable presentation data for a whole partition rendered by the
 * {@link Partition} component.
 * <p>
 * Carries the global header metrics (Stochastic Complexity, cluster count,
 * vector count, and optional overall distortion) plus the ordered list of
 * {@link ClusterData}. Independent of {@code binclass-algorithms}; the
 * translation layer in {@code binclass-ui} maps an algorithm {@code Partition}
 * into this record.
 * </p>
 */
@NullMarked
public record PartitionModel(
        /** Stochastic Complexity for the whole partition (lower is better). */
        double stochasticComplexity,
        /** Number of clusters in the partition. */
        int clusterCount,
        /** Total number of vectors across all clusters. */
        int vectorCount,
        /**
         * Overall distortion (average Hamming distance to centroid), or
         * {@code NaN} when unavailable.
         */
        double overallDistortion,
        /** The ordered list of clusters in the partition. */
        List<ClusterData> clusters) {

    /**
     * Creates a partition model with zero overall distortion and no clusters.
     *
     * @param stochasticComplexity
     *            the Stochastic Complexity for the whole partition
     */
    public PartitionModel(double stochasticComplexity) {
        this(stochasticComplexity, 0, 0, Double.NaN, List.of());
        if (clusters == null) {
            throw new IllegalArgumentException("clusters must not be null");
        }
    }

    /**
     * Returns a copy of the cluster list so callers cannot mutate internal
     * state.
     *
     * @return an unmodifiable copy of {@link #clusters()}
     */
    public List<ClusterData> clusters() {
        return List.copyOf(clusters);
    }
}
