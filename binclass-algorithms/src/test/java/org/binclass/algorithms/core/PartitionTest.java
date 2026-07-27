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
public class PartitionTest {

    @Test
    public void testCreation() {
        Partition p = new Partition(3);

        assertEquals(3, p.size());
    }

    @Test
    public void testAddElement() {
        Partition p = new Partition(2);

        int[] el1 = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 2);

        p.addElement(1, v1);

        assertEquals(1, p.getSize(1));
        assertTrue(p.contains(1, v1));
    }

    @Test
    public void testRemoveElement() {
        Partition p = new Partition(2);

        int[] el1 = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 2);

        p.addElement(1, v1);
        assertEquals(1, p.getSize(1));

        p.removeElement(1, v1);
        assertEquals(0, p.getSize(1));
    }

    @Test
    public void testGetElements() {
        Partition p = new Partition(2);

        int[] el1 = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 2);

        p.addElement(1, v1);

        VectorSet cluster1 = p.getElements(1);
        assertNotNull(cluster1);
        assertEquals(1, cluster1.size());
    }

    @Test
    public void testContains() {
        Partition p = new Partition(2);

        int[] el1 = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 2);

        assertFalse(p.contains(1, v1)); // Not added yet

        p.addElement(1, v1);
        assertTrue(p.contains(1, v1)); // Now present
    }

    @Test
    public void testMultipleClusters() {
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
    public void testBoundsChecking() {
        Partition p = new Partition(2);

        assertThrows(IndexOutOfBoundsException.class,
                () -> p.addElement(0, null));
        assertThrows(IndexOutOfBoundsException.class,
                () -> p.addElement(3, null));
        assertThrows(IndexOutOfBoundsException.class, () -> p.getSize(0));
        assertThrows(IndexOutOfBoundsException.class, () -> p.getSize(3));
    }

    @Test
    public void testToString() {
        Partition p = new Partition(2);

        int[] el1 = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 2);

        p.addElement(1, v1);

        String str = p.toString();
        assertTrue(str.contains("Partition{"));
    }

    @Test
    public void testNegativeK() {
        assertThrows(IllegalArgumentException.class, () -> new Partition(-1));
        assertThrows(IllegalArgumentException.class, () -> new Partition(0));
    }
}
