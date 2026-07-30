/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.data;

import java.util.Arrays;
import java.util.Objects;

/**
 * A jagged 2D matrix of {@code double} values, mirroring the C {@code Matrix}
 * type from {@code vectors.c}.
 * <p>
 * Each row can have a different length (jagged array), matching the original C
 * implementation. All operations use Java native {@code double} precision (IEEE
 * 754).
 * </p>
 * <p>
 * Provides static utility methods for matrix operations: transpose, multiply,
 * inverse, pseudo-inverse, and matrix-vector multiplication. These are pure
 * functions with no side effects.
 * </p>
 */
public final class DoubleMatrix {

    public double[][] el;
    private int s; // number of rows (stored as "s" to match C naming)

    /**
     * Creates a new {@code DoubleMatrix} with the given dimensions.
     *
     * @param rows
     *            number of rows
     * @param cols
     *            number of columns
     */
    public DoubleMatrix(int rows, int cols) {
        if (rows < 0 || cols < 0) {
            throw new IllegalArgumentException(
                    "Matrix dimensions must be non-negative");
        }
        this.el = new double[rows][cols];
        this.s = rows;
    }

    /**
     * Creates a new {@code DoubleMatrix} from an existing 2D array of values.
     *
     * @param data
     *            the initial values for the matrix; must not be {@code null}
     */
    public DoubleMatrix(double[][] data) {
        Objects.requireNonNull(data, "Data array must not be null");
        this.el = new double[data.length][];
        this.s = data.length;
        for (int i = 0; i < data.length; i++) {
            this.el[i] = Arrays.copyOf(data[i], data[i].length);
        }
    }

    /**
     * Returns the number of rows in this matrix.
     *
     * @return row count
     */
    public int getRows() {
        return s;
    }

    /**
     * Returns the number of columns in a specific row.
     * <p>
     * Note: In a jagged array, different rows may have different column counts.
     * </p>
     *
     * @param rowIndex
     *            the zero-based row index
     * @return number of columns in that row
     */
    public int getCols(int rowIndex) {
        checkRow(rowIndex);
        return el[rowIndex].length;
    }

    /**
     * Gets the element at the given position.
     *
     * @param row
     *            zero-based row index
     * @param col
     *            zero-based column index
     * @return the value at that position
     */
    public double get(int row, int col) {
        checkRow(row);
        return el[row][col];
    }

    /**
     * Sets the element at the given position.
     *
     * @param row
     *            zero-based row index
     * @param col
     *            zero-based column index
     * @param value
     *            the value to set
     */
    public void set(int row, int col, double value) {
        checkRow(row);
        el[row][col] = value;
    }

    /**
     * Returns the underlying 2D array. Use with caution — direct mutation
     * bypasses bounds checking.
     *
     * @return the internal storage array
     */
    public double[][] getArray() {
        return el;
    }

    private void checkRow(int row) {
        if (row < 0 || row >= s) {
            throw new IndexOutOfBoundsException("Row " + row
                    + " out of bounds for matrix with " + s + " rows");
        }
    }

    // --- Static utility methods (mirroring C functions from vectors.c) ---

