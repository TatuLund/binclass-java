/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.dist;

import java.util.Objects;

import org.binclass.algorithms.core.AlgorithmConfig;
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

    /** Module-level flag for weighted codelength calculations. */
    private static boolean useClassWeightsFlag = false;

    /** Module-level flag for rounded centroid calculations. */
    private static boolean useRoundedCentroidsFlag = false;

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
     * Computes the Hamming distance between two binary vectors.
     * <p>
     * Counts the number of positions where the bits differ. Used for finding
     * worst-matching vector pairs in clustering algorithms.
     * </p>
     *
     * @param v1
     *            first binary vector
     * @param v2
     *            second binary vector
     * @return the Hamming distance (number of differing positions)
     */
    public static int hammingDistanceVectors(BinaryVector v1, BinaryVector v2) {
        Objects.requireNonNull(v1, BINARY_VECTOR_MUST_NOT_BE_NULL);
        Objects.requireNonNull(v2, BINARY_VECTOR_MUST_NOT_BE_NULL);

        int dist = 0;
        for (int i = 0; i < v1.getLength(); i++) {
            if ((v1.get(i) == 1) != (v2.get(i) == 1)) {
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
     * all vectors in the specified class, optionally rounding centroids first
     * using {@link #infAverage(VectorSet, Centroid, boolean, int)}.
     * </p>
     *
     * @param partition
     *            the partition containing cluster assignments
     * @param centroids
     *            the infinite centroid array
     * @param classIndex
     *            1-based class index (1..k)
     * @param totalVectors
     *            total number of vectors across all classes — used to compute
     *            centroid weights when {@code roundedCentroids} is true,
     *            matching the C parameter {@code s}
     * @return average codelength for this class
     */
    public static double classCodeLength(Partition partition,
            InfiniteCentroids centroids,
            int classIndex, int totalVectors) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        VectorSet classVectors = partition.getElements(classIndex);
        Centroid centroid = centroids.get(classIndex - 1); // Convert to 0-based

        if (useRoundedCentroids()) {
            infAverage(classVectors, centroid, true, totalVectors);
        }

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
            // Empty cluster has zero distortion
            return 0.0;
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

    /** LPI constant from C's const.h: log(PI) ≈ 1.6514961 */
    private static final double LPI = 1.6514961;

    /**
     * Computes bit frequencies for a class of vectors.
     * <p>
     * Equivalent to C function {@code freq()} from {@code distmin.c}. Counts
     * the number of 1-bits at each position across all vectors in the set.
     * </p>
     *
     * @param vectors
     *            the vector set (must not be null)
     * @param d
     *            the dimensionality (length of binary vectors)
     * @return array of length {@code d} where element {@code i} contains the
     *         count of 1-bits at position {@code i} across all vectors
     */
    private static int[] computeBitFrequencies(VectorSet vectors, int d) {
        Objects.requireNonNull(vectors, "VectorSet must not be null");

        int[] freqs = new int[d]; // indices 0..d-1 (C uses 1..d)
        for (BinaryVector vector : vectors) {
            for (int i = 0; i < d; i++) {
                if (!vector.isMissing(i)) {
                    freqs[i] += vector.get(i);
                }
            }
        }
        return freqs;
    }

    /**
     * Computes stochastic complexity using Jeffreys prior.
     * <p>
     * Equivalent to C function {@code stochastic_complexity_j()} from
     * {@code distmin.c}. Uses Jeffreys prior for Bayesian model selection with
     * log-gamma functions and 0.5 offsets.
     * </p>
     *
     * @param partition
     *            the partition to evaluate (1-based class indices)
     * @param k
     *            number of clusters (1-based, must be &gt;= 2)
     * @param d
     *            dimensionality of binary vectors
     * @return stochastic complexity value using Jeffreys prior
     */
    public static double stochasticComplexityJeffreys(Partition partition,
            int k,
            int d) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);

        if (k < 1 || k > partition.size()) {
            throw new IllegalArgumentException(
                    "Invalid number of clusters: " + k);
        }

        // Allocate arrays for class sizes and bit frequencies
        int[] classSizes = new int[k]; // class sizes (indices 0..k-1, C uses
                                       // 1..k)
        int t = 0; // total elements

        // Calculate sizes of classes and total number of vectors
        for (int j = 1; j < k; j++) {
            classSizes[j] = partition.getSize(j);
            if (classSizes[j] == 0) {
                throw new IllegalStateException(
                        "Empty cluster in stochastic_complexity_j");
            }
            t += classSizes[j];
        }

        double kVal = k - 1;
        double dVal = d - 1;

        // The part for coding the class no
        double h = ((dVal * kVal) + (kVal / 2.0)) * LPI;
        h += MathUtils.log2Gamma(kVal / 2.0);
        h += MathUtils.log2Gamma(t + kVal / 2.0);
        for (int j = 1; j < k; j++) {
            h -= MathUtils.log2Gamma(classSizes[j] + 0.5);
        }

        // The part for coding the bits
        int[] bitFreqs = new int[d]; // bit frequencies (indices 0..d-1, C uses
                                     // 1..d)
        for (int j = 1; j < k; j++) {
            VectorSet classVectors = partition.getElements(j);
            bitFreqs = computeBitFrequencies(classVectors, d);
            for (int i = 1; i < d; i++) {
                h += MathUtils.log2Factorial(classSizes[j]);
                h -= MathUtils.log2Gamma(bitFreqs[i] + 0.5);
                h -= MathUtils.log2Gamma((classSizes[j] - bitFreqs[i]) + 0.5);
            }
        }

        return h / t;
    }

    /**
     * Computes stochastic complexity using uniform prior.
     * <p>
     * Equivalent to C function {@code stochastic_complexity_u()} from
     * {@code distmin.c}. Uses uniform prior with log-factorial functions.
     * </p>
     *
     * @param partition
     *            the partition to evaluate (1-based class indices)
     * @param k
     *            number of clusters (1-based, must be &gt;= 2)
     * @param d
     *            dimensionality of binary vectors
     * @return stochastic complexity value using uniform prior
     */
    public static double stochasticComplexityUniform(Partition partition, int k,
            int d) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);

        if (k < 1 || k > partition.size()) {
            throw new IllegalArgumentException(
                    "Invalid number of clusters: " + k);
        }

        // Allocate arrays for class sizes and bit frequencies
        int[] classSizes = new int[k]; // class sizes (indices 0..k-1, C uses
                                       // 1..k)
        int t = 0; // total elements

        // Calculate sizes of classes and total number of vectors
        for (int j = 1; j < k; j++) {
            classSizes[j] = partition.getSize(j);
            if (classSizes[j] == 0) {
                throw new IllegalStateException(
                        "Empty cluster in stochastic_complexity_u");
            }
            t += classSizes[j];
        }

        // The part for coding the class no
        double h1 = MathUtils.log2Factorial(t);
        for (int j = 1; j < k; j++) {
            h1 -= MathUtils.log2Factorial(classSizes[j]);
        }
        // k is one too big, thus minus extra one
        h1 += MathUtils.log2Factorial(t + k - 2);
        h1 -= MathUtils.log2Factorial(t);
        // k is one too big, thus minus extra one
        h1 -= MathUtils.log2Factorial(k - 2);

        // The part for coding the bits
        double h2 = 0.0;
        int[] bitFreqs = new int[d]; // bit frequencies (indices 0..d-1, C uses
                                     // 1..d)
        for (int j = 1; j < k; j++) {
            VectorSet classVectors = partition.getElements(j);
            bitFreqs = computeBitFrequencies(classVectors, d);
            for (int i = 1; i < d; i++) {
                h2 += MathUtils.log2Factorial(classSizes[j] + 1);
                h2 -= MathUtils.log2Factorial(bitFreqs[i]);
                h2 -= MathUtils.log2Factorial(classSizes[j] - bitFreqs[i]);
            }
        }

        return (h1 + h2) / t;
    }

    /**
     * Computes the Stochastic Complexity for a partition.
     * <p>
     * Equivalent to C function {@code stochastic_complexity()} from
     * {@code distmin.c}. Dispatches between Jeffreys prior and uniform prior
     * variants based on the {@code jeffreysPrior} flag.
     * </p>
     *
     * @param partition
     *            the partition to evaluate
     * @param k
     *            number of clusters (1-based)
     * @param l
     *            length of binary vectors
     * @param jeffreysPrior
     *            if true, use Jeffreys prior; otherwise use uniform prior
     * @return stochastic complexity value (lower is better for model selection)
     */
    public static double stochasticComplexity(Partition partition, int k,
            int l, boolean jeffreysPrior) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);

        if (k <= 0 || k > partition.size()) {
            throw new IllegalArgumentException(
                    "Invalid number of clusters: " + k);
        }

        return jeffreysPrior ? stochasticComplexityJeffreys(partition, k, l)
                : stochasticComplexityUniform(partition, k, l);
    }

    /**
     * Computes the Stochastic Complexity for a partition using uniform prior.
     * <p>
     * Equivalent to C function {@code stochastic_complexity()} from
     * {@code distmin.c} with {@code use_jeffreys_prior = false}. Measures the
     * information content of a partition — used as model selection criterion in
     * GLA algorithms.
     * </p>
     *
     * @param partition
     *            the partition to evaluate
     * @param k
     *            number of clusters (1-based)
     * @param l
     *            length of binary vectors
     * @return stochastic complexity value using uniform prior
     */
    public static double stochasticComplexity(Partition partition, int k,
            int l) {
        return stochasticComplexity(partition, k, l, false);
    }

    /**
     * Computes stochastic complexity with distortion term for MDL-based model
     * selection.
     * <p>
     * Uses the Minimum Description Length (MDL) principle: SC = (n/2) *
     * log2(D/n) + k * log2(n) + entropy_term
     * 
     * Where D is the total average distortion (average distance to centroid per
     * vector).
     * </p>
     *
     * @param partition
     *            the partition containing cluster assignments
     * @param k
     *            number of clusters (1-based)
     * @param l
     *            length of binary vectors
     * @param averageDistortion
     *            average distortion per vector (for all clusters combined)
     * @return stochastic complexity value including distortion term (lower is
     *         better)
     */
    public static double stochasticComplexityWithDistortion(Partition partition,
            int k,
            int l, double averageDistortion) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);

        if (k <= 0 || k > partition.size()) {
            throw new IllegalArgumentException(
                    "Invalid number of clusters: " + k);
        }

        int totalElements = 0;

        // Calculate total elements across all classes
        for (int i = 1; i <= k; i++) {
            totalElements += partition.getSize(i);
        }

        if (totalElements == 0) {
            return 0.0;
        }

        // Start with entropy-based cost for cluster assignments
        double sc = 0.0;
        for (int i = 1; i <= k; i++) {
            int classSize = partition.getSize(i);
            if (classSize > 0) {
                double freq = (double) classSize / totalElements;
                sc -= freq * MathUtils.log2(freq);
            }
        }

        // Add MDL cost: data encoding cost based on distortion
        // Use small epsilon to avoid log(0) when distortion is very small
        double D = Math.max(averageDistortion,
                AlgorithmConfig.NUMERICAL_STABILITY_EPSILON);
        double dataCost = (totalElements / 2.0)
                * MathUtils.log2(D / totalElements);
        sc += dataCost;

        // Add complexity penalty: model encoding cost for k clusters and l
        // dimensions
        double modelCost = k * MathUtils.log2(l);
        sc += modelCost;

        return sc;
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
            return 0.0; // No vectors means no distortion
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
        return useClassWeightsFlag;
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
        useClassWeightsFlag = useWeights;
    }

    /**
     * Returns whether rounded centroids are currently enabled.
     * <p>
     * Mirrors the C global variable {@code rounded_centroids} from
     * {@code vars.h}. When true, centroid computation rounds probabilities to 0
     * or 1 before distance calculations.
     * </p>
     *
     * @return true if rounded centroids are enabled
     */
    public static boolean useRoundedCentroids() {
        return useRoundedCentroidsFlag;
    }

    /**
     * Sets whether rounded centroids are enabled for centroid calculations.
     * <p>
     * Mirrors the C global variable {@code rounded_centroids} from
     * {@code vars.h}.
     * </p>
     *
     * @param useRounded
     *            true to enable rounding, false for fractional centroids
     */
    public static void setUseRoundedCentroids(boolean useRounded) {
        useRoundedCentroidsFlag = useRounded;
    }

    /**
     * Computes weighted centroid from a vector set, mirroring C function
     * {@code inf_average()} from {@code distmin.c}.
     * <p>
     * Accumulates bit counts across all vectors in the class, computes per-
     * position averages, and optionally rounds to 0/1. Sets centroid weight as
     * (class_size / total_vectors) for frequency-based weighting.
     * </p>
     *
     * @param vectorSet
     *            the set of vectors in this class
     * @param centroid
     *            the centroid to update with computed values
     * @param rounded
     *            if true, round probabilities to 0 or 1 (threshold 0.5)
     * @param totalVectors
     *            total number of vectors across all classes — used for weight
     *            calculation: {@code weight = classSize / totalVectors}
     */
    private static void infAverage(VectorSet vectorSet, Centroid centroid,
            boolean rounded, int totalVectors) {
        Objects.requireNonNull(vectorSet, "VectorSet must not be null");
        Objects.requireNonNull(centroid, CENTROID_MUST_NOT_BE_NULL);

        // Get length from first vector (mirrors C: l = V->el->length)
        int numBits = vectorSet.getVectorLength();

        // Accumulate bit counts per position (mirrors C: el[i] += w[i])
        double[] bitCounts = new double[numBits];
        int classSize = 0;

        for (BinaryVector vector : vectorSet) {
            for (int i = 0; i < numBits; i++) {
                bitCounts[i] += vector.get(i);
            }
            classSize++;
        }
        if (classSize == 0) {
            throw new ArithmeticException(
                    "Cannot compute centroid for empty class");
        }

        // Compute averages per position (mirrors C: x->el[i] = el[i] / n)
        for (int i = 0; i < numBits; i++) {
            centroid.set(i, bitCounts[i] / classSize);
        }

        // Set weight based on class frequency (mirrors C: x->weight = n / s)
        double weight = (double) classSize / (double) totalVectors;
        if (weight < 1e-7) { // EPS from const.h
            weight = 1e-7;
        }
        centroid.setWeight(weight);

        // Optionally round to 0 or 1 (mirrors C: if (rounded) ...)
        if (rounded) {
            for (int i = 0; i < numBits; i++) {
                double value = centroid.get(i);
                centroid.set(i, value < 0.5 ? 0.0 : 1.0);
            }
        }
    }

}