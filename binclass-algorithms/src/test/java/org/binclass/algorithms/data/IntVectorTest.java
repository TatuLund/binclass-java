/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link IntVector}.
 */
class IntVectorTest {

    @Test
    void testCreation() {
        IntVector v = new IntVector(5);
        assertEquals(5, v.getLength());
    }

    @Test
    void testSetAndGet() {
        IntVector v = new IntVector(3);
        v.set(0, 10);
        v.set(1, 20);
        v.set(2, 30);

        assertEquals(10, v.get(0));
        assertEquals(20, v.get(1));
        assertEquals(30, v.get(2));
    }

    @Test
    void testOneBasedAccess() {
        IntVector v = new IntVector(3);
        v.setOneBased(1, 100);
        v.setOneBased(2, 200);
        v.setOneBased(3, 300);

        assertEquals(100, v.get(0));
        assertEquals(200, v.get(1));
        assertEquals(300, v.get(2));
    }

    @Test
    void testBoundsChecking() {
        IntVector v = new IntVector(3);

        assertThrows(IndexOutOfBoundsException.class, () -> v.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> v.get(3));
        assertThrows(IndexOutOfBoundsException.class, () -> v.set(-1, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> v.set(3, 0));
    }

    @Test
    void testGetArray() {
        IntVector v = new IntVector(2);
        v.set(0, 5);
        v.set(1, 10);

        int[] arr = v.getArray();
        assertEquals(2, arr.length);
        assertEquals(5, arr[0]);
        assertEquals(10, arr[1]);
    }

    @Test
    void testToString() {
        IntVector v = new IntVector(2);
        v.set(0, 1);
        v.set(1, 2);

        String str = v.toString();
        assertTrue(str.contains("1"));
        assertTrue(str.contains("2"));
    }

    @Test
    void testNegativeSize() {
        assertThrows(IllegalArgumentException.class, () -> new IntVector(-1));
    }
}
