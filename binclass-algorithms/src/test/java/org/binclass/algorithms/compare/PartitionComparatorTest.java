/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.compare;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Partition;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PartitionComparator}.
 */
class PartitionComparatorTest {

    /** Builds a partition with two clusters of well-separated vectors. */
    private static Partition buildTwoClusterPartition() {
        Partition partition = new Partition(2);
        // Cluster 1: all-ones vectors (length 4).
        partition.addElement(1, new BinaryVector(new int[] { 1, 1, 1, 1 }, 0, 4,
                1, "a"));
        partition.addElement(1, new BinaryVector(new int[] { 1, 1, 1, 0 }, 0, 4,
                1, "b"));
        // Cluster 2: all-zeros vectors.
        partition.addElement(2, new BinaryVector(new int[] { 0, 0, 0, 0 }, 0, 4,
                2, "c"));
        partition.addElement(2, new BinaryVector(new int[] { 0, 0, 1, 0 }, 0, 4,
                2, "d"));
        return partition;
    }

    /** Builds a second partition with the same vectors but mixed clusters. */
    private static Partition buildMixedPartition() {
        Partition partition = new Partition(2);
        partition.addElement(1, new BinaryVector(new int[] { 1, 1, 1, 1 }, 0, 4,
                1, "a"));
        partition.addElement(1, new BinaryVector(new int[] { 0, 0, 0, 0 }, 0, 4,
                1, "c"));
        partition.addElement(2, new BinaryVector(new int[] { 1, 1, 1, 0 }, 0, 4,
                2, "b"));
        partition.addElement(2, new BinaryVector(new int[] { 0, 0, 1, 0 }, 0, 4,
                2, "d"));
        return partition;
    }

    @Test
    void testExactMatchesIdenticalPartitions() {
        Partition p = buildTwoClusterPartition();
        ComparisonResult result = PartitionComparator.comparePartitions(p, p,
                PartitionComparator.PRINT_NEARNESS, true);

        // Identical partitions in exact mode produce a diagonal matrix and zero
        // nearness distance. Vectors a,b are class 1 in both; c,d are class 2.
        assertEquals(0.0, result.distance());
        assertEquals(2, result.rows());
        assertEquals(2, result.columns());
        assertEquals(2, result.count(1, 1));
        assertEquals(2, result.count(2, 2));
        assertEquals(0, result.count(1, 2));
        assertEquals(0, result.count(2, 1));
    }

    @Test
    void testNonExactMatchesIdenticalPartitions() {
        Partition p = buildTwoClusterPartition();
        ComparisonResult result = PartitionComparator.comparePartitions(p, p,
                PartitionComparator.PRINT_NEARNESS, false);

        // Each vector should be reassigned to its nearest centroid by Shannon
        // codelength, reproducing the original partition (distance 0). This
        // also
        // exercises the length-aware centroid construction.
        assertEquals(0.0, result.distance());
    }

    @Test
    void testDifferentPartitionProducesPositiveDistance() {
        Partition p1 = buildTwoClusterPartition();
        Partition p2 = buildMixedPartition();

        ComparisonResult result = PartitionComparator.comparePartitions(p1, p2,
                PartitionComparator.PRINT_NEARNESS, true);

        // A mixed assignment yields a non-zero nearness distance.
        assertTrue(result.distance() > 0.0,
                "distance should be positive for different partitions");
    }

    @Test
    void testMatrixDimensionsMatchPartitionSizes() {
        Partition p1 = buildTwoClusterPartition();
        Partition p2 = buildMixedPartition();

        ComparisonResult result = PartitionComparator.comparePartitions(p1, p2,
                PartitionComparator.PRINT_NEARNESS, true);

        assertEquals(2, result.rows());
        assertEquals(2, result.columns());
    }

    @Test
    void testTotalFrequencyMode() {
        Partition p1 = buildTwoClusterPartition();
        Partition p2 = buildMixedPartition();

        ComparisonResult result = PartitionComparator.comparePartitions(p1, p2,
                PartitionComparator.PRINT_TOTALFREQ, true);

        assertEquals(PartitionComparator.PRINT_TOTALFREQ, result.printMode());
        assertTrue(result.render().contains("COMPARISON RESULTS"));
    }

    @Test
    void testRenderOutputContainsDistance() {
        Partition p = buildTwoClusterPartition();
        ComparisonResult result = PartitionComparator.comparePartitions(p, p,
                PartitionComparator.PRINT_NEARNESS, true);

        String rendered = result.render();
        assertTrue(rendered.contains("COMPARISON RESULTS"));
        assertTrue(rendered.contains("Overall distance = 0.0000"));
    }

    @Test
    void testCountAccessorReadsCorrectCell() {
        Partition p1 = buildTwoClusterPartition();
        Partition p2 = buildMixedPartition();

        ComparisonResult result = PartitionComparator.comparePartitions(p1, p2,
                PartitionComparator.PRINT_NEARNESS, true);

        // The four vectors split evenly across the contingency matrix.
        assertEquals(4, result.count(1, 1) + result.count(1, 2)
                + result.count(2, 1) + result.count(2, 2));
    }

    @Test
    void testInvalidPrintModeThrows() {
        Partition p = buildTwoClusterPartition();
        assertThrows(IllegalArgumentException.class, () -> PartitionComparator
                .comparePartitions(p, p, 0, true));
        assertThrows(IllegalArgumentException.class, () -> PartitionComparator
                .comparePartitions(p, p, 4, true));
    }

    @Test
    void testNullPartitionThrows() {
        Partition p = buildTwoClusterPartition();
        assertThrows(NullPointerException.class, () -> PartitionComparator
                .comparePartitions(null, p, PartitionComparator.PRINT_NEARNESS,
                        true));
        assertThrows(NullPointerException.class, () -> PartitionComparator
                .comparePartitions(p, null, PartitionComparator.PRINT_NEARNESS,
                        true));
    }

    @Test
    void testLongerVectorsDoNotThrow() {
        // Regression test for the length-16 centroid default: vectors longer
        // than 16 bits must not trigger an IndexOutOfBoundsException.
        Partition p = new Partition(2);
        int[] ones = new int[40];
        int[] zeros = new int[40];
        java.util.Arrays.fill(ones, 1);
        for (int i = 0; i < 40; i++) {
            if (i % 7 == 0) {
                ones[i] = 0;
            }
            if (i % 5 == 0) {
                zeros[i] = 1;
            }
        }
        p.addElement(1, new BinaryVector(ones.clone(), 40));
        p.addElement(1, new BinaryVector(ones.clone(), 40));
        p.addElement(2, new BinaryVector(zeros.clone(), 40));
        p.addElement(2, new BinaryVector(zeros.clone(), 40));

        ComparisonResult result = PartitionComparator.comparePartitions(p, p,
                PartitionComparator.PRINT_NEARNESS, false);

        assertEquals(2, result.rows());
        assertEquals(2, result.columns());
        assertTrue(result.distance() >= 0.0);
    }
}
