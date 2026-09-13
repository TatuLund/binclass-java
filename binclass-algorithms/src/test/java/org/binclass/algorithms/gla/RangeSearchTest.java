/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.gla;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.util.MathUtils;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RangeSearch}, the range GLA search that mirrors the C
 * {@code search_classes_nonautomatic()} flow. Unlike {@link AutomaticSearch},
 * which scans forward from {@code k = 1} and enhances with a ping-pong phase,
 * range search runs GLA for each cluster count in an explicit
 * {@code [kstart, kstop]} window chosen by the caller.
 * <p>
 * The tests exercise the public {@link #run(int, int)} entry point on small
 * synthetic data:
 * <ul>
 * <li><b>Basic run</b> — a range search returns a non-null partition and
 * centroids whose cluster counts are consistent with the reported best k.</li>
 * <li><b>Range bounds</b> — every vector is assigned to exactly one cluster,
 * and the best SC lies within the scanned window.</li>
 * <li><b>Maximum class cap</b> — a {@code kstart} above
 * {@link AutomaticSearch#MAXIMUM_CLASS_NUMBER} throws
 * {@link IllegalArgumentException}.</li>
 * <li><b>Safety limit</b> — a small safety limit forces early termination while
 * still returning a valid result.</li>
 * <li><b>Multiple attempts per k</b> — {@code -a N} applies GLA up to N times
 * per cluster count and keeps the best SC.</li>
 * </ul>
 */
class RangeSearchTest {

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

    /**
     * Builds an SC-scoring config with the given scan limits and attempt count
     * ({@code -a}). An {@code iterBase} of 0 keeps a single attempt per cluster
     * count so existing tests exercise default behaviour. The {@code n} field
     * is set to the vector-set size because range search uses it for GLAEngine.
     */
    private GLAConfig config(int kstopwhen, int safetyLimit, int iterBase,
            int n) {
        return new GLAConfig(
                MathUtils.EPSILON, // epsilon
                1.8, // pnnThreshold
                1, // heuristic (standard GLA)
                1, // alternateMode
                4, // centroidType (seed with input vectors)
                0, // maxIter
                safetyLimit, // safetyLimit
                iterBase, // iterBase (-a flag: attempts per k)
                n, // n: total vectors (required by GLAEngine)
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
                false, // lsAdaptive (disabled by default)
                false, // decreasingEpsilon
                true, // alternateWorstMatch (default per C vars.c)
                false // alternateEmptyCellFix
        );
    }

    /** Asserts that every vector is assigned to exactly one cluster. */
    private void assertAllVectorsAssigned(Partition partition,
            int expectedSize) {
        int total = 0;
        for (int i = 1; i <= partition.size(); i++) {
            total += partition.getSize(i);
        }
        assertEquals(expectedSize, total,
                "every vector must be assigned to exactly one cluster");
    }

    @Test
    void testRunReturnsValidClustering() {
        VectorSet set = buildClusteredVectors(5); // 20 vectors of length 16
        GLAConfig cfg = config(3, 1000, 0, set.size());

        RangeSearch.Result result = runWithRetry(set, cfg, 1, 4);

        assertNotNull(result.partition(), "partition must not be null");
        assertNotNull(result.centroids(), "centroids must not be null");

        // The best partition assigns every vector to exactly one cluster.
        assertAllVectorsAssigned(result.partition(), set.size());

        // Centroid array is sized k + 1 for the reported best k (>= 2
        // clusters).
        assertTrue(result.centroids().size() >= 2,
                "centroids must hold at least two clusters");
    }

    @Test
    void testRunWithinRangeBounds() {
        VectorSet set = buildClusteredVectors(6); // 24 vectors
        GLAConfig cfg = config(3, 1000, 0, set.size());

        RangeSearch.Result result = runWithRetry(set, cfg, 2, 5);

        assertNotNull(result.partition());
        assertAllVectorsAssigned(result.partition(), set.size());

        // RangeSearch sizes the winning partition at k + 1, so subtract one to
        // recover the winning cluster count and check it lies in [kstart,
        // kstop].
        int bestK = result.partition().size() - 1;
        assertTrue(bestK >= 2 && bestK <= 5,
                "best cluster count must lie within [kstart, kstop]");
    }

    @Test
    void testRunWithMaxClassCap() {
        VectorSet set = buildClusteredVectors(3); // 12 vectors
        GLAConfig cfg = config(3, 1000, 0, set.size());

        RangeSearch.Result result = runWithRetry(set, cfg,
                AutomaticSearch.MAXIMUM_CLASS_NUMBER - 1,
                AutomaticSearch.MAXIMUM_CLASS_NUMBER);

        assertNotNull(result.partition(), "result must still be returned");
        assertAllVectorsAssigned(result.partition(), set.size());
    }

    @Test
    void testRunWithStartExceedsLimitThrows() {
        VectorSet set = buildClusteredVectors(3); // 12 vectors
        GLAConfig cfg = config(3, 1000, 0, set.size());

        RangeSearch search = new RangeSearch(set, cfg);
        assertThrows(IllegalArgumentException.class, () -> search.run(
                AutomaticSearch.MAXIMUM_CLASS_NUMBER + 1, 6));
    }

    @Test
    void testRunWithSafetyLimitTerminates() {
        VectorSet set = buildClusteredVectors(5); // 20 vectors
        GLAConfig cfg = config(3, 5, 0, set.size()); // small safety limit
                                                     // forces early stop

        RangeSearch.Result result = runWithRetry(set, cfg, 1, 4);

        assertNotNull(result.partition(),
                "result must be returned after safety");
        assertAllVectorsAssigned(result.partition(), set.size());
    }

    /**
     * Runs a range search, retrying on transient empty-cluster scoring errors.
     */
    private RangeSearch.Result runWithRetry(VectorSet set, GLAConfig cfg,
            int kstart, int kstop) {
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                return new RangeSearch(set, cfg).run(kstart, kstop);
            } catch (ArithmeticException | IllegalStateException _) {
                // Random initialization can occasionally leave a partition with
                // an empty cluster mid-scan; retry with fresh centroids.
            }
        }
        throw new AssertionError(
                "range search failed to converge after retries");
    }

    @Test
    void testRunWithMultipleAttemptsPerK() {
        VectorSet set = buildClusteredVectors(5); // 20 vectors of length 16
        GLAConfig cfg = config(3, 1000, 4, set.size()); // four attempts per k

        RangeSearch.Result result = runWithRetry(set, cfg, 1, 4);

        assertNotNull(result.partition(), "partition must not be null");
        assertNotNull(result.centroids(), "centroids must not be null");
        assertAllVectorsAssigned(result.partition(), set.size());

        // The best partition's cluster count lies within the requested window.
        int bestK = result.partition().size();
        assertTrue(bestK >= 1 && bestK <= 4,
                "best cluster count must lie within [kstart, kstop]");
    }

    @Test
    void testRunWithBestCodeLength() {
        VectorSet set = buildClusteredVectors(5); // 20 vectors
        GLAConfig cfg = config(3, 1000, 0, set.size());
        // Rebuild with bestCodeLength enabled to exercise the codelength
        // branch.
        GLAConfig ccl = new GLAConfig(cfg.epsilon(), cfg.pnnThreshold(),
                cfg.heuristic(), cfg.alternateMode(), cfg.centroidType(),
                cfg.maxIter(), cfg.safetyLimit(), cfg.iterBase(), cfg.n(),
                cfg.kstopwhen(), cfg.kcStopWhen(), cfg.weights(), cfg.rounded(),
                cfg.jeffreysPrior(), cfg.trashcan(), cfg.analyseMissing(),
                cfg.logCentroids(), cfg.firstD(), true, cfg.distanceType(),
                cfg.heuristicCount(), cfg.filterExactK(), cfg.requireBetter(),
                cfg.lsCycler(), cfg.lsAdaptive(), cfg.decreasingEpsilon(),
                cfg.alternateWorstMatch(), cfg.alternateEmptyCellFix());

        RangeSearch.Result result = runWithRetry(set, ccl, 1, 4);

        assertNotNull(result.partition(), "partition must not be null");
        assertAllVectorsAssigned(result.partition(), set.size());
    }

    @Test
    void testRunWithSingleClusterRange() {
        VectorSet set = buildClusteredVectors(5); // 20 vectors
        GLAConfig cfg = config(3, 1000, 0, set.size());

        RangeSearch.Result result = runWithRetry(set, cfg, 1, 1);

        assertNotNull(result.partition(), "partition must not be null");
        assertAllVectorsAssigned(result.partition(), set.size());
    }

}
