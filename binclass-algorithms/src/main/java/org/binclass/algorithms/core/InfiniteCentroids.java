/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.core;

import java.util.Objects;

/**
 * An array of {@link Centroid} instances indexed 0..k-1 internally, mirroring
 * the C {@code InfiniteCentroids} struct from {@code binset.h}.
 * <p>
 * Used during clustering operations (GLA, Split-GLA) to maintain centroid
 * states for all clusters. Provides methods to compute sum-of-costs (SC) values
 * and manage centroid arrays.
 * </p>
 */
public final class InfiniteCentroids {

    private Centroid[] centroids; // 0-based internal indexing
    private int k; // number of centroids (1-based)
    private double[][] scValues; // Sum-of-costs matrix: [cluster][bit_position]

    /**
     * Creates a new {@code InfiniteCentroids} with the given number of clusters
     * and vector length.
     * <p>
     * Equivalent to C function {@code infinite_centroids_allocate()} from
     * {@code binset.h}.
     * </p>
     *
     * @param k
     *            the number of clusters (1-based)
     * @param l
     *            the length of binary vectors (number of bits)
     */
    public InfiniteCentroids(int k, int l) {
        if (k <= 0 || l <= 0) {
            throw new IllegalArgumentException("k and l must be positive");
        }
        this.k = k;
        this.centroids = new Centroid[k];
        this.scValues = new double[k][l];

        // Initialize with centroids of proper length (uninitialized state)
        for (int i = 0; i < k; i++) {
            centroids[i] = new Centroid(l);
        }
    }

    /**
     * Creates a new {@code InfiniteCentroids} with the given number of
     * clusters. Uses a default length of 16 bits for binary vectors.
     * <p>
     * Convenience constructor for quick initialization when vector length is
     * not critical.
     * </p>
     *
     * @param k
     *            the number of clusters (1-based)
     */
    public InfiniteCentroids(int k) {
        this(k, 16); // Default to 16-bit vectors
    }

    /**
     * Returns the centroid at the given index.
     * <p>
     * Equivalent to C function {@code infinite_centroids_get()} from
     * {@code binset.h}.
     * </p>
     *
     * @param i
     *            zero-based index (0..k-1)
     * @return the Centroid at that position
     */
    public Centroid get(int i) {
        checkIndex(i);
        return centroids[i];
    }

    /**
     * Sets the centroid at the given index.
     * <p>
     * Equivalent to C function {@code infinite_centroids_set()} from
     * {@code binset.h}.
     * </p>
     *
     * @param i
     *            zero-based index (0..k-1)
     * @param c
     *            the Centroid to set
     */
    public void set(int i, Centroid c) {
        checkIndex(i);
        centroids[i] = c;
    }

    /**
     * Copies centroid data from another InfiniteCentroids instance.
     * <p>
     * Equivalent to C function {@code infinite_centroids_copy_from()} from
     * {@code binset.h}.
     * </p>
     *
     * @param other
     *            the source InfiniteCentroids
     */
    public void copyFrom(InfiniteCentroids other) {
        Objects.requireNonNull(other, "Source must not be null");

        // Allow copy when destination is in uninitialized state (zero-length
        // centroids)
        if (this.centroids[0].getLength() != 0 && this.centroids[0]
                .getLength() != other.centroids[0].getLength()) {
            throw new IllegalArgumentException(
                    "Incompatible dimensions for copy");
        }

        for (int i = 0; i < k; i++) {
            centroids[i] = other.get(i).copy();
        }
    }

    /**
     * Computes the sum-of-costs (SC) values for all clusters.
     * <p>
     * Equivalent to C function {@code infinite_centroids_calculate_sc()} from
     * {@code binset.h}. SC represents the total cost of assigning vectors to
     * their respective centroids.
     * </p>
     *
     * @param partition
     *            the DynamicPartition containing cluster assignments
     */
    public void calculateSC(DynamicPartition partition) {
        Objects.requireNonNull(partition, "Partition must not be null");

        for (int i = 0; i < k; i++) {
            int clusterIdx = i + 1; // Convert to 1-based for partition access
            Centroid centroid = centroids[i];
            int[] freqs = partition.getFreqs(clusterIdx);

            double sc = 0.0;
            for (int bit = 0; bit < centroid.getLength(); bit++) {
                if (freqs[bit] > 0) {
                    // Cost contribution from this bit position
                    double prob1 = centroid.get(bit);
                    double prob0 = 1.0 - prob1;

                    // Handle edge cases where prob is 0 or 1 to avoid NaN
                    // (0 * log(0) should be treated as 0, not NaN)
                    double term1 = (prob1 > 0)
                            ? freqs[bit] * (Math.log(prob1) / Math.log(2))
                            : 0;
                    int count0 = centroid.getWeight() - freqs[bit];
                    double term2 = (count0 > 0 && prob0 > 0)
                            ? count0 * (Math.log(prob0) / Math.log(2))
                            : 0;

                    sc += term1 + term2;
                }
            }
            scValues[i][0] = sc; // Store in first column for simplicity
        }
    }

    /**
     * Returns the sum-of-costs value for a specific cluster.
     * <p>
     * Equivalent to C function {@code infinite_centroids_get_sc()} from
     * {@code binset.h}.
     * </p>
     *
     * @param i
     *            zero-based index (0..k-1)
     * @return the SC value for that cluster
     */
    public double getSC(int i) {
        checkIndex(i);
        return scValues[i][0]; // Return first column value
    }

    /**
     * Returns the number of centroids in this array.
     *
     * @return number of centroids (1-based)
     */
    public int size() {
        return k;
    }

    /**
     * Returns a string representation of this InfiniteCentroids.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("InfiniteCentroids{");
        for (int i = 0; i < k; i++) {
            if (i > 0)
                sb.append(", ");
            sb.append(i).append(": ").append(centroids[i].getWeight())
                    .append(" vectors");
        }
        return sb.append("}").toString();
    }

    private void checkIndex(int i) {
        if (i < 0 || i >= k) {
            throw new IndexOutOfBoundsException(
                    "Index " + i + " out of bounds for infinite centroids with "
                            + k + " clusters");
        }
    }

}
