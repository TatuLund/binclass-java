/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.core;

/**
 * A partition of binary vectors into clusters, mirroring the C
 * {@code Partition} struct from {@code binset.h}.
 * <p>
 * Internally uses 0-based indexing (Java convention), but presents a 1-based
 * API for compatibility with the original C code. Each cluster is backed by a
 * {@link VectorSet} of {@link BinaryVector} instances.
 * </p>
 */
public final class Partition {

    private final VectorSet[] clusters; // 0-based internal indexing
    private int k; // number of clusters (1-based)

    /**
     * Creates an empty partition with the given number of clusters.
     * <p>
     * Equivalent to C function {@code partition_allocate()} from
     * {@code binset.h}.
     * </p>
     *
     * @param k
     *            the number of clusters (1-based)
     */
    public Partition(int k) {
        if (k <= 0) {
            throw new IllegalArgumentException(
                    "Number of clusters must be positive, got: " + k);
        }
        this.k = k;
        this.clusters = new VectorSet[k];
        for (int i = 0; i < k; i++) {
            clusters[i] = new VectorSet();
        }
    }

    /**
     * Returns the number of clusters in this partition.
     * <p>
     * Equivalent to C function {@code partition_size()} from {@code binset.h}.
     * </p>
     *
     * @return number of clusters (1-based)
     */
    public int size() {
        return k;
    }

    /**
     * Adds an element to the specified cluster.
     * <p>
     * Equivalent to C function {@code partition_add_element()} from
     * {@code binset.h}.
     * </p>
     *
     * @param i
     *            the 1-based cluster index (1..k)
     * @param bv
     *            the BinaryVector to add
     */
    public void addElement(int i, BinaryVector bv) {
        checkClusterIndex(i);
        clusters[i - 1].addElement(bv);
    }

    /**
     * Removes an element from the specified cluster.
     * <p>
     * Equivalent to C function {@code partition_remove_element()} from
     * {@code binset.h}.
     * </p>
     *
     * @param i
     *            the 1-based cluster index (1..k)
     * @param bv
     *            the BinaryVector to remove
     */
    public void removeElement(int i, BinaryVector bv) {
        checkClusterIndex(i);
        clusters[i - 1].removeElement(bv);
    }

    /**
     * Returns the elements in the specified cluster.
     * <p>
     * Equivalent to C function {@code partition_get_elements()} from
     * {@code binset.h}.
     * </p>
     *
     * @param i
     *            the 1-based cluster index (1..k)
     * @return a VectorSet containing all elements in that cluster
     */
    public VectorSet getElements(int i) {
        checkClusterIndex(i);
        return clusters[i - 1];
    }

    /**
     * Returns the number of elements in the specified cluster.
     * <p>
     * Equivalent to C function {@code partition_get_size()} from
     * {@code binset.h}.
     * </p>
     *
     * @param i
     *            the 1-based cluster index (1..k)
     * @return number of elements in that cluster
     */
    public int getSize(int i) {
        checkClusterIndex(i);
        return clusters[i - 1].size();
    }

    /**
     * Returns whether a specific element is present in the specified cluster.
     * <p>
     * Equivalent to C function {@code partition_contains()} from
     * {@code binset.h}.
     * </p>
     *
     * @param i
     *            the 1-based cluster index (1..k)
     * @param bv
     *            the BinaryVector to check for
     * @return true if the element is present in that cluster
     */
    public boolean contains(int i, BinaryVector bv) {
        checkClusterIndex(i);
        return clusters[i - 1].contains(bv);
    }

    /**
     * Returns a string representation of this partition.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Partition{");
        for (int i = 0; i < k; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(i + 1).append(": ").append(clusters[i].size())
                    .append(" elements");
        }
        return sb.append("}").toString();
    }

    private void checkClusterIndex(int i) {
        if (i < 1 || i > k) {
            throw new IndexOutOfBoundsException("Cluster index " + i
                    + " out of bounds for partition with " + k + " clusters");
        }
    }

}
