/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BinaryVector}.
 */
class BinaryVectorTest {

    @Test
    void testCreation() {
        int[] el = { 0, 1, 1, 0 };
        BinaryVector bv = new BinaryVector(el, 4);

        assertEquals(4, bv.getLength());
        assertEquals("", bv.getStrain());
        assertEquals(0, bv.getClassname());
    }

    @Test
    void testGet() {
        int[] el = { 0, 1, 1, 0 };
        BinaryVector bv = new BinaryVector(el, 4);

        assertEquals(0, bv.get(0));
        assertEquals(1, bv.get(1));
        assertEquals(1, bv.get(2));
        assertEquals(0, bv.get(3));
    }

    @Test
    void testMissingValues() {
        int[] el = { 0, 1, 1 };
        int miss = (1 << 1); // Position 1 is missing

        BinaryVector bv = new BinaryVector(el, miss, 3, 0, "test");

        assertFalse(bv.isMissing(0));
        assertTrue(bv.isMissing(1));
        assertFalse(bv.isMissing(2));

        assertEquals(-1, bv.getWithMissing(1)); // Missing position returns -1
        assertEquals(0, bv.getWithMissing(0)); // el[0] is 0
        assertEquals(1, bv.getWithMissing(2)); // el[2] is 1
    }

    @Test
    void testClassname() {
        BinaryVector bv = new BinaryVector(new int[] { 0, 1 }, 2);

        assertEquals(0, bv.getClassname());

        bv.setClassname(5);
        assertEquals(5, bv.getClassname());
    }

    @Test
    void testStrain() {
        BinaryVector bv = new BinaryVector(new int[] { 0, 1 }, 0, 2, 0,
                "strain_A");

        assertEquals("strain_A", bv.getStrain());

        bv.setStrain("strain_B");
        assertEquals("strain_B", bv.getStrain());
    }

    @Test
    void testCopy() {
        int[] el = { 0, 1, 1 };
        BinaryVector original = new BinaryVector(el, 3);

        BinaryVector copy = original.copy();

        assertEquals(original, copy);
        assertNotSame(original.getEl(), copy.getEl()); // Different array
                                                       // instances

        // Modify copy shouldn't affect original
        copy.setStrain("modified");
        assertNotEquals(copy.getStrain(), original.getStrain());
    }

    @Test
    void testHammingDistance() {
        int[] el1 = { 0, 1, 1, 0 };
        int[] el2 = { 0, 1, 0, 1 };

        BinaryVector v1 = new BinaryVector(el1, 4);
        BinaryVector v2 = new BinaryVector(el2, 4);

        assertEquals(2, BinaryVector.hammingDistance(v1, v2)); // Positions 2
                                                               // and 3 differ
    }

    @Test
    void testHammingDistanceWithMissing() {
        int[] el1 = { 0, 1, 1 };
        int miss1 = (1 << 1); // Position 1 missing in v1

        int[] el2 = { 0, 0, 1 };

        BinaryVector v1 = new BinaryVector(el1, miss1, 3, 0, "");
        BinaryVector v2 = new BinaryVector(el2, 0, 3, 0, "");

        // Position 1 is missing in v1, so skip it
        // Only position 2 differs (v1=1, v2=1 -> same)
        assertEquals(0, BinaryVector.hammingDistance(v1, v2));
    }

    @Test
    void testEquals() {
        int[] el = { 0, 1, 1 };
        BinaryVector v1 = new BinaryVector(el, 3);
        BinaryVector v2 = new BinaryVector(el, 3);

        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    void testToString() {
        int[] el = { 0, 1, 1 };
        int miss = (1 << 1); // Position 1 missing

        BinaryVector bv = new BinaryVector(el, miss, 3, 0, "test");

        String str = bv.toString();
        assertTrue(str.contains("test"));
        assertTrue(str.contains("x")); // Missing value marker
    }

    @Test
    void testNullStrain() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BinaryVector(new int[] { 0, 1 }, 2, 0, 0, null);
        });
    }
}
