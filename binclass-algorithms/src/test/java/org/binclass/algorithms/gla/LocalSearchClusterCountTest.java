/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.gla;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.dist.DistanceCalculator;
import org.binclass.algorithms.dist.NearestNeighbor;
import org.binclass.algorithms.eventbus.EventBus;
import org.binclass.algorithms.util.MathUtils;
import org.junit.jupiter.api.Test;

/**
 * Instruments the per-k cluster count for cycler/adaptive to confirm that
 * localSearch() merges clusters, so SC computed for {@code actualClusters} is
 * stored at index {@code k}.
 */
class LocalSearchClusterCountTest {

    private static final int L = 16;

    private VectorSet buildClusteredVectors(int perCluster) {
        int[][] centers = {
                { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
                { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
                { 1, 1, 1, 1, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0 },
                { 0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 1, 1, 1, 1 },
        };
        VectorSet set = new VectorSet(perCluster * centers.length);
        for (int c = 0; c < centers.length; c++) {
            for (int i = 0; i < perCluster; i++) {
                int[] el = centers[c].clone();
                if ((i + c) % 5 == 0) {
                    el[(i + c) % L] ^= 1;
                }
                set.addElement(new BinaryVector(el, 0, L, 0,
                        "v" + (c * perCluster + i)));
            }
        }
        return set;
    }

    private int countNonEmpty(Partition p) {
        int c = 0;
        for (int i = 1; i <= p.size(); i++) {
            if (p.getClusterSize(i) > 0) {
                c++;
            }
        }
        return c;
    }

    @Test
    void instrumentClusterCounts() {
        VectorSet set = buildClusteredVectors(5); // 20 vectors, 4 true clusters
        GLAConfig cfg = new GLAConfig(
                MathUtils.EPSILON, 1.8, 7, 1, 4, 0, 1000, 1, 0,
                20, 5, false, false, false, false, false, false, 0.0,
                false, 1, 3, false, false, false, false, false, true, false);

        for (int k = 4; k <= 9; k++) {
            InfiniteCentroids centroids = new InfiniteCentroids(k, L);
            Partition partition = new Partition(k + 1);
            NearestNeighbor.mseNearestNeighbor(set, partition, centroids);
            GLAEngine.removeEmpty(partition, centroids);
            GLAEngine.recomputeCentroids(partition, centroids, false,
                    set.size());
            int before = countNonEmpty(partition);
            LocalSearch.localSearch(partition, centroids, cfg.heuristicCount(),
                    L, set.size(), false, new java.util.Random(1), "test", 7,
                    false);
            int after = countNonEmpty(partition);
            double sc = DistanceCalculator.stochasticComplexity(
                    partition, after + 1, L, false);
            System.out.println("k=" + k + " nonEmpty beforeLS=" + before
                    + " afterLS=" + after + " actualClustersScored=" + after
                    + " SC=" + String.format("%.3f", sc));
        }
    }
}
