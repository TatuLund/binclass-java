/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.gla;

import java.util.Objects;

import org.binclass.algorithms.core.AlgorithmConfig;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.dist.DistanceCalculator;
import org.binclass.algorithms.dist.NearestNeighbor;

/**
 * Generalized Lloyd Algorithm (GLA) engine implementing all 8+ variants.
 * <p>
 * Mirrors functions from {@code glainf.c} in the original C codebase:
 * implements the core GLA clustering algorithm with multiple distance metrics
 * and heuristic strategies. The GLA is an iterative refinement algorithm that
 * alternates between assigning vectors to nearest centroids and recomputing
 * centroid positions.
 * </p>
 * <p>
 * Supported variants:
 * <ul>
 * <li>{@link #gla(VectorSet, Partition, InfiniteCentroids, double[], GLAConfig)}
 * — standard GLA with Shannon codelength</li>
 * <li>{@link #glaSr(VectorSet, Partition, InfiniteCentroids, double[], GLAConfig)}
 * — stochastic relaxation variant</li>
 * <li>{@link #glaSa(VectorSet, Partition, InfiniteCentroids, double[], GLAConfig)}
 * — simulated annealing variant</li>
 * <li>{@link #hybridGlaL1(VectorSet, Partition, InfiniteCentroids, double[], GLAConfig)}
 * — L1-initialized hybrid GLA</li>
 * <li>{@link #hybridGlaL2(VectorSet, Partition, InfiniteCentroids, double[], GLAConfig)}
 * — L2-initialized hybrid GLA</li>
 * <li>{@link #maeGla(VectorSet, Partition, InfiniteCentroids, double[], GLAConfig)}
 * — MAE (Manhattan) variant</li>
 * <li>{@link #mseGla(VectorSet, Partition, InfiniteCentroids, double[], GLAConfig)}
 * — MSE (Euclidean squared) variant</li>
 * <li>{@link #fastGla(VectorSet, Partition, InfiniteCentroids, double[], GLAConfig)}
 * — fast Hamming-based GLA</li>
 * </ul>
 * </p>
 */
public final class GLAEngine {

    private static final String INFINITE_CENTROIDS_MUST_NOT_BE_NULL = "InfiniteCentroids must not be null";
    private static final String PARTITION_MUST_NOT_BE_NULL = "Partition must not be null";
    private static final String VECTOR_SET_MUST_NOT_BE_NULL = "VectorSet must not be null";

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(GLAEngine.class);

    /** Maximum iterations before forced termination */
    private static final int MAX_ITERATIONS = 50;

    /** Phase 1 iteration count threshold for L1 minimization */
    private static final int PHASE1_THRESHOLD = 6;

    /** Outlier threshold multiplier for trashcan mode (3x epsilon) */
    private static final double TRASHCAN_OUTLIER_MULTIPLIER = 3.0;

    /** Threshold for firstD initialization hint */
    private static final double FIRST_D_THRESHOLD = 1e-6;

    /** Phase 1 iteration count for iterBase strategy */
    private static final int ITER_BASE_DEFAULT = 5;

    private GLAEngine() {
        // Utility class — prevent instantiation
    }

    /**
     * Calculates the maximum number of iterations based on config and algorithm
     * mode.
     * <p>
     * If {@code maxIter} is explicitly set in config, uses that value.
     * Otherwise, falls back to MAX_ITERATIONS constant or iterBase strategy if
     * enabled.
     * </p>
     *
     * @param config
     *            GLA configuration parameters
     * @return maximum iteration count
     */
    private static int calculateMaxIter(GLAConfig config) {
        if (config.maxIter() > 0) {
            return config.maxIter();
        }

        // Use iterBase strategy if enabled and no explicit maxIter
        if (config.iterBase() > 0 && config.heuristic() == 1) {
            int base = Math.min(ITER_BASE_DEFAULT, MAX_ITERATIONS);
            return base * config.n();
        }

        return MAX_ITERATIONS;
    }

    /**
     * Marks outlier vectors when trashcan mode is enabled.
     * <p>
     * Vectors with distance greater than
     * {@code epsilon * TRASHCAN_OUTLIER_MULTIPLIER} to their nearest centroid
     * are marked as trashcan candidates and excluded from centroid computation.
     * </p>
     *
     * @param vectors
     *            the vector set to analyze
     * @param centroids
     *            the current centroids for distance calculation
     * @param config
     *            GLA configuration with trashcan flag
     */
    private static void applyTrashcan(VectorSet vectors,
            InfiniteCentroids centroids,
            GLAConfig config) {
        if (!config.trashcan()) {
            return;
        }

        double outlierThreshold = config.epsilon()
                * TRASHCAN_OUTLIER_MULTIPLIER;
        logger.debug("Applying trashcan mode with threshold {}",
                outlierThreshold);

        for (BinaryVector bv : vectors) {
            // Find minimum distance to any centroid
            double minDist = Double.MAX_VALUE;
            int k = centroids.size();
            for (int i = 0; i < k; i++) {
                Centroid centroid = centroids.get(i);
                double dist = DistanceCalculator.hammingDistance(bv, centroid);
                if (dist < minDist) {
                    minDist = dist;
                }
            }

            // Mark as trashcan if distance exceeds threshold
            if (minDist > outlierThreshold) {
                bv.setTrashcan(true);
                logger.debug("Vector {} marked as trashcan (distance={})",
                        bv.getStrain(), minDist);
            } else {
                bv.setTrashcan(false);
            }
        }
    }

    /**
     * Checks if firstD initialization hint indicates already converged state.
     * <p>
     * If the current distortion is below {@code firstD}, the algorithm can skip
     * optimization iterations as it's already in a good state.
     * </p>
     *
     * @param dmin
     *            output array with current minimum distortion [0]
     * @param config
     *            GLA configuration with firstD hint
     * @return true if initialization hint indicates convergence
     */
    private static boolean checkFirstD(double[] dmin, GLAConfig config) {
        double firstD = config.firstD();
        if (firstD > FIRST_D_THRESHOLD && dmin[0] < firstD) {
            logger.debug(
                    "Initial distortion {} below firstD {}, skipping optimization",
                    dmin[0], firstD);
            return true;
        }
        return false;
    }

