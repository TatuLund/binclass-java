/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components;

import java.util.List;

import org.jspecify.annotations.NullMarked;

/**
 * Immutable presentation data for a single cluster: its centroid plus the
 * vectors assigned to it.
 * <p>
 * Carries the summary metadata (name, distortion, weight, vector count) shown
 * in the collapsible header and tooltip, the {@link CentroidData} rendered as
 * grey cells, and the list of {@link VectorData} rendered when the cluster is
 * expanded. Vectors may be empty until lazily loaded on open. Independent of
 * {@code binclass-algorithms}.
 * </p>
 */
@NullMarked
public record ClusterData(
        /**
         * Cluster name: most frequent strain label within the cluster, or a
         * fallback index.
         */
        String name,
        /** Class distortion (average Hamming distance to centroid). */
        double classDistortion,
        /** Weight of the cluster (frequency ratio or raw count). */
        double weight,
        /** Number of vectors assigned to the cluster. */
        int vectorCount,
        /** The probability centroid rendered as grey cells. */
        CentroidData centroid,
        /** Vectors assigned to this cluster; populated lazily on open. */
        List<VectorData> vectors) {

    /**
     * Creates a cluster record with an empty vector list.
     *
     * @param name
     *            the cluster label shown in the summary
     * @param classDistortion
     *            average Hamming distance to centroid
     * @param weight
     *            cluster weight (frequency ratio or raw count)
     * @param vectorCount
     *            number of vectors assigned to the cluster
     * @param centroid
     *            the probability centroid
     */
    public ClusterData(String name, double classDistortion, double weight,
            int vectorCount,
            CentroidData centroid) {
        this(name, classDistortion, weight, vectorCount, centroid, List.of());
        if (centroid == null) {
            throw new IllegalArgumentException("centroid must not be null");
        }
    }

    /**
     * Returns a copy of the cluster's vectors so callers cannot mutate internal
     * state.
     *
     * @return an unmodifiable copy of {@link #vectors()}
     */
    public List<VectorData> vectors() {
        return List.copyOf(vectors);
    }
}
