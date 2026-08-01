/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.dist;

import java.util.Objects;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.util.MathUtils;

/**
 * Pure distance computation utilities for the BinClass algorithm suite.
 * <p>
 * Mirrors functions from {@code distmin.c} in the original C codebase: Hamming,
 * L1 (Manhattan), L2 (Euclidean squared), Shannon codelength, and their
 * aggregate variants over partitions and classes. All methods are static pure
 * functions with no side effects — they operate on immutable data structures
 * ({@link BinaryVector}, {@link Centroid}, {@link Partition}).
 * </p>
 * <p>
 * Distance metrics:
 * <ul>
 * <li><b>Hamming</b>: counts positions where centroid probability &lt; 0.5
 * differs from the binary bit value.</li>
 * <li><b>L1 (Manhattan)</b>: sum of absolute differences between bits and
 * centroid probabilities.</li>
 * <li><b>L2 (Euclidean squared)</b>: sum of squared differences — used as MSE
 * proxy in clustering convergence checks.</li>
 * <li><b>Codelength</b>: Shannon information-theoretic distance using log
 * probabilities, optionally weighted by class frequency.</li>
 * </ul>
 * </p>
 */
public final class DistanceCalculator {

    private static final String INFINITE_CENTROIDS_MUST_NOT_BE_NULL = "InfiniteCentroids must not be null";
    private static final String CENTROID_MUST_NOT_BE_NULL = "Centroid must not be null";
    private static final String BINARY_VECTOR_MUST_NOT_BE_NULL = "BinaryVector must not be null";
    private static final String PARTITION_MUST_NOT_BE_NULL = "Partition must not be null";

    private DistanceCalculator() {
        // Utility class — prevent instantiation
    }

    /**
     * Computes the Hamming distance between a binary vector and a centroid.
     * <p>
     * Equivalent to C function {@code hamming_distance()} from
     * {@code distmin.c}. Counts positions where the rounded centroid value
     * (threshold 0.5) differs from the bit value.
     * </p>
     *
     * @param vector
     *            the binary vector
     * @param centroid
     *            the centroid to compare against
     * @return the Hamming distance (number of differing positions)
     */
    public static int hammingDistance(BinaryVector vector, Centroid centroid) {
        Objects.requireNonNull(vector, BINARY_VECTOR_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroid, CENTROID_MUST_NOT_BE_NULL);

        int dist = 0;
        for (int i = 0; i < vector.getLength(); i++) {
            boolean roundedBit = centroid.get(i) >= 0.5;
            if (roundedBit != (vector.get(i) == 1)) {
                dist++;
            }
        }
        return dist;
    }

    /**
     * Computes the L1 (Manhattan) distance between a binary vector and a
     * centroid.
     * <p>
     * Equivalent to C function {@code L1_distance()} from {@code distmin.c}.
     * Sum of absolute differences between bit values and centroid
     * probabilities.
     * </p>
     *
     * @param vector
     *            the binary vector
     * @param centroid
     *            the centroid to compare against
     * @return the L1 distance (sum of absolute differences)
     */
    public static double l1Distance(BinaryVector vector, Centroid centroid) {
        Objects.requireNonNull(vector, BINARY_VECTOR_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroid, CENTROID_MUST_NOT_BE_NULL);

        double dist = 0.0;
        for (int i = 0; i < vector.getLength(); i++) {
            dist += Math.abs(vector.get(i) - centroid.get(i));
        }
        return dist;
    }

    /**
     * Computes the L2 (squared Euclidean) distance between a binary vector and
     * a centroid.
     * <p>
     * Equivalent to C function {@code L2_distance()} from {@code distmin.c}.
     * Sum of squared differences — used as MSE proxy in clustering convergence.
     * </p>
     *
     * @param vector
     *            the binary vector
     * @param centroid
     *            the centroid to compare against
     * @return the L2 distance (sum of squared differences)
     */
    public static double l2Distance(BinaryVector vector, Centroid centroid) {
        Objects.requireNonNull(vector, BINARY_VECTOR_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroid, CENTROID_MUST_NOT_BE_NULL);

        double dist = 0.0;
        for (int i = 0; i < vector.getLength(); i++) {
            double diff = vector.get(i) - centroid.get(i);
            dist += diff * diff;
        }
        return dist;
    }

