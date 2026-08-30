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
import org.binclass.algorithms.centroid.CentroidManager;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.dist.DistanceCalculator;
import org.binclass.algorithms.dist.NearestNeighbor;

/**
 * Local search operators (Multi-Operator MOLS) used by the GLA heuristic
 * search.
 * <p>
 * Mirrors the six operators and the {@code local_search()} driver from
 * {@code glainf.c} in the original C codebase. Each operator mutates a
 * partition and its centroid array in place, then the driver evaluates the
 * resulting stochastic complexity and updates adaptive selection probabilities.
 * </p>
 * <p>
 * The operators follow the C indexing convention: clusters are addressed with
 * 1-based indices (1..k) while centroids are stored 0-based internally. Random
 * helpers mirror {@code random_index()} from {@code bottom.c}.
 * </p>
 */
@SuppressWarnings({ "java:S117", "java:S3457" })
public final class LocalSearch {

    /** Success probability acceleration factor (Eq. 14). */
    public static final double ALFA = 2.5;

    /** Initial weight forgetting rate (Eq. 16). */
    public static final double BETA_INIT = 0.2;

    private static final String VECTOR_SET_MUST_NOT_NULL = "VectorSet must not be null";

    private LocalSearch() {
        // Utility class — prevent instantiation
    }

    /**
     * Mirrors C function {@code random_index()} from {@code bottom.c}. Returns
     * a value in the inclusive range {@code [1, m]} using the same clamping
     * rules as the original code.
     *
     * @param m
     *            the upper bound of the index space (must be positive)
     * @param random
     *            the random number generator
     * @return a 1-based random index in {@code [1, m]}
     */
    static int randomIndex(int m, Random random) {
        Objects.requireNonNull(random, "Random must not be null");
        if (m <= 0) {
            return 1;
        }
        int ind = (int) (random.nextDouble() * m);
        if (ind <= 1) {
            ind = 1;
        }
        if (ind >= m) {
            ind = m;
        }
        return ind;
    }

    /**
     * Returns the {@code ind}-th vector (1-based) from a set, mirroring C
     * {@code get_vector_i()} from {@code binset.c}.
     *
     * @param set
     *            the source set
     * @param ind
     *            the 1-based index
     * @return the vector at that position, or {@code null} if out of bounds
     */
    static BinaryVector getVectorAt(VectorSet set, int ind) {
        Objects.requireNonNull(set, VECTOR_SET_MUST_NOT_NULL);
        BinaryVector[] all = set.toArray(new BinaryVector[0]);
        if (ind < 1 || ind > all.length) {
            return null;
        }
        return all[ind - 1];
    }

    /**
     * Picks a random vector from a set and finds its worst match (maximizing
     * Hamming distance), mirroring C {@code worst_matching_vectors()} from
     * {@code splitgla.c}.
     *
     * @param cluster
     *            the cluster to search within
     * @param random
     *            the random number generator
     * @return a two-element array {@code [x, y]} where {@code x} is a random
     *         vector and {@code y} is its worst match
     */
    static BinaryVector[] worstMatchingVectors(VectorSet cluster,
            Random random) {
        Objects.requireNonNull(cluster, VECTOR_SET_MUST_NOT_NULL);
        int n = cluster.size();
        if (n < 2) {
            throw new IllegalArgumentException(
                    "Need at least 2 vectors for worst matching");
        }
        BinaryVector[] all = cluster.toArray(new BinaryVector[0]);
        int ind = randomIndex(n, random);
        if (ind > n - 1) {
            ind = n - 1;
        }
        BinaryVector x = all[ind - 1];
        double dm = -1.0;
        BinaryVector y = null;
        for (BinaryVector c : all) {
            int d = DistanceCalculator.hammingDistanceVectors(x, c);
            if (d >= dm) {
                y = c;
                dm = d;
            }
        }
        return new BinaryVector[] { x, y };
    }

    /**
     * Computes the squared Euclidean distance between two centroids. Equivalent
     * to C {@code edistance_2()} from {@code distmin.h}.
     *
     * @param a
     *            first centroid
     * @param b
     *            second centroid
     * @return squared Euclidean distance
     */
    private static double edistance(Centroid a, Centroid b) {
        return JoinGLA.edistance2(a, b, a.getLength());
    }

