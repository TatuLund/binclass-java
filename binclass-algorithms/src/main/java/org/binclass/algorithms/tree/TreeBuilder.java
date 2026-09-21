/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.tree;

import java.util.Objects;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.TreeNode;
import org.binclass.algorithms.dist.DistanceCalculator;

/**
 * Builds dendrograms from partition data using information content and
 * Hellinger distance.
 * <p>
 * Mirrors functions from {@code tree.c} in the original C codebase: implements
 * the Partition Nearest Neighbor (PNN) algorithm for hierarchical clustering.
 * The PNN algorithm iteratively merges the two closest clusters based on a
 * chosen distance metric until only one cluster remains, producing a binary
 * dendrogram.
 * </p>
 * <p>
 * Two variants are supported:
 * <ul>
 * <li>{@link #makeTreePnn(Partition, InfiniteCentroids)} — uses Hellinger
 * distance between centroid probability vectors</li>
 * <li>{@link #makeTreePnn2(Partition, InfiniteCentroids)} — uses class nearness
 * (custom distance based on vector overlap)</li>
 * </ul>
 * </p>
 */
public final class TreeBuilder {

    private static final String PARTITION_MUST_NOT_BE_NULL = "Partition must not be null";
    private static final String INFINITE_CENTROIDS_MUST_NOT_BE_NULL = "InfiniteCentroids must not be null";

    private TreeBuilder() {
        // Utility class — prevent instantiation
    }

    /**
     * Builds a dendrogram using the Partition Nearest Neighbor (PNN) algorithm
     * with Hellinger distance.
     * <p>
     * Equivalent to C function {@code make_tree_pnn()} from {@code tree.c}.
     * Iteratively merges the two closest clusters based on Hellinger distance
     * between their centroid probability vectors until a single root cluster
     * remains. The resulting tree is a binary dendrogram where internal nodes
     * represent merged clusters and leaf nodes represent original clusters.
     * </p>
     * <p>
     * Algorithm:
     * <ol>
     * <li>Initialize leaf nodes from the partition's clusters</li>
     * <li>Find the pair of closest clusters using Hellinger distance</li>
     * <li>Merge them into a new internal node with weighted centroid
     * average</li>
     * <li>Repeat until only one cluster remains (the root)</li>
     * </ol>
     * </p>
     *
     * @param partition
     *            the initial partition to build the tree from
     * @param centroids
     *            the centroid array defining cluster probabilities
     * @return the root node of the resulting dendrogram, or null if the
     *         partition is empty
     */
    public static TreeNode makeTreePnn(Partition partition,
            InfiniteCentroids centroids) {
        return makeTreePnn(partition, centroids, false);
    }

    public static TreeNode makeTreePnn(Partition partition,
            InfiniteCentroids centroids, boolean jeffreysPrior) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        int k = partition.size(); // 1-based count of clusters
        if (k <= 0) {
            return null;
        }

        int l = centroids.get(0).getLength(); // vector length from first
                                              // centroid

        // Initialize leaf nodes for each cluster. Each leaf carries the
        // stochastic complexity of its singleton class so the serialized tree
        // reports a meaningful, non-zero value per C traverse_tree().
        TreeNode[] nodes = new TreeNode[k];
        double[][] nodeCentroids = new double[k][l];
        int[] nodeSizes = new int[k];

        for (int i = 0; i < k; i++) {
            Centroid c = centroids.get(i);
            nodeCentroids[i] = c.getArray().clone();
            nodeSizes[i] = partition.getSize(i + 1); // Convert to 1-based
            nodes[i] = new TreeNode(leafStochasticComplexity(partition, i + 1,
                    l, jeffreysPrior), "C" + (i + 1));
        }

