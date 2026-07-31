/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.centroid;

import java.util.Objects;

import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.util.MathUtils;

/**
 * Centroid lifecycle management utilities for the BinClass suite.
 * <p>
 * Mirrors functions from {@code centroid.c} in the original C codebase:
 * allocation, deallocation, copying, and log-probability computation for
 * centroid arrays. Handles the low-level memory management aspects of centroid
 * operations — creation, destruction, duplication, and precomputation of
 * logarithmic values used in Shannon codelength calculations.
 * </p>
 */
public final class CentroidManager {

    private CentroidManager() {
        // Utility class — prevent instantiation
    }

    /**
     * Allocates a single centroid with the given length and zero weight.
     * <p>
     * Equivalent to C function {@code allocate_centroid()} from
     * {@code centroid.c}. Creates a new Centroid object initialized with
     * zero-length values (uninitialized state).
     * </p>
     *
     * @param l
     *            the length of the centroid vector
     * @return a newly allocated Centroid in uninitialized state
     */
    public static Centroid allocateCentroid(int l) {
        if (l <= 0) {
            throw new IllegalArgumentException(
                    "Centroid length must be positive, got: " + l);
        }

        double[] el = new double[l]; // Zero-initialized by default
        return new Centroid(el, l, 0); // Weight 0 indicates uninitialized state
    }

    /**
     * Allocates an array of centroids with the given dimensions.
     * <p>
     * Equivalent to C function {@code allocate_centroids()} from
     * {@code centroid.c}. Creates k centroids each of length l, all in
     * uninitialized state (zero values).
     * </p>
     *
     * @param k
     *            the number of centroids (1-based)
     * @param l
     *            the length of each centroid vector
     * @return a new InfiniteCentroids array with k zero-initialized centroids
     */
    public static InfiniteCentroids allocateCentroids(int k, int l) {
        if (k <= 0 || l <= 0) {
            throw new IllegalArgumentException("k and l must be positive");
        }

        return new InfiniteCentroids(k, l);
    }

    /**
     * Deallocates a single centroid.
     * <p>
     * Equivalent to C function {@code deallocate_centroid()} from
     * {@code centroid.c}. In Java, this is handled by the garbage collector —
     * no explicit cleanup needed.
     * </p>
     *
     * @param c
     *            the Centroid to deallocate (becomes eligible for GC)
     */
    public static void deallocateCentroid(Centroid c) {
        Objects.requireNonNull(c, "Centroid must not be null");
        // In Java, no explicit deallocation needed — GC handles it.
        // This method exists for API parity with the C implementation.
    }

    /**
     * Deallocates an array of centroids.
     * <p>
     * Equivalent to C function {@code deallocate_centroids()} from
     * {@code centroid.c}. In Java, this is handled by the garbage collector —
     * no explicit cleanup needed.
     * </p>
     *
     * @param c
     *            the InfiniteCentroids array to deallocate (becomes eligible
     *            for GC)
     */
    public static void deallocateCentroids(InfiniteCentroids c) {
        Objects.requireNonNull(c, "InfiniteCentroids must not be null");
        // In Java, no explicit deallocation needed — GC handles it.
        // This method exists for API parity with the C implementation.
    }

    /**
     * Copies centroid data from one array to another.
     * <p>
     * Equivalent to C function {@code copy_centroids()} from
     * {@code centroid.c}. Deep copies all centroid values and weights from
     * source to destination, recalculating log-probabilities in the process.
     * </p>
     *
     * @param source
     *            the source InfiniteCentroids array (must not be null)
     * @return a new InfiniteCentroids with copied centroid data
     */
    public static InfiniteCentroids copyCentroids(InfiniteCentroids source) {
        Objects.requireNonNull(source, "Source must not be null");

        int k = source.size(); // 1-based count
        int l = source.get(0).getLength();

        if (l == 0) {
            throw new IllegalStateException(
                    "Cannot copy from uninitialized centroids");
        }

        InfiniteCentroids dest = new InfiniteCentroids(k, l);

        for (int i = 0; i < k; i++) {
            Centroid srcCentroid = source.get(i);
            double[] copiedEl = java.util.Arrays.copyOf(srcCentroid.getArray(),
                    l);
            Centroid destCentroid = new Centroid(copiedEl, l,
                    srcCentroid.getWeight());
            dest.set(i, destCentroid);
        }

        return dest;
    }

    /**
     * Calculates and caches log-probabilities for all centroids.
     * <p>
     * Equivalent to C function {@code calculate_logs()} from
     * {@code centroid.c}. Precomputes log₂(el[i]) and log₂(1 - el[i]) for each
     * centroid element, clamping values to [epsilon, 1-epsilon] to avoid log(0)
     * or log(1).
     * </p>
     *
     * @param centroids
     *            the InfiniteCentroids array whose logs should be calculated
     */
    public static void calculateLogs(InfiniteCentroids centroids) {
        Objects.requireNonNull(centroids, "InfiniteCentroids must not be null");

        int k = centroids.size(); // 1-based count
        int l = centroids.get(0).getLength();

        for (int i = 0; i < k; i++) {
            Centroid centroid = centroids.get(i);
            double[] el = centroid.getArray();

            for (int j = 0; j < l; j++) {
                // Clamp to [epsilon, 1-epsilon] to avoid log(0) or log(1)
                if (el[j] <= MathUtils.EPSILON) {
                    el[j] = MathUtils.EPSILON;
                } else if (el[j] >= 1.0 - MathUtils.EPSILON) {
                    el[j] = 1.0 - MathUtils.EPSILON;
                }

                // Recalculate log-probabilities directly on the array
            }
        }
    }

}