    /**
     * Recomputes a single centroid from the vectors assigned to it. Equivalent
     * to C {@code inf_average()} from {@code glainf.c}.
     *
     * @param cluster
     *            the vectors defining the new centroid
     * @param centroid
     *            the centroid to update in place
     * @param rounded
     *            whether to round values to 0/1
     * @param n
     *            total number of vectors (for weight calculation)
     */
    static void infAverage(VectorSet cluster, Centroid centroid,
            boolean rounded, int n) {
        Objects.requireNonNull(cluster, VECTOR_SET_MUST_NOT_NULL);
        Objects.requireNonNull(centroid, "Centroid must not be null");

        int l = centroid.getLength();
        double[] el = centroid.getArray();
        int size = cluster.size();
        if (size == 0) {
            return;
        }
        for (int i = 0; i < l; i++) {
            int count1 = 0;
            for (BinaryVector bv : cluster) {
                if (!bv.isMissing(i)) {
                    count1 += bv.getElement(i);
                }
            }
            double avg = (double) count1 / size;
            el[i] = rounded ? (avg >= 0.5 ? 1.0 : 0.0) : avg;
        }
        centroid.setWeight((double) size / n);
    }

    /**
     * Moves a class's vectors out, reassigns them by MSE nearest neighbour,
     * removes empty clusters, and recomputes centroids. Equivalent to C
     * {@code local_repartition_mse()} from {@code distmin.c}.
     *
     * @param c
     *            the 1-based class whose vectors are moved out first
     * @param partition
     *            the partition being repartitioned
     * @param centroids
     *            the centroid array updated in place
     * @param random
     *            unused, kept for API symmetry with C helpers
     */
    private static void localRepartitionMse(int c, Partition partition,
            InfiniteCentroids centroids) {
        Objects.requireNonNull(partition, "Partition must not be null");
        Objects.requireNonNull(centroids, "InfiniteCentroids must not be null");

        // Capture the moved vectors before detaching cluster c so that the
        // MSE nearest-neighbour pass reassigns every one of them. Mirrors C
        // {@code V = P->el[c]; P->el[c] = NULL; MSE_nearest_neighbour(V,P,C);}.
        VectorSet moved = partition.getElements(c);
        BinaryVector[] toMove = moved.toArray(new BinaryVector[0]);
        for (BinaryVector bv : toMove) {
            partition.removeElement(c, bv);
        }

        // Reassign the moved vectors through a fresh set so that detaching them
        // from cluster c does not empty the source before reassignment. Mirrors
        // C {@code V = P->el[c]; P->el[c] = NULL;
        // MSE_nearest_neighbour(V,P,C);}.
        VectorSet reassigned = new VectorSet();
        for (BinaryVector bv : toMove) {
            reassigned.add(bv);
        }

        int k = partition.size();
        NearestNeighbor.mseNearestNeighbor(reassigned, partition, centroids);
        GLAEngine.removeEmpty(partition, centroids);
        int newK = Math.min(k, partition.size());
        int s = 0;
        for (int i = 1; i <= newK; i++) {
            s += partition.getSize(i);
        }
        for (int i = 1; i <= newK; i++) {
            infAverage(partition.getElements(i), centroids.get(i - 1), true, s);
        }
    }

    /**
     * Joins two clusters and shifts the last cluster down. Equivalent to the
     * join block in C {@code split_and_join()} from {@code glainf.c}.
     *
     * @param partition
     *            the partition holding the clusters (1-based indices)
     * @param imin
     *            1-based index of the destination cluster
     * @param jmin
     *            1-based index of the source cluster to merge in
     * @param k
     *            current number of clusters (1-based)
     */
    private static void joinClusters(Partition partition, int imin, int jmin,
            int k) {
        Objects.requireNonNull(partition, "Partition must not be null");

        VectorSet dest = partition.getElements(imin);
        VectorSet src = partition.getElements(jmin);
        BinaryVector[] toJoin = src.toArray(new BinaryVector[0]);
        for (BinaryVector bv : toJoin) {
            dest.addElement(bv);
        }
        VectorSet last = partition.getElements(k);
        BinaryVector[] toMove = last.toArray(new BinaryVector[0]);
        for (BinaryVector bv : toMove) {
            src.addElement(bv);
        }
        last.clear();
    }

