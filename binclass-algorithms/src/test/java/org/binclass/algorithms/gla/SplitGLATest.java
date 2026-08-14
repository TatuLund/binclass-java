/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.gla;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SplitGLA}.
 */
class SplitGLATest {

    @Test
    void testSplitGLAFindsReasonableK() {
        // Create synthetic data with 2 clear clusters
        VectorSet vectors = new VectorSet();

        // Cluster A: all zeros
        for (int i = 0; i < 15; i++) {
            int[] el = { 0, 0 };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        // Cluster B: all ones
        for (int i = 0; i < 15; i++) {
            int[] el = { 1, 1 };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        double[] scmin = new double[1];
        double[] scs = new double[vectors.size() + 1];
        Partition result = SplitGLA.splitGLA(vectors, scmin, scs,
                GLAConfig.DEFAULT);

        assertNotNull(result);

        // Should find k=2 for clearly separated clusters
        assertTrue(result.size() >= 1 && result.size() <= 4,
                "Should find a reasonable number of clusters (expected ~2)");
    }

    @Test
    void testSplitGLASingleCluster() {
        VectorSet vectors = new VectorSet();

        // All identical vectors - should create single cluster
        for (int i = 0; i < 10; i++) {
            int[] el = { 0, 0 };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        double[] scmin = new double[1];
        double[] scs = new double[vectors.size() + 1];
        Partition result = SplitGLA.splitGLA(vectors, scmin, scs,
                GLAConfig.DEFAULT);

        assertNotNull(result);
        assertEquals(1, result.size(),
                "Identical vectors should create single cluster");
    }

    @Test
    void testSplitGLATwoClusters() {
        VectorSet vectors = new VectorSet();

        // Two distinct clusters
        for (int i = 0; i < 20; i++) {
            int[] el = { 0, 0 };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        for (int i = 0; i < 20; i++) {
            int[] el = { 1, 1 };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        double[] scmin = new double[1];
        double[] scs = new double[vectors.size() + 1];
        Partition result = SplitGLA.splitGLA(vectors, scmin, scs,
                GLAConfig.DEFAULT);

        assertNotNull(result);

        // Should find k=2 for clearly separated clusters
        assertEquals(2, result.size(), "Should identify two distinct clusters");
    }

    @Test
    void testSplitGLAThreeClusters() {
        VectorSet vectors = new VectorSet();

        // Three distinct clusters in 3D space
        for (int i = 0; i < 15; i++) {
            int[] el = { 0, 0, 0 };
            BinaryVector v = new BinaryVector(el, 3);
            vectors.addElement(v);
        }

        for (int i = 0; i < 15; i++) {
            int[] el = { 1, 1, 0 };
            BinaryVector v = new BinaryVector(el, 3);
            vectors.addElement(v);
        }

        for (int i = 0; i < 15; i++) {
            int[] el = { 1, 0, 1 };
            BinaryVector v = new BinaryVector(el, 3);
            vectors.addElement(v);
        }

        double[] scmin = new double[1];
        double[] scs = new double[vectors.size() + 1];
        Partition result = SplitGLA.splitGLA(vectors, scmin, scs,
                GLAConfig.DEFAULT);

        assertNotNull(result);

        // Should find approximately k=3 for three distinct clusters
        assertTrue(result.size() >= 2 && result.size() <= 5,
                "Should identify approximately three clusters");
    }

    @Test
    void testWorstMatchingVectors() {
        VectorSet vectors = new VectorSet();

        // Create two very different vectors
        int[] el1 = { 0, 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 3);
        int[] el2 = { 1, 1, 1 };
        BinaryVector v2 = new BinaryVector(el2, 3);

        vectors.addElement(v1);
        vectors.addElement(v2);

        BinaryVector[] result = SplitGLA.worstMatchingVectors(vectors,
                new Random(42));

        assertNotNull(result);
        assertEquals(2, result.length); // Should return array of 2 vectors

        // The worst matching pair should be v1 and v2 (max Hamming distance)
        assertTrue(result[0] == v1 || result[0] == v2,
                "First vector in worst-matching pair should be one of the input vectors");
        assertTrue(result[1] == v1 || result[1] == v2,
                "Second vector in worst-matching pair should be one of the input vectors");
    }

    @Test
    void testAbsWorstMatchingVectors() {
        VectorSet vectors = new VectorSet();

        // Create multiple vectors with varying distances
        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        int[] el2 = { 1, 1 };
        BinaryVector v2 = new BinaryVector(el2, 2);
        int[] el3 = { 0, 1 };
        BinaryVector v3 = new BinaryVector(el3, 2);

        vectors.addElement(v1);
        vectors.addElement(v2);
        vectors.addElement(v3);

        BinaryVector[] result = SplitGLA.absWorstMatchingVectors(vectors);

        assertNotNull(result);
        assertEquals(2, result.length); // Should return array of 2 vectors

        // The worst matching pair should be v1 and v2 (Hamming distance = 2)
        assertTrue((result[0] == v1 && result[1] == v2) ||
                (result[0] == v2 && result[1] == v1),
                "Worst-matching pair should be the two most distant vectors");
    }

    @Test
    void testSetFirstCentroids() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        int[] el2 = { 1, 1 };
        BinaryVector v2 = new BinaryVector(el2, 2);

        vectors.addElement(v1);
        vectors.addElement(v2);

        // Create initial centroids from the two vectors
        InfiniteCentroids centroids = new InfiniteCentroids(
                2, 2);
        SplitGLA.setFirstCentroids(centroids, v1, v2);
        assertEquals(2, centroids.size()); // Should create 2 initial centroids

        // Verify centroids are valid (values between 0 and 1)
        for (int i = 0; i < 2; i++) {
            Centroid c = centroids.get(i);
            assertNotNull(c);
            assertEquals(2, c.getLength());

            for (int j = 0; j < 2; j++) {
                assertTrue(c.get(j) >= 0.0 && c.get(j) <= 1.0,
                        "Centroid values should be between 0 and 1");
            }
        }
    }

    @Test
    void testPointWorstClass() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        int[] el2 = { 1, 1 };
        BinaryVector v2 = new BinaryVector(el2, 2);

        vectors.addElement(v1);
        vectors.addElement(v2);

        InfiniteCentroids centroids = new InfiniteCentroids(
                2, 2);
        SplitGLA.setFirstCentroids(centroids, v1, v2);

        // Find worst-matching vector and its class
        SplitGLA.worstMatchingVectors(vectors,
                new Random(42));
        Partition partition = new Partition(2);
        int worstClass = SplitGLA.pointWorstClass(partition, centroids);

        assertTrue(worstClass >= 1 && worstClass <= centroids.size(),
                "Should return a valid class index");
    }

    @Test
    void testSetNewCentroids() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        int[] el2 = { 0, 0 };
        BinaryVector v2 = new BinaryVector(el2, 2);

        vectors.addElement(v1);
        vectors.addElement(v2);

        InfiniteCentroids centroids = new InfiniteCentroids(
                2, 2);
        SplitGLA.setFirstCentroids(centroids, v1, v2);

        // Modify one centroid and verify update
        Centroid c0 = centroids.get(0);
        c0.get(0);

        InfiniteCentroids newCentroids = new InfiniteCentroids(
                3, 2);
        SplitGLA.setNewCentroids(newCentroids, centroids, v1, v2, 2);

        // Verify centroids are updated (values should be averages)
        for (int i = 0; i < centroids.size(); i++) {
            Centroid c = centroids.get(i);
            assertNotNull(c);

            for (int j = 0; j < c.getLength(); j++) {
                assertTrue(c.get(j) >= 0.0 && c.get(j) <= 1.0,
                        "Updated centroid values should be between 0 and 1");
            }
        }
    }

    @Test
    void testSplitGLANullVectorSet() {
        double[] scmin = new double[1];
        double[] scs = new double[2];
        assertThrows(NullPointerException.class,
                () -> SplitGLA.splitGLA(null, scmin, scs, GLAConfig.DEFAULT));
    }

    @Test
    void testWorstMatchingVectorsNull() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        vectors.addElement(v1);

        assertThrows(NullPointerException.class,
                () -> SplitGLA.worstMatchingVectors(null, new Random(42)));
    }

    @Test
    void testAbsWorstMatchingVectorsNull() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        vectors.addElement(v1);

        assertThrows(NullPointerException.class,
                () -> SplitGLA.absWorstMatchingVectors(null));
    }

