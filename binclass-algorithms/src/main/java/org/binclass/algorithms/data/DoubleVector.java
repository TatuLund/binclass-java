/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.data;

import java.util.Arrays;
import java.util.Objects;

/**
 * A dynamic array of {@code double} values, mirroring the C {@code Vector} type
 * from {@code vectors.c}.
 * <p>
 * Unlike the original C implementation which uses explicit
 * allocation/deallocation, this class relies on Java's garbage collector for
 * memory management. The internal storage is a standard Java {@code double[]}
 * array with 0-based indexing (the C version used 1-based indexing).
 * </p>
 * <p>
 * This type is used throughout the algorithm suite for probability
 * distributions, frequency vectors, and intermediate computation results.
 * </p>
 */
public final class DoubleVector {

    private double[] el;
    private int l; // logical length (number of valid elements)

    /**
     * Creates a new empty {@code DoubleVector} with the given capacity.
     *
     * @param n
     *            the number of elements to allocate; must be non-negative
     */
    public DoubleVector(int n) {
        if (n < 0) {
            throw new IllegalArgumentException(
                    "Vector size must be non-negative, got: " + n);
        }
        this.el = new double[n];
        this.l = n;
    }

    /**
     * Creates a new {@code DoubleVector} from an existing array of values.
     * <p>
     * This is a convenience constructor that copies the provided array so that
     * subsequent modifications to the original array do not affect this vector.
     * </p>
     *
     * @param data
     *            the initial values for the vector; must not be {@code null}
     */
    public DoubleVector(double[] data) {
        Objects.requireNonNull(data, "Data array must not be null");
        this.el = Arrays.copyOf(data, data.length);
        this.l = data.length;
    }

    /**
     * Returns the logical length of this vector.
     *
     * @return number of elements in the vector
     */
    public int getLength() {
        return l;
    }

    /**
     * Gets the element at the given index (0-based).
     *
     * @param i
     *            the zero-based index
     * @return the value at that index
     * @throws IndexOutOfBoundsException
     *             if {@code i < 0} or {@code i >= l}
     */
    public double get(int i) {
        checkIndex(i);
        return el[i];
    }

    /**
     * Sets the element at the given index (0-based).
     *
     * @param i
     *            the zero-based index
     * @param value
     *            the value to set
     * @throws IndexOutOfBoundsException
     *             if {@code i < 0} or {@code i >= l}
     */
    public void set(int i, double value) {
        checkIndex(i);
        el[i] = value;
    }

    /**
     * Gets the element at the given index (1-based), matching the original C
     * convention.
     * <p>
     * The C code uses 1-based indexing for vectors. This method provides
     * compatibility by adding 1 to the provided index before accessing the
     * internal array.
     * </p>
     *
     * @param i
     *            the one-based index (1..l)
     * @return the value at that position
     * @throws IndexOutOfBoundsException
     *             if {@code i < 1} or {@code i > l}
     */
    public double getOneBased(int i) {
        return el[i - 1]; // Convert 1-based to 0-based
    }

    /**
     * Sets the element at the given index (1-based), matching the original C
     * convention.
     *
     * @param i
     *            the one-based index (1..l)
     * @param value
     *            the value to set
     * @throws IndexOutOfBoundsException
     *             if {@code i < 1} or {@code i > l}
     */
    public void setOneBased(int i, double value) {
        el[i - 1] = value;
    }

    /**
     * Returns the underlying array. Use with caution — direct mutation bypasses
     * bounds checking.
     *
     * @return the internal storage array
     */
    public double[] getArray() {
        return el;
    }

    private void checkIndex(int i) {
        if (i < 0 || i >= l) {
            throw new IndexOutOfBoundsException(
                    "Index " + i + " out of bounds for length " + l);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < l; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(el[i]);
        }
        return sb.append("]").toString();
    }

}
