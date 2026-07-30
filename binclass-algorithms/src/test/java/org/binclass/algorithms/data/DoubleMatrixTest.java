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

    // --- matrixVectorMultiply tests ---

    @Test
    public void testMatrixVectorMultiply_basic() {
        DoubleMatrix m = new DoubleMatrix(2, 3);
        m.set(0, 0, 1.0);
        m.set(0, 1, 2.0);
        m.set(0, 2, 3.0);
        m.set(1, 0, 4.0);
        m.set(1, 1, 5.0);
        m.set(1, 2, 6.0);

        DoubleVector v = new DoubleVector(new double[] { 7.0, 8.0, 9.0 });
        DoubleVector result = m.matrixVectorMultiply(v);

        assertEquals(50.0, result.get(0), 1e-10); // 1*7 + 2*8 + 3*9
        assertEquals(122.0, result.get(1), 1e-10); // 4*7 + 5*8 + 6*9
    }

    @Test
    public void testMatrixVectorMultiply_singleRow() {
        DoubleMatrix m = new DoubleMatrix(1, 3);
        m.set(0, 0, 2.0);
        m.set(0, 1, 3.0);
        m.set(0, 2, 4.0);

        DoubleVector v = new DoubleVector(new double[] { 5.0, 6.0, 7.0 });
        DoubleVector result = m.matrixVectorMultiply(v);

        assertEquals(1.0 * (2 * 5 + 3 * 6 + 4 * 7), result.get(0), 1e-10); // 58
    }

    @Test
    public void testMatrixVectorMultiply_singleColumn() {
        DoubleMatrix m = new DoubleMatrix(3, 1);
        m.set(0, 0, 2.0);
        m.set(1, 0, 3.0);
        m.set(2, 0, 4.0);

        DoubleVector v = new DoubleVector(new double[] { 5.0 });
        DoubleVector result = m.matrixVectorMultiply(v);

        assertEquals(10.0, result.get(0), 1e-10); // 2*5
        assertEquals(15.0, result.get(1), 1e-10); // 3*5
        assertEquals(20.0, result.get(2), 1e-10); // 4*5
    }

    @Test
    public void testMatrixVectorMultiply_zeroVector() {
        DoubleMatrix m = new DoubleMatrix(2, 2);
        m.set(0, 0, 1.0);
        m.set(0, 1, 2.0);
        m.set(1, 0, 3.0);
        m.set(1, 1, 4.0);

        DoubleVector v = new DoubleVector(new double[] { 0.0, 0.0 });
        DoubleVector result = m.matrixVectorMultiply(v);

        assertEquals(0.0, result.get(0), 1e-10); // 1*0 + 2*0
        assertEquals(0.0, result.get(1), 1e-10); // 3*0 + 4*0
    }

    @Test
    public void testMatrixVectorMultiply_negativeValues() {
        DoubleMatrix m = new DoubleMatrix(2, 2);
        m.set(0, 0, -1.0);
        m.set(0, 1, 3.0);
        m.set(1, 0, 5.0);
        m.set(1, 1, -2.0);

        DoubleVector v = new DoubleVector(new double[] { 4.0, -6.0 });
        DoubleVector result = m.matrixVectorMultiply(v);

        assertEquals(-22.0, result.get(0), 1e-10); // (-1)*4 + 3*(-6)
        assertEquals(32.0, result.get(1), 1e-10); // 5*4 + (-2)*(-6)
    }

    @Test
    public void testMatrixVectorMultiply_dimensionMismatch() {
        DoubleMatrix m = new DoubleMatrix(2, 3);
        m.set(0, 0, 1.0);
        m.set(0, 1, 2.0);
        m.set(0, 2, 3.0);
        m.set(1, 0, 4.0);
        m.set(1, 1, 5.0);
        m.set(1, 2, 6.0);

        DoubleVector v = new DoubleVector(new double[] { 7.0, 8.0 }); // only 2
                                                                      // elements
        assertThrows(IllegalArgumentException.class, () -> {
            m.matrixVectorMultiply(v);
        });
    }

    @Test
    public void testMatrixVectorMultiply_nullVector() {
        DoubleMatrix m = new DoubleMatrix(2, 3);
        assertThrows(NullPointerException.class, () -> {
            m.matrixVectorMultiply(null);
        });
    }

    @Test
    public void testMatrixVectorMultiply_identityLikeResult() {
        // Multiplying by identity matrix should return the same vector
        DoubleMatrix I = new DoubleMatrix(3, 3);
        I.set(0, 0, 1.0);
        I.set(1, 1, 1.0);
        I.set(2, 2, 1.0);

        DoubleVector v = new DoubleVector(new double[] { 5.0, -3.0, 7.0 });
        DoubleVector result = I.matrixVectorMultiply(v);

        assertEquals(5.0, result.get(0), 1e-10);
        assertEquals(-3.0, result.get(1), 1e-10);
        assertEquals(7.0, result.get(2), 1e-10);
    }

    // --- pseudoInverse tests ---

    @Test
    public void testPseudoInverse_squareMatrix() {
        // For a square invertible matrix, pseudo-inverse should equal inverse
        DoubleMatrix m = new DoubleMatrix(3, 3);
        m.set(0, 0, 1.0);
        m.set(0, 1, 2.0);
        m.set(0, 2, 3.0);
        m.set(1, 0, 0.0);
        m.set(1, 1, 4.0);
        m.set(1, 2, 5.0);
        m.set(2, 0, 1.0);
        m.set(2, 1, 0.0);
        m.set(2, 2, 6.0);

        DoubleMatrix pinv = DoubleMatrix.pseudoInverse(m);

        assertEquals(3, pinv.getRows());
        assertEquals(3, pinv.getCols(0));

        // Verify M × M⁺ ≈ I for square full-rank matrices
        DoubleMatrix product = DoubleMatrix.multiply(m, pinv);
        assertEquals(1.0, product.get(0, 0), 1e-8);
        assertEquals(0.0, product.get(0, 1), 1e-8);
        assertEquals(0.0, product.get(0, 2), 1e-8);
        assertEquals(0.0, product.get(1, 0), 1e-8);
        assertEquals(1.0, product.get(1, 1), 1e-8);
        assertEquals(0.0, product.get(1, 2), 1e-8);
        assertEquals(0.0, product.get(2, 0), 1e-8);
        assertEquals(0.0, product.get(2, 1), 1e-8);
        assertEquals(1.0, product.get(2, 2), 1e-8);
    }

    @Test
    public void testPseudoInverse_tallMatrix() {
        // Tall matrix (more rows than columns): A is 4×3, full column-rank
        DoubleMatrix m = new DoubleMatrix(4, 3);
        m.set(0, 0, 1.0);
        m.set(0, 1, 2.0);
        m.set(0, 2, 3.0);
        m.set(1, 0, 4.0);
        m.set(1, 1, 5.0);
        m.set(1, 2, 6.0);
        m.set(2, 0, 7.0);
        m.set(2, 1, 8.0);
        m.set(2, 2, 10.0); // Changed from 9 to break linear dependency
        m.set(3, 0, 11.0);
        m.set(3, 1, 12.0);
        m.set(3, 2, 14.0);

        DoubleMatrix pinv = DoubleMatrix.pseudoInverse(m);

        assertEquals(3, pinv.getRows());
        assertEquals(4, pinv.getCols(0));

        // For tall full-rank matrix: A⁺A ≈ I (left pseudo-inverse)
        DoubleMatrix product = DoubleMatrix.multiply(pinv, m);
        assertEquals(1.0, product.get(0, 0), 1e-8);
        assertEquals(0.0, product.get(0, 1), 1e-8);
        assertEquals(0.0, product.get(0, 2), 1e-8);
        assertEquals(0.0, product.get(1, 0), 1e-8);
        assertEquals(1.0, product.get(1, 1), 1e-8);
        assertEquals(0.0, product.get(1, 2), 1e-8);
        assertEquals(0.0, product.get(2, 0), 1e-8);
        assertEquals(0.0, product.get(2, 1), 1e-8);
        assertEquals(1.0, product.get(2, 2), 1e-8);
    }

    @Test
    public void testPseudoInverse_wideMatrix() {
        // Wide matrix (more columns than rows): A is 3×4, full row-rank
        DoubleMatrix m = new DoubleMatrix(3, 4);
        m.set(0, 0, 1.0);
        m.set(0, 1, 2.0);
        m.set(0, 2, 3.0);
        m.set(0, 3, 5.0); // Changed from 4 to break linear dependency
        m.set(1, 0, 6.0);
        m.set(1, 1, 7.0);
        m.set(1, 2, 8.0);
        m.set(1, 3, 9.0);
        m.set(2, 0, 10.0);
        m.set(2, 1, 11.0);
        m.set(2, 2, 13.0); // Changed from 12 to break linear dependency
        m.set(2, 3, 14.0);

        assertThrows(IllegalArgumentException.class, () -> {
            DoubleMatrix.pseudoInverse(m);
        });
    }

    @Test
    public void testPseudoInverse_identityMatrix() {
        // Pseudo-inverse of identity should be identity itself
        DoubleMatrix I = new DoubleMatrix(3, 3);
        I.set(0, 0, 1.0);
        I.set(1, 1, 1.0);
        I.set(2, 2, 1.0);

        DoubleMatrix pinv = DoubleMatrix.pseudoInverse(I);

        assertEquals(3, pinv.getRows());
        assertEquals(3, pinv.getCols(0));
        assertEquals(1.0, pinv.get(0, 0), 1e-10);
        assertEquals(0.0, pinv.get(0, 1), 1e-10);
        assertEquals(0.0, pinv.get(0, 2), 1e-10);
        assertEquals(0.0, pinv.get(1, 0), 1e-10);
        assertEquals(1.0, pinv.get(1, 1), 1e-10);
        assertEquals(0.0, pinv.get(1, 2), 1e-10);
        assertEquals(0.0, pinv.get(2, 0), 1e-10);
        assertEquals(0.0, pinv.get(2, 1), 1e-10);
        assertEquals(1.0, pinv.get(2, 2), 1e-10);
    }

    @Test
    public void testPseudoInverse_nullInput() {
        assertThrows(NullPointerException.class, () -> {
            DoubleMatrix.pseudoInverse(null);
        });
    }

    @Test
    public void testPseudoInverse_2x2Matrix() {
        // Simple 2×2 case for easy manual verification
        DoubleMatrix m = new DoubleMatrix(2, 2);
        m.set(0, 0, 1.0);
        m.set(0, 1, 2.0);
        m.set(1, 0, 3.0);
        m.set(1, 1, 4.0);

        DoubleMatrix pinv = DoubleMatrix.pseudoInverse(m);

        assertEquals(2, pinv.getRows());
        assertEquals(2, pinv.getCols(0));

        // Verify M × M⁺ ≈ I
        DoubleMatrix product = DoubleMatrix.multiply(m, pinv);
        assertEquals(1.0, product.get(0, 0), 1e-8);
        assertEquals(0.0, product.get(0, 1), 1e-8);
        assertEquals(0.0, product.get(1, 0), 1e-8);
        assertEquals(1.0, product.get(1, 1), 1e-8);
    }

    @Test
    public void testPseudoInverse_rectangularTall() {
        // Another tall matrix (5×2) to further validate the implementation
        DoubleMatrix m = new DoubleMatrix(5, 2);
        m.set(0, 0, 1.0);
        m.set(0, 1, 0.0);
        m.set(1, 0, 0.0);
        m.set(1, 1, 1.0);
        m.set(2, 0, 1.0);
        m.set(2, 1, 1.0);
        m.set(3, 0, 2.0);
        m.set(3, 1, 0.0);
        m.set(4, 0, 0.0);
        m.set(4, 1, 3.0);

        DoubleMatrix pinv = DoubleMatrix.pseudoInverse(m);

        assertEquals(2, pinv.getRows());
        assertEquals(5, pinv.getCols(0));

        // A⁺A should approximate identity for full column-rank matrices
        DoubleMatrix product = DoubleMatrix.multiply(pinv, m);
        assertEquals(1.0, product.get(0, 0), 1e-8);
        assertEquals(0.0, product.get(0, 1), 1e-8);
        assertEquals(0.0, product.get(1, 0), 1e-8);
        assertEquals(1.0, product.get(1, 1), 1e-8);
    }

    @Test
    public void testPseudoInverse_negativeValues() {
        // Matrix with negative values to ensure correctness
        DoubleMatrix m = new DoubleMatrix(3, 3);
        m.set(0, 0, -1.0);
        m.set(0, 1, 2.0);
        m.set(0, 2, 0.0);
        m.set(1, 0, 3.0);
        m.set(1, 1, -4.0);
        m.set(1, 2, 5.0);
        m.set(2, 0, 6.0);
        m.set(2, 1, 7.0);
        m.set(2, 2, -8.0);

        DoubleMatrix pinv = DoubleMatrix.pseudoInverse(m);

        assertEquals(3, pinv.getRows());
        assertEquals(3, pinv.getCols(0));

        // Verify M × M⁺ ≈ I
        DoubleMatrix product = DoubleMatrix.multiply(m, pinv);
        assertEquals(1.0, product.get(0, 0), 1e-8);
        assertEquals(0.0, product.get(0, 1), 1e-8);
        assertEquals(0.0, product.get(0, 2), 1e-8);
        assertEquals(0.0, product.get(1, 0), 1e-8);
        assertEquals(1.0, product.get(1, 1), 1e-8);
        assertEquals(0.0, product.get(1, 2), 1e-8);
        assertEquals(0.0, product.get(2, 0), 1e-8);
        assertEquals(0.0, product.get(2, 1), 1e-8);
        assertEquals(1.0, product.get(2, 2), 1e-8);
    }

    @Test
    public void testPseudoInverse_largeMatrix() {
        // Larger matrix (4×4) to stress-test the implementation
        DoubleMatrix m = new DoubleMatrix(4, 4);
        m.set(0, 0, 2.0);
        m.set(0, 1, -1.0);
        m.set(0, 2, 0.0);
        m.set(0, 3, 0.0);
        m.set(1, 0, -1.0);
        m.set(1, 1, 2.0);
        m.set(1, 2, -1.0);
        m.set(1, 3, 0.0);
        m.set(2, 0, 0.0);
        m.set(2, 1, -1.0);
        m.set(2, 2, 2.0);
        m.set(2, 3, -1.0);
        m.set(3, 0, 0.0);
        m.set(3, 1, 0.0);
        m.set(3, 2, -1.0);
        m.set(3, 3, 2.0);

        DoubleMatrix pinv = DoubleMatrix.pseudoInverse(m);

        assertEquals(4, pinv.getRows());
        assertEquals(4, pinv.getCols(0));

        // Verify M × M⁺ ≈ I
        DoubleMatrix product = DoubleMatrix.multiply(m, pinv);
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                double expected = (i == j) ? 1.0 : 0.0;
                assertEquals(expected, product.get(i, j), 1e-8);
            }
        }
    }

    @Test
    public void testPseudoInverse_singularMatrix() {
        // Singular matrix should throw an exception since (AᵀA) will be
        // singular
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

        assertThrows(IllegalArgumentException.class, () -> {
            DoubleMatrix.pseudoInverse(m);
        });
    }
}