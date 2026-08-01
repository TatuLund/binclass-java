/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.dist;

import java.util.Objects;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;

/**
 * Nearest-neighbor assignment algorithms for the BinClass suite.
 * <p>
 * Mirrors functions from {@code distmin.c} in the original C codebase: assigns
 * each vector in a set to its nearest centroid using one of three distance
 * metrics (Shannon codelength, Hamming, L1). These are the core assignment
 * routines used by GLA iterations and classification.
 * </p>
 */
public final class NearestNeighbor {

    private static final String INFINITE_CENTROIDS_MUST_NOT_BE_NULL = "InfiniteCentroids must not be null";
    private static final String PARTITION_MUST_NOT_BE_NULL = "Partition must not be null";
    private static final String VECTOR_SET_MUST_NOT_BE_NULL = "VectorSet must not be null";

    private NearestNeighbor() {
        // Utility class — prevent instantiation
    }

    /**
     * Assigns vectors to nearest centroids using Shannon codelength (with or
     * without weights).
     * <p>
     * Equivalent to C function {@code inf_nearest_neighbour()} from
     * {@code distmin.c}. Uses code_length or code_length2 depending on the
     * {@code useWeights} flag. This is the primary assignment routine for
     * information-theoretic GLA variants.
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to assign (consumed — emptied after)
     * @param partition
     *            the target partition to populate with assignments
     * @param centroids
     *            the centroid array defining clusters
     * @param useWeights
     *            if true, uses weighted codelength; otherwise unweighted
     */
    public static void infNearestNeighbor(VectorSet vectors,
            Partition partition,
            InfiniteCentroids centroids, boolean useWeights) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        int k = centroids.size(); // 1-based count
        partition.setSize(k);

        for (BinaryVector vector : vectors) {
            int closest = 0;
            double minDist = useWeights
                    ? DistanceCalculator.codeLength2(vector, centroids.get(0))
                    : DistanceCalculator.codeLength(vector, centroids.get(0));

            for (int i = 1; i < k; i++) {
                double dist = useWeights
                        ? DistanceCalculator.codeLength2(vector,
                                centroids.get(i))
                        : DistanceCalculator.codeLength(vector,
                                centroids.get(i));
                if (dist < minDist) {
                    closest = i;
                    minDist = dist;
                }
            }

            partition.addElement(closest + 1, vector); // Convert to 1-based
        }
    }

    /**
     * Assigns vectors to nearest centroids using Hamming distance.
     * <p>
     * Equivalent to C function {@code fast_nearest_neighbour()} from
     * {@code distmin.c}. Uses integer-only Hamming distance for faster
     * computation — the "fast" variant of nearest neighbor assignment.
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to assign (consumed — emptied after)
     * @param partition
     *            the target partition to populate with assignments
     * @param centroids
     *            the centroid array defining clusters
     */
    public static void fastNearestNeighbor(VectorSet vectors,
            Partition partition,
            InfiniteCentroids centroids) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        int k = centroids.size(); // 1-based count
        partition.setSize(k);

        for (BinaryVector vector : vectors) {
            int closest = 0;
            int minDist = DistanceCalculator.hammingDistance(vector,
                    centroids.get(0));

            for (int i = 1; i < k; i++) {
                int dist = DistanceCalculator.hammingDistance(vector,
                        centroids.get(i));
                if (dist < minDist) {
                    closest = i;
                    minDist = dist;
                }
            }

            partition.addElement(closest + 1, vector); // Convert to 1-based
        }
    }

    /**
     * Assigns vectors to nearest centroids using L1 (Manhattan) distance.
     * <p>
     * Equivalent to C function {@code MAE_nearest_neighbour()} from
     * {@code distmin.c}. Uses absolute error distance — the "MAE" variant of
     * nearest neighbor assignment, suitable for robust clustering.
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to assign (consumed — emptied after)
     * @param partition
     *            the target partition to populate with assignments
     * @param centroids
     *            the centroid array defining clusters
     */
    public static void maeNearestNeighbor(VectorSet vectors,
            Partition partition,
            InfiniteCentroids centroids) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        int k = centroids.size(); // 1-based count
        partition.setSize(k);

        for (BinaryVector vector : vectors) {
            int closest = 0;
            double minDist = DistanceCalculator.l1Distance(vector,
                    centroids.get(0));

            for (int i = 1; i < k; i++) {
                double dist = DistanceCalculator.l1Distance(vector,
                        centroids.get(i));
                if (dist < minDist) {
                    closest = i;
                    minDist = dist;
                }
            }

            partition.addElement(closest + 1, vector); // Convert to 1-based
        }
    }

    /**
     * Assigns vectors to nearest centroids using L2 (squared Euclidean)
     * distance.
     * <p>
     * Equivalent to C function {@code MSE_nearest_neighbour()} from
     * {@code distmin.c}. Uses squared error distance — the "MSE" variant of
     * nearest neighbor assignment, suitable for least-squares clustering.
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to assign (consumed — emptied after)
     * @param partition
     *            the target partition to populate with assignments
     * @param centroids
     *            the centroid array defining clusters
     */
    public static void mseNearestNeighbor(VectorSet vectors,
            Partition partition,
            InfiniteCentroids centroids) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        int k = centroids.size(); // 1-based count
        partition.setSize(k);

        for (BinaryVector vector : vectors) {
            int closest = 0;
            double minDist = DistanceCalculator.l2Distance(vector,
                    centroids.get(0));

            for (int i = 1; i < k; i++) {
                double dist = DistanceCalculator.l2Distance(vector,
                        centroids.get(i));
                if (dist < minDist) {
                    closest = i;
                    minDist = dist;
                }
            }

            partition.addElement(closest + 1, vector); // Convert to 1-based
        }
    }

}