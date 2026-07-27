/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DoubleMatrix}.
 */
public class DoubleMatrixTest {

    @Test
    public void testConstructorAndAccess() {
        DoubleMatrix m = new DoubleMatrix(2, 3);

        assertEquals(2, m.getRows());
        assertEquals(3, m.getCols(0)); // All rows have same length

        // Default values should be 0.0
        assertEquals(0.0, m.get(0, 0), 1e-10);
        assertEquals(0.0, m.get(1, 2), 1e-10);
    }

    @Test
    public void testSetAndGet() {
        DoubleMatrix m = new DoubleMatrix(3, 3);

        m.set(0, 0, 1.0);
        m.set(0, 1, 2.0);
        m.set(0, 2, 3.0);
        m.set(1, 0, 4.0);
        m.set(1, 1, 5.0);
        m.set(1, 2, 6.0);
        m.set(2, 0, 7.0);
        m.set(2, 1, 8.0);
        m.set(2, 2, 9.0);

        assertEquals(1.0, m.get(0, 0), 1e-10);
        assertEquals(5.0, m.get(1, 1), 1e-10);
        assertEquals(9.0, m.get(2, 2), 1e-10);
    }

    @Test
    public void testJaggedArray() {
        DoubleMatrix jagged = new DoubleMatrix(3, 0); // Initialize with 0 cols

        jagged.el[0] = new double[] { 1.0, 2.0 };
        jagged.el[1] = new double[] { 3.0, 4.0, 5.0 };
        jagged.el[2] = new double[] { 6.0 };

        assertEquals(1.0, jagged.get(0, 0), 1e-10);
        assertEquals(4.0, jagged.get(1, 1), 1e-10);
        assertEquals(6.0, jagged.get(2, 0), 1e-10);
    }

    @Test
    public void testToString() {
        DoubleMatrix m = new DoubleMatrix(2, 3);
        m.set(0, 0, 1.0);
        m.set(0, 1, 2.0);
        m.set(0, 2, 3.0);
        m.set(1, 0, 4.0);
        m.set(1, 1, 5.0);
        m.set(1, 2, 6.0);

        String str = m.toString();
        assertTrue(str.contains("1.0000"));
        assertTrue(str.contains("4.0000"));
    }

    @Test
    public void testTranspose() {
        DoubleMatrix m = new DoubleMatrix(2, 3);
        m.set(0, 0, 1.0);
        m.set(0, 1, 2.0);
        m.set(0, 2, 3.0);
        m.set(1, 0, 4.0);
        m.set(1, 1, 5.0);
        m.set(1, 2, 6.0);

        DoubleMatrix mt = DoubleMatrix.transpose(m);

        assertEquals(3, mt.getRows());
        assertEquals(2, mt.getCols(0));
        assertEquals(1.0, mt.get(0, 0), 1e-10);
        assertEquals(4.0, mt.get(0, 1), 1e-10);
        assertEquals(3.0, mt.get(2, 0), 1e-10);
        assertEquals(6.0, mt.get(2, 1), 1e-10);
    }

    @Test
    public void testMultiply() {
        DoubleMatrix m = new DoubleMatrix(2, 3);
        m.set(0, 0, 1.0);
        m.set(0, 1, 2.0);
        m.set(0, 2, 3.0);
        m.set(1, 0, 4.0);
        m.set(1, 1, 5.0);
        m.set(1, 2, 6.0);

        DoubleMatrix m2 = new DoubleMatrix(3, 2);
        m2.set(0, 0, 7.0);
        m2.set(0, 1, 8.0);
        m2.set(1, 0, 9.0);
        m2.set(1, 1, 10.0);
        m2.set(2, 0, 11.0);
        m2.set(2, 1, 12.0);

        DoubleMatrix product = DoubleMatrix.multiply(m, m2);

        assertEquals(2, product.getRows());
        assertEquals(2, product.getCols(0));
        // (1*7 + 2*9 + 3*11) = 58
        assertEquals(58.0, product.get(0, 0), 1e-10);
        // (1*8 + 2*10 + 3*12) = 64
        assertEquals(64.0, product.get(0, 1), 1e-10);
    }

    @Test
    public void testInverse() {
        DoubleMatrix square = new DoubleMatrix(2, 2);
        square.set(0, 0, 4.0);
        square.set(0, 1, 7.0);
        square.set(1, 0, 2.0);
        square.set(1, 1, 6.0);

        DoubleMatrix inv = DoubleMatrix.inverse(square);

        assertEquals(2, inv.getRows());
        assertEquals(2, inv.getCols(0));

        // Verify M × M⁻¹ ≈ I
        DoubleMatrix identity = DoubleMatrix.multiply(square, inv);
        assertEquals(1.0, identity.get(0, 0), 1e-8);
        assertEquals(0.0, identity.get(0, 1), 1e-8);
        assertEquals(0.0, identity.get(1, 0), 1e-8);
        assertEquals(1.0, identity.get(1, 1), 1e-8);
    }

    @Test
    public void testEquals() {
        DoubleMatrix m1 = new DoubleMatrix(2, 3);
        DoubleMatrix m2 = new DoubleMatrix(2, 3);

        assertEquals(m1, m2); // Both empty matrices

        m1.set(0, 0, 1.0);
        assertNotEquals(m1, m2);
    }

    @Test
    public void testHashCode() {
        DoubleMatrix m1 = new DoubleMatrix(2, 3);
        DoubleMatrix m2 = new DoubleMatrix(2, 3);

        assertEquals(m1.hashCode(), m2.hashCode());
    }
}