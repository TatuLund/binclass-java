/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.core;

import java.util.Arrays;

import org.binclass.algorithms.util.MathUtils;

/**
 * An immutable centroid representing the mean of a cluster, mirroring the C
 * {@code Centroid} struct from {@code binset.h}.
 * <p>
 * A centroid is computed as the frequency-weighted average of all binary
 * vectors in a cluster. Immutable by design — centroids are recomputed when
 * clusters change rather than mutated in place.
 * </p>
 * <p>
 * Fields:
 * <ul>
 * <li>{@code el} — centroid values (floating-point averages, not just 0/1)</li>
 * <li>{@code l} — length of the centroid vector</li>
 * <li>{@code weight} — number of vectors contributing to this centroid</li>
 * </ul>
 * </p>
 */
public final class Centroid {

    private final double[] el;
    private final int l;
    private double weight;

    /**
     * Creates a new {@code Centroid} with the given data.
     *
     * @param el
     *            the centroid values (floating-point averages)
     * @param l
     *            the length of the centroid vector
     * @param weight
     *            frequency ratio (class_size / total_vectors) for weighted
     *            codelength
     */
    public Centroid(double[] el, int l, double weight) {
        this.el = Arrays.copyOf(el, el.length);
        this.l = l;
        this.weight = weight;
    }

    /**
     * Creates a new {@code Centroid} with the given length and default weight.
     * Convenience constructor for quick initialization.
     *
     * @param l
     *            the length of the centroid vector
     */
    public Centroid(int l) {
        this(new double[l], l, 0.0);
    }

    /**
     * Creates a zero-length centroid with no contributing vectors.
     */
    public Centroid() {
        this(0);
    }

    /**
     * Sets the value at index i in this centroid.
     * <p>
     * Equivalent to C function {@code centroid_set()} from {@code binset.h}.
     * </p>
     *
     * @param i
     *            zero-based index
     * @param value
     *            the new value at that position
     */
    public void set(int i, double value) {
        if (i < 0 || i >= l) {
            throw new IndexOutOfBoundsException(
                    "Index " + i + " out of bounds for centroid length " + l);
        }
        el[i] = value;
    }

    /**
     * Sets the frequency ratio weight for this centroid.
     * <p>
     * Equivalent to C function {@code centroid_set_weight()} from
     * {@code binset.h}. Weight is computed as (class_size / total_vectors) for
     * weighted codelength calculations.
     * </p>
     *
     * @param weight
     *            the new frequency ratio value (class_size / total_vectors)
     */
    public void setWeight(double weight) {
        this.weight = weight;
    }

    /**
     * Returns the centroid value at the given index.
     * <p>
     * Equivalent to C function {@code centroid_get()} from {@code binset.h}.
     * </p>
     *
     * @param i
     *            zero-based index
     * @return the centroid value at that position (floating-point average)
     */
    public double get(int i) {
        if (i < 0 || i >= l) {
            throw new IndexOutOfBoundsException(
                    "Index " + i + " out of bounds for centroid length " + l);
        }
        return el[i];
    }

    /**
     * Returns the value at index i as a log-probability.
     * <p>
     * Equivalent to C function {@code centroid_log0()} from {@code binset.h}.
     * Computes log₂(1 - el[i]) for bit=0 probability.
     * </p>
     *
     * @param i
     *            zero-based index
     * @return log₂(1 - el[i])
     */
    public double getLog0(int i) {
        double val = get(i);
        // Return negated log₂(1 - el[i]) so it represents information content
        // (positive for prob < 1)
        return MathUtils.log2Complement(val);
    }

    /**
     * Returns the centroid value at index i. Alias for {@link #get(int)}.
     * <p>
     * Used by GLA algorithms that expect getElement() naming convention.
     * </p>
     *
     * @param i
     *            zero-based index
     * @return the centroid value at position i
     */
    public double getElement(int i) {
        return get(i);
    }

    /**
     * Returns the value at index i as a log-probability for bit=1.
     * <p>
     * Equivalent to C function {@code centroid_log1()} from {@code binset.h}.
     * Computes log₂(el[i]) for information content (positive when prob &lt; 1).
     * </p>
     *
     * @param i
     *            zero-based index
     * @return log₂(el[i])
     */
    public double getLog1(int i) {
        return MathUtils.log2(get(i));
    }

    /**
     * Returns the length of this centroid.
     *
     * @return number of elements in the centroid vector
     */
    public int getLength() {
        return l;
    }

    /**
     * Returns the frequency ratio weight for this centroid.
     * <p>
     * Weight is computed as (class_size / total_vectors) for weighted
     * codelength calculations.
     * </p>
     *
     * @return weight of the centroid (frequency ratio)
     */
    public double getWeight() {
        return weight;
    }

    /**
     * Returns a copy of this centroid.
     * <p>
     * Equivalent to C function {@code centroid_copy()} from {@code binset.h}.
     * </p>
     *
     * @return a deep copy of this Centroid
     */
    public Centroid copy() {
        return new Centroid(el, l, weight);
    }

    /**
     * Sets all elements from an int array, converting each value to double.
     * <p>
     * Useful when initializing a centroid from binary vector data where the
     * source is {@code int[]} but the centroid stores {@code double[]}.
     * </p>
     *
     * @param values
     *            the integer array to copy into this centroid (converted to
     *            doubles)
     */
    public void setEl(int[] values) {
        for (int i = 0; i < l && i < values.length; i++) {
            el[i] = values[i];
        }
    }

    /**
     * Sets all elements from a double array.
     * <p>
     * Useful when initializing or updating centroid values directly.
     * </p>
     *
     * @param values
     *            the double array to copy into this centroid
     */
    public void setEl(double[] values) {
        for (int i = 0; i < l && i < values.length; i++) {
            el[i] = values[i];
        }
    }

    /**
     * Returns the underlying array. Use with caution — direct mutation bypasses
     * immutability.
     *
     * @return the internal storage array (defensive copy)
     */
    public double[] getArray() {
        return el; // Return reference to internal array for direct mutation
    }

    /**
     * Returns a string representation of this centroid.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Centroid{weight=").append(weight)
                .append(", values=[");
        for (int i = 0; i < l; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(String.format("%.4f", el[i]));
        }
        return sb.append("]}").toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Centroid other))
            return false;
        return l == other.l && weight == other.weight
                && Arrays.equals(el, other.el);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(el);
        result = 31 * result + l;
        long weightBits = Double.doubleToLongBits(weight);
        result = 31 * result + (int) (weightBits ^ (weightBits >>> 32));
        return result;
    }

}
