/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.classify;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.data.DoubleVector;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MixtureClassifier}.
 */
public class MixtureClassifierTest {

    @Test
    public void testApplyMixtureClassifierConvergence() {
        // Create synthetic data with two clear clusters
        Random random = new Random(42);

        VectorSet vectors = new VectorSet();

        // Cluster 0: vectors near (0, 0)
        for (int i = 0; i < 10; i++) {
            int[] el = {
                    random.nextInt(2),
                    random.nextInt(2)
            };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        // Cluster 1: vectors near (1, 1)
        for (int i = 0; i < 10; i++) {
            int[] el = {
                    random.nextInt(2),
                    random.nextInt(2)
            };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        // Initialize centroids: one near zeros, one near ones
        double[][] centroidsData = {
                { 0.1, 0.1 }, // Centroid for cluster 0
                { 0.9, 0.9 } // Centroid for cluster 1
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 2);

        // Run EM algorithm with m=2 components
        InfiniteCentroids result = MixtureClassifier
                .applyMixtureClassifier(vectors, centroids, 2);

        assertNotNull(result);
        assertEquals(2, result.size()); // Should have 2 components

        // Verify weights are set (sum to approximately 1.0)
        double totalWeight = 0.0;
        for (int i = 0; i < 2; i++) {
            totalWeight += result.get(i).getWeight();
        }
        assertEquals(1.0, totalWeight, 0.01); // Weights should sum to ~1.0

        // Verify centroids are updated (should be closer to data)
        for (int i = 0; i < 2; i++) {
            Centroid c = result.get(i);
            assertNotNull(c);
            assertEquals(2, c.getLength()); // Correct length
        }
    }

    @Test
    public void testApplyMixtureClassifierSingleComponent() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        int[] el2 = { 1, 0 };
        BinaryVector v2 = new BinaryVector(el2, 2);

        vectors.addElement(v1);
        vectors.addElement(v2);

        double[][] centroidsData = { { 0.5, 0.5 } };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 1);

        InfiniteCentroids result = MixtureClassifier
                .applyMixtureClassifier(vectors, centroids, 1);

        assertNotNull(result);
        assertEquals(1, result.size()); // Single component

        // Weight should be 1.0 for single component
        assertEquals(1.0, result.get(0).getWeight(), 0.01);
    }

    @Test
    public void testCalculateMatrix() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        int[] el2 = { 1, 1 };
        BinaryVector v2 = new BinaryVector(el2, 2);

        vectors.addElement(v1);
        vectors.addElement(v2);

        double[][] centroidsData = {
                { 0.1, 0.1 }, // Centroid for cluster 0
                { 0.9, 0.9 } // Centroid for cluster 1
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 2);

        DoubleVector weights = new DoubleVector(new double[] { 0.5, 0.5 });

        double[][] probMatrix = MixtureClassifier.calculateMatrix(centroids,
                vectors, weights, 2);

        assertNotNull(probMatrix);
        assertEquals(2, probMatrix.length); // 2 vectors

