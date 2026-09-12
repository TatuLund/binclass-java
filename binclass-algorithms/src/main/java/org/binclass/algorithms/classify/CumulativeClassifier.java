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
     * Performs cumulative classification on a set of vectors with full
     * configuration.
     */
    public static DynamicPartition doCumulativeClassification(VectorSet vectors,
            CumulativeConfig config) {
        Objects.requireNonNull(vectors, "VectorSet must not be null");
        Objects.requireNonNull(config, "CumulativeConfig must not be null");

        logger.info("Starting cumulative classification with config={}",
                config);

        if (vectors.size() == 0) {
            throw new IllegalArgumentException(
                    "Need at least one vector for cumulative classification");
        }

        BinaryVector[] vectorArray = vectors.getElements()
                .toArray(new BinaryVector[0]);
        int n = vectorArray.length;

        // Shuffle vectors unless inOrder mode is enabled (-O flag)
        if (!config.inOrder()) {
            shuffle(vectorArray);
        } else {
            logger.debug("Processing vectors in input order (inOrder=true)");
        }

        DynamicPartition dynPart = initializeFromVector(vectorArray[0]);

        logger.debug("Initialized with {} classes from first vector",
                dynPart.size());

        for (int i = 1; i < n; i++) {
            BinaryVector bv = vectorArray[i];
            // extendWithNewClass returns a new DynamicPartition, so the caller
            // must reassign to keep growing the partition.
            dynPart = processVector(dynPart, bv, i, config);
        }

        logger.info(
                "Cumulative classification complete: {} classes for {} vectors",
                dynPart.size(), n);
        return dynPart;
    }

    /**
     * Processes a single vector through the cumulative classification
     * algorithm.
     */
    private static DynamicPartition processVector(DynamicPartition dynPart,
            BinaryVector bv, int i, CumulativeConfig config) {
        if (dynPart.size() == 0) {
            return extendWithNewClass(dynPart, bv);
        }

        int bestClass = findBestClassForVector(dynPart, bv, config);
        if (bestClass == -1) {
            // A new class was created; extendWithNewClass returns a fresh
            // DynamicPartition that must replace the current one.
            dynPart = extendWithNewClass(dynPart, bv);
            logger.debug("Vector {} created new class", i + 1);
        } else {
            assignToClass(dynPart, bv, bestClass);
            logger.debug("Vector {} assigned to class {}", i + 1, bestClass);
        }

        applyCumulativeAnalysisCheckpoint(i, config.cumulativeAnalysis());
        applySamplingCheckpoint(i, config.cumulativeSamples());
        if (config.testFeatureSignificance()) {
            testAndLogFeatureSignificance();
        }
        if (config.cumSaveByPf() && i % 10 == 0) {
            logger.debug("Predictive fit checkpoint at vector {}", i + 1);
        }
        return dynPart;
    }

    /**
     * Finds the best class for a vector based on configuration.
     */
    private static int findBestClassForVector(DynamicPartition dynPart,
            BinaryVector bv, CumulativeConfig config) {
        if (config.bayesianPredictive()) {
            // Default mode mirrors C's dp_find_class: Bayesian predictive
            // identification. Always returns an existing class index; a new
            // class is created only when every existing class fits worse than
            // the initial-probability cost of starting one.
            return findBestBayesianClass(dynPart, bv, config);
        }
        // -S mode: stochastic complexity selection (dp_find_class_sc).
        return findBestSCClass(dynPart, bv, config);
    }

    /**
     * Bayesian predictive class selection. Mirrors C {@code dp_find_class} with
     * {@code bayesian_predictive=true}. Returns the existing class index with
     * minimum predictive distance, or -1 when creating a new class is cheaper
     * than any existing class (bounded by delta).
     */
    private static int findBestBayesianClass(DynamicPartition dynPart,
            BinaryVector bv, CumulativeConfig config) {
        Objects.requireNonNull(dynPart, DYNAMIC_PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(bv, BINARY_VECTOR_MUST_NOT_BE_NULL);

        // cum_no_new_classes mirrors C's dp_find_class: start with class 1 as
        // the incumbent and never fall back to a new class.
        if (config.cumNoNewClasses()) {
            return findBestExistingClass(dynPart, bv, config.epsilon());
        }

        int l = bv.getLength();
        double newClassCost = calculateInitialProb(l - 1,
                Math.max(config.delta(), 1));
        double bestDist = newClassCost;
        int bestClass = -1;

        for (int i = 1; i <= dynPart.size(); i++) {
            double dist = calculateBayesianDistance(dynPart, bv, i);
            if (dist < bestDist) {
                bestDist = dist;
                bestClass = i;
            }
        }
        return bestClass;
    }

    /**
     * Stochastic complexity class selection. Mirrors C
     * {@code dp_find_class_sc}. Returns the existing class index with minimum
     * SC increase, or -1 when creating a new class is cheaper than any existing
     * class (bounded by delta).
     */
    private static int findBestSCClass(DynamicPartition dynPart,
            BinaryVector bv, CumulativeConfig config) {
        Objects.requireNonNull(dynPart, DYNAMIC_PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(bv, BINARY_VECTOR_MUST_NOT_BE_NULL);

        // Mirrors C's dp_find_class_sc. In cum_no_new_classes mode the
        // incumbent starts as "new class" with cost SC_xnew and is returned as
        // -1 when no existing class beats it (matching processVector's
        // new-class
        // sentinel); otherwise it starts at class 1 and only existing classes
        // are compared against, so a new class is never returned.
        if (config.cumNoNewClasses()) {
            double dmin = calculateStochasticComplexityXnew(dynPart, bv);
            int imin = -1;
            for (int i = 1; i <= dynPart.size(); i++) {
                double d = calculateStochasticComplexityX(dynPart, i, bv);
                if (d < dmin) {
                    dmin = d;
                    imin = i;
                }
            }
            logger.debug("SC-n: newclass={} bestClass={} bestCost={}",
                    dmin, imin == -1 ? "NEW" : String.valueOf(imin), dmin);
            return imin;
        }

        int imin = 1;
        double dmin = calculateStochasticComplexityX(dynPart, 1, bv);
        for (int i = 2; i <= dynPart.size(); i++) {
            double d = calculateStochasticComplexityX(dynPart, i, bv);
            if (d < dmin) {
                dmin = d;
                imin = i;
            }
        }
        return imin;
    }

    /**
     * Finds the existing class with the minimum stochastic complexity increase
     * without ever creating a new class.
     * <p>
     * Equivalent to C {@code dp_find_class} in {@code cum_no_new_classes} mode.
     * </p>
     */
    private static int findBestExistingClass(DynamicPartition dynPart,
            BinaryVector bv, double epsilon) {
        Objects.requireNonNull(dynPart, DYNAMIC_PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(bv, BINARY_VECTOR_MUST_NOT_BE_NULL);

        int bestClass = 1;
        double bestSC = calculateSCIncrease(dynPart, bv, 1, epsilon);
        for (int i = 2; i <= dynPart.size(); i++) {
            double scIncrease = calculateSCIncrease(dynPart, bv, i, epsilon);
            if (scIncrease < bestSC) {
                bestSC = scIncrease;
                bestClass = i;
            }
        }
        return bestClass;
    }

    /**
     * Applies cumulative analysis checkpoint when enabled.
     */
    private static void applyCumulativeAnalysisCheckpoint(int i,
            boolean enabled) {
        if (enabled && i % 10 == 0) {
            logger.debug("Cumulative analysis checkpoint at vector {}", i + 1);
        }
    }

    /**
     * Applies sampling checkpoint when enabled.
     */
    private static void applySamplingCheckpoint(int i, int samples) {
        if (samples > 0 && i % samples == 0) {
            logger.debug("Sampling checkpoint at vector {}", i + 1);
        }
    }

    /**
     * Tests and logs feature significance for the given vector.
     */
    private static void testAndLogFeatureSignificance() {
        // Placeholder for feature significance testing logic
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
            BinaryVector firstVector) {
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
     * @param epsilon
     *            minimum probability value to avoid log(0) in entropy calc
     * @return the 1-based class index, or -1 if no suitable class found
     */
    public static int findBestClass(DynamicPartition dynPart, BinaryVector bv,
            int delta, double epsilon) {
        Objects.requireNonNull(dynPart, DYNAMIC_PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(bv, BINARY_VECTOR_MUST_NOT_BE_NULL);

        // Calculate SC for creating a new class (matches C:
        // dp_stochastic_complexity_xnew)
        double bestSC = calculateNewClassSC(dynPart, bv);
        int bestClass = -1; // -1 means create a new class

        // Iterate through all existing classes to find the one with minimum SC
        for (int i = 1; i <= dynPart.size(); i++) {
            double scIncrease = calculateSCIncrease(dynPart, bv, i, epsilon);
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
     * @param epsilon
     *            minimum probability value to avoid log(0) in entropy calc
     * @return the SC increase (lower is better)
     */
    public static double calculateSCIncrease(DynamicPartition dynPart,
            BinaryVector bv, int classIndex, double epsilon) {
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
            double currentEntropy = calculateBitEntropy(count1, classSize,
                    epsilon);

            // New entropy if vector is added (assuming bit value matches)
            int newCount1 = count1 + bv.get(bit);
            int newClassSize = classSize + 1;
            double newEntropy = calculateBitEntropy(newCount1, newClassSize,
                    epsilon);

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
     * @param epsilon
     *            minimum probability value to avoid log(0)
     * @return Shannon entropy in bits (0.0 to 1.0)
     */
    public static double calculateBitEntropy(int count1, int total,
            double epsilon) {
        if (total <= 0) {
            return 0.0;
        }

        double p = (double) count1 / total;

        // Clamp to avoid log(0)
        p = Math.clamp(p, epsilon, 1.0 - epsilon);

        // entropy formula: H(p) = -p*log2(p) - (1-p)*log2(1-p)
        return -(p * MathUtils.log2(p) + (1.0 - p) * MathUtils.log2(1.0 - p));
    }

    /**
     * Bayesian predictive distance {@code dp_wij(s,n)} = log2(n+1) -
     * log2(s-n+1).
     */
    private static double dpWij(int s, int n) {
        return MathUtils.log2(n + 1) - MathUtils.log2(s - n + 1);
    }

    /**
     * Bayesian predictive term {@code dp_lambdaj(s)} = log2(s+1).
     */
    private static double dpLambdaj(int s) {
        return MathUtils.log2(s + 1);
    }

    /**
     * Bayesian predictive term {@code dp_bj(P,s,l)} = sum of [log2(s-nij[i]+1)
     * - log2(s+2)] over bit positions.
     * <p>
     * The frequency table is taken from the class being evaluated so that each
     * {@code nij} stays within {@code [0, s]} and never produces a negative
     * argument to {@link MathUtils#log2(double)}.
     * </p>
     */
    private static double dpBj(int[] freqs, int s, int l) {
        double bj = 0.0;
        for (int i = 0; i < l && i < freqs.length; i++) {
            int hmo = (freqs[i] / (double) s < 0.5) ? 0 : 1;
            int nij = (hmo == 0) ? freqs[i] : (s - freqs[i]);
            bj += MathUtils.log2(s - nij + 1) - MathUtils.log2(s + 2);
        }
        return bj;
    }

    /**
     * Calculates the Bayesian predictive distance {@code dp_prob} for assigning
     * a vector to an existing class.
     * <p>
     * Equivalent to C function {@code dp_prob()} from {@code cumulat.c}. Lower
     * values indicate a better fit; the caller returns the class (or new-class
     * option) with the minimum distance.
     * </p>
     */
    public static double calculateBayesianDistance(DynamicPartition dynPart,
            BinaryVector bv, int classIndex) {
        Objects.requireNonNull(dynPart, DYNAMIC_PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(bv, BINARY_VECTOR_MUST_NOT_BE_NULL);

        int s = dynPart.getClusterSize(classIndex);
        if (s == 0) {
            return Double.POSITIVE_INFINITY;
        }
        int l = bv.getLength();
        int[] freqs = dynPart.getFreqs(classIndex);
        double d = 0.0;
        for (int i = 0; i < l && i < freqs.length; i++) {
            int hmo = (freqs[i] / (double) s < 0.5) ? 0 : 1;
            int nij = (hmo == 1) ? (s - freqs[i]) : freqs[i];
            d += dpWij(s, nij) * ((bv.get(i) != hmo) ? 1.0 : 0.0);
        }
        d += dpBj(freqs, s, l);
        d += dpLambdaj(s);
        return -d;
    }

    /**
     * Calculates the initial probability cost of creating a new class,
     * {@code dp_initial_prob(l,d)} = l - log2(d).
     */
    public static double calculateInitialProb(int l, int d) {
        return l - MathUtils.log2(d);
    }

    /**
     * Calculates the stochastic complexity of creating a new class with one
     * vector using the exact C {@code dp_stochastic_complexity_xnew()} formula.
     * <p>
     * Unlike {@link #calculateNewClassSC}, this uses log₂(factorial) terms so
     * deterministic vectors incur a meaningful, non-zero cost.
     * </p>
     */
    public static double calculateStochasticComplexityXnew(
            DynamicPartition dynPart, BinaryVector bv) {
        Objects.requireNonNull(dynPart, DYNAMIC_PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(bv, BINARY_VECTOR_MUST_NOT_BE_NULL);

        int l = bv.getLength();
        int k = dynPart.size();
        int[] freqs = dynPart.getFreqs(1);
        int n = dynPart.getClusterSize(1) + 1; // total vectors after adding x
        double sc2 = 0.0;
        for (int bit = 0; bit < l && bit < freqs.length; bit++) {
            sc2 += MathUtils.log2Factorial(2);
            sc2 -= MathUtils.log2Factorial(bv.get(bit));
            sc2 -= MathUtils.log2Factorial(1 - bv.get(bit));
        }
        double sc1 = MathUtils.log2Factorial(n);
        sc1 += MathUtils.log2Factorial(n + k); // n+k-1 with k already 0-based
                                               // count+...
        return (sc1 + sc2) / n;
    }

    /**
     * Calculates the stochastic complexity of adding a vector {@code x} to an
     * existing class, using the exact C {@code dp_stochastic_complexity_x()}
     * factorial coding scheme.
     * <p>
     * Unlike {@link #calculateSCIncrease}, which uses Shannon entropy deltas on
     * a much smaller scale, this mirrors C's {@code dp_stochastic_complexity_x}
     * so the existing-class cost is directly comparable to the new-class cost
     * returned by {@link #calculateStochasticComplexityXnew}. This is what lets
     * stochastic-complexity mode ({@code -S}) create multiple classes on skewed
     * data instead of collapsing to a single class.
     * </p>
     *
     * @param dynPart
     *            the current dynamic partition
     * @param classIndex
     *            the 1-based index of the existing class being evaluated
     * @param bv
     *            the binary vector being considered for assignment
     * @return the stochastic complexity per vector of adding {@code bv} to the
     *         given class
     */
    public static double calculateStochasticComplexityX(
            DynamicPartition dynPart, int classIndex, BinaryVector bv) {
        Objects.requireNonNull(dynPart, DYNAMIC_PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(bv, BINARY_VECTOR_MUST_NOT_BE_NULL);

        int l = bv.getLength();
        int k = dynPart.size(); // total number of existing classes (C's final
                                // k)
        double sc2 = 0.0;
        int n = 0;
        for (int i = 1; i <= k; i++) {
            int[] f = dynPart.getFreqs(i);
            int size = dynPart.getClusterSize(i);
            if (i == classIndex) {
                // case 1: x is added to this class
                n += size + 1;
                for (int bit = 0; bit < l && bit < f.length; bit++) {
                    sc2 += MathUtils.log2Factorial(size + 2);
                    int newCount1 = f[bit] + bv.get(bit);
                    sc2 -= MathUtils.log2Factorial(newCount1);
                    sc2 -= MathUtils.log2Factorial((size + 1) - newCount1);
                }
            } else {
                // case 2: class is unchanged
                n += size;
                for (int bit = 0; bit < l && bit < f.length; bit++) {
                    sc2 += MathUtils.log2Factorial(size + 1);
                    sc2 -= MathUtils.log2Factorial(f[bit]);
                    sc2 -= MathUtils.log2Factorial(size - f[bit]);
                }
            }
        }

        // coding of the codebook
        double sc1 = MathUtils.log2Factorial(n);
        for (int i = 1; i <= k; i++) {
            int size = dynPart.getClusterSize(i);
            if (i == classIndex) {
                sc1 -= MathUtils.log2Factorial(size + 1);
            } else {
                sc1 -= MathUtils.log2Factorial(size);
            }
        }
        sc1 += MathUtils.log2Factorial(n + k - 1);
        sc1 -= MathUtils.log2Factorial(n);
        sc1 -= MathUtils.log2Factorial(k - 1);

        return (sc1 + sc2) / n;
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
