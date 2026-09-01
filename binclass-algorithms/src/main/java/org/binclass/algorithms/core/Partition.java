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
     * Creates an empty partition with the given number of clusters and default
     * vector length. Convenience constructor for quick initialization.
     *
     * @param k
     *            the number of clusters (1-based)
     */
    public Partition(int k, int l) {
        this(k);
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
     * Sets the number of clusters in this partition.
     * <p>
     * Equivalent to C assignment {@code P->k = k} from {@code distmin.c}. Used
     * by nearest-neighbor algorithms to initialize partition size before
     * assigning vectors.
     * </p>
     *
     * @param newSize
     *            the new number of clusters (1-based)
     */
    public void setSize(int newSize) {
        if (newSize <= 0) {
            throw new IllegalArgumentException(
                    "Number of clusters must be positive, got: " + newSize);
        }
        // When shrinking, compact the physical cluster array so that elements
        // stored in dropped slots remain reachable through the public API. The
        // internal array is sized to its capacity and may hold stale references
        // past {@code k}; a live view over those slots would otherwise report
        // an
        // empty cluster even though it still holds vectors. Mirrors C
        // {@code remove_empty_sets} compaction in {@code binset.c}.
        if (newSize < k) {
            int writeIdx = 0;
            for (int readIdx = 0; readIdx < k; readIdx++) {
                VectorSet cluster = clusters[readIdx];
                if (cluster != null && !cluster.isEmpty()) {
                    if (writeIdx != readIdx) {
                        clusters[writeIdx] = cluster;
                        clusters[readIdx] = null;
                    }
                    writeIdx++;
                }
            }
            // Only clear slots beyond the new size. Slots within
            // [0, newSize) keep their allocated VectorSet so that a fresh
            // partition shrunk to a smaller count still has usable clusters
            // (otherwise an all-empty shrink would null every slot and the
            // next addElement() would NPE).
            for (int i = newSize; i < k; i++) {
                clusters[i] = null;
            }
        }
        this.k = newSize;
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
     * Removes an entire cluster from this partition.
     * <p>
     * Equivalent to C function {@code partition_remove_cluster()}
     * (hypothetical). Shifts remaining clusters down by one position and
     * decrements k.
     * </p>
     *
     * @param i
     *            the 1-based cluster index (1..k)
     */
    public void removeCluster(int i) {
        checkClusterIndex(i);
        // Shift remaining clusters down by one position
        for (int j = i; j < k - 1; j++) {
            clusters[j - 1] = clusters[j];
        }
        // Remove the last cluster reference
        clusters[k - 1] = null;
        k--;
    }

    /**
     * Returns the VectorSet for a specific cluster (0-based index).
     * <p>
     * Equivalent to C function {@code partition_get_cluster()} from
     * {@code binset.h}. Uses 0-based indexing internally.
     * </p>
     *
     * @param i
     *            the 0-based cluster index (0..k-1)
     * @return a VectorSet containing all elements in that cluster
     */
    public VectorSet getCluster(int i) {
        if (i < 0 || i >= k) {
            throw new IndexOutOfBoundsException("Cluster index " + i
                    + " out of bounds for partition with " + k + " clusters");
        }
        return clusters[i];
    }

    /**
     * Copies all elements from this partition into the target VectorSet.
     * <p>
     * Equivalent to C function {@code partition_copy_all()} from
     * {@code binset.h}. Iterates through all clusters and adds their elements.
     * </p>
     *
     * @param target
     *            the destination VectorSet to copy elements into
     */
    public void copyAllTo(VectorSet target) {
        for (int i = 0; i < k; i++) {
            for (BinaryVector bv : clusters[i]) {
                target.addElement(bv);
            }
        }
    }

    /**
     * Creates a deep copy of this partition.
     * <p>
     * Each cluster is copied as a new VectorSet with the same elements.
     * Modifications to the copy do not affect this partition.
     * </p>
     *
     * @return a new Partition with copies of all clusters and elements
     */
    public Partition copy() {
        Partition copy = new Partition(this.k);
        for (int i = 0; i < k; i++) {
            VectorSet sourceClusterne = this.clusters[i];
            for (BinaryVector bv : sourceClusterne) {
                copy.clusters[i].addElement(bv);
            }
        }
        return copy;
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
