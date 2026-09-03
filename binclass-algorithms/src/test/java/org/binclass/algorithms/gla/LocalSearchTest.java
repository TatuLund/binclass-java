/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.gla;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.dist.DistanceCalculator;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LocalSearch} operators, helpers and the local search
 * driver.
 */
class LocalSearchTest {

    private static final int L = 4;

    /** Builds a partition with three clusters of distinct vectors. */
    private Partition buildThreeClusterPartition() {
        Partition p = new Partition(3);
        p.addElement(1, new BinaryVector(new int[] { 0, 0, 0, 0 }, L));
        p.addElement(1, new BinaryVector(new int[] { 0, 0, 0, 1 }, L));
        p.addElement(1, new BinaryVector(new int[] { 0, 0, 1, 0 }, L));

        p.addElement(2, new BinaryVector(new int[] { 1, 1, 1, 1 }, L));
        p.addElement(2, new BinaryVector(new int[] { 1, 1, 1, 0 }, L));
        p.addElement(2, new BinaryVector(new int[] { 1, 1, 0, 1 }, L));

        p.addElement(3, new BinaryVector(new int[] { 1, 0, 1, 0 }, L));
        p.addElement(3, new BinaryVector(new int[] { 0, 1, 0, 1 }, L));
        p.addElement(3, new BinaryVector(new int[] { 1, 1, 0, 0 }, L));
        return p;
    }

    private InfiniteCentroids buildThreeCentroids() {
        return new InfiniteCentroids(new double[][] {
                { 0.1, 0.1, 0.1, 0.1 },
                { 0.9, 0.9, 0.9, 0.9 },
                { 0.5, 0.5, 0.5, 0.5 },
        }, 3);
    }

    private int totalVectors(Partition p) {
        int tot = 0;
        for (int i = 1; i <= p.size(); i++) {
            tot += p.getSize(i);
        }
        return tot;
    }

