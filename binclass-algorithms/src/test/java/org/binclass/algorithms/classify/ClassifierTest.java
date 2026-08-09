/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.classify;

import static org.junit.jupiter.api.Assertions.*;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Classifier}.
 */
class ClassifierTest {

    @Test
    void testIdentifyVectorsSingleCluster() {
        // Create a single cluster with all vectors
        VectorSet vectors = new VectorSet();
        int[] el1 = { 0, 1, 1 };
        BinaryVector v1 = new BinaryVector(el1, 3);
        int[] el2 = { 0, 1, 0 };
        BinaryVector v2 = new BinaryVector(el2, 3);

        vectors.addElement(v1);
        vectors.addElement(v2);

        // Create single centroid (all zeros)
        double[][] centroidsData = { { 0.0, 0.0, 0.0 } };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 1);

        Partition partition = new Partition(1);
        Partition result = Classifier.identifyVectors(vectors, partition,
                centroids);

        assertNotNull(result);
        assertEquals(2, result.getSize(1)); // Both vectors in single cluster
    }

    @Test
    void testIdentifyVectorsTwoClusters() {
        // Create two distinct clusters
        VectorSet vectors = new VectorSet();

        // Cluster 0: all zeros
        int[] el1 = { 0, 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 3);
        int[] el2 = { 0, 0, 1 };
        BinaryVector v2 = new BinaryVector(el2, 3);

        // Cluster 1: all ones
        int[] el3 = { 1, 1, 1 };
        BinaryVector v3 = new BinaryVector(el3, 3);
        int[] el4 = { 1, 1, 0 };
        BinaryVector v4 = new BinaryVector(el4, 3);

        vectors.addElement(v1);
        vectors.addElement(v2);
        vectors.addElement(v3);
        vectors.addElement(v4);

        // Create centroids: one near zeros, one near ones
        double[][] centroidsData = {
                { 0.25, 0.25, 0.25 }, // Centroid for cluster 0 (zeros)
                { 0.75, 0.75, 0.75 } // Centroid for cluster 1 (ones)
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 2);

        Partition partition = new Partition(2);
        Partition result = Classifier.identifyVectors(vectors, partition,
                centroids);

        assertNotNull(result);
        assertEquals(4, result.getSize(1) + result.getSize(2)); // All vectors
                                                                // assigned

        // Verify cluster assignments (1-based) - check that both clusters have
        // elements
        assertTrue(result.getSize(1) > 0, "Cluster 1 should have elements");
        assertTrue(result.getSize(2) > 0, "Cluster 2 should have elements");
    }

    @Test
    void testIdentifyVectorsFast() {
        // Test fast Hamming distance variant
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 3);
        int[] el2 = { 1, 1, 1 };
        BinaryVector v2 = new BinaryVector(el2, 3);

        vectors.addElement(v1);
        vectors.addElement(v2);

        // Create centroids: one near zeros, one near ones
        double[][] centroidsData = {
                { 0.0, 0.0, 0.0 }, // Centroid for cluster 0 (zeros)
                { 1.0, 1.0, 1.0 } // Centroid for cluster 1 (ones)
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 2);

        Partition partition = new Partition(2);
        Partition result = Classifier.identifyVectorsFast(vectors, partition,
                centroids);

        assertNotNull(result);
        assertEquals(2, result.size()); // Both vectors assigned

        // Verify cluster assignments (1-based) - check specific vector
        // membership
        assertTrue(result.contains(1, v1), "v1 should be in cluster 1");
        assertTrue(result.contains(2, v2), "v2 should be in cluster 2");
    }

    @Test
    void testIdentifyVectorsNullVectorSet() {
        Partition partition = new Partition(1);
        InfiniteCentroids centroids = new InfiniteCentroids(
                new double[][] { { 0.5 } }, 1);

        assertThrows(NullPointerException.class,
                () -> Classifier.identifyVectors(null, partition, centroids));
    }

    @Test
    void testIdentifyVectorsNullPartition() {
        VectorSet vectors = new VectorSet();
        InfiniteCentroids centroids = new InfiniteCentroids(
                new double[][] { { 0.5 } }, 1);

        assertThrows(NullPointerException.class,
                () -> Classifier.identifyVectors(vectors, null, centroids));
    }

    @Test
    void testIdentifyVectorsNullCentroids() {
        VectorSet vectors = new VectorSet();
        Partition partition = new Partition(1);

        assertThrows(NullPointerException.class,
                () -> Classifier.identifyVectors(vectors, partition, null));
    }

    @Test
    void testClassifyVectorsPipeline() {
        // Test the full classification pipeline method (stub)
        assertDoesNotThrow(() -> Classifier.classifyVectors("data.dat",
                "out.txt", "par.par"));
    }

    @Test
    void testIdentifyVectorsEmptyVectorSet() {
        VectorSet vectors = new VectorSet();

        double[][] centroidsData = { { 0.5, 0.5 } };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 1);

        Partition partition = new Partition(1);
        Partition result = Classifier.identifyVectors(vectors, partition,
                centroids);

        assertNotNull(result);
        assertEquals(0, result.getSize(1)); // No vectors in the single cluster
    }

    @Test
    void testIdentifyVectorsMultipleClusters() {
        // Test with 3 clusters
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        int[] el2 = { 1, 0 };
        BinaryVector v2 = new BinaryVector(el2, 2);
        int[] el3 = { 1, 1 };
        BinaryVector v3 = new BinaryVector(el3, 2);

        vectors.addElement(v1);
        vectors.addElement(v2);
        vectors.addElement(v3);

        // Create 3 centroids spread across the space
        double[][] centroidsData = {
                { 0.0, 0.0 }, // Cluster 0
                { 0.5, 0.0 }, // Cluster 1
                { 1.0, 1.0 } // Cluster 2
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 3);

        Partition partition = new Partition(3);
        Partition result = Classifier.identifyVectors(vectors, partition,
                centroids);

        assertNotNull(result);
        assertEquals(3,
                result.getSize(1) + result.getSize(2) + result.getSize(3)); // All
                                                                            // vectors
                                                                            // assigned

        // Verify all clusters are represented (1-based) - check each cluster
        // has elements
        assertTrue(result.getSize(1) > 0, "Cluster 1 should have elements");
        assertTrue(result.getSize(2) > 0, "Cluster 2 should have elements");
        assertTrue(result.getSize(3) > 0, "Cluster 3 should have elements");
    }
}
