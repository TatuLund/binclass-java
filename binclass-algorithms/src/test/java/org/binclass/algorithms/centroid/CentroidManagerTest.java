/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.centroid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CentroidManager}.
 */
class CentroidManagerTest {

    @Test
    void testAllocateCentroid() {
        Centroid centroid = CentroidManager.allocateCentroid(5);

        assertEquals(5, centroid.getLength());
        assertEquals(0, centroid.getWeight()); // Weight should be 0
                                               // (uninitialized)
    }

    @Test
    void testAllocateCentroidInvalidLength() {
        assertThrows(IllegalArgumentException.class,
                () -> CentroidManager.allocateCentroid(0));

        assertThrows(IllegalArgumentException.class,
                () -> CentroidManager.allocateCentroid(-1));
    }

    @Test
    void testAllocateCentroids() {
        InfiniteCentroids centroids = CentroidManager.allocateCentroids(3, 5);

        assertEquals(3, centroids.size()); // Should have 3 centroids

        // Each centroid should have length 5
        for (int i = 0; i < 3; i++) {
            Centroid c = centroids.get(i);
            assertEquals(5, c.getLength());
        }
    }

    @Test
    void testAllocateCentroidsInvalidDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> CentroidManager.allocateCentroids(0, 5));

        assertThrows(IllegalArgumentException.class,
                () -> CentroidManager.allocateCentroids(3, 0));
    }

    @Test
    void testDeallocateCentroid() {
        Centroid centroid = CentroidManager.allocateCentroid(5);

        assertNotNull(centroid, "Allocated centroid should not be null");

        // Should not throw - just marks for GC in Java
        CentroidManager.deallocateCentroid(centroid);
    }

    @Test
    void testDeallocateCentroids() {
        InfiniteCentroids centroids = CentroidManager.allocateCentroids(3, 5);

        assertNotNull(centroids, "Allocated centroids should not be null");

        // Should not throw - just marks for GC in Java
        CentroidManager.deallocateCentroids(centroids);
    }

    @Test
    void testCopyCentroids() {
        // Create source centroids with specific values
        InfiniteCentroids source = new InfiniteCentroids(2, 3);

        Centroid centroid0 = source.get(0);
        double[] probs0 = { 0.9, 0.1, 0.8 };
        for (int i = 0; i < 3; i++) {
            centroid0.set(i, probs0[i]);
        }

        Centroid centroid1 = source.get(1);
        double[] probs1 = { 0.2, 0.7, 0.4 };
        for (int i = 0; i < 3; i++) {
            centroid1.set(i, probs1[i]);
        }

        // Copy centroids
        InfiniteCentroids dest = CentroidManager.copyCentroids(source);

        assertEquals(2, dest.size());

        // Verify values were copied correctly
        for (int i = 0; i < 3; i++) {
            assertEquals(probs0[i], dest.get(0).get(i), 1e-9);
            assertEquals(probs1[i], dest.get(1).get(i), 1e-9);
        }
    }

    @Test
    void testCopyCentroidsNullSource() {
        assertThrows(NullPointerException.class,
                () -> CentroidManager.copyCentroids(null));
    }

    @Test
    void testCalculateLogs() {
        // Create centroids with values that need log calculation
        InfiniteCentroids centroids = new InfiniteCentroids(2, 3);

        Centroid centroid0 = centroids.get(0);
        double[] probs0 = { 0.5, 0.8, 0.3 };
        for (int i = 0; i < 3; i++) {
            centroid0.set(i, probs0[i]);
        }

        Centroid centroid1 = centroids.get(1);
        double[] probs1 = { 0.2, 0.9, 0.6 };
        for (int i = 0; i < 3; i++) {
            centroid1.set(i, probs1[i]);
        }

        // Calculate logs - should not throw
        CentroidManager.calculateLogs(centroids);

        // Verify that log values were calculated (they're stored in the
        // centroid)
        // The exact values depend on the implementation, but we can verify they
        // exist
        for (int i = 0; i < 3; i++) {
            double log0I = centroid0.getLog0(i);
            double log1I = centroid0.getLog1(i);

            assertTrue(!Double.isNaN(log0I), "Log0 should not be NaN");
            assertTrue(!Double.isNaN(log1I), "Log1 should not be NaN");
        }
    }

    @Test
    void testCalculateLogsNullCentroids() {
        assertThrows(NullPointerException.class,
                () -> CentroidManager.calculateLogs(null));
    }

    @Test
    void testAllocateAndCopyWorkflow() {
        // Test a typical workflow: allocate, populate, copy

        // Allocate centroids
        InfiniteCentroids source = CentroidManager.allocateCentroids(2, 4);

        // Populate with values
        Centroid centroid0 = source.get(0);
        double[] probs0 = { 0.8, 0.2, 0.9, 0.1 };
        for (int i = 0; i < 4; i++) {
            centroid0.set(i, probs0[i]);
        }

        Centroid centroid1 = source.get(1);
        double[] probs1 = { 0.3, 0.7, 0.4, 0.6 };
        for (int i = 0; i < 4; i++) {
            centroid1.set(i, probs1[i]);
        }

        // Copy centroids
        InfiniteCentroids dest = CentroidManager.copyCentroids(source);

        // Verify copy is independent - modify source and check dest remains
        // unchanged
        centroid0.set(0, 0.95);
        assertEquals(probs0[0], dest.get(0).get(0), 1e-9);
    }

    @Test
    void testCalculateLogsClamping() {
        // Create centroids with values at boundaries that need clamping
        InfiniteCentroids centroids = new InfiniteCentroids(1, 2);

        Centroid centroid0 = centroids.get(0);
        double[] probs0 = { 0.0, 1.0 }; // At boundaries
        for (int i = 0; i < 2; i++) {
            centroid0.set(i, probs0[i]);
        }

        // Calculate logs - should clamp to epsilon range
        CentroidManager.calculateLogs(centroids);

        // Verify that values were clamped and logs are valid
        for (int i = 0; i < 2; i++) {
            double log0I = centroid0.getLog0(i);
            double log1I = centroid0.getLog1(i);

            assertTrue(!Double.isNaN(log0I),
                    "Log0 should not be NaN after clamping");
            assertTrue(!Double.isNaN(log1I),
                    "Log1 should not be NaN after clamping");
        }
    }

    @Test
    void testMultipleAllocateDeallocate() {
        // Test multiple allocation and deallocation cycles

        for (int i = 0; i < 5; i++) {
            Centroid centroid = CentroidManager.allocateCentroid(3);
            assertEquals(3, centroid.getLength());

            InfiniteCentroids centroids = CentroidManager.allocateCentroids(2,
                    4);
            assertEquals(2, centroids.size());

            CentroidManager.deallocateCentroid(centroid);
            CentroidManager.deallocateCentroids(centroids);
        }
    }

    @Test
    void testCopyCentroidsPreservesWeights() {
        // Create source centroids with specific weights
        InfiniteCentroids source = new InfiniteCentroids(2, 3);

        Centroid centroid0 = source.get(0);
        double[] probs0 = { 0.5, 0.8, 0.3 };
        for (int i = 0; i < 3; i++) {
            centroid0.set(i, probs0[i]);
        }
        // Set weight on first centroid
        source.get(0).setWeight(10);

        Centroid centroid1 = source.get(1);
        double[] probs1 = { 0.2, 0.7, 0.4 };
        for (int i = 0; i < 3; i++) {
            centroid1.set(i, probs1[i]);
        }

        // Copy centroids
        InfiniteCentroids dest = CentroidManager.copyCentroids(source);

        // Verify weights were preserved
        assertEquals(10.0, dest.get(0).getWeight(), 1e-9);
    }
}
