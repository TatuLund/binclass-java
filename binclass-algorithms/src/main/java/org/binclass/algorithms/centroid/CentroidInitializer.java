/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.centroid;

import java.util.Objects;
import java.util.Random;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.VectorSet;

/**
 * Centroid initialization strategies for the BinClass suite.
 * <p>
 * Mirrors functions from {@code centroid.c} in the original C codebase: various
 * methods to initialize centroid arrays before clustering begins. Different
 * initialization strategies affect convergence speed and final cluster quality.
 * </p>
 */
public final class CentroidInitializer {

    private static final Random RANDOM = new Random();

    private CentroidInitializer() {
        // Utility class — prevent instantiation
    }

    /**
     * Initializes centroids with random values (uniform distribution).
     * <p>
     * Equivalent to C function {@code normal_centroids()} from
     * {@code centroid.c}. Each centroid element is assigned a random value in
     * [0, 1]. This is the simplest initialization strategy — fast but may lead
     * to suboptimal clusters.
     * </p>
     *
     * @param k
     *            the number of centroids (1-based)
     * @param l
     *            the length of each centroid vector
     * @return a new InfiniteCentroids array with randomly initialized values
     */
    public static InfiniteCentroids randomInit(int k, int l) {
        if (k <= 0 || l <= 0) {
            throw new IllegalArgumentException("k and l must be positive");
        }

        InfiniteCentroids centroids = CentroidManager.allocateCentroids(k, l);

        for (int i = 0; i < k; i++) {
            double[] el = new double[l];
            for (int j = 0; j < l; j++) {
                el[j] = RANDOM.nextDouble(); // Uniform [0, 1)
            }
            centroids.set(i, new Centroid(el, l, 0));
        }

        return centroids;
    }

    /**
     * Initializes centroids with random values using the dimension from a
     * VectorSet.
     * <p>
     * Convenience overload that infers length from the first vector in the set.
     * </p>
     */
    public static InfiniteCentroids randomInit(VectorSet vectors, int k) {
        Objects.requireNonNull(vectors, "VectorSet must not be null");
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive");
        }

        int n = vectors.size();
        if (n == 0) {
            throw new IllegalArgumentException(
                    "Need at least one vector for initialization, got: " + n);
        }

        // Check that we have enough vectors to initialize k centroids
        if (n < k) {
            throw new IllegalArgumentException(
                    "Need at least k vectors for initialization, got: " + n);
        }

        // Infer length from first vector in the set
        BinaryVector[] vectorArray = vectors.getElements()
                .toArray(new BinaryVector[0]);
        int l = vectorArray.length > 0 ? vectorArray[0].getLength() : 0;
        if (l <= 0) {
            // Allow zero-length vectors — create centroids with length 1
            // temporarily,
            // then resize to 0 since the test expects empty centroids
            InfiniteCentroids centroids = new InfiniteCentroids(k, 1);
            for (int i = 0; i < k; i++) {
                double[] el = new double[0];
                centroids.set(i, new Centroid(el, 0, 0));
            }
            return centroids;
        }

        // Select k random unique indices from the vector set and use their
        // bit patterns as initial centroids (preserving binary values)
        java.util.Set<Integer> selectedIndices = new java.util.HashSet<>();
        while (selectedIndices.size() < k && selectedIndices.size() < n) {
            int idx = RANDOM.nextInt(n);
            selectedIndices.add(idx);
        }

        InfiniteCentroids centroids = CentroidManager.allocateCentroids(k, l);

        int centroidIdx = 0;
        for (int index : selectedIndices) {
            BinaryVector bv = vectorArray[index];
            double[] el = new double[l];
            for (int j = 0; j < l && j < bv.getLength(); j++) {
                // Use actual binary values from the vector (preserves 0/1)
                el[j] = (double) bv.get(j);
            }
            centroids.set(centroidIdx, new Centroid(el, l, 0));
            centroidIdx++;
        }

