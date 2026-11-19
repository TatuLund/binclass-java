/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.gla;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.util.MathUtils;
import org.junit.jupiter.api.Test;

/**
 * Reproduces the "wrong SC values in the graph for Cycler/Adaptive" issue by
 * comparing the per-k SC curve produced by a single heuristic against the
 * cycler (7) and adaptive (8) local-search heuristics on identical data.
 */
class LocalSearchSCCurveTest {

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

    private GLAConfig config(int heuristic) {
        return new GLAConfig(
                MathUtils.EPSILON, // epsilon
                1.8, // pnnThreshold
                heuristic, // heuristic
                1, // alternateMode
                4, // centroidType (seed with input vectors)
                0, // maxIter
                1000, // safetyLimit
                1, // iterBase
                0, // n
                20, // kstopwhen
                5, // kcStopWhen
                false, // weights
                false, // rounded
                false, // jeffreysPrior
                false, // trashcan
                false, // analyseMissing
                false, // logCentroids
                0.0, // firstD
                false, // bestCodeLength
                1, // distanceType
                3, // heuristicCount (-j): enough iterations to merge clusters
                false, // filterExactK
                false, // requireBetter
                false, // lsCycler
                false, // lsAdaptive
                false, // decreasingEpsilon
                true, // alternateWorstMatch
                false // alternateEmptyCellFix
        );
    }

    private void printCurve(String label, AutomaticSearch.Result result) {
        double[] scs = result.scs();
        StringBuilder sb = new StringBuilder(label + ": ");
        for (int k = 1; k < scs.length; k++) {
            double v = scs[k];
            sb.append(k).append("=");
            if (Double.isFinite(v)) {
                sb.append(String.format("%.3f", v));
            } else {
                sb.append("INF");
            }
            sb.append("  ");
        }
        System.out.println(sb.toString().trim());
    }

    @Test
    void compareCurves() {
        VectorSet set = buildClusteredVectors(5); // 20 vectors, 4 true clusters
        for (int h : new int[] { 1, 7, 8 }) {
            AutomaticSearch.Result r = new AutomaticSearch(set, config(h))
                    .run();
            System.out.println("heuristic " + h + " -> kmin=" + r.kmin()
                    + " scmin=" + String.format("%.3f", r.scmin()));
            printCurve("  curve", r);
        }
    }
}