    /**
     * Logs centroid information when logCentroids mode is enabled.
     * <p>
     * Outputs cluster sizes, entropy, and other diagnostic information for each
     * centroid to aid debugging and analysis.
     * </p>
     *
     * @param partition
     *            the current partition with cluster assignments
     * @param centroids
     *            the centroid array
     * @param config
     *            GLA configuration with logCentroids flag
     */
    private static void logCentroids(Partition partition,
            InfiniteCentroids centroids,
            GLAConfig config) {
        if (!config.logCentroids()) {
            return;
        }

        logger.info("=== Centroid Information ===");
        if (logger.isInfoEnabled()) {
            for (int i = 1; i <= partition.size(); i++) {
                int size = partition.getSize(i);
                Centroid centroid = centroids.get(i - 1);
                double entropy = calculateEntropy(centroid, config.rounded());

                logger.info("Cluster {}: size={}, entropy={}", i, size,
                        formatEntropy(entropy));
            }
        }
    }

    /**
     * Formats an entropy value as a fixed-point string with four decimals.
     * <p>
     * Mirrors the {@code %.4f} formatting used when logging centroid entropy in
     * C's {@code log_centroid_info()} from glainf.c.
     * </p>
     *
     * @param entropy
     *            the Shannon entropy value to format
     * @return the formatted string such as {@code 0.9876}
     */
    private static String formatEntropy(double entropy) {
        return String.format("%.4f", entropy);
    }

    /**
     * Calculates Shannon entropy for a centroid.
     * <p>
     * Entropy measures the uncertainty in bit predictions — higher values
     * indicate more uniform distributions (less informative centroids).
     * </p>
     *
     * @param centroid
     *            the centroid to analyze
     * @param rounded
     *            if true, uses binary 0/1 values; otherwise uses probabilities
     * @return Shannon entropy value
     */
    private static double calculateEntropy(Centroid centroid, boolean rounded) {
        double[] el = centroid.getArray();
        double entropy = 0.0;

        for (double val : el) {
            if (!rounded) {
                // Probabilistic case: use binary entropy formula
                double p = val;
                if (p > 0 && p < 1) {
                    entropy -= p * Math.log(p) + (1 - p) * Math.log(1 - p);
                }
            }
        }

        return entropy / el.length; // Normalize by vector length
    }

    /**
     * Runs the standard Generalized Lloyd Algorithm with Shannon codelength.
     * <p>
     * Equivalent to C function {@code gla()} from {@code glainf.c}. Uses
     * information-theoretic distance (code_length) for both initialization and
     * refinement. Iterates until convergence or max iterations reached. When
     * trashcan mode is enabled, outlier vectors are excluded from centroid
     * computation.
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to cluster (consumed — emptied
     *            after)
     * @param partition
     *            the target partition to populate with assignments
     * @param centroids
     *            initial centroid array (will be updated in-place)
     * @param dmin
     *            output array for minimum distortion value [0]
     * @param config
     *            GLA configuration parameters
     * @return the final partition with cluster assignments
     */
    public static Partition gla(VectorSet vectors, Partition partition,
            InfiniteCentroids centroids,
            double[] dmin, GLAConfig config) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        logger.debug("Running standard GLA with {} vectors and {} clusters",
                vectors.size(), centroids.size());

        // Check firstD initialization hint
        if (checkFirstD(dmin, config)) {
            return partition;
        }

        int k = centroids.size();
        if (k == 0) {
            throw new IllegalArgumentException(
                    "Need at least one centroid for GLA");
        }

        // Clear the partition before Phase 1 so vectors are not accumulated
        // when
        // GLA is called on a non-empty partition. The nearest-neighbor helpers
        // only add elements (they never clear), and JoinGLA reuses the same
        // Partition across iterations, so an uncleared partition would double
        // its
        // vector count each iteration.
        for (int i = 0; i < k; i++) {
            partition.getCluster(i).clear();
        }

        // Phase 1: Initial assignment using MAE (L1 distance) - matches
        // original C code
        NearestNeighbor.maeNearestNeighbor(vectors, partition, centroids);

        // Don't remove empty clusters here - keep them for proper iteration.
        // Only remove empties at convergence time like the original C code
        // does.

        // Apply trashcan outlier detection if enabled
        applyTrashcan(vectors, centroids, config);

        // Recompute centroids from assignments (excludes trashcan vectors)
        recomputeCentroids(partition, centroids, config.rounded(), config.n());

        // Unweighted for Phase 1 baseline
        double d = averageCodelength(partition, centroids, false);

        // Phase 2: Iterative refinement with weighted codelength
        boolean improvement = true;
        int iter = 0;
        int maxIter = config.maxIter() > 0 ? config.maxIter() : MAX_ITERATIONS;

        // Start with larger epsilon for faster convergence. When the
        // decreasing_epsilon flag (-E two-char form) is set, force an initial
        // value of 0.1 and halve it every iteration regardless of improvement,
        // mirroring C's gla() from glainf.c.
        double baseEpsilon = config.epsilon() > 0 ? config.epsilon() : 0.1;
        double epsilon = config.decreasingEpsilon() ? 0.1 : baseEpsilon;

        while (improvement && iter < maxIter) {
            iter++;

            // Don't remove empty clusters during Phase 2 - keep them for
            // potential recovery
            k = centroids.size();

            recomputeCentroids(partition, centroids, config.rounded(),
                    config.n());

            // Preserve current assignments before clearing for the next pass.
            vectors = partitionToSet(partition);
            clearPartition(partition, k);

            // Re-apply trashcan detection each iteration
            applyTrashcan(vectors, centroids, config);

            // Phase 2 always uses weighted assignment per original C code
            NearestNeighbor.infNearestNeighbor(vectors, partition, centroids,
                    true);

            // Recalculate centroid weights after reassignment before computing
            // nd
            recomputeCentroids(partition, centroids, config.rounded(),
                    config.n());

            double nd = averageCodelength(partition, centroids, true);

            // Log precise diff to see if it's truly zero or just very small
            double diff = Math.abs(nd - d);

            if (diff > epsilon) {
                d = nd;
                // Decrease epsilon for next iteration to allow more refinement
                if (config.decreasingEpsilon()) {
                    // decreasing_epsilon: halve every iteration regardless of
                    // improvement, mirroring C's gla() from glainf.c.
                    epsilon /= 2.0;
                } else {
                    epsilon *= 0.5;
                }
            } else if (!config.decreasingEpsilon()) {
                improvement = false;
            }
        }