    /**
     * Split and join operator (SJ1). Picks a random class, joins it with the
     * closest centroid, then splits the most incoherent class. Equivalent to C
     * {@code split_and_join()} from {@code glainf.c}.
     *
     * @param k
     *            number of clusters (1-based)
     * @param l
     *            length of binary vectors
     * @param n
     *            total number of vectors
     * @param centroids
     *            the centroid array updated in place
     * @param partition
     *            the partition updated in place
     * @param random
     *            the random number generator
     */
    public static void splitAndJoin(int k, int l, int n,
            InfiniteCentroids centroids,
            Partition partition, Random random) {
        Objects.requireNonNull(centroids, "InfiniteCentroids must not be null");
        Objects.requireNonNull(partition, "Partition must not be null");

        double[] distortion = new double[k];
        double dmin = l + 1.0;
        int imin = randomIndex(k - 1, random);
        for (int i = 1; i < k; i++) {
            distortion[i] = DistanceCalculator.classDistortion(
                    partition.getElements(i), centroids.get(i - 1));
        }
        int jmin = 1;
        for (int j = 1; j < k; j++) {
            if (j != imin) {
                double d = edistance(centroids.get(imin - 1),
                        centroids.get(j - 1));
                if (d < dmin) {
                    dmin = d;
                    jmin = j;
                }
            }
        }
        joinClusters(partition, imin, jmin, k);
        int targetIdx = (imin != k - 1) ? imin : jmin;
        infAverage(partition.getElements(targetIdx),
                centroids.get(targetIdx - 1), true, n);

        double max = 0.0;
        int imax = 1;
        for (int i = 1; i < k - 1; i++) {
            if (i != imin && distortion[i] > max) {
                max = distortion[i];
                imax = i;
            }
        }
        BinaryVector[] pair = worstMatchingVectors(partition.getElements(imax),
                random);
        double[] lastEl = centroids.get(k - 1).getArray();
        double[] imaxEl = centroids.get(imax - 1).getArray();
        for (int i = 0; i < l; i++) {
            lastEl[i] = 1.0 + pair[0].getElement(i) / 3.0;
            imaxEl[i] = 1.0 + pair[1].getElement(i) / 3.0;
        }
    }

    /**
     * Split and join variant 2 (SJ2). Joins the smallest class with its closest
     * neighbour, then splits the most incoherent class. Equivalent to C
     * {@code split_and_join2()} from {@code glainf.c}.
     *
     * @param k
     *            number of clusters (1-based)
     * @param l
     *            length of binary vectors
     * @param n
     *            total number of vectors
     * @param centroids
     *            the centroid array updated in place
     * @param partition
     *            the partition updated in place
     * @param random
     *            the random number generator
     */
    public static void splitAndJoin2(int k, int l, int n,
            InfiniteCentroids centroids,
            Partition partition, Random random) {
        Objects.requireNonNull(centroids, "InfiniteCentroids must not be null");
        Objects.requireNonNull(partition, "Partition must not be null");

        int[] size = new int[k];
        double[] distortion = new double[k];
        for (int i = 1; i < k; i++) {
            size[i] = partition.getSize(i);
            distortion[i] = DistanceCalculator.classDistortion(
                    partition.getElements(i), centroids.get(i - 1));
        }
        int smin = n + 1;
        int imin = 1;
        for (int j = 1; j < k; j++) {
            if (size[j] < smin) {
                smin = size[j];
                imin = j;
            }
        }
        double dmin = l + 1.0;
        int jmin = 1;
        for (int j = 1; j < k; j++) {
            if (j != imin) {
                double d = edistance(centroids.get(imin - 1),
                        centroids.get(j - 1));
                if (d < dmin) {
                    dmin = d;
                    jmin = j;
                }
            }
        }
        joinClusters(partition, imin, jmin, k);
        int targetIdx = (imin != k - 1) ? imin : jmin;
        infAverage(partition.getElements(targetIdx),
                centroids.get(targetIdx - 1), true, n);

        double max = 0.0;
        int imax = 1;
        for (int i = 1; i < k - 1; i++) {
            if (i != imin && distortion[i] > max) {
                max = distortion[i];
                imax = i;
            }
        }
        BinaryVector[] pair = worstMatchingVectors(partition.getElements(imax),
                random);
        double[] lastEl = centroids.get(k - 1).getArray();
        double[] imaxEl = centroids.get(imax - 1).getArray();
        for (int i = 0; i < l; i++) {
            lastEl[i] = 1.0 + pair[0].getElement(i) / 3.0;
            imaxEl[i] = 1.0 + pair[1].getElement(i) / 3.0;
        }
    }

