/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.data;

/**
 * A dynamic array of {@code int} values, mirroring the C {@code IntVector} type
 * from {@code vectors.c}.
 * <p>
 * Used for frequency counts, Hamming masks, co-occurrence tracking, and other
 * integer-valued data structures throughout the algorithm suite. Relies on
 * Java's garbage collector for memory management with 0-based internal indexing
 * (1-based accessor methods available for C compatibility).
 * </p>
 */
public final class IntVector {

    private int[] el;
    private int l; // logical length

    /**
     * Creates a new empty {@code IntVector} with the given capacity.
     *
     * @param n
     *            the number of elements to allocate; must be non-negative
     */
    public IntVector(int n) {
        if (n < 0) {
            throw new IllegalArgumentException(
                    "Vector size must be non-negative, got: " + n);
        }
        this.el = new int[n];
        this.l = n;
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
    public int get(int i) {
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
    public void set(int i, int value) {
        checkIndex(i);
        el[i] = value;
    }

    /**
     * Gets the element at the given index (1-based), matching the original C
     * convention.
     *
     * @param i
     *            the one-based index (1..l)
     * @return the value at that position
     * @throws IndexOutOfBoundsException
     *             if {@code i < 1} or {@code i > l}
     */
    public int getOneBased(int i) {
        return el[i - 1];
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
    public void setOneBased(int i, int value) {
        el[i - 1] = value;
    }

    /**
     * Returns the underlying array. Use with caution — direct mutation bypasses
     * bounds checking.
     *
     * @return the internal storage array
     */
    public int[] getArray() {
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
