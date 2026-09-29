/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.compare;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.dist.DistanceCalculator;
import org.binclass.algorithms.gla.GLAEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compares two partitions and computes nearness metrics.
 * <p>
 * Mirrors functions from {@code compare.c} in the original C codebase: matches
 * vectors between partitions, builds a contingency matrix counting how many
 * vectors share each (P1-class, P2-class) pair, and calculates the distance
 * measure defined by {@code compute_distance()}.
 * </p>
 */
public final class PartitionComparator {

    private static final Logger logger = LoggerFactory
            .getLogger(PartitionComparator.class);

    /** Print mode constants (matching C source). */
    public static final int PRINT_NEARNESS = 1;
    public static final int PRINT_TOTALFREQ = 2;
    public static final int PRINT_PARTITION = 3;

    private PartitionComparator() {
        // Utility class — prevent instantiation
    }

    /**
     * Compares two partitions and returns the comparison result.
     * <p>
     * Equivalent to C functions {@code do_comparison()} combined with
     * {@code compute_distance()} from {@code compare.c}. Each vector is
     * assigned a class in both partitions, a contingency matrix is built from
     * those assignments, and the nearness distance is derived from it.
     * </p>
     *
     * @param partition1
     *            first partition (P1)
     * @param partition2
     *            second partition (P2)
     * @param printMode
     *            output mode: 1=nearness matrix, 2=total frequencies,
     *            3=partition comparison
     * @return the comparison result containing the contingency matrix and
     *         distance
     */
    public static ComparisonResult comparePartitions(Partition partition1,
            Partition partition2, int printMode) {
        return comparePartitions(partition1, partition2, printMode, false);
    }

    /**
     * Compares two partitions and returns the comparison result.
     * <p>
     * Equivalent to C functions {@code do_comparison()} combined with
     * {@code compute_distance()} from {@code compare.c}. Each vector is
     * assigned a class in both partitions, a contingency matrix is built from
     * those assignments, and the nearness distance is derived from it. When
     * {@code exactMatches} is set (C's {@code -M} flag) each vector keeps its
     * literal cluster index; otherwise vectors are reassigned to the nearest
     * centroid by Shannon codelength before matching
     * ({@code match_partition()}).
     * </p>
     *
     * @param partition1
     *            first partition (P1)
     * @param partition2
     *            second partition (P2)
     * @param printMode
     *            output mode: 1=nearness matrix, 2=total frequencies,
     *            3=partition comparison
     * @param exactMatches
     *            when {@code true}, use each vector's literal cluster index
     *            instead of nearest-centroid assignment
     * @return the comparison result containing the contingency matrix and
     *         distance
     */
    public static ComparisonResult comparePartitions(Partition partition1,
            Partition partition2, int printMode, boolean exactMatches) {
        Objects.requireNonNull(partition1, "Partition1 must not be null");
        Objects.requireNonNull(partition2, "Partition2 must not be null");

        if (printMode < 1 || printMode > 3) {
            throw new IllegalArgumentException(
                    "Print mode must be 1, 2, or 3, got: " + printMode);
        }

        int k1 = partition1.size();
        int k2 = partition2.size();
        logger.info("Comparing partitions: P1 size={}, P2 size={}", k1, k2);

        // The matrix dimensions follow C's allocate_imatrix((k2*2), k1): the
        // row axis indexes P2 classes and the column axis indexes P1 classes.
        int rows = Math.max(1, k2);
        int cols = Math.max(1, k1);

        int[][] matrix = new int[rows][cols];

        // Match every vector to a class in both partitions. The two partitions
        // share the same underlying vectors (they are generated from the same
        // input), so we collect all unique vectors and resolve each against
        // both assignments. Exact matching resolves by unique strain ID, which
        // mirrors C's is_in_set() lookup across independently-read files where
        // object references differ even for identical data.
        VectorSet allVectors = collectVectors(partition1);
        Centroid[] c1 = computeCentroids(partition1, k1);
        Centroid[] c2 = computeCentroids(partition2, k2);
        Map<String, Integer> idToClass1 = buildIdMap(partition1, k1);
        Map<String, Integer> idToClass2 = buildIdMap(partition2, k2);

        for (BinaryVector v : allVectors) {
            String id = v.getStrain();
            int p1Class = exactMatches ? lookup(id, idToClass1)
                    : nearestClass(v, c1, k1);
            int p2Class = exactMatches ? lookup(id, idToClass2)
                    : nearestClass(v, c2, k2);
            matrix[p2Class - 1][p1Class - 1]++;
        }

        double distance = computeDistance(matrix, rows, cols);
        logger.info("Comparison complete: distance={}", distance);

        return new ComparisonResult(matrix, distance, printMode);
    }

    /**
     * Collects all vectors from every cluster of a partition in insertion
     * order.
     *
     * @param partition
     *            the partition to read
     * @return a {@link VectorSet} holding every vector exactly once
     */
    private static VectorSet collectVectors(Partition partition) {
        VectorSet all = new VectorSet();
        for (int i = 1; i <= partition.size(); i++) {
            for (BinaryVector v : partition.getElements(i)) {
                all.addElement(v);
            }
        }
        return all;
    }