    /**
     * Replace worst class operator (RWO). Sets the centroid of the most
     * incoherent class to a random vector drawn from any class, then
     * repartitions. Equivalent to C {@code replace_worst()} from
     * {@code glainf.c}.
     *
     * @param k
     *            number of clusters (1-based)
     * @param l
     *            length of binary vectors
     * @param n
     *            total number of vectors
     * @param centroids
     *            the centroid array updated in place
     * @param partition
     *            the partition updated in place
     * @param random
     *            the random number generator
     */
    public static void replaceWorst(int k, int l, int n,
            InfiniteCentroids centroids,
            Partition partition, Random random) {
        Objects.requireNonNull(centroids, "InfiniteCentroids must not be null");
        Objects.requireNonNull(partition, "Partition must not be null");

        double[] distortion = new double[k];
        for (int i = 1; i < k; i++) {
            distortion[i] = DistanceCalculator.classDistortion(
                    partition.getElements(i), centroids.get(i - 1));
        }
        int imax = 1;
        double max = 0.0;
        for (int i = 1; i < k; i++) {
            if (distortion[i] > max) {
                max = distortion[i];
                imax = i;
            }
        }
        int c = randomIndex(k - 1, random);
        VectorSet cs = partition.getElements(c);
        BinaryVector x = getVectorAt(cs, randomIndex(cs.size(), random));
        if (x == null) {
            return;
        }
        double[] el = centroids.get(imax - 1).getArray();
        for (int i = 0; i < l; i++) {
            el[i] = 1.0 + x.getElement(i) / 3.0;
        }
        localRepartitionMse(imax, partition, centroids);
    }

    /**
     * Replace smallest class operator (RSA). Sets the centroid of the largest
     * class to a random vector drawn from any class, then repartitions.
     * Equivalent to C {@code replace_smallest()} from {@code glainf.c}.
     *
     * @param k
     *            number of clusters (1-based)
     * @param l
     *            length of binary vectors
     * @param n
     *            total number of vectors
     * @param centroids
     *            the centroid array updated in place
     * @param partition
     *            the partition updated in place
     * @param random
     *            the random number generator
     */
    public static void replaceSmallest(int k, int l, int n,
            InfiniteCentroids centroids,
            Partition partition, Random random) {
        Objects.requireNonNull(centroids, "InfiniteCentroids must not be null");
        Objects.requireNonNull(partition, "Partition must not be null");

        int[] size = new int[k];
        for (int i = 1; i < k; i++) {
            size[i] = partition.getSize(i);
        }
        int imax = 1;
        int max = 0;
        for (int i = 1; i < k; i++) {
            if (size[i] > max) {
                max = size[i];
                imax = i;
            }
        }
        int c = randomIndex(k - 1, random);
        VectorSet cs = partition.getElements(c);
        BinaryVector x = getVectorAt(cs, randomIndex(cs.size(), random));
        if (x == null) {
            return;
        }
        double[] el = centroids.get(imax - 1).getArray();
        for (int i = 0; i < l; i++) {
            el[i] = 1.0 + x.getElement(i) / 3.0;
        }
        localRepartitionMse(imax, partition, centroids);
    }

    /**
     * Random swap operator (RS1). Sets the centroid of a random class to a
     * random vector drawn from another class, then repartitions. Equivalent to
     * C {@code random_swap()} from {@code glainf.c}.
     *
     * @param k
     *            number of clusters (1-based)
     * @param l
     *            length of binary vectors
     * @param n
     *            total number of vectors
     * @param centroids
     *            the centroid array updated in place
     * @param partition
     *            the partition updated in place
     * @param random
     *            the random number generator
     */
    public static void randomSwap(int k, int l, int n,
            InfiniteCentroids centroids,
            Partition partition, Random random) {
        Objects.requireNonNull(centroids, "InfiniteCentroids must not be null");
        Objects.requireNonNull(partition, "Partition must not be null");

        int c1 = randomIndex(k - 1, random);
        int c2 = randomIndex(k - 1, random);
        VectorSet cs = partition.getElements(c2);
        BinaryVector x = getVectorAt(cs, randomIndex(cs.size(), random));
        if (x == null) {
            return;
        }
        double[] el = centroids.get(c1 - 1).getArray();
        for (int i = 0; i < l; i++) {
            el[i] = 1.0 + x.getElement(i) / 3.0;
        }
        localRepartitionMse(c1, partition, centroids);
    }

