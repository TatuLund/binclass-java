/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.classify;

import java.util.Objects;
import java.util.Random;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.DynamicPartition;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.util.MathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cumulative classifier implementing Bayesian Predictive Identification.
 * <p>
 * Mirrors functions from {@code cumulat.c} in the original C codebase: performs
 * cumulative classification where vectors are processed one at a time, each new
 * vector potentially creating a new class or being assigned to an existing one.
 * Uses stochastic complexity (SC) as the criterion for class decisions.
 * </p>
 * <p>
 * Key operations:
 * <ul>
 * <li>{@link #doCumulativeClassification(VectorSet, int)} — main entry point
 * that processes vectors sequentially</li>
 * <li>{@link DynamicPartition} — dynamic partition with frequency and Hamming
 * distance tracking for efficient incremental updates</li>
 * </ul>
 * </p>
 */
public final class CumulativeClassifier {

    private static final String BINARY_VECTOR_MUST_NOT_BE_NULL = "BinaryVector must not be null";
    private static final String DYNAMIC_PARTITION_MUST_NOT_BE_NULL = "DynamicPartition must not be null";

    private static final Logger logger = LoggerFactory
            .getLogger(CumulativeClassifier.class);

    /** Random instance for vector ordering */
    private static final Random RANDOM = new Random();

    private CumulativeClassifier() {
        // Utility class — prevent instantiation
    }

    /**
     * Performs cumulative classification on a set of vectors.
     * <p>
     * Equivalent to C function {@code do_cumulative_classification()} from
     * {@code cumulat.c}. Processes vectors sequentially: for each vector,
     * decides whether to assign it to an existing class or create a new one
     * based on stochastic complexity (SC) improvement.
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to classify
     * @param delta
     *            delta value for predictive fit calculation (controls
     *            sensitivity to new class creation)
     * @return a DynamicPartition containing the cumulative classification
     *         results
     */
    public static DynamicPartition doCumulativeClassification(VectorSet vectors,
            int delta) {
        Objects.requireNonNull(vectors, "VectorSet must not be null");

        logger.info("Starting cumulative classification with delta={}", delta);

        if (vectors.size() == 0) {
            throw new IllegalArgumentException(
                    "Need at least one vector for cumulative classification");
        }

        // Convert to array and randomize order
        BinaryVector[] vectorArray = vectors.getElements()
                .toArray(new BinaryVector[0]);
        int n = vectorArray.length;

        // Randomize the processing order (equivalent to dp_redraw)
        shuffle(vectorArray);

        // Initialize with first vector as the first class
        DynamicPartition dynPart = initializeFromVector(vectorArray[0], delta);

        logger.debug("Initialized with {} classes from first vector",
                dynPart.size());

        // Process remaining vectors sequentially
        for (int i = 1; i < n; i++) {
            BinaryVector bv = vectorArray[i];

            if (dynPart.size() == 0) {
                // No classes yet — create new one
                dynPart = extendWithNewClass(dynPart, bv);
            } else {
                // Find best class or decide to create new one
                int bestClass = findBestClass(dynPart, bv, delta);

                if (bestClass == -1) {
                    // Create new class
                    dynPart = extendWithNewClass(dynPart, bv);
                    logger.debug("Vector {} created new class", i + 1);
                } else {
                    // Assign to existing class
                    assignToClass(dynPart, bv, bestClass);
                    logger.debug("Vector {} assigned to class {}", i + 1,
                            bestClass);
                }
            }
        }

        logger.info(
                "Cumulative classification complete: {} classes for {} vectors",
                dynPart.size(), n);
        return dynPart;
    }

    /**
     * Initializes a dynamic partition with a single class containing one
     * vector.
     * <p>
     * Equivalent to C function {@code dp_initialize()} from {@code cumulat.c}.
     * Creates the first class node and adds the initial vector.
     * </p>
     *
     * @param firstVector
     *            the first binary vector to initialize with
     * @param delta
     *            delta value for predictive fit calculation
     * @return a DynamicPartition with one class containing the first vector
     */
    public static DynamicPartition initializeFromVector(
            BinaryVector firstVector, int delta) {
        Objects.requireNonNull(firstVector, "First vector must not be null");

        int l = firstVector.getLength();
        DynamicPartition dynPart = new DynamicPartition(1, l);

        // Add the first vector to class 1
        assignToClass(dynPart, firstVector, 1);

        return dynPart;
    }

    /**
     * Extends a dynamic partition by creating a new class with one vector.
     * <p>
     * Equivalent to C function {@code dp_extend()} from {@code cumulat.c}.
     * Appends a new class node to the linked list of classes and adds the
     * vector.
     * </p>
     *
     * @param dynPart
     *            the current dynamic partition
     * @param bv
     *            the binary vector to add as a new class
     * @return an updated DynamicPartition with one additional class
     */
    public static DynamicPartition extendWithNewClass(DynamicPartition dynPart,
            BinaryVector bv) {
        Objects.requireNonNull(dynPart, DYNAMIC_PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(bv, BINARY_VECTOR_MUST_NOT_BE_NULL);

        int newK = dynPart.size() + 1;
        // Create a new dynamic partition with one more class and copy existing
        // data
        DynamicPartition newDynPart = createNewWithSize(newK, bv.getLength());

        // Copy all vectors from old partition to new one
        Partition oldStatic = dynPart.convert();
        for (int i = 1; i <= dynPart.size(); i++) {
            VectorSet cluster = oldStatic.getElements(i);
            if (cluster != null) {
                for (BinaryVector v : cluster) {
                    assignToClass(newDynPart, v, i);
                }
            }
        }

        // Add new vector to the new class
        assignToClass(newDynPart, bv, newK);

        return newDynPart;
    }

    /**
     * Finds the best class for a given vector based on stochastic complexity.
     * <p>
     * Equivalent to C function {@code dp_find_class_sc()} from
     * {@code cumulat.c}. Evaluates each existing class and returns the one that
     * maximizes predictive fit (minimizes SC increase). Returns -1 if no class
     * provides sufficient fit.
     * </p>
     *
     * @param dynPart
     *            the current dynamic partition
     * @param bv
     *            the binary vector to classify
     * @param delta
     *            delta value for predictive fit calculation
     * @return the 1-based class index, or -1 if no suitable class found
     */
    public static int findBestClass(DynamicPartition dynPart, BinaryVector bv,
            int delta) {
        Objects.requireNonNull(dynPart, DYNAMIC_PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(bv, BINARY_VECTOR_MUST_NOT_BE_NULL);

        // Calculate SC for creating a new class (matches C:
        // dp_stochastic_complexity_xnew)
        double bestSC = calculateNewClassSC(dynPart, bv);
        int bestClass = -1; // -1 means create a new class

        // Iterate through all existing classes to find the one with minimum SC
        for (int i = 1; i <= dynPart.size(); i++) {
            double scIncrease = calculateSCIncrease(dynPart, bv, i);
            if (scIncrease < bestSC) {
                bestSC = scIncrease;
                bestClass = i;
            } else if (scIncrease == bestSC && bestClass == -1) {
                // When SC is equal, prefer assigning to an existing class over
                // creating a new one
                bestClass = i;
            }
        }

        // If the best existing class has significantly higher SC than new class
        // + delta, create new class
        double newClassSC = calculateNewClassSC(dynPart, bv);
        if (bestClass != -1 && bestSC > newClassSC + delta) {
            return -1; // Create a new class
        }

        return bestClass;
    }

    /**
     * Assigns a vector to a specific class in the dynamic partition.
     * <p>
     * Equivalent to C function {@code dp_put_vector()} from {@code cumulat.c}.
     * Updates frequency tables and Hamming distance matrices when adding a
     * vector.
     * </p>
     *
     * @param dynPart
     *            the dynamic partition
     * @param bv
     *            the binary vector to assign
     * @param classIndex
     *            1-based class index (1..k)
     */
    public static void assignToClass(DynamicPartition dynPart, BinaryVector bv,
            int classIndex) {
        Objects.requireNonNull(dynPart, DYNAMIC_PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(bv, BINARY_VECTOR_MUST_NOT_BE_NULL);

        // Add to underlying partition and update frequency/Hamming tables
        dynPart.putVector(classIndex, bv);
    }

    /**
     * Calculates the stochastic complexity increase if a vector were assigned
     * to a class.
     * <p>
     * Equivalent to C function {@code dp_stochastic_complexity_x()} from
     * {@code cumulat.c}. Computes how much SC would change if the vector were
     * added to the specified class.
     * </p>
     *
     * @param dynPart
     *            the current dynamic partition
     * @param bv
     *            the binary vector being considered
     * @param classIndex
     *            1-based class index
     * @return the SC increase (lower is better)
     */
    public static double calculateSCIncrease(DynamicPartition dynPart,
            BinaryVector bv, int classIndex) {
        Objects.requireNonNull(dynPart, DYNAMIC_PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(bv, BINARY_VECTOR_MUST_NOT_BE_NULL);

        int[] freqs = dynPart.getFreqs(classIndex);
        int classSize = dynPart.getClusterSize(classIndex);
        int l = bv.getLength();

        if (classSize == 0) {
            // Empty class — cost is just the vector's own complexity
            return calculateVectorComplexity(bv, l);
        }

        double scIncrease = 0.0;
        for (int bit = 0; bit < l && bit < freqs.length; bit++) {
            int count1 = freqs[bit];

            // Current entropy contribution
            double currentEntropy = calculateBitEntropy(count1, classSize);

            // New entropy if vector is added (assuming bit value matches)
            int newCount1 = count1 + bv.get(bit);
            int newClassSize = classSize + 1;
            double newEntropy = calculateBitEntropy(newCount1, newClassSize);

            scIncrease += newEntropy - currentEntropy;
        }

        return scIncrease;
    }

    /**
     * Calculates the stochastic complexity of creating a new class with one
     * vector.
     * <p>
     * Equivalent to C function {@code dp_stochastic_complexity_xnew()} from
     * {@code cumulat.c}. The cost is simply the vector's own information
     * content.
     * </p>
     *
     * @param dynPart
     *            the current dynamic partition (used for context)
     * @param bv
     *            the binary vector being considered as a new class
     * @return the SC of creating a new singleton class
     */
    public static double calculateNewClassSC(DynamicPartition dynPart,
            BinaryVector bv) {
        Objects.requireNonNull(dynPart, DYNAMIC_PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(bv, BINARY_VECTOR_MUST_NOT_BE_NULL);

        return calculateVectorComplexity(bv, bv.getLength());
    }

    /**
     * Calculates the information content (complexity) of a single binary
     * vector.
     * <p>
     * Uses Shannon entropy: H(x) = -sum_i [x[i]*log2(x[i]) +
     * (1-x[i])*log2(1-x[i])]. For a deterministic bit, complexity is 0; for
     * uncertain bits, it's positive.
     * </p>
     *
     * @param bv
     *            the binary vector
     * @param l
     *            the length to consider (may differ from bv.getLength())
     * @return the information content in bits
     */
    public static double calculateVectorComplexity(BinaryVector bv, int l) {
        Objects.requireNonNull(bv, BINARY_VECTOR_MUST_NOT_BE_NULL);

        double complexity = 0.0;
        for (int i = 0; i < l && i < bv.getLength(); i++) {
            // For a deterministic bit (0 or 1), entropy is 0
            // For missing values, we use maximum entropy (1 bit)
            if (!bv.isMissing(i)) {
                complexity += 0.0; // Deterministic — no information cost
            } else {
                complexity += 1.0; // Missing — maximum uncertainty
            }
        }
        return complexity;
    }

    /**
     * Calculates the Shannon entropy for a single bit position given counts.
     * <p>
     * H(p) = -p*log2(p) - (1-p)*log2(1-p), where p = count1/total. Uses epsilon
     * clamping to avoid log(0).
     * </p>
     *
     * @param count1
     *            number of 1s in this bit position
     * @param total
     *            total number of vectors in the class
     * @return Shannon entropy in bits (0.0 to 1.0)
     */
    public static double calculateBitEntropy(int count1, int total) {
        if (total <= 0) {
            return 0.0;
        }

        double p = (double) count1 / total;

        // Clamp to avoid log(0)
        p = Math.max(MathUtils.EPSILON, Math.min(1.0 - MathUtils.EPSILON, p));

        // entropy formula: H(p) = -p*log2(p) - (1-p)*log2(1-p)
        return -(p * MathUtils.log2(p) + (1.0 - p) * MathUtils.log2(1.0 - p));
    }

    /**
     * Shuffles an array of BinaryVectors in place using Fisher-Yates algorithm.
     * <p>
     * Equivalent to C function {@code dp_redraw()} from {@code cumulat.c}.
     * Randomizes the processing order of vectors for cumulative classification.
     * </p>
     *
     * @param array
     *            the array to shuffle in place
     */
    public static void shuffle(BinaryVector[] array) {
        Objects.requireNonNull(array, "Array must not be null");

        for (int i = array.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            BinaryVector temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }

    /**
     * Creates a new DynamicPartition with the specified number of classes and
     * vector length.
     * <p>
     * Helper method to create partitions with proper initialization.
     * </p>
     *
     * @param k
     *            number of classes (1-based)
     * @param l
     *            vector length
     * @return a new DynamicPartition ready for use
     */
    public static DynamicPartition createNewWithSize(int k, int l) {
        return new DynamicPartition(k, l);
    }

}
