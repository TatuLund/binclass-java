package org.binclass.algorithms.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for
 * {@link DoubleMatrix#matrixVectorMultiply(DoubleMatrix, DoubleVector)}.
 */
class MatrixVectorMultiplyTest {

    @Test
    void testBasicMultiplication() {
        // Create a 2x3 matrix: [[1, 2], [3, 4]]
        double[][] data = { { 1.0, 2.0 }, { 3.0, 4.0 } };
        DoubleMatrix m = new DoubleMatrix(data);

        // Vector [5, 6]
        double[] vData = { 5.0, 6.0 };
        DoubleVector v = new DoubleVector(vData);

        // Result should be: [[1*5 + 2*6], [3*5 + 4*6]] = [[17], [39]]
        DoubleVector result = m.matrixVectorMultiply(v);

        assertEquals(17.0, result.get(0), 1e-10);
        assertEquals(39.0, result.get(1), 1e-10);
    }

    @Test
    void testIdentityMatrixMultiplication() {
        // Create a 2x2 identity matrix: [[1, 0], [0, 1]]
        double[][] data = { { 1.0, 0.0 }, { 0.0, 1.0 } };
        DoubleMatrix m = new DoubleMatrix(data);

        // Vector [7, 8]
        double[] vData = { 7.0, 8.0 };
        DoubleVector v = new DoubleVector(vData);

        // Result should be: [[1*7 + 0*8], [0*7 + 1*8]] = [[7], [8]] (unchanged)
        DoubleVector result = m.matrixVectorMultiply(v);

        assertEquals(7.0, result.get(0), 1e-10);
        assertEquals(8.0, result.get(1), 1e-10);
    }

    @Test
    void testZeroMatrixMultiplication() {
        // Create a zero matrix: [[0, 0], [0, 0]]
        double[][] data = { { 0.0, 0.0 }, { 0.0, 0.0 } };
        DoubleMatrix m = new DoubleMatrix(data);

        // Vector [3, 4]
        double[] vData = { 3.0, 4.0 };
        DoubleVector v = new DoubleVector(vData);

        // Result should be: [[0*3 + 0*4], [0*3 + 0*4]] = [[0], [0]]
        DoubleVector result = m.matrixVectorMultiply(v);

        assertEquals(0.0, result.get(0), 1e-10);
        assertEquals(0.0, result.get(1), 1e-10);
    }

    @Test
    void testSingleRowMatrix() {
        // Create a single row matrix: [[2, 3]]
        double[][] data = { { 2.0, 3.0 } };
        DoubleMatrix m = new DoubleMatrix(data);

        // Vector [4, 5]
        double[] vData = { 4.0, 5.0 };
        DoubleVector v = new DoubleVector(vData);

        // Result should be: [[2*4 + 3*5]] = [[23]] (single element)
        DoubleVector result = m.matrixVectorMultiply(v);

        assertEquals(1, result.getLength());
        assertEquals(23.0, result.get(0), 1e-10);
    }

    @Test
    void testSingleColumnMatrix() {
        // Create a single column matrix: [[5], [6]]
        double[][] data = { { 5.0 }, { 6.0 } };
        DoubleMatrix m = new DoubleMatrix(data);

        // Vector [7]
        double[] vData = { 7.0 };
        DoubleVector v = new DoubleVector(vData);

        // Result should be: [[5*7], [6*7]] = [[35], [42]]
        DoubleVector result = m.matrixVectorMultiply(v);

        assertEquals(35.0, result.get(0), 1e-10);
        assertEquals(42.0, result.get(1), 1e-10);
    }

    @org.junit.jupiter.api.Test
    void testDimensionMismatch() {
        // Create a 2x3 matrix: [[1, 2], [3, 4]] (only needs first row for this
        // example)
        double[][] data = { { 1.0, 2.0 }, { 3.0, 4.0 } };
        DoubleMatrix m = new DoubleMatrix(data);

        // Vector with wrong length: only 1 element instead of 2
        double[] vData = { 5.0 };
        DoubleVector v = new DoubleVector(vData);

        assertThrows(IllegalArgumentException.class, () -> {
            m.matrixVectorMultiply(v);
        });
    }

    @Test
    void testNegativeValues() {
        // Create a matrix with negative values: [[-1, 2], [3, -4]]
        double[][] data = { { -1.0, 2.0 }, { 3.0, -4.0 } };
        DoubleMatrix m = new DoubleMatrix(data);

        // Vector [-5, 6]
        double[] vData = { -5.0, 6.0 };
        DoubleVector v = new DoubleVector(vData);

        // Result should be: [[-1*-5 + 2*6], [3*-5 + -4*6]] = [[17], [-39]]
        DoubleVector result = m.matrixVectorMultiply(v);

        assertEquals(17.0, result.get(0), 1e-10);
        assertEquals(-39.0, result.get(1), 1e-10);
    }

    @Test
    void testLargeMatrix() {
        // Create a larger matrix (4x4) with known values
        double[][] data = { { 1.0, 2.0, 3.0, 4.0 },
                { 5.0, 6.0, 7.0, 8.0 },
                { 9.0, 10.0, 11.0, 12.0 },
                { 13.0, 14.0, 15.0, 16.0 } };
        DoubleMatrix m = new DoubleMatrix(data);

        // Vector [1, 1, 1, 1] (sum of each row)
        double[] vData = { 1.0, 1.0, 1.0, 1.0 };
        DoubleVector v = new DoubleVector(vData);

        // Result should be: [[1+2+3+4], [5+6+7+8], ...] = [[10], [26], [42],
        // [58]]
        DoubleVector result = m.matrixVectorMultiply(v);

        assertEquals(10.0, result.get(0), 1e-10);
        assertEquals(26.0, result.get(1), 1e-10);
        assertEquals(42.0, result.get(2), 1e-10);
        assertEquals(58.0, result.get(3), 1e-10);
    }

    @Test
    void testResultLength() {
        // Create a matrix with different row/column dimensions
        double[][] data = { { 1.0, 2.0 }, { 3.0, 4.0 } };
        DoubleMatrix m = new DoubleMatrix(data);

        // Vector [5, 6] (length matches columns)
        double[] vData = { 5.0, 6.0 };
        DoubleVector v = new DoubleVector(vData);

        DoubleVector result = m.matrixVectorMultiply(v);

        assertEquals(2, result.getLength());
    }

}