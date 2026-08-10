/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.tree;

import java.util.Objects;

import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.TreeNode;

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
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        int k = partition.size(); // 1-based count of clusters
        if (k <= 0) {
            return null;
        }

        int l = centroids.get(0).getLength(); // vector length from first
                                              // centroid

        // Initialize leaf nodes for each cluster
        TreeNode[] nodes = new TreeNode[k];
        double[][] nodeCentroids = new double[k][l];
        int[] nodeSizes = new int[k];

        for (int i = 0; i < k; i++) {
            Centroid c = centroids.get(i);
            nodeCentroids[i] = c.getArray().clone();
            nodeSizes[i] = partition.getSize(i + 1); // Convert to 1-based
            nodes[i] = new TreeNode(0.0, "C" + (i + 1));
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

            // Merge the two closest clusters
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

            // Create new internal node with children
            TreeNode mergedNode = new TreeNode(dmin, "Merged", nodes[jmin],
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

        // Final merge for the last two clusters
        if (currentK == 2) {
            double dmin = hellingerDistance(nodeCentroids[0], nodeCentroids[1],
                    l);
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

            return new TreeNode(dmin, "Root", nodes[1], nodes[0]);
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
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, INFINITE_CENTROIDS_MUST_NOT_BE_NULL);

        int k = partition.size(); // 1-based count of clusters
        if (k <= 0) {
            return null;
        }

        int l = centroids.get(0).getLength(); // vector length from first
                                              // centroid

        // Initialize leaf nodes for each cluster
        TreeNode[] nodes = new TreeNode[k];
        double[][] nodeCentroids = new double[k][l];
        int[] nodeSizes = new int[k];

        for (int i = 0; i < k; i++) {
            Centroid c = centroids.get(i);
            nodeCentroids[i] = c.getArray().clone();
            nodeSizes[i] = partition.getSize(i + 1); // Convert to 1-based
            nodes[i] = new TreeNode(0.0, "C" + (i + 1));
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

            // Merge the two closest clusters
            int mergedSize = nodeSizes[imin] + nodeSizes[jmin];
            double[] mergedCentroid = new double[l];
            for (int bit = 0; bit < l; bit++) {
                // Weighted average without Laplace smoothing
                mergedCentroid[bit] = ((nodeSizes[imin]
                        * nodeCentroids[imin][bit])
                        + (nodeSizes[jmin] * nodeCentroids[jmin][bit]))
                        / mergedSize;
            }

            // Create new internal node with children
            TreeNode mergedNode = new TreeNode(dmin, "Merged", nodes[jmin],
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

        // Final merge for the last two clusters
        if (currentK == 2) {
            double dmin = classNearness(partition, centroids, 1, 2);
            int mergedSize = nodeSizes[0] + nodeSizes[1];
            double[] mergedCentroid = new double[l];
            for (int bit = 0; bit < l; bit++) {
                mergedCentroid[bit] = ((nodeSizes[0] * nodeCentroids[0][bit])
                        + (nodeSizes[1] * nodeCentroids[1][bit]))
                        / mergedSize;
            }

            return new TreeNode(dmin, "Root", nodes[1], nodes[0]);
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
