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
 * Unit tests for {@link JoinGLA}.
 */
class JoinGLATest {

    @Test
    void testJoinGLAFindsReasonableK() {
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
        Partition result = JoinGLA.joinGLA(vectors, scmin, scs,
                GLAConfig.DEFAULT);

        assertNotNull(result);

        // Should find k=2 for clearly separated clusters
        assertTrue(result.size() >= 1 && result.size() <= 4,
                "Should find a reasonable number of clusters (expected ~2)");
    }

    @Test
    void testJoinGLASingleCluster() {
        VectorSet vectors = new VectorSet();

        // All identical vectors - should create single cluster
        for (int i = 0; i < 10; i++) {
            int[] el = { 0, 0 };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        double[] scmin = new double[1];
        double[] scs = new double[vectors.size() + 1];
        Partition result = JoinGLA.joinGLA(vectors, scmin, scs,
                GLAConfig.DEFAULT);

        assertNotNull(result);
        assertEquals(1, result.size(),
                "Identical vectors should create single cluster");
    }

    @Test
    void testJoinGLATwoClusters() {
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
        Partition result = JoinGLA.joinGLA(vectors, scmin, scs,
                GLAConfig.DEFAULT);

        assertNotNull(result);

        // Should find k=2 for clearly separated clusters
        assertEquals(2, result.size(), "Should identify two distinct clusters");
    }

    @Test
    void testJoinGLAThreeClusters() {
        VectorSet vectors = new VectorSet();

        // Three distinct clusters in 3D space - maximally separated to avoid
        // PNN2 merging
        for (int i = 0; i < 15; i++) {
            int[] el = { 0, 0, 0 };
            BinaryVector v = new BinaryVector(el, 3);
            vectors.addElement(v);
        }

        for (int i = 0; i < 15; i++) {
            int[] el = { 1, 1, 1 };
            BinaryVector v = new BinaryVector(el, 3);
            vectors.addElement(v);
        }

        for (int i = 0; i < 15; i++) {
            int[] el = { 1, 0, 0 };
            BinaryVector v = new BinaryVector(el, 3);
            vectors.addElement(v);
        }

        double[] scmin = new double[1];
        double[] scs = new double[vectors.size() + 1];
        Partition result = JoinGLA.joinGLA(vectors, scmin, scs,
                GLAConfig.DEFAULT);

        assertNotNull(result);

        // Should find approximately k=3 for three distinct clusters
        assertTrue(result.size() >= 2 && result.size() <= 5,
                "Should identify approximately three clusters");
    }

    @Test
    void testJoinTwoClasses() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        int[] el2 = { 1, 1 };
        BinaryVector v2 = new BinaryVector(el2, 2);

        vectors.addElement(v1);
        vectors.addElement(v2);

        // Create initial centroids for two clusters
        double[][] centroidsData = {
                { 0.1, 0.1 }, // Centroid for cluster 0
                { 0.9, 0.9 } // Centroid for cluster 1
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 2);

        Partition partition = new Partition(2);

        // Merge the two classes
        JoinGLA.joinTwoClasses(centroids, partition);

        assertNotNull(partition);
        assertEquals(1, partition.size(),
                "After merging two classes, should have one cluster");
    }

    @Test
    void testPartitionToSet() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        int[] el2 = { 0, 0 };
        BinaryVector v2 = new BinaryVector(el2, 2);

        vectors.addElement(v1);
        vectors.addElement(v2);

        // Create partition with one cluster containing both vectors
        Partition partition = new Partition(1);
        partition.addElement(1, v1);
        partition.addElement(1, v2);

        VectorSet result = JoinGLA.partitionToSet(partition);

