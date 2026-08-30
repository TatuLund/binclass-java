/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.gla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.util.MathUtils;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AutomaticSearch}, the automatic SC-minimizer that
 * mirrors the C {@code sca_main()} / {@code sca_scanner()} /
 * {@code sca_pingpong()} flow.
 * <p>
 * The tests exercise both phases on small synthetic data:
 * <ul>
 * <li><b>Forward scan termination</b> — the scan stops after {@code kstopwhen}
 * consecutive non-improving steps and stays within the scanned range.</li>
 * <li><b>Ping-pong convergence</b> — the enhancement loop terminates, keeps a
 * finite best SC that is no worse than the trivial one-cluster solution, and
 * returns a partition consistent with the reported cluster count.</li>
 * </ul>
 */
class AutomaticSearchTest {

    private static final int L = 16;

    /** Builds a VectorSet of four well-separated clusters. */
    private VectorSet buildClusteredVectors(int perCluster) {
        // Four well-separated cluster centers of length {@code L}.
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
                // Light perturbation to avoid exact ties while preserving the
                // cluster structure so GLA converges cleanly.
                if ((i + c) % 5 == 0) {
                    el[(i + c) % L] ^= 1;
                }
                set.addElement(new BinaryVector(el, 0, L, 0,
                        "v" + (c * perCluster + i)));
            }
        }
        return set;
    }

    /** Builds a default SC-scoring config with the given scan limits. */
    private GLAConfig config(int kstopwhen, int safetyLimit) {
        return config(kstopwhen, safetyLimit, 0);
    }

    /**
     * Builds an SC-scoring config with the given scan limits and attempt count
     * ({@code -a}). An {@code iterBase} of 0 keeps a single attempt per cluster
     * count so existing tests exercise default behaviour.
     */
    private GLAConfig config(int kstopwhen, int safetyLimit, int iterBase) {
        return new GLAConfig(
                MathUtils.EPSILON, // epsilon
                1.8, // pnnThreshold
                1, // heuristic (standard GLA)
                1, // alternateMode
                4, // centroidType (seed with input vectors)
                0, // maxIter
                safetyLimit, // safetyLimit
                iterBase, // iterBase (-a flag: attempts per k)
                0, // n (unused by AutomaticSearch)
                kstopwhen, // kstopwhen (-S flag)
                5, // kcStopWhen (-W flag)
                false, // weights
                false, // rounded
                false, // jeffreysPrior
                false, // trashcan
                false, // analyseMissing
                false, // logCentroids
                0.0, // firstD
                false, // bestCodeLength (false => stochastic complexity)
                1, // distanceType
                1, // heuristicCount
                false, // filterExactK
                false, // requireBetter
                false, // lsCycler
                false // lsAdaptive
        );
    }

    @Test
    void testRunReturnsValidClustering() {
        VectorSet set = buildClusteredVectors(5); // 20 vectors of length 16
        AutomaticSearch.Result result = new AutomaticSearch(set,
                config(3, 1000))
                .run();

        assertNotNull(result.partition(), "partition must not be null");
        assertNotNull(result.centroids(), "centroids must not be null");
        assertTrue(Double.isFinite(result.scmin()), "scmin must be finite");
        assertTrue(result.scmin() < Double.MAX_VALUE, "scmin must be computed");

        int s = set.size();
        int bound = Math.min(s, AutomaticSearch.MAXIMUM_CLASS_NUMBER);
        assertTrue(result.kmin() >= 1 && result.kmin() <= bound,
                "kmin must lie within the scanned range");
        assertTrue(result.lastk() >= 1 && result.lastk() <= bound,
                "lastk must lie within the scanned range");

        // scs[] covers every reachable cluster count and k=1 is always scanned.
        assertEquals(bound + 1, result.scs().length);
        assertFalse(Double.isNaN(result.scs()[1]), "scs[1] must be computed");
        assertTrue(result.scs()[1] < Double.MAX_VALUE, "scs[1] must be finite");
    }

    @Test
    void testForwardScanTerminatesWithSmallKstopWhen() {
        VectorSet set = buildClusteredVectors(8); // 32 vectors
        GLAConfig cfg = config(2, 1000); // small kstopwhen forces early stop

        AutomaticSearch.Result result = new AutomaticSearch(set, cfg).run();

        assertNotNull(result.partition());
        int s = set.size();
        int bound = Math.min(s, AutomaticSearch.MAXIMUM_CLASS_NUMBER);
        assertTrue(result.kmin() >= 1 && result.kmin() <= bound,
                "kmin must lie within the scanned range");
        assertTrue(result.lastk() >= 1 && result.lastk() <= bound,
                "forward scan lastk must be bounded by the scanned range");
        // The best SC found is no worse than the trivial one-cluster solution.
        assertTrue(result.scmin() <= result.scs()[1] + MathUtils.EPSILON,
                "best SC must not exceed the k=1 stochastic complexity");
    }

    @Test
    void testPingPongConverges() {
        VectorSet set = buildClusteredVectors(6); // 24 vectors
        GLAConfig cfg = config(5, 1000);

        AutomaticSearch.Result result = new AutomaticSearch(set, cfg).run();

        assertNotNull(result.partition());
        assertTrue(Double.isFinite(result.scmin()), "scmin must be finite");

        int s = set.size();
        int bound = Math.min(s, AutomaticSearch.MAXIMUM_CLASS_NUMBER);
        assertTrue(result.kmin() >= 1 && result.kmin() <= bound,
                "kmin must lie within the scanned range after ping-pong");

        // The reported best SC equals the best recorded at kmin and is no worse
        // than any computed per-k value.
        double scsMin = Double.MAX_VALUE;
        for (double sc : result.scs()) {
            if (sc < scsMin && sc < Double.MAX_VALUE) {
                scsMin = sc;
            }
        }
        assertTrue(result.scmin() <= scsMin + MathUtils.EPSILON,
                "best SC must match the minimum of the recorded per-k values");
    }

    @Test
    void testMultipleRunsTerminateWithSmallSafetyLimit() {
        VectorSet set = buildClusteredVectors(5); // 20 vectors
        // A small safety limit bounds the ping-pong passes; every run must
        // still
        // terminate and return a valid result.
        for (int i = 0; i < 5; i++) {
            AutomaticSearch.Result result = new AutomaticSearch(set,
                    config(3, 20)).run();
            assertNotNull(result.partition(),
                    "run " + i + " must return a partition");
            assertTrue(Double.isFinite(result.scmin()),
                    "run " + i + " scmin must be finite");
        }
    }

    @Test
    void testRunWithBestCodeLength() {
        VectorSet set = buildClusteredVectors(5);
        GLAConfig cfg = config(3, 1000);
        // Rebuild with bestCodeLength enabled to exercise the codelength
        // branch.
        GLAConfig ccl = new GLAConfig(
                MathUtils.EPSILON, 1.8, 1, 1, 4, 0, 1000, 0, 0, 3, 5, false,
                false,
                false, false, false, false, 0.0, true, 1, 1, false, false,
                false,
                false);

        AutomaticSearch.Result result = new AutomaticSearch(set, ccl).run();
        assertNotNull(result.partition());
        assertTrue(Double.isFinite(result.scmin()), "scmin must be finite");
    }

    /**
     * Verifies that {@code -a N} (iterBase) makes the search apply GLA up to N
     * times per cluster count with different starting centroids, keeping the
     * best SC. The result stays valid and terminates even when several attempts
     * run.
     */
    @Test
    void testRunWithMultipleAttemptsPerK() {
        VectorSet set = buildClusteredVectors(5); // 20 vectors of length 16
        GLAConfig cfg = config(3, 1000, 4); // four attempts per k

        AutomaticSearch.Result result = new AutomaticSearch(set, cfg).run();

        assertNotNull(result.partition(), "partition must not be null");
        assertNotNull(result.centroids(), "centroids must not be null");
        assertTrue(Double.isFinite(result.scmin()), "scmin must be finite");
        assertTrue(result.scmin() < Double.MAX_VALUE, "scmin must be computed");

        int s = set.size();
        int bound = Math.min(s, AutomaticSearch.MAXIMUM_CLASS_NUMBER);
        assertTrue(result.kmin() >= 1 && result.kmin() <= bound,
                "kmin must lie within the scanned range");

        // The best per-k SC recorded is no worse than any single-attempt run:
        // multiple attempts can only improve (or match) the reported minimum.
        double scsMin = Double.MAX_VALUE;
        for (double sc : result.scs()) {
            if (sc < scsMin && sc < Double.MAX_VALUE) {
                scsMin = sc;
            }
        }
        assertTrue(result.scmin() <= scsMin + MathUtils.EPSILON,
                "best SC must match the minimum of the recorded per-k values");

        // A single-attempt run on the same data should not beat the
        // multi-attempt
        // best (attempts can only lower or equal the minimum).
        AutomaticSearch.Result single = new AutomaticSearch(set,
                config(3, 1000))
                .run();
        assertTrue(result.scmin() <= single.scmin() + MathUtils.EPSILON,
                "multi-attempt search must not be worse than a single attempt");
    }
}
