/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DynamicPartition}.
 */
public class DynamicPartitionTest {

    @Test
    public void testCreation() {
        DynamicPartition dp = new DynamicPartition(3, 5);

        assertEquals(3, dp.size());
    }

    @Test
    public void testPutVector() {
        DynamicPartition dp = new DynamicPartition(2, 5);

        int[] el1 = { 0, 1, 1, 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 0, 5, 0, "v1");

        dp.putVector(1, v1);

        assertEquals(1, dp.convert().getSize(1));
    }

    @Test
    public void testRemoveVector() {
        DynamicPartition dp = new DynamicPartition(2, 5);

        int[] el1 = { 0, 1, 1, 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 0, 5, 0, "v1");

        dp.putVector(1, v1);
        assertEquals(1, dp.convert().getSize(1));

        dp.removeVector(1, v1);
        assertEquals(0, dp.convert().getSize(1));
    }

    @Test
    public void testGetFreqs() {
        DynamicPartition dp = new DynamicPartition(2, 5);

        int[] el1 = { 1, 0, 1, 1, 0 };
        BinaryVector v1 = new BinaryVector(el1, 0, 5, 0, "v1");

        dp.putVector(1, v1);

        int[] freqs = dp.getFreqs(1);
        assertEquals(5, freqs.length);
        assertEquals(1, freqs[0]); // One occurrence of bit=1 at position 0
                                   // (only v1)
        assertEquals(0, freqs[1]); // Zero occurrences of bit=1 at position 1
                                   // (v1 has 0 there)
    }

    @Test
    public void testGetHammingDistance() {
        DynamicPartition dp = new DynamicPartition(2, 5);

        int[] el1 = { 0, 1, 1, 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 0, 5, 0, "v1");

        int[] el2 = { 1, 0, 1, 0, 1 };
        BinaryVector v2 = new BinaryVector(el2, 0, 5, 0, "v2");

        dp.putVector(1, v1);
        dp.putVector(2, v2);

        double dist = dp.getHammingDistance(1, 2);
        assertEquals(2.0, dist, 1e-10); // Positions 0 and 1 differ (hamming
                                        // distance = 2)
    }

    @Test
    public void testConvert() {
        DynamicPartition dp = new DynamicPartition(2, 5);

        int[] el1 = { 0, 1, 0, 1, 0 }; // 5 elements to match length=5
        BinaryVector v1 = new BinaryVector(el1, 0, 5, 0, "v1");

        dp.putVector(1, v1);

        Partition p = dp.convert();
        assertNotNull(p);
        assertEquals(2, p.size());
    }

    @Test
    public void testBoundsChecking() {
        DynamicPartition dp = new DynamicPartition(2, 5);

        assertThrows(IndexOutOfBoundsException.class,
                () -> dp.putVector(0, null));
        assertThrows(IndexOutOfBoundsException.class,
                () -> dp.putVector(3, null));
        assertThrows(IndexOutOfBoundsException.class, () -> dp.getFreqs(0));
        assertThrows(IndexOutOfBoundsException.class,
                () -> dp.getHammingDistance(0, 1));
    }

    @Test
    public void testToString() {
        DynamicPartition dp = new DynamicPartition(2, 5);

        int[] el1 = { 0, 1, 0, 1, 0 }; // Fixed: 5 elements to match length=5
        BinaryVector v1 = new BinaryVector(el1, 0, 5, 0, "v1");

        dp.putVector(1, v1);

        String str = dp.toString();
        assertTrue(str.contains("DynamicPartition{"));
    }

    @Test
    public void testNegativeDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> new DynamicPartition(-1, 5));
        assertThrows(IllegalArgumentException.class,
                () -> new DynamicPartition(2, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new DynamicPartition(0, 5));
    }

    @Test
    public void testMultipleVectorsInCluster() {
        DynamicPartition dp = new DynamicPartition(1, 5);

        int[] el1 = { 1, 0, 1, 1, 0 };
        BinaryVector v1 = new BinaryVector(el1, 0, 5, 0, "v1");

        int[] el2 = { 1, 1, 0, 1, 0 };
        BinaryVector v2 = new BinaryVector(el2, 0, 5, 0, "v2");

        dp.putVector(1, v1);
        dp.putVector(1, v2);

        assertEquals(2, dp.convert().getSize(1));

        // Frequency table should reflect both vectors
        int[] freqs = dp.getFreqs(1);
        assertEquals(2, freqs[0]); // Both have bit=1 at position 0
        assertEquals(1, freqs[1]); // Only v2 has bit=1 at position 1
    }

    @Test
    public void testHammingDistanceSymmetry() {
        DynamicPartition dp = new DynamicPartition(2, 5);

        int[] el1 = { 0, 1, 1, 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 0, 5, 0, "v1");

        int[] el2 = { 1, 0, 1, 0, 1 };
        BinaryVector v2 = new BinaryVector(el2, 0, 5, 0, "v2");

        dp.putVector(1, v1);
        dp.putVector(2, v2);

        double dist1to2 = dp.getHammingDistance(1, 2);
        double dist2to1 = dp.getHammingDistance(2, 1);

        assertEquals(dist1to2, dist2to1, 1e-10); // Should be symmetric
    }

    @Test
    public void testEmptyClusterHammingDistance() {
        DynamicPartition dp = new DynamicPartition(2, 5);

        int[] el1 = { 0, 1, 0, 1, 0 }; // Fixed: 5 elements to match length=5
        BinaryVector v1 = new BinaryVector(el1, 0, 5, 0, "v1");

        dp.putVector(1, v1);
        // Cluster 2 is empty

        double dist = dp.getHammingDistance(1, 2);
        assertEquals(0.0, dist, 1e-10); // Empty cluster returns 0
    }
}
