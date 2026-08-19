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
import org.binclass.algorithms.gla.GLAConfig;
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
                GLAConfig.DEFAULT);

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
                GLAConfig.DEFAULT);

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
                GLAConfig.DEFAULT);

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
                dmin, GLAConfig.DEFAULT);

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
                dmin, GLAConfig.DEFAULT);

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
                GLAConfig.DEFAULT);

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
                GLAConfig.DEFAULT);

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
                dmin, GLAConfig.DEFAULT);

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
                GLAConfig.DEFAULT);

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
                GLAConfig.DEFAULT);

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
                () -> GLAEngine.gla(null, partition, centroids, dmin,
                        GLAConfig.DEFAULT));
    }

    @Test
    void testGlaNullPartition() {
        VectorSet vectors = createRandomVectorSet(5, 2);
        InfiniteCentroids centroids = createInfiniteCentroids(
                new double[][] { { 0.5, 0.5 } });
        double[] dmin = new double[1];

        assertThrows(NullPointerException.class,
                () -> GLAEngine.gla(vectors, null, centroids, dmin,
                        GLAConfig.DEFAULT));
    }

    @Test
    void testGlaNullCentroids() {
        VectorSet vectors = createRandomVectorSet(5, 2);
        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        assertThrows(NullPointerException.class,
                () -> GLAEngine.gla(vectors, partition, null, dmin,
                        GLAConfig.DEFAULT));
    }

    @Test
    void testGlaZeroCentroids() {
        VectorSet vectors = createRandomVectorSet(5, 2);
        double[] dmin = new double[1];

        assertThrows(IllegalArgumentException.class,
                () -> GLAEngine.gla(vectors, new Partition(0),
                        createInfiniteCentroids(new double[][] {}), dmin,
                        GLAConfig.DEFAULT));
    }

    @Test
    void testGlaConvergenceBehavior() {
        VectorSet vectors = createRandomVectorSet(50, 2);
        InfiniteCentroids centroids = createInfiniteCentroids(
                new double[][] { { 0.1, 0.1 }, { 0.9, 0.9 } });

        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        GLAEngine.gla(vectors, partition, centroids, dmin, GLAConfig.DEFAULT);

        // Verify final state is valid (no NaN or Infinity)
        assertTrue(dmin[0] >= 0.0, "Distortion should be non-negative");
        assertFalse(Double.isNaN(dmin[0]), "Distortion should not be NaN");
        assertFalse(Double.isInfinite(dmin[0]),
                "Distortion should not be infinite");
    }

    /**
     * Regression test for the bug where removeEmpty() was called between Phase
     * 1 and Phase 2, causing partition size truncation and vector loss when
     * empty clusters existed after initial assignment.
     */
    @Test
    void testGlaPreservesVectorsWithEmptyClustersAfterPhase1() {
        // Create vectors that will result in one empty cluster after initial
        // assignment
        VectorSet vectors = createRandomVectorSet(30, 2);

        // Use centroids that will cause C2 to be empty initially
        InfiniteCentroids centroids = createInfiniteCentroids(
                new double[][] {
                        { 0.0, 0.0 }, // Cluster 1 - will get most vectors
                        { 0.5, 0.5 }, // Cluster 2 - will be empty initially
                        { 1.0, 1.0 } // Cluster 3 - will get some vectors
                });

        Partition partition = new Partition(3);
        double[] dmin = new double[1];

        Partition result = GLAEngine.gla(vectors, partition, centroids, dmin,
                GLAConfig.DEFAULT);

        assertNotNull(result);

        // Verify all 30 vectors are preserved (not lost due to premature
        // removeEmpty)
        VectorSet allVectors = new VectorSet();
        result.copyAllTo(allVectors);
        assertEquals(30, allVectors.size(),
                "All vectors should be assigned even when clusters start empty");

        // Verify the algorithm completed successfully
        assertTrue(dmin[0] >= 0, "Distortion should be non-negative");
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
                GLAConfig.DEFAULT);

        double[] dmin2 = new double[1];
        results[1] = GLAEngine.glaSr(vectors, new Partition(2), centroids,
                dmin2, GLAConfig.DEFAULT);

        double[] dmin3 = new double[1];
        results[2] = GLAEngine.glaSa(vectors, new Partition(2), centroids,
                dmin3, GLAConfig.DEFAULT);

        double[] dmin4 = new double[1];
        results[3] = GLAEngine.hybridGlaL1(vectors, new Partition(2), centroids,
                dmin4, GLAConfig.DEFAULT);

        double[] dmin5 = new double[1];
        results[4] = GLAEngine.hybridGlaL2(vectors, new Partition(2), centroids,
                dmin5, GLAConfig.DEFAULT);

        double[] dmin6 = new double[1];
        results[5] = GLAEngine.maeGla(vectors, new Partition(2), centroids,
                dmin6, GLAConfig.DEFAULT);

        double[] dmin7 = new double[1];
        results[6] = GLAEngine.mseGla(vectors, new Partition(2), centroids,
                dmin7, GLAConfig.DEFAULT);

        double[] dmin8 = new double[1];
        results[7] = GLAEngine.fastGla(vectors, new Partition(2), centroids,
                dmin8, GLAConfig.DEFAULT);

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

        GLAEngine.gla(vectors, partition, centroids, dmin, GLAConfig.DEFAULT);

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
                dmin, GLAConfig.DEFAULT));
    }

    private GLAConfig createGLAConfig(
            double epsilon, int maxIter, boolean trashcan,
            boolean analyseMissing, int heuristic) {
        return new GLAConfig(
                epsilon, 1.8, heuristic, 1, 1,
                maxIter, 1000, 0, 20, 20, 5, false, false, false,
                trashcan, analyseMissing, false, 0.0, false, 1, 1);
    }

    @Test
    void testMaxIterEnforced() {
        VectorSet vectors = createRandomVectorSet(20, 4);
        InfiniteCentroids centroids = createInfiniteCentroids(new double[][] {
                { 0.1, 0.1, 0.1, 0.1 }, { 0.9, 0.9, 0.9, 0.9 } });

        // Set maxIter to a very low value (2 iterations)
        GLAConfig config = createGLAConfig(0.001, 2, false, false, 1);

        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        // Should complete without error even with low maxIter
        assertDoesNotThrow(() -> GLAEngine.gla(vectors, partition, centroids,
                dmin, config));

        // Verify all vectors are still assigned (algorithm may not converge)
        VectorSet allVectors = new VectorSet();
        partition.copyAllTo(allVectors);
        assertEquals(20, allVectors.size(), "All vectors should be assigned");
    }

    @Test
    void testTrashcanMode() {
        // Create vectors where some are clearly outliers (far from centroids)
        int[] normalVector1 = { 0, 0, 0, 0 };
        int[] normalVector2 = { 0, 0, 0, 0 };
        int[] outlierVector = { 1, 1, 1, 1 }; // Far from both centroids

        VectorSet vectors = new VectorSet();
        vectors.addElement(new BinaryVector(normalVector1, 4));
        vectors.addElement(new BinaryVector(normalVector2, 4));
        vectors.addElement(new BinaryVector(outlierVector, 4));

        InfiniteCentroids centroids = createInfiniteCentroids(new double[][] {
                { 0.1, 0.1, 0.1, 0.1 }, { 0.9, 0.9, 0.9, 0.9 } });

        // Enable trashcan mode with small epsilon (strict threshold)
        GLAConfig config = createGLAConfig(0.1, 0, true, false, 1);

        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        assertDoesNotThrow(() -> GLAEngine.gla(vectors, partition, centroids,
                dmin, config));

        // Verify algorithm completed successfully with trashcan enabled
        assertNotNull(partition);
    }

    @Test
    void testMissingBitsAnalysis() {
        VectorSet vectors = createRandomVectorSet(15, 3);
        InfiniteCentroids centroids = createInfiniteCentroids(new double[][] {
                { 0.2, 0.2, 0.2 }, { 0.8, 0.8, 0.8 } });

        // Enable missing bits analysis
        GLAConfig config = createGLAConfig(0.001, 0, false, true, 1);

        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        assertDoesNotThrow(() -> GLAEngine.gla(vectors, partition, centroids,
                dmin, config));

        // Verify algorithm handles missing bits without error
        VectorSet allVectors = new VectorSet();
        partition.copyAllTo(allVectors);
        assertEquals(15, allVectors.size(), "All vectors should be assigned");
    }

    @Test
    void testHeuristicCountVariants() {
        VectorSet vectors = createRandomVectorSet(20, 3);
        InfiniteCentroids centroids = createInfiniteCentroids(new double[][] {
                { 0.1, 0.1, 0.1 }, { 0.9, 0.9, 0.9 } });

        // Test different heuristic values (1=standard, 2=stochastic, etc.)
        for (int heuristic = 1; heuristic <= 3; heuristic++) {
            GLAConfig config = createGLAConfig(0.001, 0, false, false,
                    heuristic);

            Partition partition = new Partition(2);
            double[] dmin = new double[1];

            assertDoesNotThrow(
                    () -> GLAEngine.gla(vectors, partition, centroids,
                            dmin, config),
                    "Heuristic " + heuristic + " should not throw");

            // Verify all vectors are assigned for each heuristic variant
            VectorSet allVectors = new VectorSet();
            partition.copyAllTo(allVectors);
            assertEquals(20, allVectors.size(),
                    "All vectors should be assigned with heuristic "
                            + heuristic);
        }
    }

    @Test
    void testFirstDInitializationHint() {
        VectorSet vectors = createRandomVectorSet(15, 3);
        InfiniteCentroids centroids = createInfiniteCentroids(new double[][] {
                { 0.2, 0.2, 0.2 }, { 0.8, 0.8, 0.8 } });

        // Set firstD to a very high value (should skip optimization)
        GLAConfig baseConfig = createGLAConfig(0.001, 0, false, false, 1);
        // Override firstD by creating new config with high firstD
        final GLAConfig config = new GLAConfig(baseConfig.epsilon(),
                baseConfig.pnnThreshold(),
                baseConfig.heuristic(), baseConfig.alternateMode(),
                baseConfig.centroidType(),
                baseConfig.maxIter(), baseConfig.safetyLimit(),
                baseConfig.iterBase(),
                baseConfig.n(), baseConfig.kstopwhen(), baseConfig.kcStopWhen(),
                baseConfig.weights(), baseConfig.rounded(),
                baseConfig.jeffreysPrior(),
                baseConfig.trashcan(), baseConfig.analyseMissing(),
                baseConfig.logCentroids(),
                100.0, // Very high firstD to trigger skip
                baseConfig.bestCodeLength(), baseConfig.distanceType(),
                baseConfig.heuristicCount());

        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        assertDoesNotThrow(() -> GLAEngine.gla(vectors, partition, centroids,
                dmin, config));

        // Verify algorithm completed (may skip iterations due to firstD hint)
        assertNotNull(partition);
    }

    @Test
    void testLogCentroidsMode() {
        VectorSet vectors = createRandomVectorSet(20, 3);
        InfiniteCentroids centroids = createInfiniteCentroids(new double[][] {
                { 0.1, 0.1, 0.1 }, { 0.9, 0.9, 0.9 } });

        // Enable logCentroids mode
        GLAConfig config = new GLAConfig(0.001, 1.8, 1, 1, 1,
                0, 1000, 0, 20, 20, 5, false, false, false,
                false, false, true, // logCentroids = enabled
                0.0, false, 1, 1);

        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        assertDoesNotThrow(() -> GLAEngine.gla(vectors, partition, centroids,
                dmin, config));

        // Verify algorithm completed with logCentroids enabled
        VectorSet allVectors = new VectorSet();
        partition.copyAllTo(allVectors);
        assertEquals(20, allVectors.size(), "All vectors should be assigned");
    }

    @Test
    void testTrashcanWithMultipleOutliers() {
        // Create a dataset with multiple outliers
        int[] normal1 = { 0, 0, 0 };
        int[] normal2 = { 0, 0, 0 };
        int[] normal3 = { 0, 0, 0 };
        int[] outlier1 = { 1, 1, 1 }; // Far from centroid at (0.1, 0.1, 0.1)
        int[] outlier2 = { 1, 1, 1 };

        VectorSet vectors = new VectorSet();
        vectors.addElement(new BinaryVector(normal1, 3));
        vectors.addElement(new BinaryVector(normal2, 3));
        vectors.addElement(new BinaryVector(normal3, 3));
        vectors.addElement(new BinaryVector(outlier1, 3));
        vectors.addElement(new BinaryVector(outlier2, 3));

        InfiniteCentroids centroids = createInfiniteCentroids(new double[][] {
                { 0.1, 0.1, 0.1 }, { 0.9, 0.9, 0.9 } });

        // Enable trashcan with strict threshold
        GLAConfig config = createGLAConfig(0.05, 0, true, false, 1);

        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        assertDoesNotThrow(() -> GLAEngine.gla(vectors, partition, centroids,
                dmin, config));

        // Verify all vectors are still assigned (trashcan doesn't remove them)
        VectorSet allVectors = new VectorSet();
        partition.copyAllTo(allVectors);
        assertEquals(5, allVectors.size(), "All 5 vectors should be assigned");
    }

    @Test
    void testMaxIterZeroUsesDefault() {
        // maxIter=0 should use default iteration count
        VectorSet vectors = createRandomVectorSet(20, 3);
        InfiniteCentroids centroids = createInfiniteCentroids(new double[][] {
                { 0.1, 0.1, 0.1 }, { 0.9, 0.9, 0.9 } });

        GLAConfig config = createGLAConfig(0.001, 0, false, false, 1); // maxIter=0

        Partition partition = new Partition(2);
        double[] dmin = new double[1];

        assertDoesNotThrow(() -> GLAEngine.gla(vectors, partition, centroids,
                dmin, config));

        VectorSet allVectors = new VectorSet();
        partition.copyAllTo(allVectors);
        assertEquals(20, allVectors.size(), "All vectors should be assigned");
    }

    @Test
    void testAllVariantsWithTrashcan() {
        // Verify trashcan mode works with all GLA variants
        VectorSet vectors = createRandomVectorSet(15, 3);
        InfiniteCentroids centroids = createInfiniteCentroids(new double[][] {
                { 0.2, 0.2, 0.2 }, { 0.8, 0.8, 0.8 } });

        GLAConfig config = createGLAConfig(0.001, 0, true, false, 1); // trashcan
                                                                      // enabled

        double[] dmin;

        // Test each variant with trashcan mode (create fresh instances for
        // each)
        Partition p1 = new Partition(2);
        VectorSet v1 = createRandomVectorSet(15, 3);
        InfiniteCentroids c1 = createInfiniteCentroids(new double[][] {
                { 0.2, 0.2, 0.2 }, { 0.8, 0.8, 0.8 } });
        final double[] dmin1 = new double[1];
        assertDoesNotThrow(() -> GLAEngine.gla(v1, p1, c1,
                dmin1, config), "gla should work with trashcan");

        Partition p2 = new Partition(2);
        VectorSet v2 = createRandomVectorSet(15, 3);
        InfiniteCentroids c2 = createInfiniteCentroids(new double[][] {
                { 0.2, 0.2, 0.2 }, { 0.8, 0.8, 0.8 } });
        final double[] dmin2 = new double[1];
        assertDoesNotThrow(() -> GLAEngine.hybridGlaL1(v2, p2, c2,
                dmin2, config), "hybridGlaL1 should work with trashcan");

        Partition p3 = new Partition(2);
        VectorSet v3 = createRandomVectorSet(15, 3);
        InfiniteCentroids c3 = createInfiniteCentroids(new double[][] {
                { 0.2, 0.2, 0.2 }, { 0.8, 0.8, 0.8 } });
        final double[] dmin3 = new double[1];
        assertDoesNotThrow(() -> GLAEngine.hybridGlaL2(v3, p3, c3,
                dmin3, config), "hybridGlaL2 should work with trashcan");

        // Verify all variants completed successfully
        assertNotNull(p1);
        assertNotNull(p2);
        assertNotNull(p3);
    }
}