    /**
     * Random swap variant 2 (RS2). Sets the centroid of a random class to a
     * random vector drawn from that same class, then repartitions. Equivalent
     * to C {@code random_swap2()} from {@code glainf.c}.
     *
     * @param k
     *            number of clusters (1-based)
     * @param l
     *            length of binary vectors
     * @param n
     *            total number of vectors
     * @param centroids
     *            the centroid array updated in place
     * @param partition
     *            the partition updated in place
     * @param random
     *            the random number generator
     */
    public static void randomSwap2(int k, int l, int n,
            InfiniteCentroids centroids,
            Partition partition, Random random) {
        Objects.requireNonNull(centroids, "InfiniteCentroids must not be null");
        Objects.requireNonNull(partition, "Partition must not be null");

        int c1 = randomIndex(k - 1, random);
        VectorSet cs = partition.getElements(c1);
        BinaryVector x = getVectorAt(cs, randomIndex(cs.size(), random));
        if (x == null) {
            return;
        }
        double[] el = centroids.get(c1 - 1).getArray();
        for (int i = 0; i < l; i++) {
            el[i] = 1.0 + x.getElement(i) / 3.0;
        }
        localRepartitionMse(c1, partition, centroids);
    }

    /**
     * Maps an operator to its success-counter index used by the adaptive
     * probability bookkeeping (Eq. 14). Mirrors the C {@code suc[]} dispatch in
     * {@code local_search()}.
     *
     * @param op
     *            the operator whose index is requested
     * @return the success-counter index (0..5)
     */
    static int successIndex(LocalSearchOperator op) {
        return switch (op) {
        case SPLITJOIN1 -> 0;
        case REPLACEWORST -> 1;
        case REPLACESMALLEST -> 2;
        case RANDOMSWAP -> 3;
        case SPLITJOIN2 -> 4;
        case RANDOMSWAP2 -> 5;
        case NONE -> -1;
        };
    }

    /**
     * Runs the local search driver. Equivalent to C {@code local_search()} from
     * {@code glainf.c}. Applies a sequence of operators, tracks successes and
     * adaptive selection probabilities, keeps the best partition found, then
     * performs a final refinement pass.
     *
     * @param partition
     *            the partition to refine (updated in place)
     * @param centroids
     *            the centroid array updated in place
     * @param count
     *            number of operator applications ({@code ls_heuristic_count} -
     *            1)
     * @param l
     *            length of binary vectors
     * @param n
     *            total number of vectors
     * @param jeffreysPrior
     *            whether to use the Jeffreys prior for stochastic complexity
     * @param random
     *            the random number generator
     * @return the total GLA cost accumulated across all passes
     */
    public static int localSearch(Partition partition,
            InfiniteCentroids centroids,
            int count, int l, int n, boolean jeffreysPrior, Random random) {
        Objects.requireNonNull(partition, "Partition must not be null");
        Objects.requireNonNull(centroids, "InfiniteCentroids must not be null");

        int[] suc = new int[6];
        double[] w = new double[6];
        double[] p = new double[6];
        for (int i = 0; i < 6; i++) {
            p[i] = 1.0 / 6.0;
        }
        double alfa = ALFA;
        double beta = BETA_INIT;

        InfiniteCentroids cmin = CentroidManager.copyCentroids(centroids);

        LocalSearchOperator[] operators = {
                LocalSearchOperator.SPLITJOIN1,
                LocalSearchOperator.REPLACEWORST,
                LocalSearchOperator.REPLACESMALLEST,
                LocalSearchOperator.RANDOMSWAP,
                LocalSearchOperator.RANDOMSWAP2,
                LocalSearchOperator.SPLITJOIN2,
        };

        double sc = DistanceCalculator.stochasticComplexity(partition,
                centroids.size(), l, jeffreysPrior);
        int gt = 0;

        for (int i = 1; i < count; i++) {
            LocalSearchOperator op = operators[i % 6];
            applyOperator(op, partition.size(), l, n, centroids, partition,
                    random);

            VectorSet v = GLAEngine.partitionToSet(partition);
            gt += mseGla2(v, partition, centroids, n);

            double scn = DistanceCalculator.stochasticComplexity(partition,
                    centroids.size(), l, jeffreysPrior);

            for (int j = 0; j < 5; j++) {
                if ((w[j] - beta) >= 0.0) {
                    w[j] -= beta;
                }
            }
            if ((i % 10) == 0 && i < 1001) {
                beta /= 1.25;
            }

            if (scn < sc) {
                // copy_centroids(cmin, C): keep best centroids in cmin
                CentroidManager.copyCentroids(centroids, cmin);
                sc = scn;
                int idx = successIndex(op);
                if (idx >= 0) {
                    suc[idx]++;
                    w[idx] += 1.0;
                    double sumW = 0.0;
                    for (int j = 0; j < 5; j++) {
                        sumW += w[j];
                    }
                    for (int j = 0; j < 5; j++) {
                        p[j] = (w[j] + alfa) / (sumW + 6.0 * alfa);
                    }
                }
            } else {
                // copy_centroids(C, cmin): restore best centroids from cmin
                CentroidManager.copyCentroids(cmin, centroids);
            }
        }

        CentroidManager.copyCentroids(cmin, centroids);
        VectorSet v = GLAEngine.partitionToSet(partition);
        gt += mseGla2(v, partition, centroids, n);
        double scn = DistanceCalculator.stochasticComplexity(partition,
                centroids.size(), l, jeffreysPrior);
        if (scn > sc) {
            CentroidManager.copyCentroids(cmin, centroids);
        }

        gt += 2;
        return gt;
    }

