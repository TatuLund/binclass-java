/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link VectorSet}.
 */
public class VectorSetTest {

    @Test
    public void testCreation() {
        VectorSet set = new VectorSet();
        assertEquals(0, set.size());
        assertTrue(set.isEmpty());
    }

    @Test
    public void testAddElement() {
        VectorSet set = new VectorSet();

        int[] el1 = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 2);

        assertTrue(set.addElement(v1)); // First add returns true
        assertEquals(1, set.size());
    }

    @Test
    public void testAddDuplicate() {
        VectorSet set = new VectorSet();

        int[] el = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el, 2);
        BinaryVector v2 = new BinaryVector(el, 2); // Same content

        set.addElement(v1);
        assertFalse(set.addElement(v2)); // Duplicate returns false
        assertEquals(1, set.size());
    }

    @Test
    public void testRemoveElement() {
        VectorSet set = new VectorSet();

        int[] el = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el, 2);

        set.addElement(v1);
        assertTrue(set.removeElement(v1)); // Remove returns true if present
        assertEquals(0, set.size());
    }

    @Test
    public void testRemoveNonExistent() {
        VectorSet set = new VectorSet();

        int[] el = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el, 2);

        assertFalse(set.removeElement(v1)); // Remove returns false if not
                                            // present
    }

    @Test
    public void testContains() {
        VectorSet set = new VectorSet();

        int[] el = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el, 2);

        assertFalse(set.contains(v1)); // Not added yet

        set.addElement(v1);
        assertTrue(set.contains(v1)); // Now present
    }

    @Test
    public void testIterator() {
        VectorSet set = new VectorSet();

        int[] el1 = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 2);

        int[] el2 = { 1, 0 };
        BinaryVector v2 = new BinaryVector(el2, 2);

        set.addElement(v1);
        set.addElement(v2);

        int count = 0;
        for (BinaryVector bv : set) {
            assertNotNull(bv);
            count++;
        }
        assertEquals(2, count);
    }

    @Test
    public void testClear() {
        VectorSet set = new VectorSet();

        int[] el1 = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 2);

        int[] el2 = { 1, 0 };
        BinaryVector v2 = new BinaryVector(el2, 2);

        set.addElement(v1);
        set.addElement(v2);
        assertEquals(2, set.size());

        set.clear();
        assertEquals(0, set.size());
        assertTrue(set.isEmpty());
    }

    @Test
    public void testToString() {
        VectorSet set = new VectorSet();

        int[] el = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el, 2);

        set.addElement(v1);

        String str = set.toString();
        assertTrue(str.contains("1 elements"));
    }

    @Test
    public void testInitialCapacity() {
        VectorSet set = new VectorSet(100); // Initial capacity hint

        assertEquals(0, set.size());
        assertNotNull(set.iterator()); // Should not throw
    }
}