    @Test
    void testRandomIndexBounds() {
        Random random = new Random(123);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < 10_000; i++) {
            int r = LocalSearch.randomIndex(10, random);
            assertTrue(r >= 1 && r <= 10, "index must be in [1,10], got " + r);
            min = Math.min(min, r);
            max = Math.max(max, r);
        }
        // (int)(nd*m) maxes at m-1 and the ind>=m clamp only fires when nd*m
        // rounds to exactly m, which never happens for nd in [0,1), so the
        // reachable range is [1, m-1].
        assertTrue(min >= 1 && min <= 9);
        assertTrue(max >= 1 && max <= 9);
    }

    @Test
    void testRandomIndexClampsForSmallM() {
        Random random = new Random(7);
        // m == 1 always returns 1 (ind = 0 clamped up)
        for (int i = 0; i < 100; i++) {
            assertEquals(1, LocalSearch.randomIndex(1, random));
        }
        // m <= 0 also returns 1
        assertEquals(1, LocalSearch.randomIndex(0, random));
        assertEquals(1, LocalSearch.randomIndex(-3, random));
    }

    @Test
    void testGetVectorAt() {
        VectorSet set = new VectorSet();
        BinaryVector a = new BinaryVector(new int[] { 0, 0 }, L);
        BinaryVector b = new BinaryVector(new int[] { 1, 1 }, L);
        BinaryVector c = new BinaryVector(new int[] { 1, 0 }, L);
        set.addElement(a);
        set.addElement(b);
        set.addElement(c);

        BinaryVector first = LocalSearch.getVectorAt(set, 1);
        BinaryVector second = LocalSearch.getVectorAt(set, 2);
        BinaryVector third = LocalSearch.getVectorAt(set, 3);
        assertNotNull(first);
        assertEquals(0, first.getElement(0));
        assertEquals(1, second.getElement(0));
        assertEquals(1, third.getElement(0));
        assertEquals(0, third.getElement(1));
        assertNull(LocalSearch.getVectorAt(set, 0), "index 0 is out of bounds");
        assertNull(LocalSearch.getVectorAt(set, 4),
                "index > size is out of bounds");
    }

    @Test
    void testWorstMatchingVectors() {
        VectorSet cluster = new VectorSet();
        BinaryVector z = new BinaryVector(new int[] { 0, 0, 0, 0 }, L);
        BinaryVector o = new BinaryVector(new int[] { 1, 1, 1, 1 }, L);
        cluster.addElement(z);
        cluster.addElement(o);

        Random random = new Random(3);
        BinaryVector[] pair = LocalSearch.worstMatchingVectors(cluster, random);
        assertNotNull(pair[0]);
        assertNotNull(pair[1]);
        assertEquals(L,
                DistanceCalculator.hammingDistanceVectors(pair[0], pair[1]),
                "worst match should maximise Hamming distance");
    }

    @Test
    void testWorstMatchingVectorsNeedsTwo() {
        VectorSet cluster = new VectorSet();
        cluster.addElement(new BinaryVector(new int[] { 0, 0 }, L));
        assertThrows(IllegalArgumentException.class,
                () -> LocalSearch.worstMatchingVectors(cluster, new Random(1)));
    }

    @Test
    void testInfAverageUnrounded() {
        VectorSet cluster = new VectorSet();
        cluster.addElement(new BinaryVector(new int[] { 1, 1, 0, 0 }, L));
        cluster.addElement(new BinaryVector(new int[] { 1, 0, 1, 0 }, L));

        Centroid c = new Centroid(new double[] { 0, 0, 0, 0 }, L, 0.0);
        LocalSearch.infAverage(cluster, c, false, 4);

        double[] el = c.getArray();
        assertEquals(1.0, el[0], 1e-9);
        assertEquals(0.5, el[1], 1e-9);
        assertEquals(0.5, el[2], 1e-9);
        assertEquals(0.0, el[3], 1e-9);
        assertEquals(0.5, c.getWeight(), 1e-9, "weight should be size/n = 2/4");
    }

    @Test
    void testInfAverageRounded() {
        VectorSet cluster = new VectorSet();
        cluster.addElement(new BinaryVector(new int[] { 1, 1, 0, 0 }, L));
        cluster.addElement(new BinaryVector(new int[] { 1, 0, 1, 0 }, L));

        Centroid c = new Centroid(new double[] { 0, 0, 0, 0 }, L, 0.0);
        LocalSearch.infAverage(cluster, c, true, 4);

        double[] el = c.getArray();
        assertEquals(1.0, el[0], 1e-9);
        assertEquals(1.0, el[1], 1e-9);
        assertEquals(1.0, el[2], 1e-9);
        assertEquals(0.0, el[3], 1e-9);
    }

    @Test
    void testSplitAndJoinPreservesClusters() {
        Partition p = buildThreeClusterPartition();
        InfiniteCentroids c = buildThreeCentroids();
        double[] before = c.get(2).getArray().clone();

        LocalSearch.splitAndJoin(3, L, totalVectors(p), c, p, new Random(5));

        assertEquals(3, p.size(), "split and join keeps the cluster count");
        // joinClusters moves each element from the merged cluster into its
        // neighbour and removes it (mirrors C join_class()/del_element()), so
        // no vector is duplicated across clusters.
        assertEquals(9, totalVectors(p), "join preserves every vector once");
        assertNotEquals(before[0], c.get(2).getArray()[0],
                "last centroid should be modified by the split step");
    }

    @Test
    void testSplitAndJoin2PreservesClusters() {
        Partition p = buildThreeClusterPartition();
        InfiniteCentroids c = buildThreeCentroids();

        LocalSearch.splitAndJoin2(3, L, totalVectors(p), c, p, new Random(9));

        assertEquals(3, p.size(), "split and join 2 keeps the cluster count");
        // Each element is moved from the merged cluster into its neighbour and
        // removed (mirrors C join_class()/del_element()), so no vector is
        // duplicated across clusters.
        assertEquals(9, totalVectors(p), "join preserves every vector once");
    }

    @Test
    void testReplaceWorstPreservesVectors() {
        Partition p = buildThreeClusterPartition();
        InfiniteCentroids c = buildThreeCentroids();
        int before = totalVectors(p);

        LocalSearch.replaceWorst(3, L, before, c, p, new Random(11));

        assertEquals(before, totalVectors(p), "vector count is preserved");
        assertNotNull(c.get(0));
    }

    @Test
    void testReplaceSmallestPreservesVectors() {
        Partition p = buildThreeClusterPartition();
        InfiniteCentroids c = buildThreeCentroids();
        int before = totalVectors(p);

        LocalSearch.replaceSmallest(3, L, before, c, p, new Random(13));

        assertEquals(before, totalVectors(p), "vector count is preserved");
    }

    @Test
    void testRandomSwapPreservesVectors() {
        Partition p = buildThreeClusterPartition();
        InfiniteCentroids c = buildThreeCentroids();
        int before = totalVectors(p);

        LocalSearch.randomSwap(3, L, before, c, p, new Random(17));

        assertEquals(before, totalVectors(p), "vector count is preserved");
    }

    @Test
    void testRandomSwap2PreservesVectors() {
        Partition p = buildThreeClusterPartition();
        InfiniteCentroids c = buildThreeCentroids();
        int before = totalVectors(p);

        LocalSearch.randomSwap2(3, L, before, c, p, new Random(19));

        assertEquals(before, totalVectors(p), "vector count is preserved");
    }

    @Test
    void testSuccessIndexMapping() {
        assertEquals(0,
                LocalSearch.successIndex(LocalSearchOperator.SPLITJOIN1));
        assertEquals(1,
                LocalSearch.successIndex(LocalSearchOperator.REPLACEWORST));
        assertEquals(2,
                LocalSearch.successIndex(LocalSearchOperator.REPLACESMALLEST));
        assertEquals(3,
                LocalSearch.successIndex(LocalSearchOperator.RANDOMSWAP));
        assertEquals(4,
                LocalSearch.successIndex(LocalSearchOperator.SPLITJOIN2));
        assertEquals(5,
                LocalSearch.successIndex(LocalSearchOperator.RANDOMSWAP2));
        assertEquals(-1, LocalSearch.successIndex(LocalSearchOperator.NONE));
    }

    @Test
    void testUpdateProbabilitiesSumsToOne() {
        double[] w = { 1.0, 2.0, 3.0, 4.0, 5.0 };
        double[] p = new double[5];
        LocalSearch.updateProbabilities(w, p);

        // Each probability is (w[j] + alfa) / (sumW + 6*alfa), so the five
        // probabilities sum to (sumW + 5*alfa) / (sumW + 6*alfa).
        double sum = 0.0;
        for (double v : p) {
            sum += v;
        }
        double denom = 15.0 + 6.0 * LocalSearch.ALFA;
        assertEquals((15.0 + 5.0 * LocalSearch.ALFA) / denom, sum, 1e-9);

        assertEquals((1.0 + LocalSearch.ALFA) / denom, p[0], 1e-9);
        assertEquals((5.0 + LocalSearch.ALFA) / denom, p[4], 1e-9);
    }

    @Test
    void testLocalSearchDriverReturnsExpectedCost() {
        Partition p = buildThreeClusterPartition();
        InfiniteCentroids c = buildThreeCentroids();
        int n = totalVectors(p);

        int ret = LocalSearch.localSearch(p, c, 5, L, n, false, new Random(7));

        // gt accumulates 2 per loop iteration (count-1 of them) plus a final 2
        // and a closing +2: 2*(count-1) + 2 + 2 == 2*count + 2.
        assertEquals(2 * 5 + 2, ret);
        assertEquals(n, totalVectors(p), "driver preserves the vector count");
    }

    @Test
    void testLocalSearchDriverSinglePass() {
        Partition p = buildThreeClusterPartition();
        InfiniteCentroids c = buildThreeCentroids();
        int n = totalVectors(p);

        int ret = LocalSearch.localSearch(p, c, 1, L, n, false, new Random(42));

        assertEquals(4, ret, "count == 1 runs no loop iterations");
        assertEquals(n, totalVectors(p), "vector count preserved after driver");
    }

    @Test
    void testSafeWorstMatchingVectorsSingleton() throws Exception {
        java.lang.reflect.Method m = LocalSearch.class.getDeclaredMethod(
                "safeWorstMatchingVectors", VectorSet.class, Random.class);
        m.setAccessible(true);

        // A singleton cluster pairs the lone element with itself instead of
        // throwing IllegalArgumentException like the guarded
        // worstMatchingVectors() does. This is what lets the split operators
        // survive a pong-phase rescan that selects an empty class.
        VectorSet singleton = new VectorSet();
        BinaryVector only = new BinaryVector(new int[] { 1, 0, 1, 0 }, L);
        singleton.addElement(only);

        BinaryVector[] pair = (BinaryVector[]) m.invoke(null, singleton,
                new Random(2));
        assertNotNull(pair[0]);
        assertEquals(1, pair[0].getElement(0));
        assertSame(pair[0], pair[1], "singleton should pair with itself");
    }

    @Test
    void testSafeWorstMatchingVectorsNormal() throws Exception {
        java.lang.reflect.Method m = LocalSearch.class.getDeclaredMethod(
                "safeWorstMatchingVectors", VectorSet.class, Random.class);
        m.setAccessible(true);

        // With two or more vectors the resilient variant behaves like the
        // guarded one and returns a valid pair.
        VectorSet cluster = new VectorSet();
        BinaryVector z = new BinaryVector(new int[] { 0, 0, 0, 0 }, L);
        BinaryVector o = new BinaryVector(new int[] { 1, 1, 1, 1 }, L);
        cluster.addElement(z);
        cluster.addElement(o);

        BinaryVector[] pair = (BinaryVector[]) m.invoke(null, cluster,
                new Random(3));
        assertNotNull(pair[0]);
        assertNotNull(pair[1]);
        assertEquals(L, DistanceCalculator.hammingDistanceVectors(pair[0],
                pair[1]), "worst match should maximise Hamming distance");
    }

    @Test
    void testSplitAndJoinSurvivesSingletonCluster() {
        // A partition whose largest-distortion class may be a singleton must
        // not throw when the split operator calls safeWorstMatchingVectors on
        // it. Run several times with different seeds to exercise both branches.
        Partition p = buildThreeClusterPartition();
        InfiniteCentroids c = buildThreeCentroids();
        for (int seed = 1; seed <= 5; seed++) {
            LocalSearch.splitAndJoin(3, L, totalVectors(p), c, p,
                    new Random(seed));
            assertEquals(3, p.size(), "cluster count is preserved");
            // joinClusters moves each element from the merged cluster into its
            // neighbour and removes it (mirrors C join_class()/del_element()),
            // so across seeds no vector is duplicated; the total stays at nine.
            assertEquals(totalVectors(buildThreeClusterPartition()),
                    totalVectors(p),
                    "split and join preserves every vector once");
        }
    }
}
