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
     * Returns the VectorSet for a specific cluster (1-based index).
     * <p>
     * Equivalent to C function {@code dyn_partition_get_cluster()} from
     * {@code binset.h}. Uses 1-based indexing.
     * </p>
     *
     * @param i
     *            the 1-based cluster index (1..k)
     * @return a VectorSet containing all elements in that cluster
     */
    public VectorSet getCluster(int i) {
        checkClusterIndex(i);
        return partition.getCluster(i - 1);
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
     * clusters using frequency table acceleration.
     */
    private void updateHammingDistances(int i) {
        int sizeI = partition.getSize(i);
        if (sizeI == 0)
            return; // Skip empty clusters

        for (int j = 1; j <= k; j++) {
            if (j != i) {
                double dist = calculateClusterHammingDistanceOptimized(i, j);
                hammingDistances[i - 1][j - 1] = dist;
                hammingDistances[j - 1][i - 1] = dist; // Symmetric
            }
        }
    }

    /**
     * Calculates the average Hamming distance between two clusters using
     * frequency tables. This is O(l) instead of O(n_i × n_j).
     * <p>
     * Uses probability theory: for each bit position, calculates the
     * probability that two randomly selected vectors from different clusters
     * differ at that position based on their frequency distributions.
     * </p>
     */
    private double calculateClusterHammingDistanceOptimized(int i, int j) {
        int sizeI = partition.getSize(i);
        int sizeJ = partition.getSize(j);

        if (sizeI == 0 || sizeJ == 0)
            return 0.0; // No elements to compare

        int[] freqI = freqs[i - 1];
        int[] freqJ = freqs[j - 1];
        int l = freqI.length;

        double totalProbDiff = 0.0;

        for (int bit = 0; bit < l; bit++) {
            // Calculate probability of difference at this bit position
            double probI1 = (double) freqI[bit] / sizeI; // P(vector from i has
                                                         // bit=1)
            double probJ1 = (double) freqJ[bit] / sizeJ; // P(vector from j has
                                                         // bit=1)

            // P(differ at this bit) = P(i=0,j=1) + P(i=1,j=0)
            double pDiff = (1 - probI1) * probJ1 + probI1 * (1 - probJ1);
            totalProbDiff += pDiff;
        }

        // Return expected number of differing bits (sum over all positions)
        return totalProbDiff;
    }

    /**
     * Returns the complete frequency table for all clusters.
     * <p>
     * Equivalent to C function {@code dyn_partition_get_freqs_all()} from
     * {@code binset.h}.
     * </p>
     *
     * @return 2D array of frequencies: [cluster][bit_position]
     */
    public int[][] getFrequencies() {
        return freqs;
    }

    /**
     * Returns the complete Hamming distance matrix for all cluster pairs.
     * <p>
     * Equivalent to C function {@code dyn_partition_get_hamming_distances()}
     * from {@code binset.h}.
     * </p>
     *
     * @return 2D array of Hamming distances: [cluster_i][cluster_j]
     */
    public double[][] getHammingDistances() {
        return hammingDistances;
    }

    /**
     * Returns the length (number of bits) of vectors in this partition.
     * <p>
     * Equivalent to C function {@code dyn_partition_get_length()} from
     * {@code binset.h}.
     * </p>
     *
     * @return vector length (number of bits)
     */
    public int getVectorLength() {
        return freqs.length > 0 ? freqs[0].length : 0;
    }

}
