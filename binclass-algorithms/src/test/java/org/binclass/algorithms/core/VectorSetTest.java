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
class VectorSetTest {

    @Test
    void testCreation() {
        VectorSet set = new VectorSet();
        assertEquals(0, set.size());
        assertTrue(set.isEmpty());
    }

    @Test
    void testAddElement() {
        VectorSet set = new VectorSet();

        int[] el1 = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 2);

        assertTrue(set.addElement(v1)); // First add returns true
        assertEquals(1, set.size());
    }

    @Test
    void testAddDuplicate() {
        VectorSet set = new VectorSet();

        int[] el = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el, 2);
        BinaryVector v2 = new BinaryVector(el, 2); // Same content but different
                                                   // instance

        set.addElement(v1);
        assertTrue(set.addElement(v2)); // Different reference - not a duplicate
                                        // in IdentityHashMap
        assertEquals(2, set.size());
    }

    @Test
    void testRemoveElement() {
        VectorSet set = new VectorSet();

        int[] el = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el, 2);

        set.addElement(v1);
        assertTrue(set.removeElement(v1)); // Remove returns true if present
        assertEquals(0, set.size());
    }

    @Test
    void testRemoveNonExistent() {
        VectorSet set = new VectorSet();

        int[] el = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el, 2);

        assertFalse(set.removeElement(v1)); // Remove returns false if not
                                            // present
    }

    @Test
    void testContains() {
        VectorSet set = new VectorSet();

        int[] el = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el, 2);

        assertFalse(set.contains(v1)); // Not added yet

        set.addElement(v1);
        assertTrue(set.contains(v1)); // Now present
    }

    @Test
    void testIterator() {
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
    void testClear() {
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
    void testToString() {
        VectorSet set = new VectorSet();

        int[] el = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el, 2);

        set.addElement(v1);

        String str = set.toString();
        assertTrue(str.contains("1 elements"));
    }

    @Test
    void testInitialCapacity() {
        VectorSet set = new VectorSet(100); // Initial capacity hint

        assertEquals(0, set.size());
        assertNotNull(set.iterator()); // Should not throw
    }

    @Test
    void testInsertionOrderIteration() {
        VectorSet set = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 0, 0 }, 2);
        BinaryVector v2 = new BinaryVector(new int[] { 1, 1 }, 2);
        BinaryVector v3 = new BinaryVector(new int[] { 0, 1 }, 2);

        set.addElement(v1);
        set.addElement(v2);
        set.addElement(v3);

        // toArray() and iteration must both yield insertion order, not the
        // arbitrary order of the backing IdentityHashMap.
        BinaryVector[] order = set.toArray(new BinaryVector[0]);
        assertArrayEquals(new BinaryVector[] { v1, v2, v3 }, order);

        int idx = 0;
        for (BinaryVector bv : set) {
            assertEquals(order[idx++], bv);
        }
        assertEquals(3, idx);
    }
}
