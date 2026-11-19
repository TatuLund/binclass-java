/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.localsearch;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.components.graph.GraphModel;
import org.binclass.components.partition.PartitionModel;
import org.binclass.ui.classify.gla.GlaViewTranslator;

/**
 * Translation layer for the Local Search display. Delegates to
 * {@link GlaViewTranslator} because local search reuses the exact same
 * algorithm-to-presentation mappings — a best partition plus its centroids into
 * a {@link PartitionModel}, and the per-{@code k} SC curve into a
 * {@link GraphModel}. Kept as its own class so the two displays can diverge if
 * their presentation needs differ, while sharing the tested implementation.
 */
@NullMarked
public final class LocalSearchViewTranslator {

    private LocalSearchViewTranslator() {
        // Utility class — prevent instantiation.
    }

    /**
     * Builds a {@link PartitionModel} from an algorithm partition and its
     * centroids, delegating to {@link GlaViewTranslator}.
     *
     * @param partition
     *            the best partition found (must not be {@code null})
     * @param centroids
     *            the matching centroid array; may be {@code null} when only a
     *            partition is available
     * @param stochasticComplexity
     *            the SC value for the whole partition (header metric)
     * @return a {@link PartitionModel} ready to bind to the Partition component
     */
    public static PartitionModel toPartitionModel(Partition partition,
            @Nullable InfiniteCentroids centroids,
            double stochasticComplexity) {
        return GlaViewTranslator.toPartitionModel(partition, centroids,
                stochasticComplexity);
    }

    /**
     * Builds a {@link GraphModel} from the per-{@code k} SC curve, delegating
     * to {@link GlaViewTranslator}. Drops points whose SC is non-finite so the
     * chart never renders {@code NaN}/{@code Inf}.
     *
     * @param scs
     *            the per-{@code k} SC values (must not be {@code null})
     * @param ks
     *            optional cluster counts paired with each SC value; when
     *            {@code null} or shorter than {@code scs}, the array position
     *            is used as the x coordinate
     * @return a non-empty {@link GraphModel}, or an empty model list wrapped so
     *         callers can always bind it
     */
    public static GraphModel toGraphModel(double[] scs, int[] ks) {
        return GlaViewTranslator.toGraphModel(scs, ks);
    }
}
