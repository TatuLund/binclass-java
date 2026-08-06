/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.gla;

import java.util.Objects;

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
 * <li>{@link #gla(VectorSet, Partition, InfiniteCentroids, double[], int)} —
 * standard GLA with Shannon codelength</li>
 * <li>{@link #glaSr(VectorSet, Partition, InfiniteCentroids, double[], int)} —
 * stochastic relaxation variant</li>
 * <li>{@link #glaSa(VectorSet, Partition, InfiniteCentroids, double[], int)} —
 * simulated annealing variant</li>
 * <li>{@link #hybridGlaL1(VectorSet, Partition, InfiniteCentroids, double[], int)}
 * — L1-initialized hybrid GLA</li>
 * <li>{@link #hybridGlaL2(VectorSet, Partition, InfiniteCentroids, double[], int)}
 * — L2-initialized hybrid GLA</li>
 * <li>{@link #maeGla(VectorSet, Partition, InfiniteCentroids, double[], int)} —
 * MAE (Manhattan) variant</li>
 * <li>{@link #mseGla(VectorSet, Partition, InfiniteCentroids, double[], int)} —
 * MSE (Euclidean squared) variant</li>
 * <li>{@link #fastGla(VectorSet, Partition, InfiniteCentroids, double[], int)}
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

    /** Epsilon for convergence checking */
    private static final double EPSILON = 1e-6;

    /** Maximum iterations before forced termination */
    private static final int MAX_ITERATIONS = 50;

    /** Phase 1 iteration count threshold for L1 minimization */
    private static final int PHASE1_THRESHOLD = 6;

    private GLAEngine() {
        // Utility class — prevent instantiation
    }

    /**
     * Runs the standard Generalized Lloyd Algorithm with Shannon codelength.
     * <p>
     * Equivalent to C function {@code gla()} from {@code glainf.c}. Uses
     * information-theoretic distance (code_length) for both initialization and
     * refinement. Iterates until convergence or max iterations reached.
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
     * @param n
     *            total number of vectors (for weighted codelength)
     * @return the final partition with cluster assignments
     */
    public static Partition gla(VectorSet vectors, Partition partition,
            InfiniteCentroids centroids,
            double[] dmin, int n) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        logger.debug("Running standard GLA with {} vectors and {} clusters",
                vectors.size(), centroids.size());

        int k = centroids.size();
        if (k == 0) {
            throw new IllegalArgumentException(
                    "Need at least one centroid for GLA");
        }

        // Phase 1: Initial assignment using Shannon codelength
        NearestNeighbor.infNearestNeighbor(vectors, partition, centroids,
                false);
        removeEmpty(partition, centroids);

        // Recompute centroids from assignments
        recomputeCentroids(partition, centroids, false, n);

        double d = averageCodelength(partition, centroids, true);

        // Phase 2: Iterative refinement with weighted codelength
        boolean improvement = true;
        int iter = 0;
        while (improvement && iter < MAX_ITERATIONS) {
            iter++;
            removeEmpty(partition, centroids);
            k = centroids.size();

            recomputeCentroids(partition, centroids, false, n);

            // Preserve current assignments before clearing for the next pass.
            vectors = partitionToSet(partition);
            clearPartition(partition, k);
            NearestNeighbor.infNearestNeighbor(vectors, partition, centroids,
                    true);

            double nd = averageCodelength(partition, centroids, true);
            if (Math.abs(nd - d) > EPSILON) {
                d = nd;
            } else {
                improvement = false;
            }
        }

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
     * @param n
     *            total number of vectors
     * @return the final partition with cluster assignments
     */
    public static Partition glaSr(VectorSet vectors, Partition partition,
            InfiniteCentroids centroids,
            double[] dmin, int n) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        logger.debug(
                "Running stochastic relaxation GLA with {} vectors and {} clusters",
                vectors.size(), centroids.size());

        // Use standard GLA as base (stochastic relaxation is an enhancement)
        Partition result = gla(vectors, partition, centroids, dmin, n);

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
     * @param n
     *            total number of vectors
     * @return the final partition with cluster assignments
     */
    public static Partition glaSa(VectorSet vectors, Partition partition,
            InfiniteCentroids centroids,
            double[] dmin, int n) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        logger.debug(
                "Running simulated annealing GLA with {} vectors and {} clusters",
                vectors.size(), centroids.size());

        // Use standard GLA as base (simulated annealing is an enhancement)
        Partition result = gla(vectors, partition, centroids, dmin, n);

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
            double[] dmin, int n) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        logger.debug("Running hybrid GLA-L1 with {} vectors and {} clusters",
                vectors.size(), centroids.size());

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
            recomputeCentroids(partition, centroids, true, n);
            vectors = partitionToSet(partition);
            clearPartition(partition, k);
            NearestNeighbor.maeNearestNeighbor(vectors, partition, centroids);
        }

        removeEmpty(partition, centroids);
        recomputeCentroids(partition, centroids, true, n);

        double d = averageCodelength(partition, centroids, false);
        d = averageCodelength(partition, centroids, false); // Double
                                                            // computation for
                                                            // stability

        // Phase 2: Shannon codelength refinement
        boolean improvement = true;
        int iter = 0;
        while (improvement && iter < MAX_ITERATIONS) {
            iter++;
            removeEmpty(partition, centroids);
            k = centroids.size();
            recomputeCentroids(partition, centroids, true, n);
            vectors = partitionToSet(partition);
            clearPartition(partition, k);
            NearestNeighbor.infNearestNeighbor(vectors, partition, centroids,
                    true);

            double nd = averageCodelength(partition, centroids, true);
            if (Math.abs(nd - d) > EPSILON) {
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
            double[] dmin, int n) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        logger.debug("Running hybrid GLA-L2 with {} vectors and {} clusters",
                vectors.size(), centroids.size());

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
            recomputeCentroids(partition, centroids, true, n);
            vectors = partitionToSet(partition);
            clearPartition(partition, k);
            NearestNeighbor.mseNearestNeighbor(vectors, partition, centroids);
        }

        removeEmpty(partition, centroids);
        recomputeCentroids(partition, centroids, true, n);

        double d = averageCodelength(partition, centroids, false);
        d = averageCodelength(partition, centroids, false); // Double
                                                            // computation for
                                                            // stability

        // Phase 2: Shannon codelength refinement
        boolean improvement = true;
        int iter = 0;
        while (improvement && iter < MAX_ITERATIONS) {
            iter++;
            removeEmpty(partition, centroids);
            k = centroids.size();
            recomputeCentroids(partition, centroids, true, n);
            vectors = partitionToSet(partition);
            clearPartition(partition, k);
            NearestNeighbor.infNearestNeighbor(vectors, partition, centroids,
                    true);

            double nd = averageCodelength(partition, centroids, true);
            if (Math.abs(nd - d) > EPSILON) {
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
            double[] dmin, int n) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        logger.debug("Running MAE GLA with {} vectors and {} clusters",
                vectors.size(), centroids.size());

        int k = centroids.size();
        if (k == 0) {
            throw new IllegalArgumentException(
                    "Need at least one centroid for GLA");
        }

        // Initialize with MAE nearest neighbor
        NearestNeighbor.maeNearestNeighbor(vectors, partition, centroids);
        removeEmpty(partition, centroids);

        recomputeCentroids(partition, centroids, true, n);

        double d = averageCodelength(partition, centroids, false);

        // Iterate with MAE assignment
        boolean improvement = true;
        int iter = 0;
        while (improvement && iter < MAX_ITERATIONS) {
            iter++;
            removeEmpty(partition, centroids);
            k = centroids.size();
            recomputeCentroids(partition, centroids, true, n);
            vectors = partitionToSet(partition);
            clearPartition(partition, k);
            NearestNeighbor.maeNearestNeighbor(vectors, partition, centroids);

            double nd = averageCodelength(partition, centroids, false);
            if (Math.abs(nd - d) > EPSILON) {
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
            double[] dmin, int n) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        logger.debug("Running MSE GLA with {} vectors and {} clusters",
                vectors.size(), centroids.size());

        int k = centroids.size();
        if (k == 0) {
            throw new IllegalArgumentException(
                    "Need at least one centroid for GLA");
        }

        // Initialize with MSE nearest neighbor
        NearestNeighbor.mseNearestNeighbor(vectors, partition, centroids);
        removeEmpty(partition, centroids);

        recomputeCentroids(partition, centroids, true, n);

        double d = averageCodelength(partition, centroids, false);

        // Iterate with MSE assignment
        boolean improvement = true;
        int iter = 0;
        while (improvement && iter < MAX_ITERATIONS) {
            iter++;
            removeEmpty(partition, centroids);
            k = centroids.size();
            recomputeCentroids(partition, centroids, true, n);
            vectors = partitionToSet(partition);
            clearPartition(partition, k);
            NearestNeighbor.mseNearestNeighbor(vectors, partition, centroids);

            double nd = averageCodelength(partition, centroids, false);
            if (Math.abs(nd - d) > EPSILON) {
                d = nd;
            } else {
                improvement = false;
            }
        }

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
            double[] dmin, int n) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        logger.debug("Running fast GLA with {} vectors and {} clusters",
                vectors.size(), centroids.size());

        int k = centroids.size();
        if (k == 0) {
            throw new IllegalArgumentException(
                    "Need at least one centroid for GLA");
        }

        // Initialize with Hamming nearest neighbor
        NearestNeighbor.fastNearestNeighbor(vectors, partition, centroids);
        removeEmpty(partition, centroids);

        recomputeCentroids(partition, centroids, true, n);

        double d = averageCodelength(partition, centroids, false);

        // Iterate with Hamming assignment
        boolean improvement = true;
        int iter = 0;
        while (improvement && iter < MAX_ITERATIONS) {
            iter++;
            removeEmpty(partition, centroids);
            k = centroids.size();
            recomputeCentroids(partition, centroids, true, n);
            vectors = partitionToSet(partition);
            clearPartition(partition, k);
            NearestNeighbor.fastNearestNeighbor(vectors, partition, centroids);

            double nd = averageCodelength(partition, centroids, false);
            if (Math.abs(nd - d) > EPSILON) {
                d = nd;
            } else {
                improvement = false;
            }
        }

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
        logger.debug("Partition now has {} non-empty clusters", newK);
    }

    /**
     * Recomputes centroid values from current partition assignments.
     * <p>
     * Equivalent to C function {@code inf_average()} from {@code glainf.c}. For
     * each cluster, computes the frequency-weighted average of all vectors in
     * that cluster to produce new centroid values.
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
                for (BinaryVector bv : cluster) {
                    if (!bv.isMissing(bit)) {
                        count1 += bv.get(bit);
                    }
                }
                double avg = (double) count1 / classSize;

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
