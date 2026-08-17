/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.compare;

import java.util.Objects;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Partition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compares two partitions and computes nearness metrics.
 * <p>
 * Mirrors functions from {@code compare.c} in the original C codebase: performs
 * partition comparison by matching vectors between partitions, computing
 * transition matrices, and calculating distance measures.
 * </p>
 */
public final class PartitionComparator {

    private static final Logger logger = LoggerFactory
            .getLogger(PartitionComparator.class);

    /** Print mode constants (matching C source) */
    public static final int PRINT_NEARNESS = 1;
    public static final int PRINT_TOTALFREQ = 2;
    public static final int PRINT_PARTITION = 3;

    private PartitionComparator() {
        // Utility class — prevent instantiation
    }

    /**
     * Compares two partitions and returns the comparison result.
     * <p>
     * Equivalent to C function {@code do_comparison()} from {@code compare.c}.
     * Matches vectors between partitions, builds transition matrices, and
     * computes distance metrics.
     * </p>
     *
     * @param partition1
     *            first partition (P1)
     * @param partition2
     *            second partition (P2)
     * @param printMode
     *            output mode: 1=nearness matrix, 2=total frequencies,
     *            3=partition comparison
     * @return the distance between the two partitions
     */
    public static double comparePartitions(Partition partition1,
            Partition partition2, int printMode) {
        Objects.requireNonNull(partition1, "Partition1 must not be null");
        Objects.requireNonNull(partition2, "Partition2 must not be null");

        logger.info("Comparing partitions: P1 size={}, P2 size={}",
                partition1.size(), partition2.size());

        // Validate print mode
        if (printMode < 1 || printMode > 3) {
            throw new IllegalArgumentException(
                    "Print mode must be 1, 2, or 3, got: " + printMode);
        }

        int k1 = partition1.size();
        int k2 = partition2.size();

        logger.info("P1 has {} classes, P2 has {} classes", k1, k2);

        // Build transition matrix (simplified version)
        double[][] transitionMatrix = buildTransitionMatrix(partition1,
                partition2);

        // Compute distance metric
        double distance = computeDistance(transitionMatrix, k1, k2);

        logger.info("Comparison complete: distance={}", distance);

        return distance;
    }

    /**
     * Builds a transition matrix between two partitions.
     * <p>
     * Equivalent to C function {@code do_comparison()} from {@code compare.c}.
     * Counts how many vectors from each class in P1 map to each class in P2.
     * </p>
     *
     * @param partition1
     *            first partition (P1)
     * @param partition2
     *            second partition (P2)
     * @return transition matrix where [i][j] = count of vectors from class i in
     *         P1 that are also in class j in P2
     */
    private static double[][] buildTransitionMatrix(Partition partition1,
            Partition partition2) {
        int k1 = partition1.size();
        int k2 = partition2.size();

        // Initialize transition matrix (k1 x k2)
        double[][] matrix = new double[k1 + 1][k2 + 1]; // 1-based indexing

        logger.debug("Building transition matrix: {}x{}", k1, k2);

        // For each class in P1, count vectors that appear in each class of P2
        for (int i = 1; i <= k1; i++) {
            var classVectors1 = partition1.getElements(i);

            for (BinaryVector v : classVectors1) {
                // Find which class this vector belongs to in P2
                int classInP2 = findClassForVector(partition2, v);

                if (classInP2 > 0) {
                    matrix[i][classInP2]++;
                }
            }
        }

        return matrix;
    }

    /**
     * Finds which class a vector belongs to in a partition.
     * <p>
     * Searches through all classes and returns the 1-based class index, or -1
     * if not found.
     * </p>
     *
     * @param partition
     *            the partition to search
     * @param vector
     *            the binary vector to locate
     * @return 1-based class index, or -1 if not found
     */
    private static int findClassForVector(Partition partition,
            BinaryVector vector) {
        for (int i = 1; i <= partition.size(); i++) {
            var classVectors = partition.getElements(i);

            for (BinaryVector v : classVectors) {
                if (v.equals(vector)) {
                    return i;
                }
            }
        }

        return -1; // Not found
    }

    /**
     * Computes distance metric from transition matrix.
     * <p>
     * Equivalent to C function {@code compute_distance()} from
     * {@code compare.c}. Calculates the sum of (sum - max) for each row and
     * column in the matrix, then divides by 2.
     * </p>
     *
     * @param matrix
     *            transition matrix
     * @param k1
     *            number of classes in P1
     * @param k2
     *            number of classes in P2
     * @return distance metric between the two partitions
     */
    private static double computeDistance(double[][] matrix, int k1, int k2) {
        int dist = 0;

        // Sum (sum - max) for each row (P1 classes)
        for (int i = 1; i <= k1; i++) {
            int sum = 0;
            int max = 0;

            for (int j = 1; j <= k2; j++) {
                double value = matrix[i][j];
                sum += (int) value;
                if ((int) value > max) {
                    max = (int) value;
                }
            }

            dist += (sum - max);
        }

        // Sum (sum - max) for each column (P2 classes)
        for (int j = 1; j <= k2; j++) {
            int sum = 0;
            int max = 0;

            for (int i = 1; i <= k1; i++) {
                double value = matrix[i][j];
                sum += (int) value;
                if ((int) value > max) {
                    max = (int) value;
                }
            }

            dist += (sum - max);
        }

        return ((double) dist) / 2.0;
    }
}
