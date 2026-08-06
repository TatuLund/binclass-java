/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.gla;

import java.util.Objects;
import java.util.Random;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.dist.DistanceCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Split-GLA hybrid algorithm for fast search of optimal k.
 * <p>
 * Mirrors functions from {@code splitgla.c} in the original C codebase. The
 * Split-GLA algorithm searches for the best number of clusters by iteratively
 * splitting the worst-matching cluster and refining with GLA until no further
 * improvement is found (SC stagnation).
 * </p>
 * <p>
 * Key methods:
 * <ul>
 * <li>{@link #splitGLA(VectorSet, double[], double[], String)} — main search
 * loop</li>
 * <li>{@link #worstMatchingVectors(VectorSet, Random)} — find worst pair from
 * random start</li>
 * <li>{@link #absWorstMatchingVectors(VectorSet, Random)} — brute-force worst
 * pair</li>
 * <li>{@link #setFirstCentroids(InfiniteCentroids, BinaryVector, BinaryVector)}
 * — initialize centroids</li>
 * <li>{@link #pointWorstClass(Partition, InfiniteCentroids)} — find class with
 * max distortion</li>
 * <li>{@link #setNewCentroids(InfiniteCentroids, InfiniteCentroids, BinaryVector, BinaryVector, int)}
 * — update after split</li>
 * </ul>
 * </p>
 */
@SuppressWarnings({ "java:S117", "java:S3457" })
public final class SplitGLA {

    private static final String VECTOR_SET_MUST_NOT_BE_NULL = "VectorSet must not be null";

    private static final Logger logger = LoggerFactory
            .getLogger(SplitGLA.class);

    /** Maximum number of clusters to search (s + 1 where s is vector count) */
    private static final int KSTOPWHEN = 20;

    /** Number of steps without SC improvement before stopping */
    private static final int KC_STOP_WHEN = 5;

    private SplitGLA() {
        // Utility class — prevent instantiation
    }

    /**
     * Runs the Split-GLA algorithm to search for optimal k.
     * <p>
     * Equivalent to C function {@code split_gla()} from {@code splitgla.c}.
     * Starts with 1 cluster, then iteratively splits the worst-matching cluster
     * and refines with GLA until SC stagnates or max clusters reached.
     * </p>
     * <p>
     * The algorithm:
     * <ol>
     * <li>Evaluate k=1 (single cluster, no refinement)</li>
     * <li>Find worst-matching vector pair</li>
     * <li>Initialize centroids from the pair</li>
     * <li>Iterate: refine with GLA, evaluate SC, split worst class if
     * improving</li>
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
    public static Partition splitGLA(VectorSet vectors, double[] scmin,
            double[] scs) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);
        Objects.requireNonNull(scmin, "scmin array must not be null");
        Objects.requireNonNull(scs, "scs array must not be null");

        int s = vectors.size();
        if (s < 2) {
            logger.warn("Split-GLA requires at least 2 vectors, got {}", s);
            return new Partition(1);
        }

        Random random = new Random();
        int l = getVectorLength(vectors);

        // Initialize SC array with sentinel values
        for (int i = 0; i <= s; i++) {
            scs[i] = Double.MAX_VALUE;
        }

        // Step 1: Evaluate k=1 (single cluster)
        Partition P1 = new Partition(1);
        VectorSet cluster0 = P1.getElements(1);
        vectors.copyTo(cluster0);
        // Use L2-based MSE for k=1 (single cluster, no centroids yet, use
        // simple SC)
        double sc1 = DistanceCalculator.stochasticComplexity(P1, 1, l);
        scs[1] = sc1;
        if (sc1 < scmin[0]) {
            scmin[0] = sc1;
            logger.debug("k=1: SC = {:.4f} — best so far", sc1);
        }

        // Step 2: Find initial worst-matching pair
        BinaryVector[] xy = worstMatchingVectors(vectors, random);
        BinaryVector x = xy[0];
        BinaryVector y = xy[1];

        // Initialize centroids from the pair
        InfiniteCentroids C = new InfiniteCentroids(3, l); // k+1 = 3 for k=2
        setFirstCentroids(C, x, y);

        int kc = 0; // stagnation counter
        double scm = scmin[0]; // best SC seen so far (for relative improvement)

        // Track best partition found so far
        Partition bestP = P1.copy(); // Start with k=1 partition

        // Step 3: Iterative split-and-refine loop
        Partition P; // Initialize outside loop for later use
        for (int k = 2; k <= KSTOPWHEN && k < s; k++) {
            int newK = k + 1;
            P = new Partition(newK);

            // Refine with GLA from current centroids
            double[] dmin = new double[1];
            GLAEngine.gla(vectors, P, C, dmin, s);

            // Use actual centroid count and MSE-based SC for model selection
            int kActual = C.size();
            double mse = DistanceCalculator.overallMse(P, C);
            double sc = DistanceCalculator.stochasticComplexity(P, kActual, l,
                    mse);
            logger.debug("k={}: SC = {}, MSE = {}", kActual, sc, mse);

            if (kActual <= s && sc < scs[kActual]) {
                scs[kActual] = sc;
            }

            // Check for improvement over best
            if (sc < scmin[0]) {
                scmin[0] = sc;
                scm = sc;
                kc = 0;
                bestP = P.copy(); // Save copy of best partition found
                logger.debug("k={}: SC = {:.4f} — new best", kActual, sc);
            } else if (sc < scm) {
                scm = sc;
                kc = 0;
            } else {
                kc++;
            }

            // Stop if stagnating or max reached
            if (kc >= KC_STOP_WHEN || k + 1 >= s) {
                logger.debug("Stagnation detected at k={}, stopping", k);
                break;
            }

            // Find worst-matching class and split it
            int wp = pointWorstClass(P, C); // Returns 1-based index
            BinaryVector[] xyNew = absWorstMatchingVectors(
                    P.getCluster(wp - 1)); // Convert to 0-based for
                                           // getCluster()
            BinaryVector xNew = xyNew[0];
            BinaryVector yNew = xyNew[1];

            // Create new centroid array with k+2 centroids (split worst class)
            InfiniteCentroids Cn = new InfiniteCentroids(k + 2, l);
            setNewCentroids(Cn, C, xNew, yNew, wp - 1); // convert to 0-based
                                                        // index

            // Move vectors from old partition for next iteration
            VectorSet Vnext = new VectorSet();
            P.copyAllTo(Vnext);

            C = Cn;
        }

        return bestP; // Return the partition with the best SC found
    }

    /**
     * Finds the worst-matching vector pair using a random start + L2 search.
     * <p>
     * Equivalent to C function {@code worst_matching_vectors()} from
     * {@code splitgla.c}. Picks a random vector, then finds the most distant
     * vector from it (maximizing Hamming distance).
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to search in
     * @param random
     *            random number generator for initial selection
     * @return array [x, y] where x is the starting vector and y is its worst
     *         match
     */
    public static BinaryVector[] worstMatchingVectors(VectorSet vectors,
            Random random) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);

        int n = vectors.size();
        if (n < 2) {
            throw new IllegalArgumentException(
                    "Need at least 2 vectors for worst matching");
        }

        // Pick a random vector as starting point
        int ind = random.nextInt(n);
        BinaryVector[] allVectors = vectors.toArray(new BinaryVector[0]);
        BinaryVector x = allVectors[ind];

        // Find the most distant vector from x (max Hamming distance)
        double dm = -1;
        BinaryVector y = null;
        for (BinaryVector c : allVectors) {
            int d = DistanceCalculator.hammingDistanceVectors(x, c);
            if (d >= dm) {
                y = c;
                dm = d;
            }
        }

        return new BinaryVector[] { x, y };
    }

    /**
     * Finds the absolute worst-matching vector pair via brute-force O(n²)
     * search.
     * <p>
     * Equivalent to C function {@code abs_worst_matching_vectors()} from
     * {@code splitgla.c}. Exhaustively searches all pairs to find the one with
     * maximum Hamming distance.
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to search in
     * @return array [x, y] forming the worst-matching pair
     */
    public static BinaryVector[] absWorstMatchingVectors(VectorSet vectors) {
        Objects.requireNonNull(vectors, VECTOR_SET_MUST_NOT_BE_NULL);

        int n = vectors.size();
        if (n < 2) {
            throw new IllegalArgumentException(
                    "Need at least 2 vectors for worst matching");
        }

        BinaryVector[] allVectors = vectors.toArray(new BinaryVector[0]);
        double dm = -1;
        BinaryVector xBest = null;
        BinaryVector yBest = null;

        // O(n²) exhaustive search for maximum Hamming distance pair
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    int d = DistanceCalculator.hammingDistanceVectors(
                            allVectors[i], allVectors[j]);
                    if (d >= dm) {
                        xBest = allVectors[i];
                        yBest = allVectors[j];
                        dm = d;
                    }
                }
            }
        }

        return new BinaryVector[] { xBest, yBest };
    }

    /**
     * Sets the first two centroids from a pair of binary vectors.
     * <p>
     * Equivalent to C function {@code set_first_centroids()} from
     * {@code splitgla.c}. Initializes centroid 0 with vector x and centroid 1
     * with vector y, using their raw bit values as probability distributions.
     * </p>
     *
     * @param centroids
     *            the centroid array to initialize (must have at least 2
     *            centroids)
     * @param x
     *            first binary vector → centroid[0]
     * @param y
     *            second binary vector → centroid[1]
     */
    public static void setFirstCentroids(InfiniteCentroids centroids,
            BinaryVector x, BinaryVector y) {
        Objects.requireNonNull(centroids, "InfiniteCentroids must not be null");
        Objects.requireNonNull(x, "BinaryVector x must not be null");
        Objects.requireNonNull(y, "BinaryVector y must not be null");

        int l = x.getLength();
        Centroid c0 = centroids.get(0);
        Centroid c1 = centroids.get(1);

        // Set centroid values from raw bit arrays (0.0 or 1.0)
        for (int i = 0; i < l; i++) {
            c0.set(i, x.get(i));
            c1.set(i, y.get(i));
        }
    }

    /**
     * Finds the class with the worst distortion relative to its centroid.
     * <p>
     * Equivalent to C function {@code point_worst_class()} from
     * {@code splitgla.c}. Returns the 1-based index of the cluster whose
     * vectors have maximum average distance to their centroid
     * (class_distortion).
     * </p>
     *
     * @param partition
     *            the current partition with k clusters
     * @param centroids
     *            the corresponding centroid array
     * @return 1-based index of the worst-matching class
     */
    public static int pointWorstClass(Partition partition,
            InfiniteCentroids centroids) {
        Objects.requireNonNull(partition, "Partition must not be null");
        Objects.requireNonNull(centroids, "InfiniteCentroids must not be null");

        int k = partition.size();
        double dm = -1.0;
        int worst = 0; // 0-based index

        for (int i = 0; i < k; i++) {
            VectorSet cluster = partition.getCluster(i);
            Centroid centroid = centroids.get(i);
            double d = DistanceCalculator.classDistortion(cluster, centroid);
            if (d >= dm) {
                worst = i;
                dm = d;
            }
        }

        return worst + 1; // Convert to 1-based index for C compatibility
    }

    /**
     * Creates a new centroid array by splitting the worst class.
     * <p>
     * Equivalent to C function {@code set_new_centroids()} from
     * {@code splitgla.c}. Copies existing centroids, replaces the worst class
     * with one of the split vectors, and adds the other as a new cluster.
     * </p>
     *
     * @param cnew
     *            output centroid array (must have k+1 centroids)
     * @param c
     *            existing centroid array (k centroids)
     * @param x
     *            first split vector → replaces worst class centroid
     * @param y
     *            second split vector → becomes new cluster centroid
     * @param wp
     *            1-based index of the worst-matching class to split
     */
    public static void setNewCentroids(InfiniteCentroids cnew,
            InfiniteCentroids c, BinaryVector x,
            BinaryVector y, int wp) {
        Objects.requireNonNull(cnew, "cnew must not be null");
        Objects.requireNonNull(c, "c must not be null");
        Objects.requireNonNull(x, "x must not be null");
        Objects.requireNonNull(y, "y must not be null");

        int l = x.length();
        int kOld = c.size(); // number of old centroids (1-based)
        int wp0 = wp - 1; // convert to 0-based index

        // Copy existing centroids, replacing the worst class with x
        for (int i = 0; i < kOld; i++) {
            Centroid n = cnew.get(i);
            if (i == wp0) {
                // Replace worst class centroid with vector x
                for (int j = 0; j < l; j++) {
                    n.set(j, x.getElement(j));
                }
            } else {
                // Copy existing centroid from old array
                Centroid o = c.get(i);
                for (int j = 0; j < l; j++) {
                    n.set(j, o.getElement(j));
                }
            }
        }

        // Set the last centroid to vector y (new cluster)
        Centroid nLast = cnew.get(kOld);
        for (int j = 0; j < l; j++) {
            nLast.set(j, y.get(j));
        }
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
        return all[0].length();
    }
}
