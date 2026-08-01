/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.core;

/**
 * A dynamic partition with frequency and Hamming distance tracking, mirroring
 * the C {@code DynPartition} struct from {@code binset.h}.
 * <p>
 * Extends {@link Partition} by maintaining:
 * <ul>
 * <li>A frequency table for each cluster (counting bit occurrences)</li>
 * <li>A Hamming distance matrix between clusters</li>
 * </ul>
 * This enables efficient incremental updates during clustering operations like
 * GLA and Split-GLA.
 * </p>
 */
public final class DynamicPartition {

    private final Partition partition; // The underlying static partition
    private final int[][] freqs; // Frequency table: [cluster][bit_position]
    private final double[][] hammingDistances; // Hamming distance matrix
                                               // between clusters
    private final int k; // Number of clusters (1-based)

    /**
     * Creates a new {@code DynamicPartition} with the given number of clusters
     * and vector length.
     * <p>
     * Equivalent to C function {@code dyn_partition_allocate()} from
     * {@code binset.h}.
     * </p>
     *
     * @param k
     *            the number of clusters (1-based)
     * @param l
     *            the length of binary vectors (number of bits)
     */
    public DynamicPartition(int k, int l) {
        if (k <= 0 || l <= 0) {
            throw new IllegalArgumentException("k and l must be positive");
        }
        this.k = k;
        this.partition = new Partition(k);
        this.freqs = new int[k][l]; // Frequency table for each cluster
        this.hammingDistances = new double[k][k]; // Hamming distance matrix
    }

    /**
     * Returns the underlying static {@link Partition}.
     * <p>
     * Equivalent to C function {@code dyn_partition_convert()} from
     * {@code binset.h}.
     * </p>
     *
     * @return the static partition representation
     */
    public Partition convert() {
        return partition;
    }

    /**
     * Adds an element to a cluster and updates frequency/Hamming distance
     * tables.
     * <p>
     * Equivalent to C function {@code dyn_partition_put_vector()} from
     * {@code binset.h}.
     * </p>
     *
     * @param i
     *            the 1-based cluster index (1..k)
     * @param bv
     *            the BinaryVector to add
     */
    public void putVector(int i, BinaryVector bv) {
        checkClusterIndex(i);

        // Add to underlying partition
        partition.addElement(i, bv);

        // Update frequency table for this cluster
        int[] freq = freqs[i - 1];
        for (int bit = 0; bit < freq.length; bit++) {
            if (!bv.isMissing(bit)) {
                freq[bit] += bv.get(bit);
            }
        }

        // Update Hamming distances with all other clusters
        updateHammingDistances(i);
    }

    /**
     * Removes an element from a cluster and updates frequency/Hamming distance
     * tables.
     * <p>
     * Equivalent to C function {@code dyn_partition_remove_vector()} from
     * {@code binset.h}.
     * </p>
     *
     * @param i
     *            the 1-based cluster index (1..k)
     * @param bv
     *            the BinaryVector to remove
     */
    public void removeVector(int i, BinaryVector bv) {
        checkClusterIndex(i);

        // Remove from underlying partition
        partition.removeElement(i, bv);

        // Update frequency table for this cluster
        int[] freq = freqs[i - 1];
        for (int bit = 0; bit < freq.length; bit++) {
            if (!bv.isMissing(bit)) {
                freq[bit] -= bv.get(bit);
            }
        }

        // Update Hamming distances with all other clusters
        updateHammingDistances(i);
    }

    /**
     * Returns the frequency table for a specific cluster.
     * <p>
     * Equivalent to C function {@code dyn_partition_get_freqs()} from
     * {@code binset.h}.
     * </p>
     *
     * @param i
     *            the 1-based cluster index (1..k)
     * @return array of frequency counts for each bit position
     */
    public int[] getFreqs(int i) {
        checkClusterIndex(i);
        return freqs[i - 1];
    }

    /**
     * Returns the Hamming distance between two clusters.
     * <p>
     * Equivalent to C function {@code dyn_partition_get_hamming_distance()}
     * from {@code binset.h}.
     * </p>
     *
     * @param i
     *            the 1-based index of the first cluster (1..k)
     * @param j
     *            the 1-based index of the second cluster (1..k)
     * @return the Hamming distance between clusters i and j
     */
    public double getHammingDistance(int i, int j) {
        checkClusterIndex(i);
        checkClusterIndex(j);
        return hammingDistances[i - 1][j - 1];
    }

    /**
     * Returns the number of clusters in this dynamic partition.
     *
     * @return number of clusters (1-based)
     */
    public int size() {
        return k;
    }

    /**
     * Returns the number of vectors in a specific cluster.
     * <p>
     * Equivalent to C function {@code dyn_partition_get_size()} from
     * {@code binset.h}.
     * </p>
     *
     * @param i
     *            the 1-based cluster index (1..k)
     * @return number of vectors in that cluster
     */
    public int getClusterSize(int i) {
        checkClusterIndex(i);
        return partition.getSize(i);
    }

    /**
     * Returns a string representation of this DynamicPartition.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("DynamicPartition{");
        for (int i = 0; i < k; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(i + 1).append(": ").append(partition.getSize(i + 1))
                    .append(" elements");
        }
        return sb.append("}").toString();
    }

    private void checkClusterIndex(int i) {
        if (i < 1 || i > k) {
            throw new IndexOutOfBoundsException("Cluster index " + i
                    + " out of bounds for dynamic partition with " + k
                    + " clusters");
        }
    }

    /**
     * Updates Hamming distances between cluster {@code i} and all other
     * clusters.
     */
    private void updateHammingDistances(int i) {
        // Recalculate Hamming distance for cluster i with all other clusters
        for (int j = 0; j < k; j++) {
            if (j != i - 1) { // Skip self-comparison
                double dist = calculateClusterHammingDistance(i, j);
                hammingDistances[i - 1][j] = dist;
                hammingDistances[j][i - 1] = dist; // Symmetric
            }
        }
    }

    /**
     * Calculates the average Hamming distance between two clusters.
     */
    private double calculateClusterHammingDistance(int i, int j) {
        VectorSet clusterI = partition.getElements(i);
        VectorSet clusterJ = partition.getElements(j + 1); // Convert to 1-based
                                                           // index

        if (clusterI.isEmpty() || clusterJ.isEmpty()) {
            return 0.0; // No elements to compare
        }

        double totalDist = 0.0;
        int count = 0;

        for (BinaryVector bvI : clusterI) {
            for (BinaryVector bvJ : clusterJ) {
                totalDist += BinaryVector.hammingDistance(bvI, bvJ);
                count++;
            }
        }

        return count > 0 ? totalDist / count : 0.0;
    }

}
