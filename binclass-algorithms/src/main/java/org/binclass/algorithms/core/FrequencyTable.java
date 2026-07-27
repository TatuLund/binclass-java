/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.core;

import java.util.Arrays;

/**
 * An immutable frequency table tracking bit occurrences in a cluster, mirroring
 * the C {@code FrequencyTable} struct from {@code binset.h}.
 * <p>
 * Stores:
 * <ul>
 * <li>{@code freq} — frequency counts for each bit position</li>
 * <li>{@code size} — total number of vectors in the cluster</li>
 * <li>{@code linked} — whether this table is linked to a parent partition</li>
 * <li>{@code linkage} — reference to the linked data structure (if any)</li>
 * </ul>
 * Immutable by design — frequency tables are recomputed when clusters change.
 * </p>
 */
public final class FrequencyTable {

    private final int[] freq; // Frequency counts for each bit position
    private final int size; // Total number of vectors in the cluster
    private final boolean linked; // Whether this table is linked to a parent
                                  // partition
    private final Object linkage; // Reference to linked data structure (if any)

    /**
     * Creates a new {@code FrequencyTable} with the given data.
     *
     * @param freq
     *            frequency counts for each bit position
     * @param size
     *            total number of vectors in the cluster
     * @param linked
     *            whether this table is linked to a parent partition
     * @param linkage
     *            reference to linked data structure (null if not linked)
     */
    public FrequencyTable(int[] freq, int size, boolean linked,
            Object linkage) {
        this.freq = Arrays.copyOf(freq, freq.length);
        this.size = size;
        this.linked = linked;
        this.linkage = linkage;
    }

    /**
     * Creates a new {@code FrequencyTable} with the given data (simplified
     * constructor).
     * <p>
     * Equivalent to C function {@code frequency_table_create()} from
     * {@code binset.h}.
     * </p>
     *
     * @param freq
     *            frequency counts for each bit position
     * @param size
     *            total number of vectors in the cluster
     */
    public FrequencyTable(int[] freq, int size) {
        this(freq, size, false, null);
    }

    /**
     * Returns the frequency count at the given index.
     * <p>
     * Equivalent to C function {@code frequency_table_get()} from
     * {@code binset.h}.
     * </p>
     *
     * @param i
     *            zero-based index (bit position)
     * @return frequency count at that position
     */
    public int get(int i) {
        if (i < 0 || i >= freq.length) {
            throw new IndexOutOfBoundsException("Index " + i
                    + " out of bounds for frequency table with length "
                    + freq.length);
        }
        return freq[i];
    }

    /**
     * Returns the total number of vectors in the cluster.
     * <p>
     * Equivalent to C function {@code frequency_table_get_size()} from
     * {@code binset.h}.
     * </p>
     *
     * @return size (number of vectors)
     */
    public int getSize() {
        return size;
    }

    /**
     * Returns whether this table is linked to a parent partition.
     * <p>
     * Equivalent to C function {@code frequency_table_is_linked()} from
     * {@code binset.h}.
     * </p>
     *
     * @return true if linked, false otherwise
     */
    public boolean isLinked() {
        return linked;
    }

    /**
     * Returns the linkage reference (if any).
     * <p>
     * Equivalent to C function {@code frequency_table_get_linkage()} from
     * {@code binset.h}.
     * </p>
     *
     * @return linkage object (null if not linked)
     */
    public Object getLinkage() {
        return linkage;
    }

    /**
     * Returns the length of the frequency array.
     *
     * @return number of bit positions tracked
     */
    public int getLength() {
        return freq.length;
    }

    /**
     * Returns a copy of this frequency table.
     * <p>
     * Equivalent to C function {@code frequency_table_copy()} from
     * {@code binset.h}.
     * </p>
     *
     * @return a deep copy of this FrequencyTable
     */
    public FrequencyTable copy() {
        return new FrequencyTable(freq, size, linked, linkage);
    }

    /**
     * Returns the underlying frequency array. Use with caution — direct
     * mutation bypasses immutability.
     *
     * @return the internal storage array (defensive copy)
     */
    public int[] getArray() {
        return Arrays.copyOf(freq, freq.length);
    }

    /**
     * Returns a string representation of this frequency table.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("FrequencyTable{size=")
                .append(size).append(", linked=").append(linked)
                .append(", freq=[");
        for (int i = 0; i < freq.length; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(freq[i]);
        }
        return sb.append("]}").toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof FrequencyTable other))
            return false;
        return size == other.size && linked == other.linked
                && Arrays.equals(freq, other.freq);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(freq);
        result = 31 * result + size;
        result = 31 * result + (linked ? 1 : 0);
        return result;
    }

}
