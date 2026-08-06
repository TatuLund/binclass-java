/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.classify;

import java.util.Objects;
import java.util.Random;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.data.DoubleVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mixture classifier implementing the EM (Expectation-Maximization) algorithm.
 * <p>
 * Mirrors functions from {@code mixture.c} in the original C codebase: performs
 * mixture classification using Bernoulli distributions weighted by class
 * priors. The EM algorithm iteratively refines centroid estimates and class
 * weights to maximize the likelihood of observing the data under a mixture
 * model.
 * </p>
 * <p>
 * Key operations:
 * <ul>
 * <li>{@link #applyMixtureClassifier(VectorSet, InfiniteCentroids, int)} — runs
 * full EM algorithm with E-step and M-step iterations</li>
 * <li>{@link #calculateMatrix(InfiniteCentroids, VectorSet, DoubleVector, int)}
 * — computes posterior probability matrix (E-step)</li>
 * <li>{@link #updateWeights(DoubleVector, double[][], int, int)} — updates
 * class weights from posterior probabilities</li>
 * <li>{@link #updateCentroids(InfiniteCentroids, double[][], VectorSet, int, int, int)}
 * — re-estimates centroids from weighted data (M-step)</li>
 * </ul>
 * </p>
 */
public final class MixtureClassifier {

    private static final String PROBABILITY_MATRIX_MUST_NOT_BE_NULL = "Probability matrix must not be null";
    private static final String WEIGHTS_VECTOR_MUST_NOT_BE_NULL = "Weights vector must not be null";
    private static final String INFINITE_CENTROIDS_MUST_NOT_BE_NULL = "InfiniteCentroids must not be null";
    private static final String VECTOR_SET_MUST_NOT_BE_NULL = "VectorSet must not be null";

    private static final Logger logger = LoggerFactory
            .getLogger(MixtureClassifier.class);

    /** Epsilon for numerical stability in probability calculations */
    private static final double EPSILON = 1e-10;

    /** Maximum number of EM iterations before convergence check */
    private static final int MAX_ITERATIONS = 100;

    /** Convergence threshold for log-likelihood change */
    private static final double CONVERGENCE_THRESHOLD = 1e-6;

    private MixtureClassifier() {
        // Utility class — prevent instantiation
    }

    /**
     * Applies the mixture classifier using the EM algorithm.
     * <p>
     * Equivalent to C function {@code apply_mixture_classifier()} from
     * {@code mixture.c}. Runs the full EM algorithm: initializes centroids and
     * weights, then iterates E-step (calculate posterior probabilities) and
     * M-step (update centroids and weights) until convergence or max
     * iterations.
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to classify
     * @param centroids
     *            initial centroid array (will be updated in-place by EM)
     * @param m
     *            number of mixture components (clusters)
     * @return the final InfiniteCentroids with updated centroid values and
     *         weights
     */
    public static InfiniteCentroids applyMixtureClassifier(VectorSet vectors,
            InfiniteCentroids centroids, int m) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        int n = vectors.size();
        if (n == 0) {
            throw new IllegalArgumentException(
                    "Need at least one vector for mixture classification");
        }

        logger.info("Starting EM algorithm: {} vectors, {} components", n, m);

        // Initialize weights uniformly if not already set
        DoubleVector weights = initializeWeights(m);

        double prevLogLikelihood = Double.NEGATIVE_INFINITY;

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            logger.debug("EM iteration {}", iter + 1);

            // E-step: calculate posterior probability matrix
            double[][] probMatrix = calculateMatrix(centroids, vectors, weights,
                    m);

            // M-step: update weights and centroids
            updateWeights(weights, probMatrix, m, n);
            updateCentroids(centroids, probMatrix, vectors, m, n);

            // Check convergence using log-likelihood
            double logLikelihood = computeLogLikelihood(probMatrix, weights, n,
                    m);
            logger.debug("Iteration {}: log-likelihood = {}", iter + 1,
                    logLikelihood);

            if (Math.abs(logLikelihood
                    - prevLogLikelihood) < CONVERGENCE_THRESHOLD) {
                logger.info("EM converged after {} iterations", iter + 1);
                break;
            }
            prevLogLikelihood = logLikelihood;
        }

        // Set weights on centroids for downstream use
        for (int i = 0; i < m; i++) {
            centroids.get(i).setWeight(weights.get(i));
        }

        logger.info(
                "EM algorithm complete: {} iterations, final log-likelihood = {}",
                MAX_ITERATIONS, prevLogLikelihood);
        return centroids;
    }

    /**
     * Calculates the posterior probability matrix (E-step of EM).
     * <p>
     * Equivalent to C function {@code calculate_matrix()} from
     * {@code mixture.c}. For each vector and each component, computes
     * P(component | vector) using Bayes' theorem with Bernoulli likelihoods.
     * </p>
     *
     * @param centroids
     *            the current centroid estimates (m components)
     * @param vectors
     *            the set of binary vectors (n observations)
     * @param weights
     *            current class prior probabilities (sum to 1.0)
     * @param m
     *            number of mixture components
     * @return an n×m matrix of posterior probabilities
     */
    public static double[][] calculateMatrix(InfiniteCentroids centroids,
            VectorSet vectors,
            DoubleVector weights, int m) {
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(weights, WEIGHTS_VECTOR_MUST_NOT_BE_NULL);

        int n = vectors.size();
        double[][] probMatrix = new double[n][m];

        BinaryVector[] vectorArray = vectors.getElements()
                .toArray(new BinaryVector[0]);

        for (int i = 0; i < n; i++) {
            BinaryVector bv = vectorArray[i];
            int d = bv.getLength();

            // Compute marginal probability P(x) = sum_j w_j * P(x|j)
            double marginalProb = computeMarginalProbability(bv, centroids,
                    weights, m, d);

            for (int j = 0; j < m; j++) {
                double likelihood = computeBernoulliLikelihood(bv,
                        centroids.get(j), d);
                probMatrix[i][j] = (weights.get(j) * likelihood) / marginalProb;
            }
        }

        return probMatrix;
    }

    /**
     * Updates class weights from the posterior probability matrix.
     * <p>
     * Equivalent to C function {@code update_weights()} from {@code mixture.c}.
     * New weight for component j = (1/n) * sum_i P(j | x_i).
     * </p>
     *
     * @param weights
     *            the weight vector to update in-place
     * @param probMatrix
     *            the posterior probability matrix from E-step
     * @param m
     *            number of mixture components
     * @param n
     *            number of observations
     */
    public static void updateWeights(DoubleVector weights,
            double[][] probMatrix, int m, int n) {
        Objects.requireNonNull(weights, WEIGHTS_VECTOR_MUST_NOT_BE_NULL);
        Objects.requireNonNull(probMatrix,
                PROBABILITY_MATRIX_MUST_NOT_BE_NULL);

        for (int j = 0; j < m; j++) {
            double sum = 0.0;
            for (int i = 0; i < n; i++) {
                sum += probMatrix[i][j];
            }
            weights.set(j, sum / n);
        }

        // Normalize to ensure weights sum to 1.0
        double totalWeight = 0.0;
        for (int j = 0; j < m; j++) {
            totalWeight += weights.get(j);
        }
        if (totalWeight > 0) {
            for (int j = 0; j < m; j++) {
                weights.set(j, weights.get(j) / totalWeight);
            }
        }
    }

    /**
     * Updates centroid estimates from the posterior probability matrix
     * (M-step).
     * <p>
     * Equivalent to C function {@code update_centroids()} from
     * {@code mixture.c}. For each component j and bit position k:
     * new_centroid[j][k] = sum_i P(j|x_i) * x_i[k] / sum_i P(j|x_i).
     * </p>
     *
     * @param centroids
     *            the centroid array to update in-place
     * @param probMatrix
     *            the posterior probability matrix from E-step
     * @param vectors
     *            the set of binary vectors
     * @param m
     *            number of mixture components
     * @param n
     *            number of observations
     */
    public static void updateCentroids(InfiniteCentroids centroids,
            double[][] probMatrix,
            VectorSet vectors, int m, int n) {
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);
        Objects.requireNonNull(probMatrix,
                PROBABILITY_MATRIX_MUST_NOT_BE_NULL);
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);

        BinaryVector[] vectorArray = vectors.getElements()
                .toArray(new BinaryVector[0]);

        for (int j = 0; j < m; j++) {
            Centroid centroid = centroids.get(j);
            int d = centroid.getLength();

            // Compute weighted sums for each bit position
            double[] numerator = new double[d];
            double denominator = 0.0;

            for (int i = 0; i < n; i++) {
                BinaryVector bv = vectorArray[i];
                double prob = probMatrix[i][j];
                denominator += prob;

                for (int k = 0; k < d && k < bv.getLength(); k++) {
                    numerator[k] += prob * bv.get(k);
                }
            }

            // Update centroid values: P(bit=1 | component j) = weighted average
            if (denominator > EPSILON) {
                for (int k = 0; k < d; k++) {
                    double val = numerator[k] / denominator;
                    // Clamp to [epsilon, 1-epsilon] for numerical stability
                    val = Math.max(EPSILON, Math.min(1.0 - EPSILON, val));
                    centroid.set(k, val);
                }
            }
        }
    }

    /**
     * Computes the Bernoulli likelihood P(x | component j) for a single vector.
     * <p>
     * Equivalent to C function {@code m_fx()} from {@code mixture.c}. Uses the
     * product of individual bit probabilities under the Bernoulli model: P(x|j)
     * = prod_k (b_j[k]^x[k] * (1-b_j[k])^(1-x[k])).
     * </p>
     *
     * @param vector
     *            the binary vector
     * @param centroid
     *            the centroid representing component j's Bernoulli parameters
     * @param d
     *            the dimension (vector length)
     * @return the likelihood P(x | component j)
     */
    public static double computeBernoulliLikelihood(BinaryVector vector,
            Centroid centroid, int d) {
        Objects.requireNonNull(vector, "BinaryVector must not be null");
        Objects.requireNonNull(centroid, "Centroid must not be null");

        double likelihood = 1.0;
        for (int k = 0; k < d && k < vector.getLength(); k++) {
            double prob = centroid.get(k);
            // Clamp to avoid log(0) in downstream computations
            prob = Math.max(EPSILON, Math.min(1.0 - EPSILON, prob));

            if (vector.get(k) == 1) {
                likelihood *= prob;
            } else {
                likelihood *= (1.0 - prob);
            }
        }
        return likelihood;
    }

    /**
     * Computes the marginal probability P(x) for a single vector under the
     * mixture model.
     * <p>
     * Equivalent to C function {@code m_fM()} from {@code mixture.c}. Sums
     * weighted Bernoulli likelihoods across all components: P(x) = sum_j w_j *
     * P(x|j).
     * </p>
     *
     * @param vector
     *            the binary vector
     * @param centroids
     *            the centroid array (m components)
     * @param weights
     *            class prior probabilities
     * @param m
     *            number of mixture components
     * @param d
     *            the dimension (vector length)
     * @return the marginal probability P(x)
     */
    public static double computeMarginalProbability(BinaryVector vector,
            InfiniteCentroids centroids,
            DoubleVector weights, int m, int d) {
        Objects.requireNonNull(vector, "BinaryVector must not be null");
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);
        Objects.requireNonNull(weights, WEIGHTS_VECTOR_MUST_NOT_BE_NULL);

        double marginalProb = 0.0;
        for (int j = 0; j < m; j++) {
            double likelihood = computeBernoulliLikelihood(vector,
                    centroids.get(j), d);
            marginalProb += weights.get(j) * likelihood;
        }
        return marginalProb;
    }

    /**
     * Computes the log-likelihood of the data under the current mixture model.
     * <p>
     * Used for convergence checking: LL = sum_i log(P(x_i)).
     * </p>
     *
     * @param probMatrix
     *            the posterior probability matrix
     * @param weights
     *            class prior probabilities
     * @param n
     *            number of observations
     * @param m
     *            number of mixture components
     * @return the log-likelihood value
     */
    public static double computeLogLikelihood(double[][] probMatrix,
            DoubleVector weights, int n, int m) {
        Objects.requireNonNull(probMatrix,
                PROBABILITY_MATRIX_MUST_NOT_BE_NULL);
        Objects.requireNonNull(weights, WEIGHTS_VECTOR_MUST_NOT_BE_NULL);

        // Compute marginal probabilities for each observation
        double logLikelihood = 0.0;
        for (int i = 0; i < n; i++) {
            double marginalProb = 0.0;
            for (int j = 0; j < m; j++) {
                marginalProb += weights.get(j) * probMatrix[i][j];
            }
            if (marginalProb > EPSILON) {
                logLikelihood += Math.log(marginalProb);
            } else {
                logLikelihood += Math.log(EPSILON);
            }
        }
        return logLikelihood;
    }

    /**
     * Initializes class weights with random values that sum to 1.0.
     * <p>
     * Equivalent to C function {@code random_weights()} from {@code mixture.c}.
     * Generates uniform random weights in [0.05, 1.05] and normalizes them.
     * </p>
     *
     * @param m
     *            number of mixture components
     * @return a DoubleVector with initialized weights summing to 1.0
     */
    public static DoubleVector initializeWeights(int m) {
        if (m <= 0) {
            throw new IllegalArgumentException(
                    "Number of components must be positive");
        }

        DoubleVector weights = new DoubleVector(m);
        Random random = new Random();

        // Generate random weights in [0.05, 1.05] and normalize
        double sum = 0.0;
        for (int j = 0; j < m; j++) {
            double w = random.nextDouble() + 0.05;
            weights.set(j, w);
            sum += w;
        }

        if (sum == 0) {
            throw new IllegalStateException(
                    "Sum of random weights must be positive");
        }

        // Normalize to sum to 1.0
        for (int j = 0; j < m; j++) {
            weights.set(j, weights.get(j) / sum);
        }

        return weights;
    }

}
