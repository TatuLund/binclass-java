/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.events;

import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Minimal immutable payload the UI binds to when a GLA-style search reports a
 * best partition or completes. It carries only what the presentation layer
 * needs — the optimal cluster count, its stochastic complexity, and the full
 * per-{@code k} SC curve so the graph tab can be rebuilt from completion alone.
 * <p>
 * Kept as plain data (not an {@link AbstractEvent}) because it rides inside the
 * search events rather than being posted on its own. Independent of
 * {@code binclass-algorithms}; the UI maps algorithm objects into this record.
 * </p>
 */
@NullMarked
public record PartitionResult(
        /**
         * Cluster count of the best partition found (the global SC minimum).
         */
        int kmin,
        /** Stochastic Complexity of the best partition (lower is better). */
        double scmin,
        /** Number of clusters in the best partition. */
        int clusterCount,
        /**
         * Per-{@code k} SC values from the search curve; index aligned with
         * {@link #ks()}.
         */
        double[] scs,
        /** Cluster counts paired with each value in {@link #scs()}. */
        int[] ks,
        /**
         * The best partition itself. Present when the event carries a full
         * snapshot (e.g. {@code SearchBestPartition}); may be {@code null} for
         * searches that report only scalar results.
         */
        Partition partition,
        /**
         * Centroids matching the best partition's cluster count. Present when
         * the search carried them (automatic and range searches); may be
         * {@code null} for events built from scalars alone, in which case the
         * UI renders an empty centroid per cluster so the Partition tab still
         * shows structure.
         */
        @Nullable InfiniteCentroids centroids) {

    /**
     * Backward-compatible constructor that leaves the best-partition centroids
     * unset as {@code null}. Used by callers that only carry a partition and
     * its scalar metadata, letting the UI fall back to empty centroids.
     *
     * @param kmin
     *            cluster count of the best partition found
     * @param scmin
     *            stochastic complexity of the best partition
     * @param clusterCount
     *            number of clusters in the best partition
     * @param scs
     *            per-{@code k} SC values, index aligned with {@code ks}
     * @param ks
     *            cluster counts paired with each value in {@code scs}
     * @param partition
     *            the best partition, or {@code null} for scalar-only events
     */
    public PartitionResult(int kmin, double scmin, int clusterCount,
            double[] scs, int[] ks, Partition partition) {
        this(kmin, scmin, clusterCount, scs, ks, partition, null);
    }
}
