/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.generate;

import java.util.Objects;
import java.util.Random;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;

/**
 * Generates synthetic binary vectors for testing and demonstration.
 * <p>
 * Mirrors functions from {@code gendat.c} in the original C codebase: produces
 * random binary vectors using various probability distributions including
 * Bernoulli, Markov chain, and uniform random generation. These generators are
 * useful for creating test datasets with known properties.
 * </p>
 * <p>
 * Key operations:
 * <ul>
 * <li>{@link #bernoulliGen(Partition, int)} — generates vectors using Bernoulli
 * distribution based on cluster centroids</li>
 * <li>{@link #markovGen(VectorSet, int)} — generates Markov chain sequences
 * with transition probabilities from existing vectors</li>
 * <li>{@link #vectorGen(VectorSet, int)} — generates random binary vectors
 * using uniform distribution</li>
 * <li>{@link #randomGen(int)} — generates a single random binary vector of
 * specified length</li>
 * </ul>
 * </p>
 */
public final class DataGenerator {

    private static final String PARTITION_MUST_NOT_BE_NULL = "Partition must not be null";
    private static final String VECTOR_SET_MUST_NOT_BE_NULL = "VectorSet must not be null";

    /** Default random number generator for reproducible results */
    private static final Random DEFAULT_RANDOM = new Random(42); // Seed 42 for
                                                                 // reproducibility

    private DataGenerator() {
        // Utility class — prevent instantiation
    }

