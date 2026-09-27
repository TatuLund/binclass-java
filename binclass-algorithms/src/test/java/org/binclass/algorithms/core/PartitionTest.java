/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Partition}.
 */
class PartitionTest {

    @Test
    void testCreation() {
        Partition p = new Partition(3);

        assertEquals(3, p.size());
    }

    @Test
    void testAddElement() {
        Partition p = new Partition(2);

        int[] el1 = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 2);

        p.addElement(1, v1);

        assertEquals(1, p.getSize(1));
        assertTrue(p.contains(1, v1));
    }

    @Test
    void testRemoveElement() {
        Partition p = new Partition(2);

        int[] el1 = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 2);

        p.addElement(1, v1);
        assertEquals(1, p.getSize(1));

        p.removeElement(1, v1);
        assertEquals(0, p.getSize(1));
    }

    @Test
    void testGetElements() {
        Partition p = new Partition(2);

        int[] el1 = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 2);

        p.addElement(1, v1);

        VectorSet cluster1 = p.getElements(1);
        assertNotNull(cluster1);
        assertEquals(1, cluster1.size());
    }

    @Test
    void testContains() {
        Partition p = new Partition(2);

        int[] el1 = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 2);

        assertFalse(p.contains(1, v1)); // Not added yet

        p.addElement(1, v1);
        assertTrue(p.contains(1, v1)); // Now present
    }

    @Test
    void testMultipleClusters() {
        Partition p = new Partition(3);

        int[] el1 = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 2);

        int[] el2 = { 1, 0 };
        BinaryVector v2 = new BinaryVector(el2, 2);

        p.addElement(1, v1);
        p.addElement(2, v2);

        assertEquals(1, p.getSize(1));
        assertEquals(1, p.getSize(2));
        assertEquals(0, p.getSize(3));
    }

    @Test
    void testBoundsChecking() {
        Partition p = new Partition(2);

        assertThrows(IndexOutOfBoundsException.class,
                () -> p.addElement(0, null));
        assertThrows(IndexOutOfBoundsException.class,
                () -> p.addElement(3, null));
        assertThrows(IndexOutOfBoundsException.class, () -> p.getSize(0));
        assertThrows(IndexOutOfBoundsException.class, () -> p.getSize(3));
    }

    @Test
    void testToString() {
        Partition p = new Partition(2);

        int[] el1 = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 2);

        p.addElement(1, v1);

        String str = p.toString();
        assertTrue(str.contains("Partition{"));
    }

    @Test
    void testNegativeK() {
        assertThrows(IllegalArgumentException.class, () -> new Partition(-1));
        assertThrows(IllegalArgumentException.class, () -> new Partition(0));
    }

    @Test
    void testSetSizeCompactsDroppedClusters() {
        Partition p = new Partition(3);

        BinaryVector v1 = new BinaryVector(new int[] { 0, 1 }, 2);
        BinaryVector v3 = new BinaryVector(new int[] { 1, 0 }, 2);

        p.addElement(1, v1); // cluster 1 non-empty
        // cluster 2 left empty
        p.addElement(3, v3); // cluster 3 non-empty

        p.setSize(2); // shrink: the empty middle cluster must be compacted away

        assertEquals(1, p.getSize(1));
        assertTrue(p.contains(2, v3),
                "vectors from a dropped slot stay accessible after compaction");
    }

    @Test
    void testRemoveClusterPreservesAllVectors() {
        Partition p = new Partition(4);

        BinaryVector v1 = new BinaryVector(new int[] { 0, 1 }, 2);
        BinaryVector v3 = new BinaryVector(new int[] { 1, 1 }, 2);

        p.addElement(1, v1); // cluster 1
        // clusters 2 and 3 left empty
        p.addElement(4, v3); // cluster 4

        p.removeCluster(2); // remove an empty middle cluster; v3 must shift
                            // down

        assertEquals(3, p.size());
        assertEquals(1, p.getSize(1), "cluster 1 keeps its vector");
        assertTrue(p.contains(3, v3),
                "vector from a later cluster shifts down into the gap");

        int total = p.getSize(1) + p.getSize(2) + p.getSize(3);
        assertEquals(2, total,
                "no vectors lost when removing an empty cluster");
    }

    @Test
    void testRemoveClusterNoReferenceAliasing() {
        Partition p = new Partition(5);

        BinaryVector v1 = new BinaryVector(new int[] { 0, 0 }, 2);
        BinaryVector v3 = new BinaryVector(new int[] { 0, 1 }, 2);

        p.addElement(1, v1); // cluster 1
        p.addElement(2, v3); // cluster 2 (will be removed)
        p.addElement(4, v3.copy()); // cluster 4 — a survivor after the gap

        BinaryVector survivor = p.getElements(4).iterator().next();

        p.removeCluster(2); // remove middle non-empty cluster; survivor shifts
                            // down

        assertEquals(4, p.size());
        assertTrue(p.contains(1, v1), "first cluster keeps its vector");
        assertTrue(p.contains(3, survivor),
                "survivor shifts down exactly once into the gap");

        // Each live cluster is a distinct object: no aliasing that would
        // inflate counts.
        VectorSet c1 = p.getCluster(0);
        VectorSet c2 = p.getCluster(1);
        VectorSet c3 = p.getCluster(2);
        assertTrue(c1 != c2 && c2 != c3 && c1 != c3,
                "clusters are distinct objects");

        int total = p.getSize(1) + p.getSize(2) + p.getSize(3) + p.getSize(4);
        assertEquals(2, total,
                "sum of per-cluster sizes equals number of surviving vectors (v1 + survivor)");
    }
}