    /**
     * Updates adaptive selection probabilities after a successful operator,
     * mirroring Eq. 14 of {@code glainf.c}. Weights are accelerated by
     * {@link #ALFA} and normalised so the first five probabilities sum to one.
     *
     * @param w
     *            the per-operator weight array (length at least 5)
     * @param p
     *            the per-operator probability array, updated in place
     */
    static void updateProbabilities(double[] w, double[] p) {
        double sumW = 0.0;
        for (int j = 0; j < 5; j++) {
            sumW += w[j];
        }
        for (int j = 0; j < 5; j++) {
            p[j] = (w[j] + ALFA) / (sumW + 6.0 * ALFA);
        }
    }

    /**
     * Applies a single operator to the partition and centroids.
     *
     * @param op
     *            the operator to apply
     * @param k
     *            number of clusters (1-based)
     * @param l
     *            length of binary vectors
     * @param n
     *            total number of vectors
     * @param centroids
     *            the centroid array updated in place
     * @param partition
     *            the partition updated in place
     * @param random
     *            the random number generator
     */
    private static void applyOperator(LocalSearchOperator op, int k, int l,
            int n,
            InfiniteCentroids centroids, Partition partition, Random random) {
        switch (op) {
        case SPLITJOIN1 -> LocalSearch.splitAndJoin(k, l, n, centroids,
                partition, random);
        case SPLITJOIN2 -> LocalSearch.splitAndJoin2(k, l, n, centroids,
                partition, random);
        case REPLACEWORST -> LocalSearch.replaceWorst(k, l, n, centroids,
                partition, random);
        case REPLACESMALLEST -> LocalSearch.replaceSmallest(k, l, n,
                centroids, partition, random);
        case RANDOMSWAP -> LocalSearch.randomSwap(k, l, n, centroids,
                partition, random);
        case RANDOMSWAP2 -> LocalSearch.randomSwap2(k, l, n, centroids,
                partition, random);
        case NONE -> {
        }
        }
    }

    /**
     * Two-round MSE GLA refinement used by the driver. Equivalent to C
     * {@code MSE_gla2()} from {@code glainf.c}.
     *
     * @param vectors
     *            all vectors to redistribute
     * @param partition
     *            the partition updated in place
     * @param centroids
     *            the centroid array updated in place
     * @param n
     *            total number of vectors
     * @return a fixed cost constant (2), matching the C return value
     */
    private static int mseGla2(VectorSet vectors, Partition partition,
            InfiniteCentroids centroids, int n) {
        clearPartition(partition);
        NearestNeighbor.mseNearestNeighbor(vectors, partition, centroids);
        GLAEngine.removeEmpty(partition, centroids);
        GLAEngine.recomputeCentroids(partition, centroids, true, n);
        VectorSet v = GLAEngine.partitionToSet(partition);
        clearPartition(partition);
        NearestNeighbor.mseNearestNeighbor(v, partition, centroids);
        GLAEngine.removeEmpty(partition, centroids);
        return 2;
    }

    /** Empties every cluster so a full reassignment does not accumulate. */
    private static void clearPartition(Partition partition) {
        for (int i = 1; i <= partition.size(); i++) {
            partition.getElements(i).clear();
        }
    }
}
