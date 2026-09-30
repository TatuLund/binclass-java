/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.cut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Partition;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CutEngine}.
 */
class CutEngineTest {

    /**
     * Builds a partition with the given cluster count and assigns each vector
     * to the requested 1-based cluster.
     */
    private static final BinaryVector V0 = vector(0);
    private static final BinaryVector V1 = vector(1);
    private static final BinaryVector V2 = vector(2);
    private static final BinaryVector V3 = vector(3);

    private static Partition buildPartition(int k,
            List<List<BinaryVector>> byCluster) {
        Partition partition = new Partition(k);
        for (int i = 0; i < byCluster.size(); i++) {
            for (BinaryVector bv : byCluster.get(i)) {
                partition.addElement(i + 1, bv);
            }
        }
        return partition;
    }

    private static BinaryVector vector(int id) {
        return new BinaryVector(new int[] { id & 1, (id >> 1) & 1 }, 0, 2, 0,
                "v" + id);
    }

    @Test
    void testSimpleIntersectionKeepsSharedElements() {
        Partition p1 = buildPartition(2, List.of(List.of(V0, V1),
                List.of(V2)));
        Partition p2 = buildPartition(2, List.of(List.of(V0),
                List.of(V1, V2)));

        Partition result = CutEngine.simpleIntersection(p1, p2);

        // Every element of p1 that also appears in some cluster of p2 is kept.
        assertEquals(3, totalElements(result));
    }

    @Test
    void testSimpleIntersectionTrimsEmptyClusters() {
        Partition p1 = buildPartition(3, List.of(List.of(V0),
                List.of(V1), List.of(V2)));
        // p2 keeps only vector 0 in cluster 1; clusters 2 and 3 of p1 have no
        // match there.
        Partition p2 = buildPartition(1, List.of(List.of(V0)));

        Partition result = CutEngine.simpleIntersection(p1, p2);

        assertEquals(1, result.size());
        assertTrue(result.getSize(1) > 0);
    }

    @Test
    void testMinimalIntervalUsesUniqueMaxOverlap() {
        // Cluster 1 of p1 shares two elements with cluster 1 of p2 and one with
        // cluster 2; the unique maximum is cluster 1.
        Partition p1 = buildPartition(2, List.of(List.of(V0, V1, V2),
                List.of(V3)));
        Partition p2 = buildPartition(2, List.of(List.of(V0, V1),
                List.of(V2)));

        Partition result = CutEngine.minimalInterval(p1, p2);

        assertEquals(2, totalElements(result));
    }

    @Test
    void testMaximalIntervalKeepsAllTies() {
        // Cluster 1 of p1 ties between both clusters of p2 (one element each),
        // so maximal interval produces two result clusters.
        Partition p1 = buildPartition(1, List.of(List.of(V0, V1)));
        Partition p2 = buildPartition(2, List.of(List.of(V0),
                List.of(V1)));

        Partition result = CutEngine.maximalInterval(p1, p2);

        assertEquals(2, result.size());
        assertEquals(1, result.getSize(1));
        assertEquals(1, result.getSize(2));
    }

    @Test
    void testSimpleIntersectionRejectsNull() {
        Partition p = buildPartition(1, List.of(List.of(vector(0))));
        assertThrows(NullPointerException.class,
                () -> CutEngine.simpleIntersection(p, null));
        assertThrows(NullPointerException.class,
                () -> CutEngine.simpleIntersection(null, p));
    }

    @Test
    void testMinimalIntersectionTwoPass() {
        // p1: cluster 1 = {V0,V1}, cluster 2 = {V2}
        // p2: cluster 1 = {V0}, cluster 2 = {V1,V2}
        Partition p1 = buildPartition(2, List.of(List.of(V0, V1),
                List.of(V2)));
        Partition p2 = buildPartition(2, List.of(List.of(V0),
                List.of(V1, V2)));

        Partition result = CutEngine.minimalIntersection(p1, p2);

        // Direction p1->p2 keeps only V2 (unique best match in cluster 2).
        // Direction p2->p1 keeps only V0 (unique best match in cluster 1).
        // The merge makes each result cluster equal to its whole counterpart,
        // but V2 is not in the {V0} counterpart, so nothing survives.
        assertEquals(0, totalElements(result));
    }

    @Test
    void testMinimalIntersectionIdenticalPartitionsPreservesAll() {
        Partition p = buildPartition(2, List.of(List.of(V0, V1),
                List.of(V2)));

        Partition result = CutEngine.minimalIntersection(p, p);

        // Identical partitions yield every element back through the merge.
        assertEquals(3, totalElements(result));
    }

    @Test
    void testMaximalIntersectionTwoPass() {
        Partition p1 = buildPartition(1, List.of(List.of(V0, V1)));
        Partition p2 = buildPartition(2, List.of(List.of(V0),
                List.of(V1)));

        Partition result = CutEngine.maximalIntersection(p1, p2);

        // Both directions tie; the two-pass merge keeps shared elements grouped
        // by counterpart cluster: {V0} and {V1}.
        assertEquals(2, totalElements(result));
    }

    @Test
    void testMaximalIntersectionIdenticalPartitionsPreservesAll() {
        Partition p = buildPartition(2, List.of(List.of(V0, V1),
                List.of(V2)));

        Partition result = CutEngine.maximalIntersection(p, p);

        // Identical partitions yield every element back through the merge.
        assertEquals(3, totalElements(result));
    }

    @Test
    void testMinimalIntersectionRejectsNull() {
        Partition p = buildPartition(1, List.of(List.of(V0)));
        assertThrows(NullPointerException.class,
                () -> CutEngine.minimalIntersection(p, null));
        assertThrows(NullPointerException.class,
                () -> CutEngine.minimalIntersection(null, p));
    }

    @Test
    void testMaximalIntersectionRejectsNull() {
        Partition p = buildPartition(1, List.of(List.of(V0)));
        assertThrows(NullPointerException.class,
                () -> CutEngine.maximalIntersection(p, null));
        assertThrows(NullPointerException.class,
                () -> CutEngine.maximalIntersection(null, p));
    }

    @Test
    void testMinimalAndMaximalAreCommutative() {
        Partition p1 = buildPartition(2, List.of(List.of(V0, V1),
                List.of(V2)));
        Partition p2 = buildPartition(2, List.of(List.of(V0),
                List.of(V1, V2)));

        Partition a = CutEngine.minimalIntersection(p1, p2);
        Partition b = CutEngine.minimalIntersection(p2, p1);
        assertEquals(totalElements(a), totalElements(b));

        Partition c = CutEngine.maximalIntersection(p1, p2);
        Partition d = CutEngine.maximalIntersection(p2, p1);
        assertEquals(totalElements(c), totalElements(d));
    }

    private static int totalElements(Partition partition) {
        int total = 0;
        for (int i = 1; i <= partition.size(); i++) {
            total += partition.getSize(i);
        }
        return total;
    }
}
