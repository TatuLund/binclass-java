/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.gla;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GLAEngine}.
 */
class GLAEngineTest {

    private VectorSet createRandomVectorSet(int count, int length) {
        Random random = new Random(42);
        VectorSet vectors = new VectorSet();
        for (int i = 0; i < count; i++) {
            int[] el = new int[length];
            for (int j = 0; j < length; j++) {
                el[j] = random.nextInt(2);
            }
            BinaryVector v = new BinaryVector(el, length);
            vectors.addElement(v);
        }
        return vectors;
    }

    private InfiniteCentroids createInfiniteCentroids(double[][] data) {
        return new InfiniteCentroids(data, data.length);
    }

    @Test
    void testGlaStandard() {
        VectorSet vectors = createRandomVectorSet(20, 2);
        double[][] centroidsData = {
                { 0.1, 0.1 }, // Centroid for cluster 0
                { 0.9, 0.9 } // Centroid for cluster 1
        };
        InfiniteCentroids centroids = createInfiniteCentroids(centroidsData);

        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        Partition result = GLAEngine.gla(vectors, partition, centroids, dmin,
                vectors.size());

        assertNotNull(result);
        // Verify all 20 vectors are assigned to some cluster (not just checking
        // k)
        VectorSet allVectors = new VectorSet();
        result.copyAllTo(allVectors);
        assertEquals(20, allVectors.size(), "All vectors should be assigned");

        // Verify distortion is non-negative and finite
        assertTrue(dmin[0] >= 0.0, "Distortion should be non-negative");
        assertFalse(Double.isNaN(dmin[0]), "Distortion should not be NaN");
        assertFalse(Double.isInfinite(dmin[0]),
                "Distortion should not be infinite");
    }

    @Test
    void testGlaSr() {
        VectorSet vectors = createRandomVectorSet(15, 2);
        InfiniteCentroids centroids = createInfiniteCentroids(
                new double[][] { { 0.1, 0.1 }, { 0.9, 0.9 } });

        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        Partition result = GLAEngine.glaSr(vectors, partition, centroids, dmin,
                vectors.size());

        assertNotNull(result);
        VectorSet allVectors = new VectorSet();
        result.copyAllTo(allVectors);
        assertEquals(15, allVectors.size(), "All vectors should be assigned");
    }

    @Test
    void testGlaSa() {
        VectorSet vectors = createRandomVectorSet(20, 2);
        InfiniteCentroids centroids = createInfiniteCentroids(
                new double[][] { { 0.1, 0.1 }, { 0.9, 0.9 } });

        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        Partition result = GLAEngine.glaSa(vectors, partition, centroids, dmin,
                vectors.size());

        assertNotNull(result);
        VectorSet allVectors = new VectorSet();
        result.copyAllTo(allVectors);
        assertEquals(20, allVectors.size(), "All vectors should be assigned");
    }

    @Test
    void testHybridGlaL1() {
        VectorSet vectors = createRandomVectorSet(25, 2);
        InfiniteCentroids centroids = createInfiniteCentroids(
                new double[][] { { 0.1, 0.1 }, { 0.9, 0.9 } });

        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        Partition result = GLAEngine.hybridGlaL1(vectors, partition, centroids,
                dmin, vectors.size());

        assertNotNull(result);
        VectorSet allVectors = new VectorSet();
        result.copyAllTo(allVectors);
        assertEquals(25, allVectors.size(), "All vectors should be assigned");
    }

    @Test
    void testHybridGlaL2() {
        VectorSet vectors = createRandomVectorSet(30, 2);
        InfiniteCentroids centroids = createInfiniteCentroids(
                new double[][] { { 0.1, 0.1 }, { 0.9, 0.9 } });

        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        Partition result = GLAEngine.hybridGlaL2(vectors, partition, centroids,
                dmin, vectors.size());

        assertNotNull(result);
        VectorSet allVectors = new VectorSet();
        result.copyAllTo(allVectors);
        assertEquals(30, allVectors.size(), "All vectors should be assigned");
    }