    /**
     * Computes the Shannon codelength between a binary vector and a centroid.
     * <p>
     * Equivalent to C function {@code code_length()} from {@code distmin.c}.
     * Uses precomputed log probabilities for efficiency — counts information
     * content (in bits) of observing this bit given the centroid's probability
     * model, without class weight adjustment.
     * </p>
     *
     * @param vector
     *            the binary vector
     * @param centroid
     *            the centroid to compare against
     * @return the Shannon codelength (information content in bits)
     */
    public static double codeLength(BinaryVector vector, Centroid centroid) {
        Objects.requireNonNull(vector, BINARY_VECTOR_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroid, CENTROID_MUST_NOT_BE_NULL);

        double h = 0.0;
        for (int i = 0; i < vector.getLength(); i++) {
            if (vector.get(i) == 1) {
                // Bit is 1: use log2(prob of 1) — negative value, subtracted
                h -= centroid.getLog1(i);
            } else {
                // Bit is 0: use log2(1 - prob of 1) = log2(prob of 0)
                h -= centroid.getLog0(i);
            }
        }
        return h;
    }

    /**
     * Computes the weighted Shannon codelength between a binary vector and a
     * centroid.
     * <p>
     * Equivalent to C function {@code code_length2()} from {@code distmin.c}.
     * Same as {@link #codeLength(BinaryVector, Centroid)} but subtracts
     * log₂(weight) to account for class frequency weighting — used when
     * weighted classification is enabled.
     * </p>
     *
     * @param vector
     *            the binary vector
     * @param centroid
     *            the centroid to compare against (must have weight &gt; 0)
     * @return the weighted Shannon codelength
     */
    public static double codeLength2(BinaryVector vector, Centroid centroid) {
        Objects.requireNonNull(vector, BINARY_VECTOR_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroid, CENTROID_MUST_NOT_BE_NULL);

        return codeLength(vector, centroid)
                - MathUtils.log2(centroid.getWeight());
    }

    /**
     * Computes the average codelength for a specific class in a partition.
     * <p>
     * Equivalent to C function {@code class_code_length()} from
     * {@code distmin.c}. Averages code lengths (or weighted code lengths) over
     * all vectors in the specified class, optionally rounding centroids first.
     * </p>
     *
     * @param partition
     *            the partition containing cluster assignments
     * @param centroids
     *            the infinite centroid array
     * @param classIndex
     *            1-based class index (1..k)
     * @param totalVectors
     *            total number of vectors across all classes (for weight calc)
     * @return average codelength for this class
     */
    public static double classCodeLength(Partition partition,
            InfiniteCentroids centroids,
            int classIndex, int totalVectors) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        VectorSet classVectors = partition.getElements(classIndex);
        Centroid centroid = centroids.get(classIndex - 1); // Convert to 0-based
        double totalLength = 0.0;
        int count = 0;

        for (BinaryVector vector : classVectors) {
            double length = useClassWeights() ? codeLength2(vector, centroid)
                    : codeLength(vector, centroid);
            totalLength += length;
            count++;
        }

