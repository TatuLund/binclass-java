/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link DoubleVector}.
 */
public class DoubleVectorTest {

    @Test
    public void testCreation() {
        DoubleVector v = new DoubleVector(5);
        assertEquals(5, v.getLength());
    }

    @Test
    public void testSetAndGet() {
        DoubleVector v = new DoubleVector(3);
        v.set(0, 1.5);
        v.set(1, 2.5);
        v.set(2, 3.5);

        assertEquals(1.5, v.get(0));
        assertEquals(2.5, v.get(1));
        assertEquals(3.5, v.get(2));
    }

    @Test
    public void testOneBasedAccess() {
        DoubleVector v = new DoubleVector(3);
        v.setOneBased(1, 10.0);
        v.setOneBased(2, 20.0);
        v.setOneBased(3, 30.0);

        assertEquals(10.0, v.get(0));
        assertEquals(20.0, v.get(1));
        assertEquals(30.0, v.get(2));
    }

    @Test
    public void testBoundsChecking() {
        DoubleVector v = new DoubleVector(3);

        assertThrows(IndexOutOfBoundsException.class, () -> v.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> v.get(3));
        assertThrows(IndexOutOfBoundsException.class, () -> v.set(-1, 0.0));
        assertThrows(IndexOutOfBoundsException.class, () -> v.set(3, 0.0));
    }

    @Test
    public void testGetArray() {
        DoubleVector v = new DoubleVector(2);
        v.set(0, 1.0);
        v.set(1, 2.0);

        double[] arr = v.getArray();
        assertEquals(2, arr.length);
        assertEquals(1.0, arr[0]);
        assertEquals(2.0, arr[1]);
    }

    @Test
    public void testToString() {
        DoubleVector v = new DoubleVector(2);
        v.set(0, 1.5);
        v.set(1, 2.5);

        String str = v.toString();
        assertTrue(str.contains("1.5"));
        assertTrue(str.contains("2.5"));
    }

    @Test
    public void testNegativeSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new DoubleVector(-1));
    }
}
