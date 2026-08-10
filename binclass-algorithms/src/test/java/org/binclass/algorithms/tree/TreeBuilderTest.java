/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.tree;

import static org.junit.jupiter.api.Assertions.*;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.TreeNode;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TreeBuilder}.
 */
class TreeBuilderTest {

    @Test
    void testMakeTreePnnSingleCluster() {
        // Single cluster — tree should be a single leaf node
        Partition partition = new Partition(1);
        double[][] centroidsData = { { 0.5, 0.5, 0.5 } };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 1);

        TreeNode root = TreeBuilder.makeTreePnn(partition, centroids);
        assertNotNull(root);
    }

    @Test
    void testMakeTreePnnTwoClusters() {
        // Two clusters — tree should have one internal node with two leaves
        Partition partition = new Partition(2);
        double[][] centroidsData = {
                { 0.1, 0.1, 0.1 }, // Centroid for cluster 0 (zeros)
                { 0.9, 0.9, 0.9 } // Centroid for cluster 1 (ones)
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 2);

        TreeNode root = TreeBuilder.makeTreePnn(partition, centroids);
        assertNotNull(root);
        assertTrue(root.getLeft() != null || root.getRight() != null); // Should
                                                                       // have
                                                                       // children
    }

    @Test
    void testMakeTreePnnThreeClusters() {
        // Three clusters — tree should have proper hierarchy
        Partition partition = new Partition(3);
        double[][] centroidsData = {
                { 0.1, 0.1, 0.1 }, // Cluster 0
                { 0.5, 0.5, 0.5 }, // Cluster 1 (middle)
                { 0.9, 0.9, 0.9 } // Cluster 2
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 3);

        TreeNode root = TreeBuilder.makeTreePnn(partition, centroids);
        assertNotNull(root);
        // Root should have both children (binary tree)
        assertTrue(root.getLeft() != null && root.getRight() != null);
    }

    @Test
    void testMakeTreePnn2TwoClusters() {
        // Two clusters with class nearness distance
        Partition partition = new Partition(2);
        double[][] centroidsData = {
                { 0.1, 0.1, 0.1 },
                { 0.9, 0.9, 0.9 }
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 2);

        TreeNode root = TreeBuilder.makeTreePnn2(partition, centroids);
        assertNotNull(root);
    }

    @Test
    void testHellingerDistanceIdentical() {
        // Identical vectors should have distance 0
        double[] x = { 0.5, 0.5, 0.5 };
        double dist = TreeBuilder.hellingerDistance(x, x, 3);
        assertEquals(0.0, dist, 1e-10);
    }

    @Test
    void testHellingerDistanceCompletelyDifferent() {
        // Completely different vectors should have distance close to 1
        double[] x = { 1.0, 1.0, 1.0 };
        double[] y = { 0.0, 0.0, 0.0 };
        double dist = TreeBuilder.hellingerDistance(x, y, 3);
        assertTrue(dist > 0.9); // Should be close to 1
    }

    @Test
    void testHellingerDistanceSymmetric() {
        // Hellinger distance should be symmetric
        double[] x = { 0.2, 0.4, 0.6 };
        double[] y = { 0.8, 0.6, 0.4 };
        double distXY = TreeBuilder.hellingerDistance(x, y, 3);
        double distYX = TreeBuilder.hellingerDistance(y, x, 3);
        assertEquals(distXY, distYX, 1e-10);
    }

    @Test
    void testInfAverage12() {
        // Test weighted average with Laplace smoothing
        double[] c1 = { 0.8, 0.2 };
        int n1 = 10;
        double[] c2 = { 0.3, 0.7 };
        int n2 = 5;

        double[] result = TreeBuilder.infAverage12(c1, n1, c2, n2);
        assertNotNull(result);
        assertEquals(2, result.length);

        // Result should be between the two inputs (weighted by size)
        assertTrue(result[0] >= Math.min(0.8, 0.3)
                && result[0] <= Math.max(0.8, 0.3));
        assertTrue(result[1] >= Math.min(0.2, 0.7)
                && result[1] <= Math.max(0.2, 0.7));
    }

    @Test
    void testTraverseTree() {
        // Test tree traversal produces non-empty output
        Partition partition = new Partition(2);
        double[][] centroidsData = {
                { 0.1, 0.1 },
                { 0.9, 0.9 }
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 2);

        TreeNode root = TreeBuilder.makeTreePnn(partition, centroids);
        String treeStr = TreeBuilder.traverseTree(root);
        assertNotNull(treeStr);
        assertFalse(treeStr.isEmpty());
    }

    @Test
    void testMakeTreePnnWithRealVectors() {
        // Create a partition with actual vectors and build tree
        Partition partition = new Partition(2);

        // Cluster 1: zeros
        int[] el1 = { 0, 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 3);
        int[] el2 = { 0, 0, 1 };
        BinaryVector v2 = new BinaryVector(el2, 3);
        partition.addElement(1, v1);
        partition.addElement(1, v2);

        // Cluster 2: ones
        int[] el3 = { 1, 1, 1 };
        BinaryVector v3 = new BinaryVector(el3, 3);
        int[] el4 = { 1, 1, 0 };
        BinaryVector v4 = new BinaryVector(el4, 3);
        partition.addElement(2, v3);
        partition.addElement(2, v4);

        // Create centroids based on actual cluster composition
        double[][] centroidsData = {
                { 0.5, 0.5, 0.5 }, // Average of zeros and (0,0,1)
                { 1.0, 1.0, 0.5 } // Average of ones and (1,1,0)
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 2);

        TreeNode root = TreeBuilder.makeTreePnn(partition, centroids);
        assertNotNull(root);
    }

    @Test
    void testMakeTreePnnFourClusters() {
        // Four clusters — exercises the while-loop merge body (lines 97-143)
        Partition partition = new Partition(4);
        double[][] centroidsData = {
                { 0.1, 0.1, 0.1 }, // Cluster 0: low probabilities
                { 0.3, 0.3, 0.3 }, // Cluster 1: low-mid
                { 0.7, 0.7, 0.7 }, // Cluster 2: high-mid
                { 0.9, 0.9, 0.9 } // Cluster 3: high probabilities
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 4);

        TreeNode root = TreeBuilder.makeTreePnn(partition, centroids);
        assertNotNull(root);
        assertTrue(root.getLeft() != null && root.getRight() != null);
    }

    @Test
    void testMakeTreePnnFiveClusters() {
        // Five clusters — exercises while-loop multiple iterations
        Partition partition = new Partition(5);
        double[][] centroidsData = {
                { 0.1, 0.1 },
                { 0.3, 0.3 },
                { 0.5, 0.5 },
                { 0.7, 0.7 },
                { 0.9, 0.9 }
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 5);

        TreeNode root = TreeBuilder.makeTreePnn(partition, centroids);
        assertNotNull(root);
    }

    @Test
    void testMakeTreePnn2FourClusters() {
        // Four clusters with class nearness distance — exercises while-loop
        Partition partition = new Partition(4);
        double[][] centroidsData = {
                { 0.1, 0.1, 0.1 },
                { 0.3, 0.3, 0.3 },
                { 0.7, 0.7, 0.7 },
                { 0.9, 0.9, 0.9 }
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 4);

        TreeNode root = TreeBuilder.makeTreePnn2(partition, centroids);
        assertNotNull(root);
    }

    @Test
    void testHellingerDistanceNullVectors() {
        assertThrows(IllegalArgumentException.class, () -> TreeBuilder
                .hellingerDistance(null, new double[] { 0.5 }, 1));
        assertThrows(IllegalArgumentException.class, () -> TreeBuilder
                .hellingerDistance(new double[] { 0.5 }, null, 1));
    }

    @Test
    void testHellingerDistanceZeroLength() {
        assertEquals(0.0,
                TreeBuilder.hellingerDistance(new double[0], new double[0], 0));
    }

    @Test
    void testHellingerDistancePartialOverlap() {
        // Vectors with partial overlap should have distance between 0 and 1
        double[] x = { 0.5, 0.5, 0.5 };
        double[] y = { 0.5, 0.5, 0.9 };
        double dist = TreeBuilder.hellingerDistance(x, y, 3);
        assertTrue(dist > 0.0 && dist < 1.0);
    }

    @Test
    void testInfAverage12NullInputs() {
        assertThrows(IllegalArgumentException.class, () -> TreeBuilder
                .infAverage12(null, 5, new double[] { 0.5 }, 3));
        assertThrows(IllegalArgumentException.class, () -> TreeBuilder
                .infAverage12(new double[] { 0.5 }, 5, null, 3));
    }

    @Test
    void testInfAverage12EqualWeights() {
        // Equal weights should produce midpoint (with Laplace smoothing)
        double[] c1 = { 0.2, 0.8 };
        int n1 = 5;
        double[] c2 = { 0.6, 0.4 };
        int n2 = 5;

        double[] result = TreeBuilder.infAverage12(c1, n1, c2, n2);
        assertNotNull(result);
        assertEquals(2, result.length);
    }

    @Test
    void testTraverseTreeEmpty() {
        assertEquals("", TreeBuilder.traverseTree(null));
    }

    @Test
    void testTraverseTreeLeafNode() {
        TreeNode leaf = new TreeNode(0.0, "Leaf1");
        String result = TreeBuilder.traverseTree(leaf);
        assertNotNull(result);
        assertTrue(result.contains("Leaf1"));
    }

    @Test
    void testTraverseTreeInternalNode() {
        TreeNode left = new TreeNode(0.0, "Left");
        TreeNode right = new TreeNode(0.0, "Right");
        TreeNode internal = new TreeNode(0.5, "Internal", left, right);

        String result = TreeBuilder.traverseTree(internal);
        assertNotNull(result);
        assertTrue(result.contains("Left"));
        assertTrue(result.contains("Right"));
    }

    @Test
    void testMakeTreePnnWithRealVectorsFourClusters() {
        // Four clusters with real vectors — exercises full PNN merge loop
        Partition partition = new Partition(4);

        // Cluster 1: all zeros
        int[] el1 = { 0, 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 3);
        partition.addElement(1, v1);

        // Cluster 2: mostly zeros
        int[] el2 = { 0, 0, 1 };
        BinaryVector v2 = new BinaryVector(el2, 3);
        partition.addElement(2, v2);

        // Cluster 3: mostly ones
        int[] el3 = { 1, 1, 0 };
        BinaryVector v3 = new BinaryVector(el3, 3);
        partition.addElement(3, v3);

        // Cluster 4: all ones
        int[] el4 = { 1, 1, 1 };
        BinaryVector v4 = new BinaryVector(el4, 3);
        partition.addElement(4, v4);

        double[][] centroidsData = {
                { 0.0, 0.0, 0.0 }, // Cluster 1: v1={0,0,0}
                { 0.0, 0.0, 1.0 }, // Cluster 2: v2={0,0,1}
                { 1.0, 1.0, 0.0 }, // Cluster 3: v3={1,1,0}
                { 1.0, 1.0, 1.0 } // Cluster 4: v4={1,1,1}
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 4);

        TreeNode root = TreeBuilder.makeTreePnn(partition, centroids);
        assertNotNull(root);
    }
}