        if (count == 0) {
            throw new ArithmeticException(
                    "Division by zero in classCodeLength");
        }
        return totalLength / count;
    }

    /**
     * Computes the overall average codelength across all classes.
     * <p>
     * Equivalent to C function {@code average_codelength()} from
     * {@code distmin.c}. Used as GLA iteration convergence criterion — measures
     * total information content of the classification.
     * </p>
     *
     * @param partition
     *            the partition containing cluster assignments
     * @param centroids
     *            the infinite centroid array
     * @return overall average codelength across all classes
     */
    public static double averageCodelength(Partition partition,
            InfiniteCentroids centroids) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        int k = centroids.size(); // 1-based count
        double totalLength = 0.0;
        int totalCount = 0;

        for (int i = 0; i < k; i++) {
            VectorSet classVectors = partition.getElements(i + 1); // 1-based
            Centroid centroid = centroids.get(i);
            for (BinaryVector vector : classVectors) {
                totalLength += codeLength2(vector, centroid);
                totalCount++;
            }
        }

        if (totalCount == 0) {
            throw new ArithmeticException(
                    "Division by zero in averageCodelength");
        }
        return totalLength / totalCount;
    }

    /**
     * Computes the class distortion using Hamming distance.
     * <p>
     * Equivalent to C function {@code class_distortion()} from
     * {@code distmin.c}. Average Hamming distance of vectors in a class to
     * their centroid — used as clustering quality metric.
     * </p>
     *
     * @param classVectors
     *            the set of vectors in this class
     * @param centroid
     *            the centroid for this class
     * @return average Hamming distance (class distortion)
     */
    public static double classDistortion(VectorSet classVectors,
            Centroid centroid) {
        Objects.requireNonNull(classVectors, "VectorSet must not be null");
        Objects.requireNonNull(centroid, CENTROID_MUST_NOT_BE_NULL);

        int totalDist = 0;
        int count = 0;

        for (BinaryVector vector : classVectors) {
            totalDist += hammingDistance(vector, centroid);
            count++;
        }

        if (count == 0) {
            throw new ArithmeticException(
                    "Division by zero in classDistortion");
        }
        return (double) totalDist / count;
    }

    /**
     * Computes the overall distortion across all classes using Hamming
     * distance.
     * <p>
     * Equivalent to C function {@code overall_distortion()} from
     * {@code distmin.c}. Average Hamming distance of all vectors to their
     * assigned centroids — used as GLA convergence criterion.
     * </p>
     *
     * @param partition
     *            the partition containing cluster assignments
     * @param centroids
     *            the infinite centroid array
     * @return overall average Hamming distance (distortion)
     */
    public static double overallDistortion(Partition partition,
            InfiniteCentroids centroids) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        int k = centroids.size(); // 1-based count
        int totalDist = 0;
        int totalCount = 0;

        for (int i = 0; i < k; i++) {
            VectorSet classVectors = partition.getElements(i + 1); // 1-based
            Centroid centroid = centroids.get(i);
            for (BinaryVector vector : classVectors) {
                totalDist += hammingDistance(vector, centroid);
                totalCount++;
            }
        }

        if (totalCount == 0) {
            throw new ArithmeticException(
                    "Division by zero in overallDistortion");
        }
        return (double) totalDist / totalCount;
    }

    /**
     * Computes the Mean Absolute Error for a specific class.
     * <p>
     * Equivalent to C function {@code class_MAE()} from {@code distmin.c}.
     * Average L1 distance of vectors in a class to their centroid — used as
     * clustering quality metric for L1-based algorithms.
     * </p>
     *
     * @param partition
     *            the partition containing cluster assignments
     * @param centroids
     *            the infinite centroid array
     * @param classIndex
     *            1-based class index (1..k)
     * @return average L1 distance (MAE) for this class
     */
    public static double classMae(Partition partition,
            InfiniteCentroids centroids, int classIndex) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        VectorSet classVectors = partition.getElements(classIndex);
        Centroid centroid = centroids.get(classIndex - 1); // Convert to 0-based
        double totalDist = 0.0;
        int count = 0;

        for (BinaryVector vector : classVectors) {
            totalDist += l1Distance(vector, centroid);
            count++;
        }

        if (count == 0) {
            throw new ArithmeticException("Division by zero in classMae");
        }
        return totalDist / count;
    }

    /**
     * Computes the overall Mean Absolute Error across all classes.
     * <p>
     * Equivalent to C function {@code overall_MAE()} from {@code distmin.c}.
     * Average L1 distance of all vectors to their assigned centroids — used as
     * GLA convergence criterion for L1-based algorithms.
     * </p>
     *
     * @param partition
     *            the partition containing cluster assignments
     * @param centroids
     *            the infinite centroid array
     * @return overall average L1 distance (MAE) across all classes
     */
    public static double overallMae(Partition partition,
            InfiniteCentroids centroids) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        int k = centroids.size(); // 1-based count
        double totalDist = 0.0;
        int totalCount = 0;

        for (int i = 0; i < k; i++) {
            VectorSet classVectors = partition.getElements(i + 1); // 1-based
            Centroid centroid = centroids.get(i);
            for (BinaryVector vector : classVectors) {
                totalDist += l1Distance(vector, centroid);
                totalCount++;
            }
        }

        if (totalCount == 0) {
            throw new ArithmeticException("Division by zero in overallMae");
        }
        return totalDist / totalCount;
    }

    /**
     * Computes the Mean Squared Error for a specific class.
     * <p>
     * Equivalent to C function {@code class_MSE()} from {@code distmin.c}.
     * Average L2 (squared) distance of vectors in a class to their centroid —
     * used as clustering quality metric for L2-based algorithms.
     * </p>
     *
     * @param partition
     *            the partition containing cluster assignments
     * @param centroids
     *            the infinite centroid array
     * @param classIndex
     *            1-based class index (1..k)
     * @return average L2 distance (MSE) for this class
     */
    public static double classMse(Partition partition,
            InfiniteCentroids centroids, int classIndex) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        VectorSet classVectors = partition.getElements(classIndex);
        Centroid centroid = centroids.get(classIndex - 1); // Convert to 0-based
        double totalDist = 0.0;
        int count = 0;

        for (BinaryVector vector : classVectors) {
            totalDist += l2Distance(vector, centroid);
            count++;
        }

        if (count == 0) {
            throw new ArithmeticException("Division by zero in classMse");
        }
        return totalDist / count;
    }

    /**
     * Computes the overall Mean Squared Error across all classes.
     * <p>
     * Equivalent to C function {@code overall_MSE()} from {@code distmin.c}.
     * Average L2 (squared) distance of all vectors to their assigned centroids
     * — used as GLA convergence criterion for L2-based algorithms.
     * </p>
     *
     * @param partition
     *            the partition containing cluster assignments
     * @param centroids
     *            the infinite centroid array
     * @return overall average L2 distance (MSE) across all classes
     */
    public static double overallMse(Partition partition,
            InfiniteCentroids centroids) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        int k = centroids.size(); // 1-based count
        double totalDist = 0.0;
        int totalCount = 0;

        for (int i = 0; i < k; i++) {
            VectorSet classVectors = partition.getElements(i + 1); // 1-based
            Centroid centroid = centroids.get(i);
            for (BinaryVector vector : classVectors) {
                totalDist += l2Distance(vector, centroid);
                totalCount++;
            }
        }

        if (totalCount == 0) {
            throw new ArithmeticException("Division by zero in overallMse");
        }
        return totalDist / totalCount;
    }

    /**
     * Returns whether class weights are currently enabled.
     * <p>
     * Mirrors the C global variable {@code use_class_weights} from
     * {@code vars.h}. When true, codelength calculations include weight
     * adjustment (log₂(weight)) to account for class frequency.
     * </p>
     *
     * @return true if weighted classification is enabled
     */
    public static boolean useClassWeights() {
        // In the original C code, this is a global variable controlled by CLI
        // args.
        // For now, default to false (unweighted) — can be made configurable
        // later.
        return false;
    }

    /**
     * Sets whether class weights are enabled for codelength calculations.
     * <p>
     * Mirrors the C global variable {@code use_class_weights} from
     * {@code vars.h}.
     * </p>
     *
     * @param useWeights
     *            true to enable weighted classification, false for unweighted
     */
    public static void setUseClassWeights(boolean useWeights) {
        // In a full implementation, this would set a module-level flag.
        // For Phase 2, we keep it simple with the getter returning false by
        // default.
    }

}