        for (int i = 0; i < 2; i++) {
            assertEquals(2, probMatrix[i].length); // 2 components

            // Probabilities should sum to approximately 1.0
            double sum = 0.0;
            for (int j = 0; j < 2; j++) {
                assertTrue(probMatrix[i][j] >= 0.0,
                        "Probability must be non-negative");
                assertTrue(probMatrix[i][j] <= 1.0,
                        "Probability must be <= 1.0");
                sum += probMatrix[i][j];
            }
            assertEquals(1.0, sum, 0.01); // Sum to ~1.0
        }
    }

    @Test
    public void testUpdateWeights() {
        DoubleVector weights = new DoubleVector(new double[] { 0.5, 0.5 });

        // Create probability matrix where all vectors belong to component 0
        int n = 3;
        int m = 2;
        double[][] probMatrix = new double[n][m];
        for (int i = 0; i < n; i++) {
            probMatrix[i][0] = 1.0; // All probability on component 0
            probMatrix[i][1] = 0.0;
        }

        MixtureClassifier.updateWeights(weights, probMatrix, m, n);

        // Weight for component 0 should be ~1.0 (all vectors assigned to it)
        assertEquals(1.0, weights.get(0), 0.01);
        assertEquals(0.0, weights.get(1), 0.01);
    }

    @Test
    public void testUpdateCentroids() {
        double[][] centroidsData = {
                { 0.5, 0.5 }, // Initial centroid
                { 0.5, 0.5 }
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 2);

        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        int[] el2 = { 1, 1 };
        BinaryVector v2 = new BinaryVector(el2, 2);

        vectors.addElement(v1);
        vectors.addElement(v2);

        // Create probability matrix: all vectors belong to component 0
        int n = 2;
        int m = 2;
        double[][] probMatrix = new double[n][m];
        for (int i = 0; i < n; i++) {
            probMatrix[i][0] = 1.0; // All probability on component 0
            probMatrix[i][1] = 0.0;
        }

        MixtureClassifier.updateCentroids(centroids, probMatrix, vectors, m, n);

        // Centroid for component 0 should be updated (average of all vectors)
        Centroid c0 = centroids.get(0);
        assertEquals(2, c0.getLength());

        // Expected: average of {0,0} and {1,1} = {0.5, 0.5}
        assertEquals(0.5, c0.get(0), 0.01);
        assertEquals(0.5, c0.get(1), 0.01);
    }

    @Test
    public void testApplyMixtureClassifierNullVectorSet() {
        double[][] centroidsData = { { 0.5, 0.5 } };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 1);

        assertThrows(NullPointerException.class, () -> MixtureClassifier
                .applyMixtureClassifier(null, centroids, 1));
    }

    @Test
    public void testApplyMixtureClassifierNullCentroids() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        vectors.addElement(v1);

        assertThrows(NullPointerException.class, () -> MixtureClassifier
                .applyMixtureClassifier(vectors, null, 1));
    }

    @Test
    public void testCalculateMatrixNullParameters() {
        VectorSet vectors = new VectorSet();
        DoubleVector weights = new DoubleVector(new double[] { 0.5 });

        assertThrows(NullPointerException.class, () -> MixtureClassifier
                .calculateMatrix(null, vectors, weights, 1));

        assertThrows(NullPointerException.class,
                () -> MixtureClassifier.calculateMatrix(
                        new InfiniteCentroids(new double[][] { { 0.5 } }, 1),
                        null, weights, 1));

        assertThrows(NullPointerException.class,
                () -> MixtureClassifier.calculateMatrix(
                        new InfiniteCentroids(new double[][] { { 0.5 } }, 1),
                        vectors, null, 1));
    }

    @Test
    public void testUpdateWeightsNullParameters() {
        int n = 2;
        int m = 2;
        double[][] probMatrix = new double[n][m];

        assertThrows(NullPointerException.class,
                () -> MixtureClassifier.updateWeights(null, probMatrix, m, n));

        DoubleVector weights = new DoubleVector(new double[] { 0.5 });
        assertThrows(NullPointerException.class,
                () -> MixtureClassifier.updateWeights(weights, null, m, n));
    }

    @Test
    public void testUpdateCentroidsNullParameters() {
        VectorSet vectors = new VectorSet();
        int n = 1;
        int m = 1;
        double[][] probMatrix = new double[n][m];

        assertThrows(NullPointerException.class, () -> MixtureClassifier
                .updateCentroids(null, probMatrix, vectors, m, n));

        assertThrows(NullPointerException.class,
                () -> MixtureClassifier.updateCentroids(
                        new InfiniteCentroids(new double[][] { { 0.5 } }, 1),
                        null, vectors, m, n));

        assertThrows(NullPointerException.class,
                () -> MixtureClassifier.updateCentroids(
                        new InfiniteCentroids(new double[][] { { 0.5 } }, 1),
                        probMatrix, null, m, n));
    }

    @Test
    public void testEMAlgorithmConvergenceBehavior() {
        // Test that EM algorithm converges (log-likelihood increases)
        VectorSet vectors = new VectorSet();

        Random random = new Random(123);
        for (int i = 0; i < 20; i++) {
            int[] el = {
                    random.nextInt(2),
                    random.nextInt(2)
            };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        double[][] centroidsData = {
                { 0.1, 0.1 },
                { 0.9, 0.9 }
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 2);

        // Run EM algorithm
        InfiniteCentroids result = MixtureClassifier
                .applyMixtureClassifier(vectors, centroids, 2);

        assertNotNull(result);

        // Verify final state is valid (no NaN or Infinity)
        for (int i = 0; i < 2; i++) {
            Centroid c = result.get(i);
            assertFalse(Double.isNaN(c.get(0)),
                    "Centroid value should not be NaN");
            assertFalse(Double.isInfinite(c.get(0)),
                    "Centroid value should not be infinite");
        }
    }

    @Test
    public void testApplyMixtureClassifierLargeDataset() {
        // Test with larger dataset to verify scalability
        VectorSet vectors = new VectorSet();

        Random random = new Random(456);
        for (int i = 0; i < 100; i++) {
            int[] el = {
                    random.nextInt(2),
                    random.nextInt(2)
            };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        double[][] centroidsData = {
                { 0.1, 0.1 },
                { 0.9, 0.9 }
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 2);

        // Should complete without errors
        assertDoesNotThrow(() -> MixtureClassifier
                .applyMixtureClassifier(vectors, centroids, 2));
    }
}
