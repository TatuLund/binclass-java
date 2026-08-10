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
}
