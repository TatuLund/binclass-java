/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.data;

/**
 * A jagged 2D matrix of {@code int} values, mirroring the C {@code IntMatrix}
 * type from {@code vectors.c}.
 * <p>
 * Each row can have a different length (jagged array), matching the original C
 * implementation. Used for frequency tables, co-occurrence matrices, and other
 * integer-valued data structures.
 * </p>
 */
public final class IntMatrix {

    protected int[][] el;
    private int s; // number of rows

    /**
     * Creates a new {@code IntMatrix} with the given dimensions.
     *
     * @param rows
     *            number of rows
     * @param cols
     *            number of columns
     */
    public IntMatrix(int rows, int cols) {
        if (rows < 0 || cols < 0) {
            throw new IllegalArgumentException(
                    "Matrix dimensions must be non-negative");
        }
        this.el = new int[rows][cols];
        this.s = rows;
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
    public int get(int row, int col) {
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
    public void set(int row, int col, int value) {
        checkRow(row);
        el[row][col] = value;
    }

    /**
     * Returns a copy of the specified row.
     *
     * @param row
     *            zero-based row index
     * @return a new array containing the row values
     */
    public int[] getRow(int row) {
        checkRow(row);
        return el[row].clone();
    }

    /**
     * Returns a column as an array.
     *
     * @param col
     *            zero-based column index
     * @return a new array containing the column values
     */
    public int[] getColumn(int col) {
        int rows = getRows();
        int[] result = new int[rows];
        for (int i = 0; i < rows; i++) {
            checkRow(i);
            if (col >= el[i].length) {
                throw new IndexOutOfBoundsException(
                        "Column " + col + " out of bounds for row " + i
                                + " with length " + el[i].length);
            }
            result[i] = el[i][col];
        }
        return result;
    }

    /**
     * Sets all values in a row.
     *
     * @param row
     *            zero-based row index
     * @param values
     *            the new values for the row (must match row length)
     */
    public void setRow(int row, int[] values) {
        checkRow(row);
        if (values.length != el[row].length) {
            throw new IllegalArgumentException("Value array length ("
                    + values.length + ") doesn't match row length ("
                    + el[row].length + ")");
        }
        System.arraycopy(values, 0, el[row], 0, values.length);
    }

    /**
     * Sets all values in a column.
     *
     * @param col
     *            zero-based column index
     * @param values
     *            the new values for the column (must match row count)
     */
    public void setColumn(int col, int[] values) {
        if (values.length != getRows()) {
            throw new IllegalArgumentException(
                    "Value array length (" + values.length
                            + ") doesn't match row count (" + getRows() + ")");
        }
        for (int i = 0; i < getRows(); i++) {
            checkRow(i);
            if (col >= el[i].length) {
                throw new IndexOutOfBoundsException(
                        "Column " + col + " out of bounds for row " + i
                                + " with length " + el[i].length);
            }
            el[i][col] = values[i];
        }
    }

    /**
     * Returns the underlying 2D array. Use with caution — direct mutation
     * bypasses bounds checking.
     *
     * @return the internal storage array
     */
    public int[][] getArray() {
        return el;
    }

    private void checkRow(int row) {
        if (row < 0 || row >= s) {
            throw new IndexOutOfBoundsException("Row " + row
                    + " out of bounds for matrix with " + s + " rows");
        }
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
                sb.append(el[i][j]);
            }
            sb.append("]");
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof IntMatrix))
            return false;

        IntMatrix other = (IntMatrix) o;
        if (s != other.s)
            return false;

        for (int i = 0; i < s; i++) {
            if (el[i].length != other.el[i].length)
                return false;
            for (int j = 0; j < el[i].length; j++) {
                if (el[i][j] != other.el[i][j])
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
                result = 31 * result + el[i][j];
            }
        }
        return result;
    }

}
