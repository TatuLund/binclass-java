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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Join-GLA algorithm with Pairwise Nearest Neighbor (PNN) merging.
 * <p>
 * Mirrors functions from {@code joingla.c} in the original C codebase. The
 * Join-GLA algorithm starts with many clusters and iteratively merges the
 * closest pair until a threshold is reached, then refines with GLA.
 * </p>
 * <p>
 * Key methods:
 * <ul>
 * <li>{@link #joinGLA(VectorSet, double[], double[])} — main search loop</li>
 * <li>{@link #setFirstCentroidsPNN2(VectorSet, double)} — weighted PNN
 * merging</li>
 * <li>{@link #setFirstCentroidsPNN(VectorSet, double)} — simple 50/50
 * merge</li>
 * <li>{@link #joinTwoClasses(InfiniteCentroids, Partition)} — merge closest
 * pair</li>
 * </ul>
 * </p>
 */
@SuppressWarnings({ "java:S117", "java:S3457" })
public final class JoinGLA {

    private static final String VECTOR_SET_MUST_NOT_BE_NULL = "VectorSet must not be null";

    private static final Logger logger = LoggerFactory.getLogger(JoinGLA.class);

    /** Minimum number of clusters to maintain */
    private static final int MIN_CLUSTERS = 1;

    private JoinGLA() {
        // Utility class — prevent instantiation
    }

    /**
     * Runs the Join-GLA algorithm to search for optimal k by merging.
     * <p>
     * Equivalent to C function {@code join_gla()} from {@code joingla.c}.
     * Starts with many clusters (one per vector), then iteratively merges the
     * closest pair until threshold reached, refining with GLA at each step.
     * </p>
     * <p>
     * The algorithm:
     * <ol>
     * <li>Initialize centroids using PNN merging from all vectors</li>
     * <li>Evaluate SC for current k</li>
     * <li>Merge closest pair of clusters</li>
     * <li>Refine with GLA and evaluate SC</li>
     * <li>Repeat until minimum cluster count reached</li>
     * </ol>
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to cluster (consumed — emptied
     *            after)
     * @param scmin
     *            output array for minimum SC value found [0]
     * @param scs
     *            output array of SC values indexed by k (1-based, size s+1)
     * @return the final partition with cluster assignments at optimal k
     */
    public static Partition joinGLA(VectorSet vectors, double[] scmin,
            double[] scs, GLAConfig config) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(scmin, "scmin array must not be null");
        Objects.requireNonNull(scs, "scs array must not be null");

        int s = vectors.size();
        if (s < MIN_CLUSTERS) {
            logger.warn("Join-GLA requires at least {} vectors, got {}",
                    MIN_CLUSTERS, s);
            return new Partition(1);
        }

        int l = getVectorLength(vectors);

        // Initialize SC array with sentinel values
        for (int i = 0; i <= s; i++) {
            scs[i] = Double.MAX_VALUE;
        }

        // Step 1: Initialize centroids using PNN2 (weighted merging)
        InfiniteCentroids C = setFirstCentroidsPNN2(vectors,
                config.pnnThreshold());
        int k = C.size();

        logger.debug("Initial clusters after PNN2: {}", k);

        // Step 2: Refine with GLA and evaluate SC
        Partition P = new Partition(k);
        VectorSet cluster0 = P.getElements(1);
        vectors.copyTo(cluster0);

        logger.debug("Initial k={}, partition.size()={}", k, P.size());
        for (int i = 1; i <= P.size(); i++) {
            logger.debug("  Cluster {}: size={}", i, P.getSize(i));
        }

        double[] dmin = new double[1];
        GLAEngine.gla(vectors, P, C, dmin, config);

        // Use L2-based MSE (not codelength) for MDL-based SC calculation
        // Codelength doesn't work well for MDL because uniform centroids have
        // low codelengths
        double mse = DistanceCalculator.overallMse(P, C);
        int actualK = P.size();
        logger.debug(
                "After GLA - k={}, partition.size()={}, centroids.size()={}",
                k, P.size(), C.size());
        for (int i = 1; i <= P.size(); i++) {
            logger.debug("  Cluster {}: size={}", i, P.getSize(i));
        }
        double sc = DistanceCalculator.stochasticComplexityWithDistortion(P,
                actualK, l, mse);
        logger.debug("Initial SC: {}, MSE = {}", sc, mse);

        if (sc < scs[k]) {
            scs[k] = sc;
        }
        if (sc < scmin[0]) {
            scmin[0] = sc;
            logger.debug("Initial: SC = {:.4f} — best so far", sc);
        }

        // Step 3: Iterative merge loop - track the best partition by SC score
        Partition bestP = P.copy(); // Make a copy to avoid reference aliasing
        double bestSC = sc;

        int maxIterations = s + 1; // safety limit to prevent infinite loops
        while (k > MIN_CLUSTERS && maxIterations-- > 0) {
            // Merge closest pair - modifies both C and P in-place
            joinTwoClasses(C, P);
            k = C.size(); // Update k based on actual centroid count after merge

            logger.debug("Merged to {} clusters", k);

            // Refine with GLA using the updated partition (not a new empty one)
            Partition Pnew = P;
            VectorSet Vnext = partitionToSet(P);
            double[] dminNew = new double[1];
            GLAEngine.gla(Vnext, Pnew, C, dminNew, config);

            // Use L2-based MSE (not codelength) for MDL-based SC calculation
            double mseNew = DistanceCalculator.overallMse(Pnew, C);
            int actualKNew = Pnew.size();
            double scNew = DistanceCalculator
                    .stochasticComplexityWithDistortion(Pnew, actualKNew, l,
                            mseNew);
            logger.debug("k={}: SC = {}, MSE = {}", k, scNew, mseNew);

            if (scNew < scs[k]) {
                scs[k] = scNew;
            }
            if (scNew < bestSC) {
                bestSC = scNew;
                bestP = Pnew.copy(); // Make a copy to preserve this partition
                logger.debug("k={}: SC = {:.4f} — new best", k, scNew);
            }

            P = Pnew;
        }

        return bestP;
    }

    /**
     * Initializes centroids using Pairwise Nearest Neighbor (PNN) with weighted
     * averaging.
     * <p>
     * Equivalent to C function {@code set_first_centroids_pnn2()} from
     * {@code joingla.c}. Starts with one centroid per vector, then iteratively
     * merges the closest pair using weighted average until threshold reached.
     * </p>
     * <p>
     * Weighted averaging: when merging cluster i (with count w_i) and cluster j
     * (with count w_j), the new centroid is:
     * 
     * <pre>
     * c_new = (w_i * c_i + w_j * c_j) / (w_i + w_j)
     * </pre>
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to cluster
     * @param threshold
     *            L2 distance threshold for stopping merges
     * @return initialized centroid array with merged clusters
     */
    public static InfiniteCentroids setFirstCentroidsPNN2(VectorSet vectors,
            double threshold) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);

        int n = vectors.size();
        if (n < 2) {
            throw new IllegalArgumentException(
                    "Need at least 2 vectors for PNN merging");
        }

        int l = getVectorLength(vectors);
        BinaryVector[] allVectors = vectors.toArray(new BinaryVector[0]);

        // Initialize centroids and weights from input vectors
        double[][] centroids = new double[n][l];
        int[] weights = new int[n];

        for (int i = 0; i < n; i++) {
            BinaryVector v = allVectors[i];
            for (int j = 0; j < l; j++) {
                centroids[i][j] = v.get(j);
            }
            weights[i] = 1;
        }

        int currentN = n;

        // Iteratively merge closest pair until threshold reached
        while (currentN > MIN_CLUSTERS) {
            PairInfo pair = findClosestPair(centroids, l, currentN);
            if (pair.dmin >= threshold) {
                logger.debug(
                        "PNN2: Stopping at {} clusters, dmin = {:.4f} >= threshold",
                        currentN, pair.dmin);
                break;
            }

            mergeWeighted(centroids, weights, pair.i, pair.j, l, currentN);
            currentN--;
        }

        // Create InfiniteCentroids from merged centroids
        return createInfiniteCentroids(centroids, currentN, l);
    }

    /**
     * Initializes centroids using Pairwise Nearest Neighbor (PNN) with simple
     * averaging.
     * <p>
     * Equivalent to C function {@code set_first_centroids_pnn()} from
     * {@code joingla.c}. Similar to PNN2 but uses unweighted 50/50 merging:
     * 
     * <pre>
     * c_new = (c_i + c_j) * 0.5
     * </pre>
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to cluster
     * @param threshold
     *            L2 distance threshold for stopping merges
     * @return initialized centroid array with merged clusters
     */
    public static InfiniteCentroids setFirstCentroidsPNN(VectorSet vectors,
            double threshold) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);

        int n = vectors.size();
        if (n < 2) {
            throw new IllegalArgumentException(
                    "Need at least 2 vectors for PNN merging");
        }

        int l = getVectorLength(vectors);
        BinaryVector[] allVectors = vectors.toArray(new BinaryVector[0]);

        // Initialize centroids from input vectors
        double[][] centroids = new double[n][l];

        for (int i = 0; i < n; i++) {
            BinaryVector v = allVectors[i];
            for (int j = 0; j < l; j++) {
                centroids[i][j] = v.getElement(j);
            }
        }

        int currentN = n;

        // Iteratively merge closest pair until threshold reached
        while (currentN > MIN_CLUSTERS) {
            PairInfo pair = findClosestPair(centroids, l, currentN);
            if (pair.dmin >= threshold) {
                logger.debug(
                        "PNN: Stopping at {} clusters, dmin = {:.4f} >= threshold",
                        currentN, pair.dmin);
                break;
            }

            mergeUnweighted(centroids, pair.i, pair.j, l, currentN);
            currentN--;
        }

        // Create InfiniteCentroids from merged centroids
        return createInfiniteCentroids(centroids, currentN, l);
    }

    /**
     * Merges the closest pair of clusters using weighted averaging.
     * <p>
     * Equivalent to C function {@code join_two_classes()} from
     * {@code joingla.c}. Finds the two centroids with minimum L2 distance, then
     * merges them by:
     * <ol>
     * <li>Computing weighted average of all vectors in both clusters</li>
     * <li>Removing the second centroid</li>
     * </ol>
     * </p>
     *
     * @param centroids
     *            the centroid array (modified — k decreases by 1)
     * @param partition
     *            the current partition with cluster assignments
     */
    public static void joinTwoClasses(InfiniteCentroids centroids,
            Partition partition) {
        Objects.requireNonNull(centroids, "InfiniteCentroids must not be null");
        Objects.requireNonNull(partition, "Partition must not be null");

        int k = centroids.size();
        if (k < 2) {
            logger.warn("Cannot merge: less than 2 clusters");
            return;
        }

        int l = getCentroidLength(centroids);

        // Find closest pair using L2 distance between centroids
        double dmin = Double.MAX_VALUE;
        int imin = -1;
        int jmin = -1;

        for (int i = 0; i < k; i++) {
            Centroid ci = centroids.get(i);
            for (int j = i + 1; j < k; j++) {
                Centroid cj = centroids.get(j);
                double d = edistance2(ci, cj, l);
                if (d < dmin) {
                    dmin = d;
                    imin = i;
                    jmin = j;
                }
            }
        }

        logger.debug("Joining clusters {} and {}, distance = {:.4f}", imin,
                jmin, dmin);

        // Get vectors from both clusters as arrays (convert 0-based to 1-based)
        BinaryVector[] vi = partition.getElements(imin + 1)
                .toArray(new BinaryVector[0]);
        BinaryVector[] vj = partition.getElements(jmin + 1)
                .toArray(new BinaryVector[0]);

        // Compute weighted average of all vectors in both clusters
        double[] newCentroid = computeMergedCentroid(vi, vj, l);

        // Update centroid i with the merged values
        Centroid cI = centroids.get(imin);
        for (int j = 0; j < l; j++) {
            cI.set(j, newCentroid[j]);
        }

        // Move all vectors from cluster j to cluster i in the partition
        moveVectors(partition, vj, imin + 1, jmin + 1);

        // Remove centroid j by shifting last centroid into its place
        removeLastCentroid(centroids, jmin, k, l);

        // Also remove cluster j from the partition to keep them in sync
        partition.removeCluster(jmin + 1);

        logger.debug("Merged: {} → {} clusters", k, k - 1);
    }

    /**
     * Computes squared Euclidean distance between two double arrays.
     * <p>
     * Equivalent to C function {@code edistance_2()} from {@code distmin.h}.
     * </p>
     *
     * @param a
     *            first vector
     * @param b
     *            second vector
     * @param l
     *            length of vectors
     * @return squared Euclidean distance
     */
    public static double edistance2(double[] a, double[] b, int l) {
        double sum = 0.0;
        for (int i = 0; i < l; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return sum;
    }

    /**
     * Computes squared Euclidean distance between two Centroids.
     *
     * @param a
     *            first centroid
     * @param b
     *            second centroid
     * @param l
     *            length of centroids
     * @return squared Euclidean distance
     */
    public static double edistance2(Centroid a, Centroid b, int l) {
        if (a == null || b == null) {
            throw new NullPointerException("Centroids must not be null");
        }
        double sum = 0.0;
        for (int i = 0; i < l; i++) {
            double diff = a.getElement(i) - b.getElement(i);
            sum += diff * diff;
        }
        return sum;
    }

    /**
     * Converts a Partition to a VectorSet by collecting all cluster vectors.
     * <p>
     * Equivalent to C function {@code partition_to_set()} from
     * {@code joingla.c}.
     * </p>
     *
     * @param partition
     *            the partition to convert
     * @return a new VectorSet containing all vectors from all clusters
     */
    public static VectorSet partitionToSet(Partition partition) {
        Objects.requireNonNull(partition, "Partition must not be null");

        VectorSet result = new VectorSet();
        int k = partition.size();

        // Iterate 1-based clusters (partition API is 1-based, internal array is
        // 0-based)
        for (int i = 1; i <= k; i++) {
            BinaryVector[] cluster = partition.getElements(i)
                    .toArray(new BinaryVector[0]);
            for (BinaryVector v : cluster) {
                result.add(v);
            }
        }

        return result;
    }

    /**
     * Returns the length of centroids in an InfiniteCentroids array.
     *
     * @param centroids
     *            the centroid array
     * @return the length (number of dimensions) of centroids
     */
    private static int getCentroidLength(InfiniteCentroids centroids) {
        Centroid c = centroids.get(0);
        if (c == null) {
            throw new IllegalArgumentException(
                    "Cannot determine centroid length from empty array");
        }
        return c.getLength();
    }

    /**
     * Returns the length of binary vectors in a VectorSet.
     * <p>
     * Convenience method to extract vector length from any vector in the set.
     * </p>
     *
     * @param vectors
     *            the vector set
     * @return the length (number of bits) of vectors in this set
     */
    private static int getVectorLength(VectorSet vectors) {
        BinaryVector[] all = vectors.toArray(new BinaryVector[0]);
        if (all.length == 0) {
            throw new IllegalArgumentException(
                    "Cannot determine vector length from empty VectorSet");
        }
        return all[0].getLength();
    }

    /**
     * Creates an {@link InfiniteCentroids} instance from raw centroid data.
     *
     * @param centroids
     *            the raw double arrays of centroid values
     * @param count
     *            number of valid centroids in the arrays
     * @param l
     *            length of each centroid
     * @return a new InfiniteCentroids instance with merged data
     */
    private static InfiniteCentroids createInfiniteCentroids(
            double[][] centroids, int count, int l) {
        InfiniteCentroids result = new InfiniteCentroids(count, l);
        for (int i = 0; i < count; i++) {
            Centroid c = result.get(i);
            for (int j = 0; j < l; j++) {
                c.set(j, centroids[i][j]);
            }
        }
        return result;
    }

    /**
     * Computes the merged centroid by averaging all vectors from two clusters.
     *
     * @param vi
     *            vectors in cluster i
     * @param vj
     *            vectors in cluster j
     * @param l
     *            length of each vector
     * @return the averaged centroid values
     */
    private static double[] computeMergedCentroid(BinaryVector[] vi,
            BinaryVector[] vj, int l) {
        double[] newCentroid = new double[l];
        int totalVectors = vi.length + vj.length;

        for (BinaryVector v : vi) {
            for (int j = 0; j < l; j++) {
                newCentroid[j] += v.getElement(j);
            }
        }
        for (BinaryVector v : vj) {
            for (int j = 0; j < l; j++) {
                newCentroid[j] += v.getElement(j);
            }
        }

        if (totalVectors > 0) {
            for (int j = 0; j < l; j++) {
                newCentroid[j] /= totalVectors;
            }
        }

        return newCentroid;
    }

    /**
     * Moves vectors from one cluster to another in a partition.
     *
     * @param partition
     *            the partition containing the clusters
     * @param vjMove
     *            vectors to move
     * @param targetCluster
     *            1-based index of the destination cluster
     * @param sourceCluster
     *            1-based index of the source cluster
     */
    private static void moveVectors(Partition partition, BinaryVector[] vjMove,
            int targetCluster, int sourceCluster) {
        for (BinaryVector v : vjMove) {
            partition.addElement(targetCluster, v);
            partition.removeElement(sourceCluster, v);
        }
    }

    /**
     * Removes a centroid by shifting the last one into its place.
     *
     * @param centroids
     *            the centroid array (modified in-place)
     * @param jmin
     *            0-based index of the centroid to remove
     * @param k
     *            current number of centroids before removal
     * @param l
     *            length of each centroid
     */
    private static void removeLastCentroid(InfiniteCentroids centroids,
            int jmin, int k, int l) {
        if (jmin != k - 1) {
            Centroid cjLast = centroids.get(k - 1);
            for (int j = 0; j < l; j++) {
                centroids.get(jmin).set(j, cjLast.getElement(j));
            }
        }
        centroids.remove(k - 1);
    }

    /**
     * Information about the closest pair of clusters.
     *
     * @param dmin
     *            squared distance between the pair
     * @param i
     *            index of first cluster
     * @param j
     *            index of second cluster
     */
    private record PairInfo(double dmin, int i, int j) {
    }

    /**
     * Finds the closest pair of clusters in a centroid array.
     *
     * @param centroids
     *            the raw double arrays of centroid values
     * @param l
     *            length of each centroid
     * @param count
     *            number of valid centroids
     * @return information about the closest pair found
     */
    private static PairInfo findClosestPair(double[][] centroids, int l,
            int count) {
        double dmin = Double.MAX_VALUE;
        int imin = -1;
        int jmin = -1;

        for (int i = 0; i < count; i++) {
            for (int j = i + 1; j < count; j++) {
                double d = edistance2(centroids[i], centroids[j], l);
                if (d < dmin) {
                    dmin = d;
                    imin = i;
                    jmin = j;
                }
            }
        }

        return new PairInfo(dmin, imin, jmin);
    }

    /**
     * Merges two clusters using weighted averaging and shifts the last cluster
     * into the removed position.
     *
     * @param centroids
     *            the raw double arrays of centroid values
     * @param weights
     *            weight array for each cluster
     * @param i
     *            index of first cluster (kept)
     * @param j
     *            index of second cluster (merged into i)
     * @param l
     *            length of each centroid
     * @param count
     *            current number of clusters before removal
     */
    private static void mergeWeighted(double[][] centroids, int[] weights,
            int i, int j, int l, int count) {
        double wI = weights[i];
        double wJ = weights[j];
        double totalW = wI + wJ;

        for (int k = 0; k < l; k++) {
            centroids[i][k] = ((wI * centroids[i][k])
                    + (wJ * centroids[j][k])) / totalW;
        }
        weights[i] = (int) totalW;

        if (j != count - 1) {
            System.arraycopy(centroids[count - 1], 0, centroids[j], 0, l);
            weights[j] = weights[count - 1];
        }
    }

    /**
     * Merges two clusters using simple 50/50 averaging and shifts the last
     * cluster into the removed position.
     *
     * @param centroids
     *            the raw double arrays of centroid values
     * @param i
     *            index of first cluster (kept)
     * @param j
     *            index of second cluster (merged into i)
     * @param l
     *            length of each centroid
     * @param count
     *            current number of clusters before removal
     */
    private static void mergeUnweighted(double[][] centroids, int i, int j,
            int l, int count) {
        for (int k = 0; k < l; k++) {
            centroids[i][k] = (centroids[i][k] + centroids[j][k]) * 0.5;
        }

        if (j != count - 1) {
            System.arraycopy(centroids[count - 1], 0, centroids[j], 0, l);
        }
    }
}