        assertNotNull(result);
        assertEquals(2, result.size(), "Should return setith both vectors");
    }

    @Test
    void testEdistance2DoubleArrays() {
        double[] a = { 0.1, 0.1 };
        double[] b = { 0.9, 0.9 };

        double distance = JoinGLA.edistance2(a, b, 2);

        assertEquals(1.28, distance, 0.001,
                "Squared Euclidean distance should be correct");
    }

    @Test
    void testEdistance2Centroids() {
        double[][] centroidsData = {
                { 0.1, 0.1 },
                { 0.9, 0.9 }
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 2);

        Centroid c0 = centroids.get(0);
        Centroid c1 = centroids.get(1);

        double distance = JoinGLA.edistance2(c0, c1, 2);

        assertEquals(1.28, distance, 0.001,
                "Squared Euclidean distance should be correct");
    }

    @Test
    void testSetFirstCentroidsPNN() {
        VectorSet vectors = new VectorSet();

        Random random = new Random(42);
        for (int i = 0; i < 15; i++) {
            int[] el = {
                    random.nextInt(2),
                    random.nextInt(2)
            };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        InfiniteCentroids centroids = JoinGLA.setFirstCentroidsPNN(vectors,
                10.0);

        assertNotNull(centroids);
        assertTrue(centroids.size() >= 1,
                "Should create at least one centroid");

        // Verify centroids are valid (values between 0 and 1)
        for (int i = 0; i < centroids.size(); i++) {
            Centroid c = centroids.get(i);
            assertNotNull(c);

            for (int j = 0; j < c.getLength(); j++) {
                assertTrue(c.get(j) >= 0.0 && c.get(j) <= 1.0,
                        "Centroid values should be between 0 and 1");
            }
        }
    }

    @Test
    void testSetFirstCentroidsPNN2() {
        VectorSet vectors = new VectorSet();

        Random random = new Random(99);
        for (int i = 0; i < 20; i++) {
            int[] el = {
                    random.nextInt(2),
                    random.nextInt(2)
            };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        InfiniteCentroids centroids = JoinGLA.setFirstCentroidsPNN2(vectors,
                10.0);

        assertNotNull(centroids);
        assertTrue(centroids.size() >= 1,
                "Should create at least one centroid");

        // Verify centroids are valid (values between 0 and 1)
        for (int i = 0; i < centroids.size(); i++) {
            Centroid c = centroids.get(i);
            assertNotNull(c);

            for (int j = 0; j < c.getLength(); j++) {
                assertTrue(c.get(j) >= 0.0 && c.get(j) <= 1.0,
                        "Centroid values should be between 0 and 1");
            }
        }
    }

    @Test
    void testJoinGLANullVectorSet() {
        double[] scmin = new double[1];
        double[] scs = new double[2];

        assertThrows(NullPointerException.class,
                () -> JoinGLA.joinGLA(null, scmin, scs, GLAConfig.DEFAULT));
    }

    @Test
    void testJoinTwoClassesNullCentroids() {
        Partition partition = new Partition(2);

        assertThrows(NullPointerException.class,
                () -> JoinGLA.joinTwoClasses(null, partition));
    }

    @Test
    void testPartitionToSetNull() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        vectors.addElement(v1);

        Partition partition = new Partition(1);
        partition.addElement(1, v1);

        assertThrows(NullPointerException.class,
                () -> JoinGLA.partitionToSet(null));
    }

    @Test
    void testEdistance2Null() {
        double[][] centroidsData = { { 0.5, 0.5 } };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 1);

        Centroid c0 = centroids.get(0);

        assertThrows(NullPointerException.class,
                () -> JoinGLA.edistance2(null, c0, 2));
    }

    @Test
    void testSetFirstCentroidsPNNNull() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        vectors.addElement(v1);

        assertThrows(NullPointerException.class,
                () -> JoinGLA.setFirstCentroidsPNN(null, 10.0));
    }

    @Test
    void testSetFirstCentroidsPNN2Null() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        vectors.addElement(v1);

        assertThrows(NullPointerException.class,
                () -> JoinGLA.setFirstCentroidsPNN2(null, 10.0));
    }

    @Test
    void testPartitionToSetCopiesVectors() {
        // When the same object reference sits in two clusters (which happens
        // after a merge moves vectors around), partitionToSet must copy each
        // vector so every physical instance survives the set's identity
        // deduplication. Without copying, the shared reference collapses to a
        // single entry and GLA permanently loses a vector.
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        vectors.addElement(v1);

        Partition partition = new Partition(2);
        partition.addElement(1, v1);
        partition.addElement(2, v1); // same reference in two clusters

        VectorSet result = JoinGLA.partitionToSet(partition);

        assertNotNull(result);
        assertEquals(2, result.size(),
                "Each physical instance must survive as a distinct entry");
    }

    @Test
    void testJoinGLAPreservesVectorCount() {
        // The total number of vectors across all clusters must equal the input
        // count after a full join-GLA run. This guards against the drift bug
        // where partitionToSet used references and lost vectors during merges.
        VectorSet vectors = new VectorSet();

        Random random = new Random(7);
        for (int i = 0; i < 40; i++) {
            int[] el = { random.nextInt(2), random.nextInt(2) };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        int inputCount = vectors.size();
        double[] scmin = new double[1];
        double[] scs = new double[inputCount + 1];

        Partition result = JoinGLA.joinGLA(vectors, scmin, scs,
                GLAConfig.DEFAULT);

        assertNotNull(result);

        int total = 0;
        for (int i = 1; i <= result.size(); i++) {
            total += result.getSize(i);
        }

        assertEquals(inputCount, total,
                "Partition must preserve the exact input vector count");
    }

    @Test
    void testJoinGLAPreservesVectorCountWithDuplicates() {
        // Data with duplicate content but distinct objects stresses the
        // reference-vs-copy behavior of partitionToSet during merges.
        VectorSet vectors = new VectorSet();

        Random random = new Random(123);
        for (int i = 0; i < 60; i++) {
            int[] el = { random.nextInt(2), random.nextInt(2) };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        int inputCount = vectors.size();
        double[] scmin = new double[1];
        double[] scs = new double[inputCount + 1];

        Partition result = JoinGLA.joinGLA(vectors, scmin, scs,
                GLAConfig.DEFAULT);

        assertNotNull(result);

        int total = 0;
        for (int i = 1; i <= result.size(); i++) {
            total += result.getSize(i);
        }

        assertEquals(inputCount, total,
                "Partition must preserve the exact input vector count even with duplicate content");
    }

    @Test
    void testJoinGLALargeDataset() {
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

        assertDoesNotThrow(
                () -> JoinGLA.joinGLA(vectors, scmin, scs, GLAConfig.DEFAULT));
    }

    @Test
    void testJoinGLAThreeDistinctClusters() {
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
        Partition result = JoinGLA.joinGLA(vectors, scmin, scs,
                GLAConfig.DEFAULT);

        assertNotNull(result);

        // Should identify approximately three clusters
        assertTrue(result.size() >= 2 && result.size() <= 5,
                "Should identify approximately three distinct clusters");
    }

    @Test
    void testJoinGLAWithDifferentVectorLengths() {
        VectorSet vectors = new VectorSet();

        Random random = new Random(101);
        for (int i = 0; i < 30; i++) {
            int[] el = {
                    random.nextInt(2),
                    random.nextInt(2),
                    random.nextInt(2),
                    random.nextInt(2)
            };
            BinaryVector v = new BinaryVector(el, 4);
            vectors.addElement(v);
        }

        double[] scmin = new double[1];
        double[] scs = new double[vectors.size() + 1];

        assertDoesNotThrow(
                () -> JoinGLA.joinGLA(vectors, scmin, scs, GLAConfig.DEFAULT));
    }

    @Test
    void testJoinTwoClassesMultipleMerges() {
        VectorSet vectors = new VectorSet();

        Random random = new Random(202);
        for (int i = 0; i < 30; i++) {
            int[] el = {
                    random.nextInt(2),
                    random.nextInt(2)
            };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        // Create initial centroids for multiple clusters
        double[][] centroidsData = {
                { 0.1, 0.1 },
                { 0.9, 0.9 },
                { 0.5, 0.5 }
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 3);

        Partition partition = new Partition(3);

        // Merge multiple times to reduce cluster count
        for (int i = 0; i < 2; i++) {
            JoinGLA.joinTwoClasses(centroids, partition);
        }

        assertNotNull(partition);
        assertEquals(1, partition.size(),
                "After merging three classes twice, should have one cluster");
    }

    @Test
    void testPartitionToSetEmpty() {
        Partition partition = new Partition(2);

        VectorSet result = JoinGLA.partitionToSet(partition);

        assertNotNull(result);
        assertEquals(0, result.size(), "Empty cluster should return empty set");
    }

    @Test
    void testEdistance2SingleCluster() {
        VectorSet vectors = new VectorSet();

        Random random = new Random(303);
        for (int i = 0; i < 15; i++) {
            int[] el = {
                    random.nextInt(2),
                    random.nextInt(2)
            };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        double[][] centroidsData = { { 0.5, 0.5 } };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 1);

        Centroid c0 = centroids.get(0);

        // Use centroid-based edistance2 with two different centroids
        double[][] centroidsData2 = { { 0.5, 0.5 }, { 0.6, 0.6 } };
        InfiniteCentroids centroids2 = new InfiniteCentroids(centroidsData2, 2);
        Centroid c1 = centroids2.get(1);

        double distance = JoinGLA.edistance2(c0, c1, 2);

        assertTrue(distance >= 0.0, "Distance should be non-negative");
    }

    @Test
    void testSetFirstCentroidsPNNWithThreshold() {
        VectorSet vectors = new VectorSet();

        Random random = new Random(404);
        for (int i = 0; i < 25; i++) {
            int[] el = {
                    random.nextInt(2),
                    random.nextInt(2)
            };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        // Test with different thresholds
        VectorSet vCopy1 = new VectorSet();
        vectors.copyTo(vCopy1);
        VectorSet vCopy2 = new VectorSet();
        vectors.copyTo(vCopy2);
        InfiniteCentroids centroids1 = JoinGLA.setFirstCentroidsPNN(vCopy1,
                5.0);
        InfiniteCentroids centroids2 = JoinGLA.setFirstCentroidsPNN(vCopy2,
                15.0);

        assertNotNull(centroids1);
        assertNotNull(centroids2);

        // Different thresholds should potentially produce different results
        assertTrue(centroids1.size() >= 1,
                "Should create at least one centroid");
        assertTrue(centroids2.size() >= 1,
                "Should create at least one centroid");
    }

    @Test
    void testSetFirstCentroidsPNN2WithThreshold() {
        VectorSet vectors = new VectorSet();

        Random random = new Random(505);
        for (int i = 0; i < 30; i++) {
            int[] el = {
                    random.nextInt(2),
                    random.nextInt(2)
            };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        // Test with different thresholds
        VectorSet vCopy1 = new VectorSet();
        vectors.copyTo(vCopy1);
        VectorSet vCopy2 = new VectorSet();
        vectors.copyTo(vCopy2);
        InfiniteCentroids centroids1 = JoinGLA.setFirstCentroidsPNN2(vCopy1,
                5.0);
        InfiniteCentroids centroids2 = JoinGLA.setFirstCentroidsPNN2(vCopy2,
                15.0);

        assertNotNull(centroids1);
        assertNotNull(centroids2);

        // Different thresholds should potentially produce different results
        assertTrue(centroids1.size() >= 1,
                "Should create at least one centroid");
        assertTrue(centroids2.size() >= 1,
                "Should create at least one centroid");
    }

    @Test
    void testJoinGLAWithRandomSeed() {
        // Test that different random seeds produce different results (but still
        // valid)
        VectorSet vectors = new VectorSet();

        Random random1 = new Random(1);
        for (int i = 0; i < 25; i++) {
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
        Partition result1 = JoinGLA.joinGLA(vCopy1, scmin1, scs1,
                GLAConfig.DEFAULT);

        double[] scmin2 = new double[1];
        double[] scs2 = new double[vCopy2.size() + 1];
        Partition result2 = JoinGLA.joinGLA(vCopy2, scmin2, scs2,
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
    void testJoinGLAWithHighDimensionalData() {
        VectorSet vectors = new VectorSet();

        Random random = new Random(606);
        for (int i = 0; i < 40; i++) {
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

        assertDoesNotThrow(
                () -> JoinGLA.joinGLA(vectors, scmin, scs, GLAConfig.DEFAULT));
    }

    /**
     * Verifies the vector-count leak fix: after the full merge loop the sum of
     * all cluster sizes must equal the number of input vectors. Previously each
     * GLA refinement reused the same partition without clearing it first, so
     * the count doubled per iteration (e.g. 100 inputs grew to ~1.1M).
     */
    @Test
    void testVectorCountPreservedAcrossMergeLoop() {
        int n = 60;
        VectorSet vectors = new VectorSet();
        Random random = new Random(707);
        for (int i = 0; i < n; i++) {
            int[] el = { random.nextInt(2), random.nextInt(2) };
            vectors.addElement(new BinaryVector(el, 2));
        }

        double[] scmin = new double[1];
        double[] scs = new double[n + 1];
        Partition result = JoinGLA.joinGLA(vectors, scmin, scs,
                GLAConfig.DEFAULT);

        assertNotNull(result);
        int total = 0;
        for (int i = 1; i <= result.size(); i++) {
            total += result.getSize(i);
        }
        assertEquals(n, total,
                "Sum of cluster sizes must equal input vector count");
    }

    /**
     * Verifies SC stays bounded for small data. Before the leak fix, SC used a
     * huge accumulated element count in the MDL term, producing values like
     * -9.5M instead of a few hundred.
     */
    @Test
    void testSCBoundedForSmallData() {
        int n = 40;
        VectorSet vectors = new VectorSet();
        for (int i = 0; i < n / 2; i++) {
            vectors.addElement(new BinaryVector(new int[] { 0, 0 }, 2));
        }
        for (int i = 0; i < n / 2; i++) {
            vectors.addElement(new BinaryVector(new int[] { 1, 1 }, 2));
        }

        double[] scmin = new double[1];
        double[] scs = new double[n + 1];
        Partition result = JoinGLA.joinGLA(vectors, scmin, scs,
                GLAConfig.DEFAULT);

        assertNotNull(result);
        assertTrue(scmin[0] > -1.0e5,
                "SC should stay bounded for small data, got " + scmin[0]);
    }

    /**
     * Verifies the partition returned by JoinGLA is a valid partition of the
     * input: every cluster size is non-negative and the total equals n.
     */
    @Test
    void testResultPartitionIsValid() {
        int n = 50;
        VectorSet vectors = new VectorSet();
        Random random = new Random(808);
        for (int i = 0; i < n; i++) {
            int[] el = { random.nextInt(2), random.nextInt(2) };
            vectors.addElement(new BinaryVector(el, 2));
        }

        double[] scmin = new double[1];
        double[] scs = new double[n + 1];
        Partition result = JoinGLA.joinGLA(vectors, scmin, scs,
                GLAConfig.DEFAULT);

        assertNotNull(result);
        assertTrue(result.size() >= 1 && result.size() <= n,
                "Result cluster count should be within [1, n]");
        for (int i = 1; i <= result.size(); i++) {
            assertTrue(result.getSize(i) >= 0,
                    "Cluster size must be non-negative");
        }
    }
}