    @Test
    void testMaeGla() {
        VectorSet vectors = createRandomVectorSet(18, 2);
        InfiniteCentroids centroids = createInfiniteCentroids(
                new double[][] { { 0.1, 0.1 }, { 0.9, 0.9 } });

        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        Partition result = GLAEngine.maeGla(vectors, partition, centroids, dmin,
                vectors.size());

        assertNotNull(result);
        VectorSet allVectors = new VectorSet();
        result.copyAllTo(allVectors);
        assertEquals(18, allVectors.size(), "All vectors should be assigned");
    }

    @Test
    void testMseGla() {
        VectorSet vectors = createRandomVectorSet(22, 2);
        InfiniteCentroids centroids = createInfiniteCentroids(
                new double[][] { { 0.1, 0.1 }, { 0.9, 0.9 } });

        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        Partition result = GLAEngine.mseGla(vectors, partition, centroids, dmin,
                vectors.size());

        assertNotNull(result);
        VectorSet allVectors = new VectorSet();
        result.copyAllTo(allVectors);
        assertEquals(22, allVectors.size(), "All vectors should be assigned");
    }

    @Test
    void testFastGla() {
        VectorSet vectors = createRandomVectorSet(16, 2);
        InfiniteCentroids centroids = createInfiniteCentroids(
                new double[][] { { 0.1, 0.1 }, { 0.9, 0.9 } });

        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        Partition result = GLAEngine.fastGla(vectors, partition, centroids,
                dmin, vectors.size());

        assertNotNull(result);
        VectorSet allVectors = new VectorSet();
        result.copyAllTo(allVectors);
        assertEquals(16, allVectors.size(), "All vectors should be assigned");
    }

    @Test
    void testGlaSingleCluster() {
        VectorSet vectors = createRandomVectorSet(10, 2);
        InfiniteCentroids centroids = createInfiniteCentroids(
                new double[][] { { 0.5, 0.5 } });

        Partition partition = new Partition(1);
        double[] dmin = new double[1];

        Partition result = GLAEngine.gla(vectors, partition, centroids, dmin,
                vectors.size());

        assertNotNull(result);
        VectorSet allVectors = new VectorSet();
        result.copyAllTo(allVectors);
        assertEquals(10, allVectors.size(),
                "All vectors should be assigned to single cluster");
    }

    @Test
    void testGlaThreeClusters() {
        VectorSet vectors = createRandomVectorSet(30, 2);
        InfiniteCentroids centroids = createInfiniteCentroids(
                new double[][] { { 0.0, 0.0 }, { 0.5, 0.5 }, { 1.0, 1.0 } });

        Partition partition = new Partition(3);
        double[] dmin = new double[1];

        Partition result = GLAEngine.gla(vectors, partition, centroids, dmin,
                vectors.size());

        assertNotNull(result);
        VectorSet allVectors = new VectorSet();
        result.copyAllTo(allVectors);
        assertEquals(30, allVectors.size(), "All vectors should be assigned");

        // Verify all clusters are represented (1-based) - check each cluster
        // has elements
        assertTrue(result.getSize(1) > 0, "Cluster 1 should have elements");
        assertTrue(result.getSize(2) > 0, "Cluster 2 should have elements");
        assertTrue(result.getSize(3) > 0, "Cluster 3 should have elements");
    }

    @Test
    void testGlaNullVectorSet() {
        Partition partition = new Partition(2);
        InfiniteCentroids centroids = createInfiniteCentroids(
                new double[][] { { 0.5, 0.5 } });
        double[] dmin = new double[1];

        assertThrows(NullPointerException.class,
                () -> GLAEngine.gla(null, partition, centroids, dmin, 10));
    }

    @Test
    void testGlaNullPartition() {
        VectorSet vectors = createRandomVectorSet(5, 2);
        InfiniteCentroids centroids = createInfiniteCentroids(
                new double[][] { { 0.5, 0.5 } });
        double[] dmin = new double[1];

        assertThrows(NullPointerException.class,
                () -> GLAEngine.gla(vectors, null, centroids, dmin, 10));
    }

    @Test
    void testGlaNullCentroids() {
        VectorSet vectors = createRandomVectorSet(5, 2);
        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        assertThrows(NullPointerException.class,
                () -> GLAEngine.gla(vectors, partition, null, dmin, 10));
    }