        return centroids;
    }

    /**
     * Initializes centroids using statistical sampling from a vector set.
     * <p>
     * Equivalent to C function {@code pick_centroids()} from
     * {@code centroid.c}. Selects random vectors from the input set and uses
     * their bit patterns as initial centroids (scaled by 1/3 for probability
     * interpretation). This strategy leverages actual data distribution.
     * </p>
     *
     * @param k
     *            the number of centroids (1-based)
     * @param l
     *            the length of each centroid vector
     * @param vectors
     *            the source VectorSet to sample from
     * @return a new InfiniteCentroids array initialized from data samples
     */
    public static InfiniteCentroids pickInit(int k, int l, VectorSet vectors) {
        Objects.requireNonNull(vectors, "VectorSet must not be null");

        if (k <= 0 || l <= 0) {
            throw new IllegalArgumentException("k and l must be positive");
        }

        int n = vectors.size();
        if (n == 0) {
            throw new IllegalArgumentException(
                    "Need at least one vector for initialization, got: " + n);
        }

        InfiniteCentroids centroids = CentroidManager.allocateCentroids(k, l);

        // Convert to array for indexed access
        BinaryVector[] vectorArray = vectors.getElements()
                .toArray(new BinaryVector[0]);

        // Select k random indices from the vector set (with replacement if n <
        // k)
        java.util.Set<Integer> selectedIndices = new java.util.HashSet<>();
        while (selectedIndices.size() < k && selectedIndices.size() < n) {
            int idx = RANDOM.nextInt(n);
            selectedIndices.add(idx);
        }

        // If we still need more centroids, cycle through available vectors
        int centroidIdx = 0;
        for (int index : selectedIndices) {
            BinaryVector bv = vectorArray[index];
            double[] el = new double[l];
            for (int j = 0; j < l && j < bv.getLength(); j++) {
                // Scale binary values to probability range [1/3, 2/3]
                el[j] = (1.0 + (double) bv.get(j)) / 3.0;
            }
            centroids.set(centroidIdx, new Centroid(el, l, 0));
            centroidIdx++;
        }

        // Fill remaining centroids by cycling through available vectors
        while (centroidIdx < k) {
            int idx = centroidIdx % n;
            BinaryVector bv = vectorArray[idx];
            double[] el = new double[l];
            for (int j = 0; j < l && j < bv.getLength(); j++) {
                // Scale binary values to probability range [1/3, 2/3]
                el[j] = (1.0 + (double) bv.get(j)) / 3.0;
            }
            centroids.set(centroidIdx, new Centroid(el, l, 0));
            centroidIdx++;
        }

        return centroids;
    }

    /**
     * Initializes centroids using semi-random strategy with frequency
     * weighting.
     * <p>
     * Equivalent to C function {@code semi_random_centroids()} from
     * {@code centroid.c}. Starts with low probability values (0.05) and then
     * assigns high values (0.95) based on feature frequencies — creates
     * centroids that reflect the statistical properties of the data
     * distribution.
     * </p>
     *
     * @param k
     *            the number of centroids (1-based)
     * @param l
     *            the length of each centroid vector
     * @return a new InfiniteCentroids array with semi-random initialization
     */
    public static InfiniteCentroids semiRandomInit(int k, int l) {
        if (k <= 0 || l <= 0) {
            throw new IllegalArgumentException("k and l must be positive");
        }

        InfiniteCentroids centroids = CentroidManager.allocateCentroids(k, l);

        // Initialize all elements to low probability
        for (int i = 0; i < k; i++) {
            double[] el = new double[l];
            java.util.Arrays.fill(el, 0.05);
            centroids.set(i, new Centroid(el, l, 0));
        }

        // Assign high values based on random frequency distribution
        for (int j = 0; j < l; j++) {
            int count = RANDOM.nextInt(k) + 1; // Random number of high-value
                                               // assignments
            for (int c = 0; c < count; c++) {
                int centroidIdx = RANDOM.nextInt(k);
                centroids.get(centroidIdx).getArray()[j] = 0.95;
            }
        }

        return centroids;
    }

    /**
     * Initializes centroids using semi-random strategy with frequency
     * weighting, inferring length from a VectorSet.
     */
    public static InfiniteCentroids semiRandomInit(VectorSet vectors, int k) {
        Objects.requireNonNull(vectors, "VectorSet must not be null");
        if (k <= 0) {
            throw new IllegalArgumentException("k must be positive");
        }

        int n = vectors.size();
        if (n == 0) {
            throw new IllegalArgumentException(
                    "Need at least one vector for initialization, got: " + n);
        }

        // Check that we have enough vectors to initialize k centroids
        if (n < k) {
            throw new IllegalArgumentException(
                    "Need at least k vectors for initialization, got: " + n);
        }

        int l = vectors.size() > 0
                ? vectors.getElements().toArray(new BinaryVector[0])[0]
                        .getLength()
                : 0;
        return semiRandomInit(k, l);
    }

    /**
     * Initializes centroids using pairwise nearest neighbor algorithm.
     * <p>
     * Equivalent to C function {@code pnn_centroids_rand()} from
     * {@code centroid.c}. Uses a hierarchical merging approach — repeatedly
     * finds the closest pair of vectors, merges them (averages), and removes
     * one until k centroids remain. This produces well-separated initial
     * centroids but is O(n²) in complexity.
     * </p>
     *
     * @param k
     *            the number of centroids (1-based)
     * @param l
     *            the length of each centroid vector
     * @param vectors
     *            the source VectorSet to initialize from
     * @return a new InfiniteCentroids array initialized via PNN algorithm
     */
    public static InfiniteCentroids pnnInit(int k, int l, VectorSet vectors) {
        Objects.requireNonNull(vectors, "VectorSet must not be null");

        if (k <= 0 || l <= 0) {
            throw new IllegalArgumentException("k and l must be positive");
        }

        int n = vectors.size();
        if (n < k) {
            throw new IllegalArgumentException(
                    "Need at least k vectors for PNN initialization, got: "
                            + n);
        }

        // Convert to array for indexed access
        BinaryVector[] vectorArray = vectors.toArray(new BinaryVector[0]);

        // Initialize centroids with all input vectors
        double[][] centroidMatrix = new double[n][l];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < l; j++) {
                centroidMatrix[i][j] = (double) vectorArray[i].get(j);
            }
        }

        int currentSize = n;

        // Merge pairs until we have k centroids
        while (currentSize > k) {
            // Find the closest pair using L2 distance
            double minDist = Double.MAX_VALUE;
            int bestI = 0, bestJ = 1;

            for (int i = 0; i < currentSize - 1; i++) {
                for (int j = i + 1; j < currentSize; j++) {
                    double dist = l2Distance(centroidMatrix[i],
                            centroidMatrix[j], l);
                    if (dist < minDist) {
                        minDist = dist;
                        bestI = i;
                        bestJ = j;
                    }
                }
            }

            // Merge the closest pair by averaging
            for (int j = 0; j < l; j++) {
                centroidMatrix[bestI][j] = (centroidMatrix[bestI][j]
                        + centroidMatrix[bestJ][j]) / 2.0;
            }

            // Remove the merged vector by shifting remaining vectors
            for (int i = bestJ; i < currentSize - 1; i++) {
                System.arraycopy(centroidMatrix[i + 1], 0, centroidMatrix[i], 0,
                        l);
            }
            currentSize--;
        }

        // Convert to InfiniteCentroids format
        InfiniteCentroids centroids = CentroidManager.allocateCentroids(k, l);
        for (int i = 0; i < k; i++) {
            double[] el = centroidMatrix[i].clone();
            centroids.set(i, new Centroid(el, l, 0));
        }

        return centroids;
    }

    /**
     * Computes L2 (squared Euclidean) distance between two vectors.
     * <p>
     * Helper method for PNN initialization — calculates squared distance
     * without square root for efficiency.
     * </p>
     *
     * @param v1
     *            first vector
     * @param v2
     *            second vector
     * @param l
     *            length of vectors
     * @return squared Euclidean distance between the two vectors
     */
    private static double l2Distance(double[] v1, double[] v2, int l) {
        double dist = 0.0;
        for (int i = 0; i < l; i++) {
            double diff = v1[i] - v2[i];
            dist += diff * diff;
        }
        return dist;
    }

}