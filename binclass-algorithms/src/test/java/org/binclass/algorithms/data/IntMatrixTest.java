/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link IntMatrix}.
 */
class IntMatrixTest {

    @Test
    void testConstructorAndAccess() {
        IntMatrix m = new IntMatrix(2, 3);

        assertEquals(2, m.getRows());
        assertEquals(3, m.getCols(0)); // All rows have same length; // All rows
                                       // have same length

        // Default values should be 0
        assertEquals(0, m.get(0, 0));
        assertEquals(0, m.get(1, 2));
    }

    @Test
    void testSetAndGet() {
        IntMatrix m = new IntMatrix(3, 3);

        m.set(0, 0, 1);
        m.set(0, 1, 2);
        m.set(0, 2, 3);
        m.set(1, 0, 4);
        m.set(1, 1, 5);
        m.set(1, 2, 6);
        m.set(2, 0, 7);
        m.set(2, 1, 8);
        m.set(2, 2, 9);

        assertEquals(1, m.get(0, 0));
        assertEquals(5, m.get(1, 1));
        assertEquals(9, m.get(2, 2));
    }

    @Test
    void testJaggedArray() {
        IntMatrix jagged = new IntMatrix(3, 0); // Initialize with 0 cols

        jagged.el[0] = new int[] { 1, 2 };
        jagged.el[1] = new int[] { 3, 4, 5 };
        jagged.el[2] = new int[] { 6 };

        assertEquals(1, jagged.get(0, 0)); // First element of first row
        assertEquals(4, jagged.get(1, 1)); // Second element of second row
        assertEquals(6, jagged.get(2, 0)); // Only element in third row
    }

    @Test
    void testToString() {
        IntMatrix m = new IntMatrix(2, 3);
        m.set(0, 0, 1);
        m.set(0, 1, 2);
        m.set(0, 2, 3);
        m.set(1, 0, 4);
        m.set(1, 1, 5);
        m.set(1, 2, 6);

        String str = m.toString();
        assertTrue(str.contains("[1, 2, 3]"));
        assertTrue(str.contains("[4, 5, 6]"));
    }

    @Test
    void testGetRow() {
        IntMatrix m = new IntMatrix(2, 3);
        m.set(0, 0, 1);
        m.set(0, 1, 2);
        m.set(0, 2, 3);

        int[] row = m.getRow(0);
        assertArrayEquals(new int[] { 1, 2, 3 }, row);
    }

    @Test
    void testGetColumn() {
        IntMatrix m = new IntMatrix(2, 3);
        m.set(0, 0, 1);
        m.set(0, 1, 2);
        m.set(0, 2, 3);
        m.set(1, 0, 4);
        m.set(1, 1, 5);
        m.set(1, 2, 6);

        int[] col = m.getColumn(1);
        assertArrayEquals(new int[] { 2, 5 }, col);
    }

    @Test
    void testSetRow() {
        IntMatrix m = new IntMatrix(2, 3);
        int[] newRow = { 7, 8, 9 };

        m.setRow(0, newRow);

        assertEquals(7, m.get(0, 0));
        assertEquals(8, m.get(0, 1));
        assertEquals(9, m.get(0, 2));
    }

    @Test
    void testSetColumn() {
        IntMatrix m = new IntMatrix(2, 3);
        int[] newCol = { 7, 8 };

        m.setColumn(1, newCol);

        assertEquals(7, m.get(0, 1));
        assertEquals(8, m.get(1, 1));
    }

    @Test
    void testEquals() {
        IntMatrix m1 = new IntMatrix(2, 3);
        IntMatrix m2 = new IntMatrix(2, 3);

        assertEquals(m1, m2); // Both empty matrices

        m1.set(0, 0, 1);
        assertNotEquals(m1, m2);
    }

    @Test
    void testHashCode() {
        IntMatrix m1 = new IntMatrix(2, 3);
        IntMatrix m2 = new IntMatrix(2, 3);

        assertEquals(m1.hashCode(), m2.hashCode());
    }
}