    /**
     * Builds a map from strain identifier to its cluster index in a partition.
     * <p>
     * Mirrors C's {@code is_in_set()} lookup by unique vector ID used when
     * matching partitions that were read independently and therefore hold
     * distinct object references for identical data.
     * </p>
     *
     * @param partition
     *            the partition to index
     * @param k
     *            number of clusters (1-based)
     * @return a map from strain identifier to 1-based class index
     */
    private static Map<String, Integer> buildIdMap(Partition partition, int k) {
        Map<String, Integer> idToClass = new HashMap<>();
        for (int i = 1; i <= k; i++) {
            for (BinaryVector v : partition.getElements(i)) {
                idToClass.putIfAbsent(v.getStrain(), i);
            }
        }
        return idToClass;
    }

    /**
     * Looks up the class index for a strain identifier, defaulting to 1 when
     * absent.
     *
     * @param id
     *            the strain identifier of the vector
     * @param idToClass
     *            map from strain identifier to 1-based class index
     * @return the matched class index, or 1 if the identifier is unknown
     */
    private static int lookup(String id, Map<String, Integer> idToClass) {
        return idToClass.getOrDefault(id, 1);
    }

    /**
     * Computes the centroid of each cluster in a partition.
     * <p>
     * Mirrors C {@code match_partition()} which builds infinite centroids via
     * {@code inf_average()} before assigning vectors. Uses the shared recompute
     * routine so centroids reflect the actual class composition.
     * </p>
     *
     * @param partition
     *            the partition whose clusters define the centroids
     * @param k
     *            number of clusters (1-based)
     * @return an array of {@link Centroid} instances indexed 0..k-1
     */
    private static Centroid[] computeCentroids(Partition partition, int k) {
        int length = getVectorLength(partition);
        InfiniteCentroids centroids = new InfiniteCentroids(k, length);
        GLAEngine.recomputeCentroids(partition, centroids, false,
                partition.size());
        Centroid[] result = new Centroid[k];
        for (int i = 0; i < k; i++) {
            result[i] = centroids.get(i);
        }
        return result;
    }

    /**
     * Determines the bit-length of vectors stored in a partition.
     * <p>
     * Iterates over every cluster and returns the length of the first vector
     * found, since all vectors in a valid partition share the same length.
     * Mirrors {@link VectorSet#getVectorLength()} but tolerates empty clusters.
     * </p>
     *
     * @param partition
     *            the partition to inspect
     * @return the vector bit-length, or 0 when no vectors are present
     */
    private static int getVectorLength(Partition partition) {
        for (int i = 1; i <= partition.size(); i++) {
            VectorSet cluster = partition.getElements(i);
            if (!cluster.isEmpty()) {
                return cluster.getVectorLength();
            }
        }
        return 0;
    }

    /**
     * Assigns a vector to the nearest centroid by Shannon codelength.
     * <p>
     * Mirrors C {@code match_partition()} non-exact mode, which builds infinite
     * centroids via {@code inf_average()} before assigning each vector to its
     * closest class.
     * </p>
     *
     * @param v
     *            the vector to assign
     * @param centroids
     *            precomputed centroids for each cluster (indexed 0..k-1)
     * @param k
     *            number of clusters (1-based)
     * @return the 1-based nearest class index
     */
    private static int nearestClass(BinaryVector v, Centroid[] centroids,
            int k) {
        if (centroids == null || k <= 0) {
            return 1;
        }
        int closest = 0;
        double minDist = Double.POSITIVE_INFINITY;
        for (int i = 0; i < k; i++) {
            double dist = DistanceCalculator.codeLength(v, centroids[i]);
            if (dist < minDist) {
                minDist = dist;
                closest = i;
            }
        }
        return closest + 1; // Convert to 1-based
    }

    /**
     * Computes the nearness distance from a contingency matrix.
     * <p>
     * Equivalent to C function {@code compute_distance()} from
     * {@code compare.c}: sums {@code (sum - max)} over every row and column,
     * then divides by two. The matrix is indexed as {@code [row][col]} where
     * rows correspond to P2 classes and columns to P1 classes.
     * </p>
     *
     * @param matrix
     *            the contingency matrix (1-based class counts)
     * @param rows
     *            number of row dimensions (P2 classes)
     * @param cols
     *            number of column dimensions (P1 classes)
     * @return the computed distance metric
     */
    private static double computeDistance(int[][] matrix, int rows, int cols) {
        int dist = 0;

        // Sum (sum - max) for each row.
        for (int r = 0; r < rows; r++) {
            int sum = 0;
            int max = 0;
            for (int c = 0; c < cols; c++) {
                sum += matrix[r][c];
                if (matrix[r][c] > max) {
                    max = matrix[r][c];
                }
            }
            dist += (sum - max);
        }

        // Sum (sum - max) for each column.
        for (int c = 0; c < cols; c++) {
            int sum = 0;
            int max = 0;
            for (int r = 0; r < rows; r++) {
                sum += matrix[r][c];
                if (matrix[r][c] > max) {
                    max = matrix[r][c];
                }
            }
            dist += (sum - max);
        }

        return dist / 2.0;
    }
}
