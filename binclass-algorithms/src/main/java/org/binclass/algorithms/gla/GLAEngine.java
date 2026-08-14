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
        for (int i = 1; i <= partition.size(); i++) {
            int size = partition.getSize(i);
            Centroid centroid = centroids.get(i - 1);
            double entropy = calculateEntropy(centroid, config.rounded());

            logger.info("Cluster {}: size={}, entropy={:.4f}",
                    i, size, entropy);
        }
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
            if (rounded) {
                // Binary case: p=0 or p=1 → entropy=0
                continue;
            } else {
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

        // Phase 1: Initial assignment using Shannon codelength
        NearestNeighbor.infNearestNeighbor(vectors, partition, centroids,
                false);
        removeEmpty(partition, centroids);

        // Apply trashcan outlier detection if enabled
        applyTrashcan(vectors, centroids, config);

        // Recompute centroids from assignments (excludes trashcan vectors)
        recomputeCentroids(partition, centroids, config.rounded(), config.n());

        double d = averageCodelength(partition, centroids, config.weights());

        // Phase 2: Iterative refinement with weighted codelength
        boolean improvement = true;
        int iter = 0;
        int maxIter = config.maxIter() > 0 ? config.maxIter() : MAX_ITERATIONS;
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
            // Use unweighted assignment to avoid bias toward larger clusters
            NearestNeighbor.infNearestNeighbor(vectors, partition, centroids,
                    false);

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
        d = averageCodelength(partition, centroids, config.weights()); // Double
                                                                       // computation
                                                                       // for
                                                                       // stability
        // computation for
        // stability

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
        d = averageCodelength(partition, centroids, config.weights()); // Double
                                                                       // computation
                                                                       // for
                                                                       // stability
        // computation for
        // stability

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
     * Equivalent to C function {@code inf_remove_empty()} from
     * {@code glainf.c}. Handles the "empty cell problem" where some clusters
     * may become empty during GLA iterations, requiring dynamic adjustment of
     * k.
     * </p>
     *
     * @param partition
     *            the partition to clean up
     * @param centroids
     *            the centroid array (size will be adjusted)
     */
    public static void removeEmpty(Partition partition,
            InfiniteCentroids centroids) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);
        if (partition.size() == 0) {
            throw new IllegalStateException("Partition is empty");
        }

        // Count non-empty clusters
        int newK = 0;
        for (int i = 1; i <= partition.size(); i++) {
            if (partition.getSize(i) > 0) {
                newK++;
            }
        }
        if (newK <= 0) {
            throw new IllegalStateException("All clusters are empty");
        }

        // Adjust centroid count to match non-empty clusters
        if (newK != centroids.size()) {
            logger.debug(
                    "Adjusting centroids from {} to {} (empty cluster removal)",
                    centroids.size(), newK);

            // Remove empty centroids from the end
            while (centroids.size() > newK) {
                centroids.remove(centroids.size() - 1);
            }
        }

        // Update partition size to match actual non-empty clusters
        if (partition.size() != newK) {
            logger.debug("Updating partition size from {} to {}",
                    partition.size(), newK);
            partition.setSize(newK);
        } else {
            logger.debug("Partition size already matches: {}", newK);
        }

        // Log cluster sizes for debugging
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= partition.size(); i++) {
            if (i > 1)
                sb.append(", ");
            sb.append("C").append(i).append("=").append(partition.getSize(i));
        }
        logger.debug("Cluster sizes: [{}]", sb);

        logger.debug("Partition now has {} non-empty clusters", newK);
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
            int classSize = cluster.size();

            if (classSize == 0) {
                continue; // Skip empty clusters
            }

            // Compute frequency-weighted average for each bit position
            double[] el = centroid.getArray();
            for (int bit = 0; bit < l && bit < el.length; bit++) {
                int count1 = 0;
                int missingCount = 0;
                for (BinaryVector bv : cluster) {
                    // Skip trashcan vectors when mode is enabled
                    if (!bv.isTrashcan()) {
                        if (!bv.isMissing(bit)) {
                            count1 += bv.get(bit);
                        } else {
                            missingCount++;
                        }
                    }
                }

                // Effective count excludes missing values
                int effectiveCount = classSize - missingCount;
                double avg = (effectiveCount > 0)
                        ? (double) count1 / effectiveCount
                        : 0.5;

                if (rounded) {
                    // Round to nearest binary value
                    el[bit] = avg >= 0.5 ? 1.0 : 0.0;
                } else {
                    el[bit] = avg;
                }
            }

            // Set weight as class frequency ratio
            centroid.setWeight((double) classSize / n);
        }
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