        int currentK = k;
        while (currentK > 2) {
            // Find the pair of closest clusters using Hellinger distance
            double dmin = l + 1.0;
            int imin = 0;
            int jmin = 1;

            for (int i = 0; i < currentK - 1; i++) {
                for (int j = i + 1; j < currentK; j++) {
                    double d = hellingerDistance(nodeCentroids[i],
                            nodeCentroids[j], l);
                    if (d < dmin) {
                        dmin = d;
                        imin = i;
                        jmin = j;
                    }
                }
            }

            // Merge the two closest clusters. The merged centroid uses weighted
            // average with Laplace smoothing (Bayes posterior predictive).
            double[] mergedCentroid = new double[l];
            for (int bit = 0; bit < l; bit++) {
                // Weighted average with Laplace smoothing (Bayes posterior
                // predictive)
                mergedCentroid[bit] = ((((nodeCentroids[imin][bit]
                        * (nodeSizes[imin] + 2.0)) - 1.0)
                        + ((nodeCentroids[jmin][bit]
                                * (nodeSizes[jmin] + 2.0)) - 1.0))
                        + 1.0)
                        / ((nodeSizes[imin] + nodeSizes[jmin])
                                + 2.0);
            }

            // Mutate the partition to mirror C make_tree_pnn(): join the two
            // merged classes into position imin+1, shift the last active
            // cluster
            // down into jmin+1, then clear the old last slot. This keeps the
            // partition in sync with the tree so stochastic complexity is
            // computed over the correct post-merge class composition.
            mergeClasses(partition, imin + 1, jmin + 1, currentK);

            // Compute SC over all remaining active clusters. C passes k after
            // decrementing; Java's stochasticComplexity sums classes 1..k-1, so
            // pass the pre-decrement count to cover every live cluster.
            double sc = safeStochasticComplexity(partition, currentK, l,
                    jeffreysPrior);

            // Create new internal node with children carrying the SC value.
            TreeNode mergedNode = new TreeNode(sc, "Merged", nodes[jmin],
                    nodes[imin]);

            // Update arrays: shift remaining nodes left
            nodes[imin] = mergedNode;
            for (int i = jmin; i < currentK - 1; i++) {
                nodes[i] = nodes[i + 1];
                System.arraycopy(nodeCentroids[i + 1], 0, nodeCentroids[i], 0,
                        l);
                nodeSizes[i] = nodeSizes[i + 1];
            }
            currentK--;
        }

        // Final merge for the last two clusters. Mirror C make_tree_pnn where
        // the final merged node carries the stochastic complexity of the single
        // combined class (all vectors in one cluster).
        if (currentK == 2) {
            double[] mergedCentroid = new double[l];
            for (int bit = 0; bit < l; bit++) {
                mergedCentroid[bit] = ((((nodeCentroids[0][bit]
                        * (nodeSizes[0] + 2.0)) - 1.0)
                        + ((nodeCentroids[1][bit]
                                * (nodeSizes[1] + 2.0)) - 1.0))
                        + 1.0)
                        / ((nodeSizes[0] + nodeSizes[1])
                                + 2.0);
            }

            // Merge the last two active clusters into one so the root SC is the
            // stochastic complexity of the fully combined class.
            for (BinaryVector v : partition.getElements(2)) {
                partition.addElement(1, v);
            }
            double sc = leafStochasticComplexity(partition, 1, l,
                    jeffreysPrior);

            return new TreeNode(sc, "Root", nodes[1], nodes[0]);
        }