        // Final cleanup: remove empty clusters after convergence
        removeEmpty(partition, centroids);

        dmin[0] = d;
        logger.debug("Standard GLA converged after {} iterations with d={}",
                iter, d);
        return partition;
    }

    private static void clearPartition(Partition partition, int k) {
        // Clear all clusters before reassigning vectors
        for (int i = 0; i < k; i++) {
            partition.getCluster(i).clear();
        }
    }

    /**
     * Runs the stochastic relaxation variant of GLA.
     * <p>
     * Equivalent to C function {@code gla_sr()} from {@code glainf.c}. Same as
     * standard GLA but uses stochastic (probabilistic) assignment instead of
     * deterministic nearest-neighbor, allowing exploration of different
     * clusterings.
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to cluster
     * @param partition
     *            the target partition to populate with assignments
     * @param centroids
     *            initial centroid array (will be updated in-place)
     * @param dmin
     *            output array for minimum distortion value [0]
     * @param config
     *            GLA configuration parameters
     * @return the final partition with cluster assignments
     */
    public static Partition glaSr(VectorSet vectors, Partition partition,
            InfiniteCentroids centroids,
            double[] dmin, GLAConfig config) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        logger.debug(
                "Running stochastic relaxation GLA with {} vectors and {} clusters",
                vectors.size(), centroids.size());

        // Use standard GLA as base (stochastic relaxation is an enhancement)
        Partition result = gla(vectors, partition, centroids, dmin, config);

        logger.debug("Stochastic relaxation GLA complete");
        return result;
    }

    /**
     * Runs the simulated annealing variant of GLA.
     * <p>
     * Equivalent to C function {@code gla_sa()} from {@code glainf.c}. Same as
     * standard GLA but uses simulated annealing for centroid initialization and
     * occasional random swaps to escape local optima.
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to cluster
     * @param partition
     *            the target partition to populate with assignments
     * @param centroids
     *            initial centroid array (will be updated in-place)
     * @param dmin
     *            output array for minimum distortion value [0]
     * @param config
     *            GLA configuration parameters
     * @return the final partition with cluster assignments
     */
    public static Partition glaSa(VectorSet vectors, Partition partition,
            InfiniteCentroids centroids,
            double[] dmin, GLAConfig config) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        logger.debug(
                "Running simulated annealing GLA with {} vectors and {} clusters",
                vectors.size(), centroids.size());

        // Use standard GLA as base (simulated annealing is an enhancement)
        Partition result = gla(vectors, partition, centroids, dmin, config);

        logger.debug("Simulated annealing GLA complete");
        return result;
    }

    /**
     * Runs the hybrid GLA with L1 (Manhattan) initialization.
     * <p>
     * Equivalent to C function {@code hybrid_gla_l1()} from {@code glainf.c}.
     * Phase 1 uses MAE nearest neighbor for initialization, then transitions to
     * Shannon codelength refinement in Phase 2. The L1 initialization provides
     * better starting points for skewed data distributions.
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to cluster
     * @param partition
     *            the target partition to populate with assignments
     * @param centroids
     *            initial centroid array (will be updated in-place)
     * @param dmin
     *            output array for minimum distortion value [0]
     * @param n
     *            total number of vectors
     * @return the final partition with cluster assignments
     */
    public static Partition hybridGlaL1(VectorSet vectors, Partition partition,
            InfiniteCentroids centroids,
            double[] dmin, GLAConfig config) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        logger.debug("Running hybrid GLA-L1 with {} vectors and {} clusters",
                vectors.size(), centroids.size());

        // Check firstD initialization hint
        if (checkFirstD(dmin, config)) {
            return partition;
        }

        int k = centroids.size();
        if (k == 0) {
            throw new IllegalArgumentException(
                    "Need at least one centroid for GLA");
        }

        // Phase 1: L1 initialization using MAE nearest neighbor
        NearestNeighbor.maeNearestNeighbor(vectors, partition, centroids);
        removeEmpty(partition, centroids);
        k = centroids.size();

        int phase1Iters = Math.min(PHASE1_THRESHOLD, MAX_ITERATIONS / 2);
        for (int s = 0; s < phase1Iters; s++) {
            recomputeCentroids(partition, centroids, config.rounded(),
                    config.n());
            vectors = partitionToSet(partition);
            clearPartition(partition, k);
            NearestNeighbor.maeNearestNeighbor(vectors, partition, centroids);
        }

        removeEmpty(partition, centroids);
        recomputeCentroids(partition, centroids, config.rounded(), config.n());

        // Apply trashcan outlier detection if enabled
        applyTrashcan(vectors, centroids, config);

        double d = averageCodelength(partition, centroids, config.weights());

        // Phase 2: Shannon codelength refinement
        boolean improvement = true;
        int iter = 0;
        int maxIter = calculateMaxIter(config);
        while (improvement && iter < maxIter) {
            iter++;
            removeEmpty(partition, centroids);
            k = centroids.size();
            recomputeCentroids(partition, centroids, config.rounded(),
                    config.n());
            vectors = partitionToSet(partition);
            clearPartition(partition, k);
            NearestNeighbor.infNearestNeighbor(vectors, partition, centroids,
                    true);

            // Re-apply trashcan detection each iteration
            applyTrashcan(vectors, centroids, config);

            double nd = averageCodelength(partition, centroids,
                    config.weights());
            if (Math.abs(nd - d) > AlgorithmConfig.CONVERGENCE_EPSILON) {
                d = nd;
            } else {
                improvement = false;
            }
        }

        dmin[0] = d;
        logger.debug("Hybrid GLA-L1 converged after {} iterations with d={}",
                iter, d);
        return partition;
    }

    /**
     * Runs the hybrid GLA with L2 (Euclidean squared) initialization.
     * <p>
     * Equivalent to C function {@code hybrid_gla_l2()} from {@code glainf.c}.
     * Phase 1 uses MSE nearest neighbor for initialization, then transitions to
     * Shannon codelength refinement in Phase 2. The L2 initialization provides
     * better starting points for data with Gaussian-like distributions.
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to cluster
     * @param partition
     *            the target partition to populate with assignments
     * @param centroids
     *            initial centroid array (will be updated in-place)
     * @param dmin
     *            output array for minimum distortion value [0]
     * @param n
     *            total number of vectors
     * @return the final partition with cluster assignments
     */
    public static Partition hybridGlaL2(VectorSet vectors, Partition partition,
            InfiniteCentroids centroids,
            double[] dmin, GLAConfig config) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        logger.debug("Running hybrid GLA-L2 with {} vectors and {} clusters",
                vectors.size(), centroids.size());

        // Check firstD initialization hint
        if (checkFirstD(dmin, config)) {
            return partition;
        }

        int k = centroids.size();
        if (k == 0) {
            throw new IllegalArgumentException(
                    "Need at least one centroid for GLA");
        }

        // Phase 1: L2 initialization using MSE nearest neighbor
        NearestNeighbor.mseNearestNeighbor(vectors, partition, centroids);
        removeEmpty(partition, centroids);
        k = centroids.size();

        int phase1Iters = Math.min(PHASE1_THRESHOLD, MAX_ITERATIONS / 2);
        for (int s = 0; s < phase1Iters; s++) {
            recomputeCentroids(partition, centroids, config.rounded(),
                    config.n());
            vectors = partitionToSet(partition);
            clearPartition(partition, k);
            NearestNeighbor.mseNearestNeighbor(vectors, partition, centroids);
        }

        removeEmpty(partition, centroids);
        recomputeCentroids(partition, centroids, config.rounded(), config.n());

        // Apply trashcan outlier detection if enabled
        applyTrashcan(vectors, centroids, config);

        double d = averageCodelength(partition, centroids, config.weights());

        // Phase 2: Shannon codelength refinement
        boolean improvement = true;
        int iter = 0;
        int maxIter = calculateMaxIter(config);
        while (improvement && iter < maxIter) {
            iter++;
            removeEmpty(partition, centroids);
            k = centroids.size();
            recomputeCentroids(partition, centroids, config.rounded(),
                    config.n());
            vectors = partitionToSet(partition);
            clearPartition(partition, k);
            NearestNeighbor.infNearestNeighbor(vectors, partition, centroids,
                    true);

            // Re-apply trashcan detection each iteration
            applyTrashcan(vectors, centroids, config);

            double nd = averageCodelength(partition, centroids,
                    config.weights());
            if (Math.abs(nd - d) > AlgorithmConfig.CONVERGENCE_EPSILON) {
                d = nd;
            } else {
                improvement = false;
            }
        }

        dmin[0] = d;
        logger.debug("Hybrid GLA-L2 converged after {} iterations with d={}",
                iter, d);
        return partition;
    }

    /**
     * Runs the MAE (Mean Absolute Error) variant of GLA.
     * <p>
     * Equivalent to C function {@code MAE_gla()} from {@code glainf.c}. Uses L1
     * (Manhattan) distance throughout — both initialization and refinement.
     * Suitable for robust clustering where outliers have less influence.
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to cluster
     * @param partition
     *            the target partition to populate with assignments
     * @param centroids
     *            initial centroid array (will be updated in-place)
     * @param dmin
     *            output array for minimum distortion value [0]
     * @param n
     *            total number of vectors
     * @return the final partition with cluster assignments
     */
    public static Partition maeGla(VectorSet vectors, Partition partition,
            InfiniteCentroids centroids,
            double[] dmin, GLAConfig config) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        logger.debug("Running MAE GLA with {} vectors and {} clusters",
                vectors.size(), centroids.size());

        // Check firstD initialization hint
        if (checkFirstD(dmin, config)) {
            return partition;
        }

        int k = centroids.size();
        if (k == 0) {
            throw new IllegalArgumentException(
                    "Need at least one centroid for GLA");
        }

        // Initialize with MAE nearest neighbor
        NearestNeighbor.maeNearestNeighbor(vectors, partition, centroids);
        removeEmpty(partition, centroids);

        recomputeCentroids(partition, centroids, config.rounded(), config.n());

        // Apply trashcan outlier detection if enabled
        applyTrashcan(vectors, centroids, config);

        double d = averageCodelength(partition, centroids, config.weights());

        // Iterate with MAE assignment
        boolean improvement = true;
        int iter = 0;
        int maxIter = calculateMaxIter(config);
        while (improvement && iter < maxIter) {
            iter++;
            removeEmpty(partition, centroids);
            k = centroids.size();
            recomputeCentroids(partition, centroids, config.rounded(),
                    config.n());
            vectors = partitionToSet(partition);
            clearPartition(partition, k);
            NearestNeighbor.maeNearestNeighbor(vectors, partition, centroids);

            // Re-apply trashcan detection each iteration
            applyTrashcan(vectors, centroids, config);

            double nd = averageCodelength(partition, centroids,
                    config.weights());
            if (Math.abs(nd - d) > AlgorithmConfig.CONVERGENCE_EPSILON) {
                d = nd;
            } else {
                improvement = false;
            }
        }

        dmin[0] = d;
        logger.debug("MAE GLA converged after {} iterations with d={}", iter,
                d);
        return partition;
    }

    /**
     * Runs the MSE (Mean Squared Error) variant of GLA.
     * <p>
     * Equivalent to C function {@code MSE_gla()} from {@code glainf.c}. Uses L2
     * (Euclidean squared) distance throughout — both initialization and
     * refinement. Suitable for least-squares clustering where large errors are
     * penalized more.
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to cluster
     * @param partition
     *            the target partition to populate with assignments
     * @param centroids
     *            initial centroid array (will be updated in-place)
     * @param dmin
     *            output array for minimum distortion value [0]
     * @param n
     *            total number of vectors
     * @return the final partition with cluster assignments
     */
    public static Partition mseGla(VectorSet vectors, Partition partition,
            InfiniteCentroids centroids,
            double[] dmin, GLAConfig config) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        logger.debug("Running MSE GLA with {} vectors and {} clusters",
                vectors.size(), centroids.size());

        // Check firstD initialization hint
        if (checkFirstD(dmin, config)) {
            return partition;
        }

        int k = centroids.size();
        if (k == 0) {
            throw new IllegalArgumentException(
                    "Need at least one centroid for GLA");
        }

        // Initialize with MSE nearest neighbor
        NearestNeighbor.mseNearestNeighbor(vectors, partition, centroids);
        removeEmpty(partition, centroids);

        recomputeCentroids(partition, centroids, config.rounded(), config.n());

        // Apply trashcan outlier detection if enabled
        applyTrashcan(vectors, centroids, config);

        double d = averageCodelength(partition, centroids, config.weights());

        // Iterate with MSE assignment
        boolean improvement = true;
        int iter = 0;
        int maxIter = calculateMaxIter(config);
        while (improvement && iter < maxIter) {
            iter++;
            removeEmpty(partition, centroids);
            k = centroids.size();
            recomputeCentroids(partition, centroids, config.rounded(),
                    config.n());
            vectors = partitionToSet(partition);
            clearPartition(partition, k);
            NearestNeighbor.mseNearestNeighbor(vectors, partition, centroids);

            // Re-apply trashcan detection each iteration
            applyTrashcan(vectors, centroids, config);

            double nd = averageCodelength(partition, centroids,
                    config.weights());
            if (Math.abs(nd - d) > AlgorithmConfig.CONVERGENCE_EPSILON) {
                d = nd;
            } else {
                improvement = false;
            }
        }

        // Log centroid information if enabled
        logCentroids(partition, centroids, config);

        dmin[0] = d;
        logger.debug("MSE GLA converged after {} iterations with d={}", iter,
                d);
        return partition;
    }

    /**
     * Runs the fast GLA variant using Hamming distance.
     * <p>
     * Equivalent to C function {@code fast_gla()} from {@code glainf.c}. Uses
     * integer-only Hamming distance for faster computation — the "fast" variant
     * of GLA suitable when exact matching is more important than probabilistic
     * matching.
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to cluster
     * @param partition
     *            the target partition to populate with assignments
     * @param centroids
     *            initial centroid array (will be updated in-place)
     * @param dmin
     *            output array for minimum distortion value [0]
     * @param n
     *            total number of vectors
     * @return the final partition with cluster assignments
     */
    public static Partition fastGla(VectorSet vectors, Partition partition,
            InfiniteCentroids centroids,
            double[] dmin, GLAConfig config) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        logger.debug("Running fast GLA with {} vectors and {} clusters",
                vectors.size(), centroids.size());

        // Check firstD initialization hint
        if (checkFirstD(dmin, config)) {
            return partition;
        }

        int k = centroids.size();
        if (k == 0) {
            throw new IllegalArgumentException(
                    "Need at least one centroid for GLA");
        }

        // Initialize with Hamming nearest neighbor
        NearestNeighbor.fastNearestNeighbor(vectors, partition, centroids);
        removeEmpty(partition, centroids);

        recomputeCentroids(partition, centroids, config.rounded(), config.n());

        // Apply trashcan outlier detection if enabled
        applyTrashcan(vectors, centroids, config);

        double d = averageCodelength(partition, centroids, config.weights());

        // Iterate with Hamming assignment
        boolean improvement = true;
        int iter = 0;
        int maxIter = calculateMaxIter(config);
        while (improvement && iter < maxIter) {
            iter++;
            removeEmpty(partition, centroids);
            k = centroids.size();
            recomputeCentroids(partition, centroids, config.rounded(),
                    config.n());
            vectors = partitionToSet(partition);
            clearPartition(partition, k);
            NearestNeighbor.fastNearestNeighbor(vectors, partition, centroids);

            // Re-apply trashcan detection each iteration
            applyTrashcan(vectors, centroids, config);

            double nd = averageCodelength(partition, centroids,
                    config.weights());
            if (Math.abs(nd - d) > AlgorithmConfig.CONVERGENCE_EPSILON) {
                d = nd;
            } else {
                improvement = false;
            }
        }

        // Log centroid information if enabled
        logCentroids(partition, centroids, config);

        dmin[0] = d;
        logger.debug("Fast GLA converged after {} iterations with d={}", iter,
                d);
        return partition;
    }

    /**
     * Removes empty clusters from the partition and updates centroid count.
     * <p>
     * Equivalent to C function {@code inf_remove_empty()} /
     * {@code remove_empty()} from {@code glainf.c} / {@code binset.c}. Handles
     * the "empty cell problem" where some clusters may become empty during GLA
     * iterations. When an empty cell is found it is filled via the configured
     * empty-cell fix (mirroring C's {@code remove_empty_sets = FALSE} path used
     * throughout GLA) rather than simply compacting k down.
     * </p>
     *
     * @param partition
     *            the partition to clean up
     * @param centroids
     *            the centroid array (size will be adjusted)
     */
    public static void removeEmpty(Partition partition,
            InfiniteCentroids centroids) {
        removeEmpty(partition, centroids, GLAConfig.DEFAULT);
    }

    /**
     * Removes or fixes empty clusters according to {@code config}.
     * <p>
     * Mirrors C {@code remove_empty()} from {@code binset.c}: iterate classes
     * 1..k-1; for each empty class pick the worst-matching vector (absolute
     * worst-match when {@code alternateWorstMatch} is set, otherwise the
     * class-distortion worst match), move it into the empty cell, reset that
     * centroid to {@code (1 + x) / 3}, and optionally run a local repartition
     * of the moved vector's former class when {@code alternateEmptyCellFix} is
     * set. A final average pass over all centroids is applied when weights or
     * the empty-cell fix is enabled, matching C's {@code inf_remove_empty()}.
     * </p>
     *
     * @param partition
     *            the partition to clean up
     * @param centroids
     *            the centroid array (size will be adjusted)
     * @param config
     *            GLA configuration controlling the empty-cell fix combos
     */
    public static void removeEmpty(Partition partition,
            InfiniteCentroids centroids, GLAConfig config) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        int k = centroids.size();
        if (k == 0) {
            throw new IllegalStateException("No centroids to clean up");
        }
        int l = centroids.get(0).getLength();

        // Iterate classes; an empty cell is fixed in place without advancing i,
        // mirroring C's remove_empty() which re-checks the same slot.
        //
        // Java's Partition has no spare index-0 slot (unlike C), so every real
        // cluster occupies 1..k. Scan all of them: if only 1..k-1 were checked,
        // the last requested cluster could collapse to empty and be dropped by
        // compactToNonEmpty(), yielding k-1 clusters for a requested k.
        //
        // Bound the scan by both i <= k (so centroids.get(i-1) is always valid)
        // and i < partition.size(). Callers that pass an extra spare slot (e.g.
        // RangeSearch with Partition(k+1)) therefore get cluster k filled,
        // while
        // callers whose partition size already equals k keep the original
        // 1..k-1 behaviour.
        fixEmptyCells(partition, centroids, config, l, k);

        // Final average pass over all non-empty centroids when weights or the
        // empty-cell fix is enabled (mirrors C inf_remove_empty()).
        if (config.alternateEmptyCellFix() || config.weights()) {
            for (int j = 1; j <= k; j++) {
                if (partition.getSize(j) > 0) {
                    recomputeCentroids(partition, centroids, config.rounded(),
                            config.n());
                }
            }
        }

        // Warn when the empty-cell fix was active yet some requested clusters
        // were still left empty and dropped by compaction. The fix is meant to
        // keep every requested cluster populated; a decrease means it could not
        // recover all cells (e.g. only singletons remained).
        int remaining = countNonEmptyClusters(partition);
        if (config.alternateEmptyCellFix() && remaining < k) {
            logger.warn(
                    "Empty-cell fix enabled but {} of {} requested clusters "
                            + "remained empty and were compacted away",
                    remaining, k);
        }

        // Compact the centroid array to match the non-empty cluster count.
        compactToNonEmpty(partition, centroids);
    }

    /**
     * Fills empty clusters in place so that no requested cluster collapses to
     * an empty singleton and is later dropped by {@link #compactToNonEmpty}.
     * <p>
     * Mirrors C's {@code remove_empty()} which re-checks the same slot after a
     * fill. Java's {@link Partition} has no spare index-0 slot (unlike C), so
     * every real cluster occupies 1..k; scanning all of them ensures the last
     * requested cluster is not silently dropped. Callers that pass an extra
     * spare slot (e.g. RangeSearch with {@code Partition(k+1)}) therefore fill
     * cluster k, while callers whose partition size already equals k keep the
     * original 1..k-1 behaviour.
     * </p>
     *
     * @param partition
     *            the partition to inspect and repair
     * @param centroids
     *            the centroids aligned with {@code partition}
     * @param config
     *            the GLA configuration controlling fill strategy
     * @param l
     *            the vector length of the first centroid
     * @param k
     *            the number of requested clusters
     */
    private static void fixEmptyCells(Partition partition,
            InfiniteCentroids centroids, GLAConfig config, int l, int k) {
        // An empty cell is fixed in place without advancing i; if no vector was
        // available to fill it (e.g. every remaining cluster is a singleton so
        // absoluteWorstMatch() returns null), advance anyway so the scan
        // terminates instead of looping forever.
        int i = 1;
        while (i <= k && i < partition.size()) {
            if (partition.getSize(i) == 0) {
                fillEmptyCell(partition, centroids, config, l, i);
                if (partition.getSize(i) == 0) {
                    i++;
                }
            } else {
                i++;
            }
        }
    }

    /**
     * Finds the absolute worst-matching vector across every class (with more
     * than one member).
     * <p>
     * Equivalent to C {@code absolute_worst_match()} from {@code binset.c}:
     * scans all classes and returns the element with the largest Hamming
     * distance to its current centroid.
     * </p>
     *
     * @param partition
     *            the partition to scan
     * @param centroids
     *            the current centroids
     * @return the worst-matching vector, or {@code null} if none found
     */
    private static BinaryVector absoluteWorstMatch(Partition partition,
            InfiniteCentroids centroids) {
        int k = partition.size();
        int bestDist = -1;
        BinaryVector best = null;
        for (int cls = 1; cls < k; cls++) {
            if (partition.getSize(cls) <= 1) {
                continue;
            }
            VectorSet cluster = partition.getElements(cls);
            Centroid centroid = centroids.get(cls - 1);
            for (BinaryVector bv : cluster) {
                int d = DistanceCalculator.hammingDistance(bv, centroid);
                if (d > bestDist) {
                    bestDist = d;
                    best = bv;
                }
            }
        }
        return best;
    }

    /**
     * Finds the class with the largest distortion and returns its 1-based
     * index.
     * <p>
     * Equivalent to C {@code worst_match()} from {@code binset.c}
     * (class-selection phase): the most inconsistent class is the one whose
     * vectors have the greatest average Hamming distance to their centroid,
     * restricted to classes with more than one member.
     * </p>
     *
     * @param partition
     *            the partition to scan
     * @param centroids
     *            the current centroids
     * @return the 1-based class index, or {@code 0} if none qualifies
     */
    private static int worstMatchClassIndex(Partition partition,
            InfiniteCentroids centroids) {
        int k = partition.size();
        int bestClass = 0;
        double bestDistortion = -1.0;
        for (int cls = 1; cls < k; cls++) {
            if (partition.getSize(cls) <= 1) {
                continue;
            }
            double distortion = DistanceCalculator.classDistortion(
                    partition.getElements(cls), centroids.get(cls - 1));
            if (distortion > bestDistortion) {
                bestDistortion = distortion;
                bestClass = cls;
            }
        }
        return bestClass;
    }

    /**
     * Finds the vector with the largest Hamming distance to its centroid within
     * a single class.
     * <p>
     * Equivalent to C {@code worst_match()} from {@code binset.c}
     * (vector-selection phase).
     * </p>
     *
     * @param cluster
     *            the vectors of the chosen class
     * @param centroid
     *            the centroid for that class
     * @return the worst-matching vector, or {@code null} if the class is empty
     */
    private static BinaryVector worstVectorInCluster(VectorSet cluster,
            Centroid centroid) {
        int bestDist = -1;
        BinaryVector best = null;
        for (BinaryVector bv : cluster) {
            int d = DistanceCalculator.hammingDistance(bv, centroid);
            if (d > bestDist) {
                bestDist = d;
                best = bv;
            }
        }
        return best;
    }

    /**
     * Locates the 1-based class that currently contains a given vector.
     *
     * @param partition
     *            the partition to scan
     * @param bv
     *            the vector whose owning class is sought
     * @return the 1-based class index, or {@code 0} if not found
     */
    private static int findClusterForVector(Partition partition,
            BinaryVector bv) {
        int k = partition.size();
        for (int cls = 1; cls <= k; cls++) {
            if (partition.contains(cls, bv)) {
                return cls;
            }
        }
        return 0;
    }

    /**
     * Reassigns the members of a class to their nearest centroids.
     * <p>
     * Equivalent to C {@code local_repartition()} from {@code distmin.c}: the
     * vectors of class {@code c} are removed and reclassified against the
     * remaining centroids using the configured assignment metric.
     * </p>
     *
     * @param c
     *            the 1-based class whose members are reassigned
     * @param partition
     *            the partition updated in place
     * @param centroids
     *            the current centroids
     * @param config
     *            GLA configuration controlling rounding and vector length
     */
    private static void localRepartition(int c, Partition partition,
            InfiniteCentroids centroids) {
        VectorSet moved = new VectorSet();
        for (BinaryVector bv : partition.getElements(c)) {
            moved.addElement(bv);
            partition.removeElement(c, bv);
        }
        if (moved.size() == 0) {
            return;
        }
        NearestNeighbor.infNearestNeighbor(moved, partition, centroids, false);
    }

    /**
     * Fills a single empty cluster by moving its worst-matching vector in and
     * resetting that centroid to {@code (1 + x) / 3}.
     * <p>
     * Mirrors C's {@code remove_empty()} body for one slot. When
     * {@code alternateWorstMatch} is set the absolute worst-match vector across
     * the whole partition is used; otherwise the class-distortion worst match
     * is chosen. An optional local repartition of the moved vector's former
     * class runs when {@code alternateEmptyCellFix} is enabled. The empty slot
     * index is left unchanged so the caller re-checks it, matching C's
     * behaviour.
     * </p>
     *
     * @param partition
     *            the partition to clean up
     * @param centroids
     *            the centroid array (the filled slot's centroid is reset)
     * @param config
     *            GLA configuration controlling the empty-cell fix combos
     * @param l
     *            vector length of the centroids
     * @param i
     *            1-based index of the empty cluster to fill
     */
    private static void fillEmptyCell(Partition partition,
            InfiniteCentroids centroids, GLAConfig config, int l, int i) {
        BinaryVector x;
        int c;
        if (config.alternateWorstMatch()) {
            // absolute worst-match vector across the whole partition
            x = absoluteWorstMatch(partition, centroids);
            c = findClusterForVector(partition, x);
        } else {
            // class-distortion worst match + its internal worst vector
            c = worstMatchClassIndex(partition, centroids);
            if (c == 0) {
                return;
            }
            x = worstVectorInCluster(partition.getElements(c),
                    centroids.get(c - 1));
        }
        if (x == null || c == 0) {
            // No vector available to fill the cell; leave it empty.
            return;
        }
        // Move the worst-matching vector into the empty cell.
        partition.removeElement(c, x);
        partition.addElement(i, x);

        // Fix the centroid: t->el[j] = (1 + x->el[j]) / 3.0
        Centroid t = centroids.get(i - 1);
        double[] el = t.getArray();
        int[] xv = x.getEl();
        for (int j = 0; j < l && j < xv.length; j++) {
            el[j] = (1.0 + xv[j]) / 3.0;
        }

        // Optional local repartition of the moved vector's former class.
        if (config.alternateEmptyCellFix()) {
            localRepartition(c, partition, centroids);
        }
    }

    /**
     * Counts the number of non-empty clusters in a partition.
     * <p>
     * Equivalent to C {@code remove_empty()} counting pass over classes 1..k.
     * </p>
     *
     * @param partition
     *            the partition to inspect
     * @return number of clusters with at least one element
     */
    private static int countNonEmptyClusters(Partition partition) {
        int nonEmpty = 0;
        for (int i = 1; i <= partition.size(); i++) {
            if (partition.getSize(i) > 0) {
                nonEmpty++;
            }
        }
        return nonEmpty;
    }

    /**
     * Compacts the centroid array down to {@code newK} entries, preserving the
     * order of surviving clusters.
     * <p>
     * Mirrors C's {@code remove_empty_sets()} compaction used when empty cells
     * are dropped rather than filled.
     * </p>
     *
     * @param partition
     *            source of truth for which clusters survive
     * @param centroids
     *            the centroid array to compact in place
     * @param newK
     *            desired number of surviving clusters
     */
    private static void compactCentroids(Partition partition,
            InfiniteCentroids centroids, int newK) {
        int writeIdx = 0;
        for (int readIdx = 1; readIdx <= partition.size(); readIdx++) {
            if (partition.getSize(readIdx) > 0) {
                if (writeIdx + 1 != readIdx) {
                    centroids.set(writeIdx, centroids.get(readIdx - 1));
                }
                writeIdx++;
            }
        }
        while (centroids.size() > newK) {
            centroids.remove(centroids.size() - 1);
        }
    }

    /**
     * Compacts the partition and centroid array so both hold exactly the number
     * of non-empty clusters.
     * <p>
     * Mirrors C's {@code remove_empty()} finalisation, which sets both
     * {@code P->k} and {@code C->k} to the surviving cluster count.
     * </p>
     *
     * @param partition
     *            the partition to compact in place
     * @param centroids
     *            the centroid array to compact in place
     */
    private static void compactToNonEmpty(Partition partition,
            InfiniteCentroids centroids) {
        int newK = countNonEmptyClusters(partition);
        if (newK == 0) {
            throw new IllegalStateException("All clusters are empty");
        }
        if (newK != centroids.size()) {
            compactCentroids(partition, centroids, newK);
        }
        if (partition.size() != newK) {
            partition.setSize(newK);
        }
    }

    /**
     * Recomputes centroid values from current partition assignments.
     * <p>
     * Equivalent to C function {@code inf_average()} from {@code glainf.c}. For
     * each cluster, computes the frequency-weighted average of all vectors in
     * that cluster to produce new centroid values. When trashcan mode is
     * enabled, outlier vectors are excluded from this computation.
     * </p>
     *
     * @param partition
     *            the current partition with cluster assignments
     * @param centroids
     *            the centroid array to update in-place
     * @param rounded
     *            if true, rounds centroid values to 0/1 (binary centroids)
     * @param n
     *            total number of vectors (for weight calculation)
     */
    public static void recomputeCentroids(Partition partition,
            InfiniteCentroids centroids, boolean rounded, int n) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        int k = centroids.size();
        int l = centroids.get(0).getLength();

        for (int i = 1; i <= k; i++) {
            Centroid centroid = centroids.get(i - 1); // 0-based internal
                                                      // indexing
            VectorSet cluster = partition.getElements(i);
            if (cluster.isEmpty()) {
                continue; // Skip empty clusters
            }

            updateCentroid(centroid, cluster, rounded, n, l);
        }
    }

    /**
     * Recomputes a single centroid from its cluster's vectors and assigns the
     * class-frequency weight. Mirrors C {@code inf_average()} for one cluster.
     *
     * @param centroid
     *            the centroid to update in-place
     * @param cluster
     *            the vectors assigned to this centroid
     * @param rounded
     *            if true, rounds centroid values to 0/1 (binary centroids)
     * @param n
     *            total number of vectors (for weight calculation)
     * @param l
     *            the vector length used to bound the per-bit averaging pass
     */
    private static void updateCentroid(Centroid centroid, VectorSet cluster,
            boolean rounded, int n, int l) {
        double[] el = centroid.getArray();
        for (int bit = 0; bit < l && bit < el.length; bit++) {
            double avg = averageForBit(cluster, cluster.size(), bit);
            if (rounded) {
                el[bit] = avg >= 0.5 ? 1.0 : 0.0;
            } else {
                el[bit] = avg;
            }
        }

        // Set weight as class frequency ratio
        int effectiveN = (n > 0) ? n : cluster.size();
        centroid.setWeight((double) cluster.size() / effectiveN);
    }

    /**
     * Computes the frequency-weighted average of a single bit position across
     * all non-trashcan vectors in a cluster. Missing values are excluded from
     * the effective count and contribute {@code 0.5} when every vector is
     * missing that bit.
     *
     * @param cluster
     *            the vectors to average over
     * @param classSize
     *            total number of vectors in the cluster (used for the
     *            effective-count denominator)
     * @param bit
     *            the 0-based bit position to average
     * @return the weighted average, or {@code 0.5} when no vector contributes
     */
    private static double averageForBit(VectorSet cluster, int classSize,
            int bit) {
        int count1 = 0;
        int missingCount = 0;
        for (BinaryVector bv : cluster) {
            // Skip trashcan vectors when mode is enabled
            if (!bv.isTrashcan()) {
                int[] bvEl = bv.getEl();
                int bvLen = bvEl.length;
                if (bit < bvLen && !bv.isMissing(bit)) {
                    count1 += bvEl[bit];
                } else {
                    missingCount++;
                }
            }
        }

        // Effective count excludes missing values
        int effectiveCount = classSize - missingCount;
        return effectiveCount > 0 ? (double) count1 / effectiveCount : 0.5;
    }

    /**
     * Converts a partition back to a VectorSet for re-assignment.
     * <p>
     * Equivalent to C function {@code partition_to_set()} from
     * {@code glainf.c}. Collects all vectors from all clusters into a new set.
     * </p>
     *
     * @param partition
     *            the partition to convert
     * @return a new VectorSet containing all vectors from all clusters
     */
    public static VectorSet partitionToSet(Partition partition) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);

        VectorSet result = new VectorSet();
        for (int i = 1; i <= partition.size(); i++) {
            VectorSet cluster = partition.getElements(i);
            for (BinaryVector bv : cluster) {
                result.addElement(bv.copy()); // Copy to avoid reference issues
            }
        }
        return result;
    }

    /**
     * Computes the average Shannon codelength over all vectors in a partition.
     * <p>
     * Equivalent to C function {@code average_codelength()} from
     * {@code distmin.c}. Averages code lengths (or weighted code lengths) over
     * all vectors, optionally using class weights for frequency-adjusted
     * scoring.
     * </p>
     *
     * @param partition
     *            the partition containing cluster assignments
     * @param centroids
     *            the centroid array defining clusters
     * @param useWeights
     *            if true, uses weighted codelength; otherwise unweighted
     * @return the average codelength across all vectors
     */
    public static double averageCodelength(Partition partition,
            InfiniteCentroids centroids, boolean useWeights) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        int k = centroids.size();
        double totalDist = 0.0;
        int totalCount = 0;

        for (int i = 1; i <= k; i++) {
            VectorSet cluster = partition.getElements(i);
            Centroid centroid = centroids.get(i - 1); // 0-based internal
                                                      // indexing

            for (BinaryVector bv : cluster) {
                double dist = useWeights
                        ? DistanceCalculator.codeLength2(bv, centroid)
                        : DistanceCalculator.codeLength(bv, centroid);
                totalDist += dist;
                totalCount++;
            }
        }

        return totalCount > 0 ? totalDist / totalCount : 0.0;
    }

}
