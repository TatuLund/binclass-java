/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.core;

import java.util.Arrays;
import java.util.Objects;

/**
 * An immutable binary vector, mirroring the C {@code BV} struct from
 * {@code binset.h}.
 * <p>
 * Represents a single observation (e.g., a gene expression profile) as a
 * fixed-length array of bits. Immutable by design — once created, its contents
 * cannot change. This matches the original C implementation where binary
 * vectors are typically read-only after parsing.
 * </p>
 * <p>
 * Fields:
 * <ul>
 * <li>{@code el} — bit array (0 or 1 values)</li>
 * <li>{@code miss} — missing value mask (bit i set if position i is
 * missing)</li>
 * <li>{@code length} — number of elements</li>
 * <li>{@code classname} — cluster assignment (0 = unassigned)</li>
 * <li>{@code strain} — identifier string for the observation</li>
 * </ul>
 * </p>
 */
public final class BinaryVector {

    private final int[] el;
    private final int miss; // bitmask: bit i set if position i is missing
    private final int length;
    private int classname;
    private String strain;

    /**
     * Creates a new {@code BinaryVector} with the given data.
     *
     * @param el
     *            the bit values (0 or 1)
     * @param miss
     *            the missing value bitmask
     * @param length
     *            the number of elements
     * @param classname
     *            initial cluster assignment (0 = unassigned)
     * @param strain
     *            identifier string for this vector
     */
    public BinaryVector(int[] el, int miss, int length, int classname,
            String strain) {
        this.el = Arrays.copyOf(el, el.length);
        this.miss = miss;
        this.length = length;
        this.classname = classname;
        this.strain = strain != null ? strain : "";
    }

    /**
     * Creates a new {@code BinaryVector} with no missing values and unassigned.
     *
     * @param el
     *            the bit values (0 or 1)
     * @param length
     *            the number of elements
     */
    public BinaryVector(int[] el, int length) {
        this(el, 0, length, 0, "");
    }

    /**
     * Creates a new {@code BinaryVector} with no missing values and unassigned.
     * Length is inferred from the array (convenience constructor).
     *
     * @param el
     *            the bit values (0 or 1)
     */
    public BinaryVector(int[] el) {
        this(el, el.length);
    }

    /**
     * Returns a copy of the underlying bit array.
     *
     * @return a defensive copy of the internal {@code el} array
     */
    public int[] getEl() {
        return el;
    }

    /**
     * Returns the element at the given index.
     *
     * @param i
     *            zero-based index
     * @return the bit value (0 or 1), or -1 if missing
     */
    public int get(int i) {
        return el[i];
    }

    /**
     * Returns the element at the given index. Alias for {@link #get(int)}.
     *
     * @param i
     *            zero-based index
     * @return the bit value (0 or 1), or -1 if missing
     */
    public int getElement(int i) {
        return el[i];
    }

    /**
     * Returns the length of this vector. Alias for {@link #getLength()}.
     *
     * @return length of the vector
     */
    public int length() {
        return length;
    }

    /**
     * Returns the element at the given index, accounting for missing values.
     * <p>
     * Equivalent to C function {@code bv_get()} from {@code binset.h}.
     * </p>
     *
     * @param i
     *            zero-based index
     * @return the bit value (0 or 1), or -1 if missing at that position
     */
    public int getWithMissing(int i) {
        return (miss & (1 << i)) != 0 ? -1 : el[i];
    }

    /**
     * Returns whether the element at the given index is missing.
     * <p>
     * Equivalent to C function {@code bv_miss()} from {@code binset.h}.
     * </p>
     *
     * @param i
     *            zero-based index
     * @return true if position i has a missing value
     */
    public boolean isMissing(int i) {
        return (miss & (1 << i)) != 0;
    }

    /**
     * Returns the number of elements in this vector.
     *
     * @return length of the vector
     */
    public int getLength() {
        return length;
    }

    /**
     * Returns the cluster assignment for this vector.
     * <p>
     * 0 means unassigned (not yet clustered).
     * </p>
     *
     * @return cluster index (1-based) or 0 if unassigned
     */
    public int getClassname() {
        return classname;
    }

    /**
     * Sets the cluster assignment for this vector.
     * <p>
     * Note: While BinaryVector is mostly immutable, classname can change during
     * clustering.
     * </p>
     *
     * @param classname
     *            new cluster index (1-based) or 0 to unassign
     */
    public void setClassname(int classname) {
        this.classname = classname;
    }

    /**
     * Returns the identifier string for this vector.
     *
     * @return strain name
     */
    public String getStrain() {
        return strain;
    }

    /**
     * Sets the identifier string for this vector.
     *
     * @param strain
     *            new strain name
     */
    public void setStrain(String strain) {
        this.strain = strain != null ? strain : "";
    }

    /**
     * Returns a copy of this binary vector.
     * <p>
     * Equivalent to C function {@code bv_copy()} from {@code binset.h}.
     * </p>
     *
     * @return a deep copy of this BinaryVector
     */
    public BinaryVector copy() {
        return new BinaryVector(el, miss, length, classname, strain);
    }

    /**
     * Computes the Hamming distance between two binary vectors.
     * <p>
     * Counts the number of positions where the two vectors differ (excluding
     * missing values). Equivalent to C function {@code hamming_distance()} from
     * {@code binset.h}.
     * </p>
     *
     * @param other
     *            the other BinaryVector
     * @return the Hamming distance (number of differing positions)
     */
    public int hammingDistance(BinaryVector other) {
        Objects.requireNonNull(other, "Other vector must not be null");

        int dist = 0;
        for (int i = 0; i < length; i++) {
            boolean thisMissing = isMissing(i);
            boolean otherMissing = other.isMissing(i);

            if (!thisMissing && !otherMissing && el[i] != other.el[i]) {
                dist++;
            }
        }
        return dist;
    }

    /**
     * Returns the Hamming distance between two binary vectors.
     * <p>
     * Static convenience method equivalent to C function
     * {@code hamming_distance()} from {@code binset.h}.
     * </p>
     *
     * @param v1
     *            first vector
     * @param v2
     *            second vector
     * @return the Hamming distance
     */
    public static int hammingDistance(BinaryVector v1, BinaryVector v2) {
        return v1.hammingDistance(v2);
    }

    /**
     * Returns a string representation of this binary vector.
     * <p>
     * Format: {@code "strain_name: [0, 1, 0, ...]"} with missing values marked
     * as 'x'.
     * </p>
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(strain).append(": [");
        for (int i = 0; i < length; i++) {
            if (i > 0)
                sb.append(", ");
            if (isMissing(i)) {
                sb.append('x');
            } else {
                sb.append(el[i]);
            }
        }
        return sb.append("]").toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof BinaryVector other))
            return false;
        return length == other.length &&
                Arrays.equals(el, other.el) &&
                miss == other.miss &&
                classname == other.classname &&
                strain.equals(other.strain);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(el);
        result = 31 * result + miss;
        result = 31 * result + length;
        result = 31 * result + classname;
        result = 31 * result + strain.hashCode();
        return result;
    }

}