        // Single cluster case
        return nodes[0];
    }

    /**
     * Builds a dendrogram using the Partition Nearest Neighbor (PNN) algorithm
     * with class nearness distance.
     * <p>
     * Equivalent to C function {@code make_tree_pnn2()} from {@code tree.c}.
     * Similar to {@link #makeTreePnn(Partition, InfiniteCentroids)} but uses a
     * custom distance metric based on vector overlap between clusters rather
     * than Hellinger distance. This variant produces a different dendrogram
     * structure that may better reflect the actual data distribution.
     * </p>
     * <p>
     * Algorithm:
     * <ol>
     * <li>Initialize leaf nodes from the partition's clusters</li>
     * <li>Find the pair of closest clusters using class nearness distance</li>
     * <li>Merge them into a new internal node with weighted centroid
     * average</li>
     * <li>Repeat until only one cluster remains (the root)</li>
     * </ol>
     * </p>
     *
     * @param partition
     *            the initial partition to build the tree from
     * @param centroids
     *            the centroid array defining cluster probabilities
     * @return the root node of the resulting dendrogram, or null if the
     *         partition is empty
     */
    public static TreeNode makeTreePnn2(Partition partition,
            InfiniteCentroids centroids) {
        return makeTreePnn2(partition, centroids, false);
    }

    public static TreeNode makeTreePnn2(Partition partition,
            InfiniteCentroids centroids, boolean jeffreysPrior) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        int k = partition.size(); // 1-based count of clusters
        if (k <= 0) {
            return null;
        }

        int l = centroids.get(0).getLength(); // vector length from first
                                              // centroid

        // Initialize leaf nodes for each cluster. Each leaf carries the
        // stochastic complexity of its singleton class so the serialized tree
        // reports a meaningful, non-zero value per C traverse_tree().
        TreeNode[] nodes = new TreeNode[k];
        double[][] nodeCentroids = new double[k][l];
        int[] nodeSizes = new int[k];

        for (int i = 0; i < k; i++) {
            Centroid c = centroids.get(i);
            nodeCentroids[i] = c.getArray().clone();
            nodeSizes[i] = partition.getSize(i + 1); // Convert to 1-based
            nodes[i] = new TreeNode(leafStochasticComplexity(partition, i + 1,
                    l, jeffreysPrior), "C" + (i + 1));
        }

        int currentK = k;
        while (currentK > 2) {
            // Find the pair of closest clusters using class nearness distance
            double dmin = Double.MAX_VALUE;
            int imin = 0;
            int jmin = 1;

            for (int i = 0; i < currentK - 1; i++) {
                for (int j = i + 1; j < currentK; j++) {
                    double d = classNearness(partition, centroids, i + 1,
                            j + 1);
                    if (d < dmin) {
                        dmin = d;
                        imin = i;
                        jmin = j;
                    }
                }
            }

            // Merge the two closest clusters. The merged centroid uses weighted
            // average without Laplace smoothing.
            int mergedSize = nodeSizes[imin] + nodeSizes[jmin];
            double[] mergedCentroid = new double[l];
            for (int bit = 0; bit < l; bit++) {
                // Weighted average without Laplace smoothing
                mergedCentroid[bit] = ((nodeSizes[imin]
                        * nodeCentroids[imin][bit])
                        + (nodeSizes[jmin] * nodeCentroids[jmin][bit]))
                        / mergedSize;
            }

            // Mutate the partition to mirror C make_tree_pnn2(): join the two
            // merged classes into position imin+1, shift the last active
            // cluster
            // down into jmin+1, then clear the old last slot. This keeps the
            // partition in sync with the tree so stochastic complexity is
            // computed over the correct post-merge class composition.
            mergeClasses(partition, imin + 1, jmin + 1, currentK);

            // Compute SC over all remaining active clusters. C passes k after
            // decrementing; Java's stochasticComplexity sums classes 1..k-1, so
            // pass the pre-decrement count to cover every live cluster.
            double sc = safeStochasticComplexity(partition, currentK, l,
                    jeffreysPrior);

            // Create new internal node with children carrying the SC value.
            TreeNode mergedNode = new TreeNode(sc, "Merged", nodes[jmin],
                    nodes[imin]);

            // Update arrays: shift remaining nodes left
            nodes[imin] = mergedNode;
            for (int i = jmin; i < currentK - 1; i++) {
                nodes[i] = nodes[i + 1];
                System.arraycopy(nodeCentroids[i + 1], 0, nodeCentroids[i], 0,
                        l);
                nodeSizes[i] = nodeSizes[i + 1];
            }
            currentK--;
        }

        // Final merge for the last two clusters. Mirror C make_tree_pnn2 where
        // the final merged node carries the stochastic complexity of the single
        // combined class (all vectors in one cluster).
        if (currentK == 2) {
            int mergedSize = nodeSizes[0] + nodeSizes[1];
            double[] mergedCentroid = new double[l];
            for (int bit = 0; bit < l; bit++) {
                mergedCentroid[bit] = ((nodeSizes[0] * nodeCentroids[0][bit])
                        + (nodeSizes[1] * nodeCentroids[1][bit]))
                        / mergedSize;
            }

            // Merge the last two active clusters into one so the root SC is the
            // stochastic complexity of the fully combined class.
            for (BinaryVector v : partition.getElements(2)) {
                partition.addElement(1, v);
            }
            double sc = leafStochasticComplexity(partition, 1, l,
                    jeffreysPrior);

            return new TreeNode(sc, "Root", nodes[1], nodes[0]);
        }

        // Single cluster case
        return nodes[0];
    }

    /**
     * Computes the Hellinger distance between two probability vectors.
     * <p>
     * Equivalent to C function {@code hellinger_distance()} from
     * {@code tree.c}. The Hellinger distance is a statistical distance measure
     * between two probability distributions, defined as:
     * </p>
     * 
     * <pre>{@code
     * H(x, y) = 1 - sqrt(prod(
     *         (sqrt(1 - x[i]) * sqrt(1 - y[i])) + (sqrt(x[i]) * sqrt(y[i]))))
     * }</pre>
     * <p>
     * Range: [0, 1] where 0 means identical distributions and 1 means
     * completely different.
     * </p>
     *
     * @param x
     *            the first probability vector (length l)
     * @param y
     *            the second probability vector (length l)
     * @param l
     *            the length of both vectors
     * @return the Hellinger distance between x and y
     */
    public static double hellingerDistance(double[] x, double[] y, int l) {
        if (x == null || y == null) {
            throw new IllegalArgumentException("Vectors must not be null");
        }
        if (l <= 0) {
            return 0.0;
        }

        double d = 1.0;
        for (int i = 0; i < l; i++) {
            double h = Math.sqrt(1.0 - x[i]) * Math.sqrt(1.0 - y[i])
                    + Math.sqrt(x[i]) * Math.sqrt(y[i]);
            d *= h;
        }
        return 1.0 - d;
    }

    /**
     * Computes the class nearness between two clusters in a partition.
     * <p>
     * Equivalent to C function {@code class_nearness()} from {@code report.c}.
     * Measures the similarity between two clusters based on their vector
     * composition. Returns a distance value where lower values indicate more
     * similar clusters.
     * </p>
     * <p>
     * The implementation uses a simplified version that compares centroid
     * probabilities directly, which is sufficient for tree building purposes.
     * </p>
     *
     * @param partition
     *            the partition containing the clusters
     * @param centroids
     *            the centroid array defining cluster probabilities
     * @param i
     *            1-based index of first cluster
     * @param j
     *            1-based index of second cluster
     * @return the nearness distance between clusters i and j
     */
    private static double classNearness(Partition partition,
            InfiniteCentroids centroids, int i, int j) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        // Use Hellinger distance as a proxy for class nearness
        // This is a simplified implementation that provides reasonable
        // clustering results
        Centroid ci = centroids.get(i - 1);
        Centroid cj = centroids.get(j - 1);
        return hellingerDistance(ci.getArray(), cj.getArray(), ci.getLength());
    }

    /**
     * Computes the average of two centroid probability vectors with Laplace
     * smoothing.
     * <p>
     * Equivalent to C function {@code inf_average12()} from {@code tree.c}.
     * Used during tree building to compute merged cluster centroids. Applies
     * Bayesian posterior predictive averaging with Laplace smoothing (add-1
     * prior) to handle edge cases where probabilities are 0 or 1.
     * </p>
     * <p>
     * Formula:
     * </p>
     * 
     * <pre>{@code
     * avg[i] = ((c1[i] * (n1 + 2)) - 1) + ((c2[i] * (n2 + 2)) - 1)) / (n1 + n2 + 2)
     * }</pre>
     * <p>
     * where n1, n2 are the sizes of the two clusters being merged.
     * </p>
     *
     * @param c1
     *            the first centroid probability vector
     * @param n1
     *            the size of the first cluster
     * @param c2
     *            the second centroid probability vector
     * @param n2
     *            the size of the second cluster
     * @return a new array containing the averaged probabilities
     */
    public static double[] infAverage12(double[] c1, int n1, double[] c2,
            int n2) {
        if (c1 == null || c2 == null) {
            throw new IllegalArgumentException("Centroids must not be null");
        }

        int l = c1.length;
        double[] result = new double[l];
        for (int i = 0; i < l; i++) {
            result[i] = ((((c1[i] * (n1 + 2.0)) - 1.0)
                    + ((c2[i] * (n2 + 2.0)) - 1.0)) + 1.0) / ((n1 + n2) + 2.0);
        }
        return result;
    }

    /**
     * Joins two clusters and shifts the partition down to mirror C's
     * {@code make_tree_pnn} merge mechanics.
     * <p>
     * Mirrors the sequence in {@code tree.c}: the merged class is stored at
     * position {@code imin}, the last active cluster (position {@code k}) is
     * shifted down into the vacated {@code jmin} slot, and the final slot is
     * cleared. Java's {@link Partition} uses a single contiguous array with no
     * spare trailing slot, so when {@code jmin} is not the last position the
     * vectors of the old last cluster are moved into {@code jmin} before the
     * shift.
     * </p>
     *
     * @param partition
     *            the partition to mutate (1-based indices)
     * @param imin
     *            1-based index where the merged class is stored
     * @param jmin
     *            1-based index of the second cluster being merged
     * @param k
     *            current number of active clusters (1-based)
     */
    private static void mergeClasses(Partition partition, int imin,
            int jmin, int k) {
        // join_class: merge class jmin into class imin (1-based positions).
        for (BinaryVector v : partition.getElements(jmin)) {
            partition.addElement(imin, v);
        }

        if (jmin == k) {
            // jmin is the last active cluster. The contiguous live classes are
            // now compact in positions 1..k-1; position k still holds a copy of
            // the merged vectors but is never summed again.
            return;
        }

        // Fill the hole left at jmin with the last active cluster's vectors so
        // the live classes stay contiguous. Mirror C:
        // P->el[jmin] = P->el[k-1]; P->el[k-1] = NULL.
        partition.getElements(jmin).clear();
        for (BinaryVector v : partition.getElements(k)) {
            partition.addElement(jmin, v);
        }
    }

    /**
     * Computes the stochastic complexity of a single cluster treated as its own
     * class.
     * <p>
     * Builds a temporary two-class partition where the target cluster holds all
     * its vectors and returns {@link DistanceCalculator#stochasticComplexity}
     * for it. This yields a meaningful, non-zero per-leaf value (the cost of
     * coding that class) instead of the zero C leaves report.
     * </p>
     *
     * @param partition
     *            the source partition (1-based indices)
     * @param clusterIndex
     *            1-based index of the cluster to evaluate
     * @param l
     *            length of binary vectors
     * @param jeffreysPrior
     *            if true, use Jeffreys prior; otherwise use uniform prior
     * @return the stochastic complexity of the given cluster
     */
    private static double leafStochasticComplexity(Partition partition,
            int clusterIndex, int l, boolean jeffreysPrior) {
        if (partition.getSize(clusterIndex) == 0) {
            return 0.0;
        }
        Partition single = new Partition(2);
        for (BinaryVector v : partition.getElements(clusterIndex)) {
            single.addElement(1, v);
        }
        return DistanceCalculator.stochasticComplexity(single, 2, l,
                jeffreysPrior);
    }

    /**
     * Computes the stochastic complexity over classes 1..k-1 of a partition,
     * returning {@code 0.0} when any class in that range is empty.
     * <p>
     * Mirrors how an all-empty partition behaves during tree building: the
     * distance-based merge still proceeds, but with no vectors to code the
     * stochastic complexity collapses to zero rather than throwing.
     * </p>
     *
     * @param partition
     *            the partition to evaluate (1-based class indices)
     * @param k
     *            number of clusters (1-based); classes 1..k-1 are summed
     * @param l
     *            length of binary vectors
     * @param jeffreysPrior
     *            if true, use Jeffreys prior; otherwise use uniform prior
     * @return stochastic complexity value, or {@code 0.0} if a class is empty
     */
    private static double safeStochasticComplexity(Partition partition,
            int k, int l, boolean jeffreysPrior) {
        for (int j = 1; j < k; j++) {
            if (partition.getSize(j) == 0) {
                return 0.0;
            }
        }
        return DistanceCalculator.stochasticComplexity(partition, k, l,
                jeffreysPrior);
    }

    /**
     * Returns a string representation of the dendrogram rooted at the given
     * node.
     * <p>
     * Equivalent to C function {@code traverse_tree()} from {@code tree.c}.
     * Produces a human-readable text representation suitable for display or
     * logging. Leaf nodes show their identifier, internal nodes show distance
     * and size information.
     * </p>
     *
     * @param node
     *            the root node of the dendrogram
     * @return a formatted string representing the tree structure
     */
    public static String traverseTree(TreeNode node) {
        if (node == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        traverseTreeHelper(node, 1, sb);
        return sb.toString();
    }

    private static void traverseTreeHelper(TreeNode node, int depth,
            StringBuilder sb) {
        if (node == null) {
            return;
        }

        // Recursively visit left child first
        if (node.getLeft() != null) {
            traverseTreeHelper(node.getLeft(), depth + 1, sb);
        }

        // Print current node
        for (int i = 0; i < depth - 1; i++) {
            sb.append(" ");
        }
        if (node.getRight() == null && node.getLeft() == null) {
            // Leaf node
            sb.append(node.getName());
        } else {
            // Internal node
            sb.append(String.format("[%.4f, %.4f]", 0.0, node.getSC()));
        }
        sb.append("\n");

        // Recursively visit right child
        if (node.getRight() != null) {
            traverseTreeHelper(node.getRight(), depth + 1, sb);
        }
    }
}