    @Test
    void testGlaZeroCentroids() {
        VectorSet vectors = createRandomVectorSet(5, 2);
        double[] dmin = new double[1];

        assertThrows(IllegalArgumentException.class,
                () -> GLAEngine.gla(vectors, new Partition(0),
                        createInfiniteCentroids(new double[][] {}), dmin,
                        vectors.size()));
    }

    @Test
    void testGlaConvergenceBehavior() {
        VectorSet vectors = createRandomVectorSet(50, 2);
        InfiniteCentroids centroids = createInfiniteCentroids(
                new double[][] { { 0.1, 0.1 }, { 0.9, 0.9 } });

        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        GLAEngine.gla(vectors, partition, centroids, dmin, vectors.size());

        // Verify final state is valid (no NaN or Infinity)
        assertTrue(dmin[0] >= 0.0, "Distortion should be non-negative");
        assertFalse(Double.isNaN(dmin[0]), "Distortion should not be NaN");
        assertFalse(Double.isInfinite(dmin[0]),
                "Distortion should not be infinite");
    }

    @Test
    void testAllVariantsProduceValidPartitions() {
        VectorSet vectors = createRandomVectorSet(25, 2);
        InfiniteCentroids centroids = createInfiniteCentroids(
                new double[][] { { 0.1, 0.1 }, { 0.9, 0.9 } });

        // Test each variant produces a valid partition with all vectors
        // assigned
        Partition[] results = new Partition[8];

        double[] dmin1 = new double[1];
        results[0] = GLAEngine.gla(vectors, new Partition(2), centroids, dmin1,
                vectors.size());

        double[] dmin2 = new double[1];
        results[1] = GLAEngine.glaSr(vectors, new Partition(2), centroids,
                dmin2, vectors.size());

        double[] dmin3 = new double[1];
        results[2] = GLAEngine.glaSa(vectors, new Partition(2), centroids,
                dmin3, vectors.size());

        double[] dmin4 = new double[1];
        results[3] = GLAEngine.hybridGlaL1(vectors, new Partition(2), centroids,
                dmin4, vectors.size());

        double[] dmin5 = new double[1];
        results[4] = GLAEngine.hybridGlaL2(vectors, new Partition(2), centroids,
                dmin5, vectors.size());

        double[] dmin6 = new double[1];
        results[5] = GLAEngine.maeGla(vectors, new Partition(2), centroids,
                dmin6, vectors.size());

        double[] dmin7 = new double[1];
        results[6] = GLAEngine.mseGla(vectors, new Partition(2), centroids,
                dmin7, vectors.size());

        double[] dmin8 = new double[1];
        results[7] = GLAEngine.fastGla(vectors, new Partition(2), centroids,
                dmin8, vectors.size());

        // Verify all results are valid and contain all vectors
        for (int i = 0; i < results.length; i++) {
            assertNotNull(results[i],
                    "GLA variant " + i + " should produce a result");
            VectorSet allVectors = new VectorSet();
            results[i].copyAllTo(allVectors);
            assertEquals(vectors.size(), allVectors.size(),
                    "GLA variant " + i + " should assign all vectors");
        }
    }

    @Test
    void testGlaWithDifferentVectorLengths() {
        VectorSet vectors = createRandomVectorSet(20, 4);
        InfiniteCentroids centroids = createInfiniteCentroids(new double[][] {
                { 0.1, 0.1, 0.1, 0.1 }, { 0.9, 0.9, 0.9, 0.9 } });

        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        GLAEngine.gla(vectors, partition, centroids, dmin, vectors.size());

        VectorSet allVectors = new VectorSet();
        partition.copyAllTo(allVectors);
        assertEquals(20, allVectors.size(),
                "All vectors should be assigned with different lengths");
    }

    @Test
    void testGlaLargeDataset() {
        VectorSet vectors = createRandomVectorSet(100, 2);
        InfiniteCentroids centroids = createInfiniteCentroids(
                new double[][] { { 0.1, 0.1 }, { 0.9, 0.9 } });

        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        assertDoesNotThrow(() -> GLAEngine.gla(vectors, partition, centroids,
                dmin, vectors.size()));
    }
}