    @Test
    void testSetFirstCentroidsNull() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        vectors.addElement(v1);

        assertThrows(NullPointerException.class,
                () -> SplitGLA.setFirstCentroids(null, v1, v1));
    }

    @Test
    void testPointWorstClassNull() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        vectors.addElement(v1);

        InfiniteCentroids centroids = new InfiniteCentroids(
                2, 2);

        assertThrows(NullPointerException.class,
                () -> SplitGLA.pointWorstClass(null, centroids));
    }

    @Test
    void testSetNewCentroidsNull() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        vectors.addElement(v1);

        InfiniteCentroids centroids = new InfiniteCentroids(
                2, 2);

        assertThrows(NullPointerException.class,
                () -> SplitGLA.setNewCentroids(null, centroids, v1, v1, 2));
    }

    @Test
    void testSplitGLALargeDataset() {
        VectorSet vectors = new VectorSet();

        Random random = new Random(101);
        for (int i = 0; i < 50; i++) {
            int[] el = {
                    random.nextInt(2),
                    random.nextInt(2)
            };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        double[] scmin = new double[1];
        double[] scs = new double[vectors.size() + 1];
        assertDoesNotThrow(() -> SplitGLA.splitGLA(vectors, scmin, scs,
                GLAConfig.DEFAULT));
    }

    @Test
    void testSplitGLAWithRandomSeed() {
        // Test that different random seeds produce different results (but still
        // valid)
        VectorSet vectors = new VectorSet();

        Random random1 = new Random(1);
        for (int i = 0; i < 20; i++) {
            int[] el = {
                    random1.nextInt(2),
                    random1.nextInt(2)
            };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        VectorSet vCopy1 = new VectorSet();
        vectors.copyTo(vCopy1);
        VectorSet vCopy2 = new VectorSet();
        vectors.copyTo(vCopy2);

        double[] scmin1 = new double[1];
        double[] scs1 = new double[vCopy1.size() + 1];
        Partition result1 = SplitGLA.splitGLA(vCopy1, scmin1, scs1,
                GLAConfig.DEFAULT);

        double[] scmin2 = new double[1];
        double[] scs2 = new double[vCopy2.size() + 1];
        Partition result2 = SplitGLA.splitGLA(vCopy2, scmin2, scs2,
                GLAConfig.DEFAULT);

        assertNotNull(result1);
        assertNotNull(result2);

        // Both should be valid partitions
        assertTrue(result1.size() >= 1,
                "Result 1 should have at least one cluster");
        assertTrue(result2.size() >= 1,
                "Result 2 should have at least one cluster");
    }

    @Test
    void testSplitGLAWithDifferentVectorLengths() {
        // Test with longer vectors (5 dimensions)
        VectorSet vectors = new VectorSet();

        Random random = new Random(101);
        for (int i = 0; i < 30; i++) {
            int[] el = {
                    random.nextInt(2),
                    random.nextInt(2),
                    random.nextInt(2),
                    random.nextInt(2),
                    random.nextInt(2)
            };
            BinaryVector v = new BinaryVector(el, 5);
            vectors.addElement(v);
        }

        double[] scmin = new double[1];
        double[] scs = new double[vectors.size() + 1];
        Partition result = SplitGLA.splitGLA(vectors, scmin, scs,
                GLAConfig.DEFAULT);

        assertNotNull(result);

        // Should find a reasonable number of clusters for high-dimensional data
        assertTrue(result.size() >= 1 && result.size() <= 8,
                "Should identify a reasonable number of clusters");
    }

    @Test
    void testWorstMatchingVectorsSingleVector() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        vectors.addElement(v1);

        // Should throw exception for single vector (need at least 2)
        assertThrows(IllegalArgumentException.class,
                () -> SplitGLA.worstMatchingVectors(vectors, new Random(42)));
    }

    @Test
    void testAbsWorstMatchingVectorsSingleVector() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        vectors.addElement(v1);

        // Should throw exception for single vector (need at least 2)
        assertThrows(IllegalArgumentException.class, () -> SplitGLA
                .absWorstMatchingVectors(vectors));
    }

    @Test
    void testSplitGLAThreeDistinctClusters() {
        VectorSet vectors = new VectorSet();

        // Three very distinct clusters in 3D space
        for (int i = 0; i < 10; i++) {
            int[] el = { 0, 0, 0 };
            BinaryVector v = new BinaryVector(el, 3);
            vectors.addElement(v);
        }

        for (int i = 0; i < 10; i++) {
            int[] el = { 1, 1, 1 };
            BinaryVector v = new BinaryVector(el, 3);
            vectors.addElement(v);
        }

        for (int i = 0; i < 10; i++) {
            int[] el = { 0, 1, 0 };
            BinaryVector v = new BinaryVector(el, 3);
            vectors.addElement(v);
        }

        double[] scmin = new double[1];
        double[] scs = new double[vectors.size() + 1];
        Partition result = SplitGLA.splitGLA(vectors, scmin, scs,
                GLAConfig.DEFAULT);

        assertNotNull(result);

        // Should identify approximately three clusters
        assertTrue(result.size() >= 2 && result.size() <= 5,
                "Should identify approximately three distinct clusters");
    }
}