    /**
     * Generates binary vectors using Bernoulli distribution based on cluster
     * centroids.
     * <p>
     * Equivalent to C function {@code bernouli_gen()} from {@code gendat.c}.
     * For each bit position, generates a random value (0 or 1) with probability
     * equal to the centroid's probability at that position. This creates
     * vectors that statistically match the cluster's expected distribution.
     * </p>
     * <p>
     * Algorithm:
     * <ol>
     * <li>For each of the specified amount of vectors</li>
     * <li>For each bit position in the vector</li>
     * <li>Generate random number and compare to centroid probability</li>
     * <li>If random &lt; probability, set bit to 1, otherwise 0</li>
     * </ol>
     * </p>
     *
     * @param partition
     *            the partition containing cluster centroids (uses first k
     *            clusters)
     * @param amount
     *            number of vectors to generate per cluster
     * @return a VectorSet containing all generated vectors
     */
    public static VectorSet bernoulliGen(Partition partition, int amount) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);

        VectorSet result = new VectorSet();
        int k = partition.size(); // 1-based count of clusters

        // Get vector length from first cluster's first vector
        Centroid firstCentroid = getClusterCentroid(partition, 1);
        int l = firstCentroid.getLength();

        for (int i = 0; i < amount; i++) {
            for (int c = 1; c <= k; c++) {
                Centroid centroid = getClusterCentroid(partition, c);
                int[] el = new int[l];

                // Generate vector using Bernoulli distribution
                for (int bit = 0; bit < l; bit++) {
                    double prob = centroid.getArray()[bit];
                    if (DEFAULT_RANDOM.nextDouble() < prob) {
                        el[bit] = 1;
                    } else {
                        el[bit] = 0;
                    }
                }

                BinaryVector bv = new BinaryVector(el, l);
                result.addElement(bv);
            }
        }

        return result;
    }

    /**
     * Generates binary vectors using Markov chain transitions from existing
     * data.
     * <p>
     * Equivalent to C function {@code markov_gen()} from {@code gendat.c}.
     * Analyzes transition probabilities between consecutive bit positions in
     * the input vector set and generates new sequences that follow similar
     * patterns. This preserves local correlation structure present in the
     * original data.
     * </p>
     * <p>
     * Algorithm:
     * <ol>
     * <li>Compute transition probabilities from input vectors (P(bit[i+1]=1 |
     * bit[i]))</li>
     * <li>For each new vector, randomly select starting bit based on marginal
     * distribution</li>
     * <li>Generate subsequent bits using learned transition probabilities</li>
     * </ol>
     * </p>
     *
     * @param vectors
     *            the input VectorSet to learn Markov transitions from
     * @param amount
     *            number of new vectors to generate
     * @return a VectorSet containing all generated vectors
     */
    public static VectorSet markovGen(VectorSet vectors, int amount) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);

        if (vectors.isEmpty()) {
            return new VectorSet();
        }

        // Get vector length from first vector
        BinaryVector first = vectors.iterator().next();
        int l = first.getLength();

        // Compute transition probabilities: P(bit[i+1]=1 | bit[i])
        double[][] transitions = computeTransitionProbabilities(vectors, l);

        VectorSet result = new VectorSet();

        for (int i = 0; i < amount; i++) {
            int[] el = new int[l];

            // Randomly select starting bit based on marginal distribution
            el[0] = DEFAULT_RANDOM.nextDouble() < transitions[0][1] ? 1 : 0;

            // Generate subsequent bits using transition probabilities
            for (int j = 1; j < l; j++) {
                int prevBit = el[j - 1];
                double prob = transitions[prevBit][j > 0 ? j - 1 : 0];
                el[j] = DEFAULT_RANDOM.nextDouble() < prob ? 1 : 0;
            }

            BinaryVector bv = new BinaryVector(el, l);
            result.addElement(bv);
        }

        return result;
    }

    /**
     * Generates random binary vectors using uniform distribution.
     * <p>
     * Equivalent to C function {@code vector_gen()} from {@code gendat.c}.
     * Creates completely random binary vectors where each bit has 50%
     * probability of being 0 or 1, independent of other positions. Useful for
     * baseline testing.
     * </p>
     * <p>
     * Algorithm:
     * <ol>
     * <li>For each vector to generate</li>
     * <li>For each bit position</li>
     * <li>Generate random number and set bit to 1 if &lt; 0.5, otherwise 0</li>
     * </ol>
     * </p>
     *
     * @param vectors
     *            the input VectorSet (used only for vector length)
     * @param amount
     *            number of random vectors to generate
     * @return a VectorSet containing all generated random vectors
     */
    public static VectorSet vectorGen(VectorSet vectors, int amount) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);

        if (vectors.isEmpty()) {
            return new VectorSet();
        }

        // Get vector length from first vector
        BinaryVector first = vectors.iterator().next();
        int l = first.getLength();

        VectorSet result = new VectorSet();

        for (int i = 0; i < amount; i++) {
            int[] el = new int[l];

            // Generate random binary vector with uniform distribution
            for (int bit = 0; bit < l; bit++) {
                el[bit] = DEFAULT_RANDOM.nextDouble() < 0.5 ? 1 : 0;
            }

            BinaryVector bv = new BinaryVector(el, l);
            result.addElement(bv);
        }

        return result;
    }

    /**
     * Generates a single random binary vector of specified length.
     * <p>
     * Equivalent to C function {@code random_gen()} from {@code gendat.c}.
     * Creates a completely random binary vector where each bit has 50%
     * probability of being 0 or 1, independent of other positions. Useful for
     * quick testing.
     * </p>
     * <p>
     * Algorithm:
     * <ol>
     * <li>For each bit position from 0 to length-1</li>
     * <li>Generate random number and set bit to 1 if &lt; 0.5, otherwise 0</li>
     * </ol>
     * </p>
     *
     * @param amount
     *            the length of the vector (number of bits)
     * @return a single random BinaryVector with the specified length
     */
    public static BinaryVector randomGen(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "Amount must be positive, got: " + amount);
        }

        int[] el = new int[amount];

        // Generate random binary vector with uniform distribution
        for (int i = 0; i < amount; i++) {
            el[i] = DEFAULT_RANDOM.nextDouble() < 0.5 ? 1 : 0;
        }

        return new BinaryVector(el, amount);
    }

    /**
     * Computes transition probabilities from a set of binary vectors.
     * <p>
     * Helper method that analyzes consecutive bit pairs to learn Markov chain
     * transition probabilities. Returns a matrix where transitions[i][j] =
     * P(bit=1 | previous_bit=i).
     * </p>
     *
     * @param vectors
     *            the input VectorSet to analyze
     * @param l
     *            vector length (number of bits)
     * @return a 2x2 matrix of transition probabilities [prevBit][currentBit]
     */
    private static double[][] computeTransitionProbabilities(VectorSet vectors,
            int l) {
        // Count transitions: count[prevBit][currentBit]
        int[][] counts = new int[2][2];

        for (BinaryVector bv : vectors) {
            int[] el = bv.getEl();
            for (int i = 0; i < l - 1; i++) {
                int prevBit = el[i];
                int currentBit = el[i + 1];
                counts[prevBit][currentBit]++;
            }
        }

        // Convert to probabilities: P(currentBit=1 | prevBit)
        double[][] transitions = new double[2][l];
        for (int prevBit = 0; prevBit < 2; prevBit++) {
            int totalPrev = counts[prevBit][0] + counts[prevBit][1];
            if (totalPrev > 0) {
                transitions[prevBit][1] = (double) counts[prevBit][1]
                        / totalPrev;
            } else {
                // Default to 0.5 if no data for this previous bit
                transitions[prevBit][1] = 0.5;
            }
        }

        return transitions;
    }

    /**
     * Retrieves the centroid for a specific cluster in the partition.
     * <p>
     * Helper method that extracts centroid data from partition's vector
     * composition. Computes average bit values across all vectors in the
     * cluster to form the probability distribution.
     * </p>
     *
     * @param partition
     *            the partition containing clusters
     * @param clusterIndex
     *            1-based index of the target cluster
     * @return a Centroid representing the cluster's average bit probabilities
     */
    private static Centroid getClusterCentroid(Partition partition,
            int clusterIndex) {
        VectorSet clusterVectors = partition.getElements(clusterIndex);

        if (clusterVectors.isEmpty()) {
            throw new IllegalStateException(
                    "Cluster " + clusterIndex + " is empty");
        }

        // Get vector length from first vector in cluster
        BinaryVector first = clusterVectors.iterator().next();
        int l = first.getLength();

        // Compute average bit values across all vectors in cluster
        double[] avgBits = new double[l];
        int count = 0;

        for (BinaryVector bv : clusterVectors) {
            int[] el = bv.getEl();
            for (int i = 0; i < l && i < el.length; i++) {
                avgBits[i] += el[i];
            }
            count++;
        }

        // Normalize to get probabilities
        if (count > 0) {
            for (int i = 0; i < l; i++) {
                avgBits[i] /= count;
            }
        }

        return new Centroid(avgBits, l, 1.0); // Default weight of 1.0
    }
}