    /**
     * Computes the transpose of a matrix.
     * <p>
     * Equivalent to C function {@code matrix_transpose()} from
     * {@code vectors.c}.
     * </p>
     *
     * @param m
     *            the input matrix
     * @return the transposed matrix (rows become columns)
     */
    public static DoubleMatrix transpose(DoubleMatrix m) {
        Objects.requireNonNull(m, "Input matrix must not be null");

        int rows = m.getRows();
        int cols = m.getCols(0); // Assume uniform column count for transpose

        DoubleMatrix result = new DoubleMatrix(cols, rows);
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                result.set(j, i, m.get(i, j));
            }
        }
        return result;
    }

    /**
     * Multiplies two matrices: {@code M1 × M2}.
     * <p>
     * Equivalent to C function {@code matrix_multiply()} from
     * {@code vectors.c}.
     * </p>
     *
     * @param m1
     *            left operand (rows × k)
     * @param m2
     *            right operand (k × cols)
     * @return the product matrix (rows × cols)
     * @throws IllegalArgumentException
     *             if inner dimensions don't match
     */
    public static DoubleMatrix multiply(DoubleMatrix m1, DoubleMatrix m2) {
        Objects.requireNonNull(m1, "First matrix must not be null");
        Objects.requireNonNull(m2, "Second matrix must not be null");

        int rows1 = m1.getRows();
        int cols1 = m1.getCols(0);
        int rows2 = m2.getRows();
        int cols2 = m2.getCols(0);

        if (cols1 != rows2) {
            throw new IllegalArgumentException(
                    "Matrix dimensions incompatible for multiplication: " +
                            "(" + rows1 + "×" + cols1 + ") × (" + rows2 + "×"
                            + cols2 + ")");
        }

        DoubleMatrix result = new DoubleMatrix(rows1, cols2);
        for (int i = 0; i < rows1; i++) {
            for (int j = 0; j < cols2; j++) {
                double sum = 0.0;
                for (int k = 0; k < cols1; k++) {
                    sum += m1.get(i, k) * m2.get(k, j);
                }
                result.set(i, j, sum);
            }
        }
        return result;
    }

    /**
     * Multiplies a matrix by a vector: {@code M × v}.
     * <p>
     * Equivalent to C function {@code matrix_vector_multiply()} from
     * {@code vectors.c}.
     * </p>
     *
     * @param v
     *            the vector (length must equal cols)
     * @return the resulting vector (length = rows)
     */
    public DoubleVector matrixVectorMultiply(DoubleVector v) {
        Objects.requireNonNull(v, "Vector must not be null");

        int rows = getRows();
        int cols = getCols(0);

        if (v.getLength() != cols) {
            throw new IllegalArgumentException("Vector length (" + v.getLength()
                    + ") doesn't match matrix columns (" + cols + ")");
        }

        DoubleVector result = new DoubleVector(rows);
        for (int i = 0; i < rows; i++) {
            double sum = 0.0;
            for (int j = 0; j < cols; j++) {
                sum += get(i, j) * v.get(j);
            }
            result.set(i, sum);
        }
        return result;
    }

    /**
     * Computes the inverse of a square matrix using Gauss-Jordan elimination.
     * <p>
     * Equivalent to C function {@code matrix_inverse()} from {@code vectors.c}.
     * </p>
     *
     * @param m
     *            the input square matrix
     * @return the inverse matrix
     * @throws IllegalArgumentException
     *             if matrix is not square or singular
     */
    public static DoubleMatrix inverse(DoubleMatrix m) {
        Objects.requireNonNull(m, "Input matrix must not be null");

        int n = m.getRows();
        int cols0 = m.getCols(0);
        if (n != cols0) {
            throw new IllegalArgumentException(
                    "Matrix must be square for inversion: " + n + "×" + cols0);
        }

        // Create augmented matrix [M | I]
        DoubleMatrix aug = new DoubleMatrix(n, 2 * n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                aug.set(i, j, m.get(i, j));
            }
            aug.set(i, n + i, 1.0); // Identity matrix on right side
        }

        // Gauss-Jordan elimination
        for (int i = 0; i < n; i++) {
            // Find pivot
            double maxVal = Math.abs(aug.get(i, i));
            int maxRow = i;
            for (int k = i + 1; k < n; k++) {
                if (Math.abs(aug.get(k, i)) > maxVal) {
                    maxVal = Math.abs(aug.get(k, i));
                    maxRow = k;
                }
            }

            // Swap rows
            if (maxRow != i) {
                for (int j = 0; j < 2 * n; j++) {
                    double temp = aug.get(i, j);
                    aug.set(i, j, aug.get(maxRow, j));
                    aug.set(maxRow, j, temp);
                }
            }

            // Check for singular matrix
            if (Math.abs(aug.get(i, i)) < 1e-12) {
                throw new IllegalArgumentException(
                        "Matrix is singular (near-zero pivot at row " + i
                                + ")");
            }

            // Scale pivot row
            double pivot = aug.get(i, i);
            for (int j = 0; j < 2 * n; j++) {
                aug.set(i, j, aug.get(i, j) / pivot);
            }

            // Eliminate column
            for (int k = 0; k < n; k++) {
                if (k != i) {
                    double factor = aug.get(k, i);
                    for (int j = 0; j < 2 * n; j++) {
                        aug.set(k, j, aug.get(k, j) - factor * aug.get(i, j));
                    }
                }
            }
        }

        // Extract inverse from right side of augmented matrix
        DoubleMatrix result = new DoubleMatrix(n, n);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                result.set(i, j, aug.get(i, n + j));
            }
        }
        return result;
    }

    /**
     * Computes the pseudo-inverse (Moore-Penrose) of a matrix using SVD-like
     * approach.
     * <p>
     * For simplicity, uses the formula: {@code A⁺ = (AᵀA)⁻¹Aᵀ} for full-rank
     * matrices. Equivalent to C function {@code matrix_pseudo_inverse()} from
     * {@code vectors.c}.
     * </p>
     *
     * @param m
     *            the input matrix
     * @return the pseudo-inverse matrix
     */
    public static DoubleMatrix pseudoInverse(DoubleMatrix m) {
        Objects.requireNonNull(m, "Input matrix must not be null");

        // A⁺ = (AᵀA)⁻¹Aᵀ for full-rank matrices
        DoubleMatrix at = transpose(m);
        DoubleMatrix ata = multiply(at, m);
        DoubleMatrix ataInv = inverse(ata);
        return multiply(ataInv, at);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s; i++) {
            if (i > 0)
                sb.append("\n");
            sb.append("[");
            for (int j = 0; j < el[i].length; j++) {
                if (j > 0)
                    sb.append(", ");
                sb.append(String.format("%8.4f", el[i][j]));
            }
            sb.append("]");
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof DoubleMatrix))
            return false;

        DoubleMatrix other = (DoubleMatrix) o;
        if (s != other.s)
            return false;

        for (int i = 0; i < s; i++) {
            if (el[i].length != other.el[i].length)
                return false;
            for (int j = 0; j < el[i].length; j++) {
                if (Double.compare(el[i][j], other.el[i][j]) != 0)
                    return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = s;
        for (int i = 0; i < s; i++) {
            result = 31 * result + el[i].length;
            for (int j = 0; j < el[i].length; j++) {
                long bits = Double.doubleToLongBits(el[i][j]);
                result = 31 * result + (int) (bits ^ (bits >>> 32));
            }
        }
        return result;
    }
}
