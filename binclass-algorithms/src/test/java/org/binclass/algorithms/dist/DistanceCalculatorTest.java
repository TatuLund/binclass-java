/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DistanceCalculator}.
 */
public class DistanceCalculatorTest {

    @Test
    public void testHammingDistanceIdenticalVectors() {
        // Create a centroid with all 1s (probability = 1.0)
        double[] el = { 1.0, 1.0, 1.0, 1.0, 1.0 };
        Centroid centroid = new Centroid(el, 5, 0);

        // Create a binary vector [1, 1, 1, 1, 1]
        BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 }, 5);

        int distance = DistanceCalculator.hammingDistance(vector, centroid);
        assertEquals(0, distance);
    }

    @Test
    public void testHammingDistanceCompletelyDifferentVectors() {
        // Create a centroid with all 0s (probability = 0.0)
        double[] el = { 0.0, 0.0, 0.0, 0.0, 0.0 };
        Centroid centroid = new Centroid(el, 5, 0);

        // Create a binary vector [1, 1, 1, 1, 1]
        BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 }, 5);

        int distance = DistanceCalculator.hammingDistance(vector, centroid);
        assertEquals(5, distance);
    }

    @Test
    public void testHammingDistancePartialMatch() {
        // Create a centroid with mixed probabilities
        double[] probs = { 0.9, 0.1, 0.8, 0.2, 0.7 };
        Centroid centroid = new Centroid(probs, 5, 0);

        // Round to nearest bit: [1, 0, 1, 0, 1]
        for (int i = 0; i < 5; i++) {
            double[] arr = centroid.getArray();
            arr[i] = probs[i];
        }

        // Create a binary vector that differs in 2 positions
        BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 0, 0 }, 5);

        int distance = DistanceCalculator.hammingDistance(vector, centroid);
        assertEquals(2, distance);
    }

    @Test
    public void testL1DistanceIdenticalVectors() {
        // Create a centroid with all 1s (probability = 1.0)
        double[] el = { 1.0, 1.0, 1.0, 1.0, 1.0 };
        Centroid centroid = new Centroid(el, 5, 0);

        // Create a binary vector [1, 1, 1, 1, 1]
        BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 }, 5);

        double distance = DistanceCalculator.l1Distance(vector, centroid);
        assertEquals(0.0, distance, 1e-9);
    }

    @Test
    public void testL1DistanceCompletelyDifferentVectors() {
        // Create a centroid with all 0s (probability = 0.0)
        double[] el = { 0.0, 0.0, 0.0, 0.0, 0.0 };
        Centroid centroid = new Centroid(el, 5, 0);

        // Create a binary vector [1, 1, 1, 1, 1]
        BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 }, 5);

        double distance = DistanceCalculator.l1Distance(vector, centroid);
        assertEquals(5.0, distance, 1e-9);
    }

    @Test
    public void testL2DistanceIdenticalVectors() {
        // Create a centroid with all 1s (probability = 1.0)
        double[] el = { 1.0, 1.0, 1.0, 1.0, 1.0 };
        Centroid centroid = new Centroid(el, 5, 0);

        // Create a binary vector [1, 1, 1, 1, 1]
        BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 }, 5);

        double distance = DistanceCalculator.l2Distance(vector, centroid);
        assertEquals(0.0, distance, 1e-9);
    }

    @Test
    public void testL2DistanceCompletelyDifferentVectors() {
        // Create a centroid with all 0s (probability = 0.0)
        double[] el = { 0.0, 0.0, 0.0, 0.0, 0.0 };
        Centroid centroid = new Centroid(el, 5, 0);

        // Create a binary vector [1, 1, 1, 1, 1]
        BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 }, 5);

        double distance = DistanceCalculator.l2Distance(vector, centroid);
        assertEquals(5.0, distance, 1e-9);
    }

    @Test
    public void testCodeLength() {
        // Create a centroid with all 1s (probability = 1.0)
        double[] el = { 1.0, 1.0, 1.0, 1.0, 1.0 };
        Centroid centroid = new Centroid(el, 5, 0);

        // Create a binary vector [1, 1, 1, 1, 1]
        BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 }, 5);

        double codelength = DistanceCalculator.codeLength(vector, centroid);
        assertTrue(codelength <= 0.0); // Should be negative (information
                                       // content)
    }

    @Test
    public void testCodeLength2() {
        // Create a centroid with weight > 0
        double[] el = { 1.0, 1.0, 1.0, 1.0, 1.0 };
        Centroid centroid = new Centroid(el, 5, 1);

        // Create a binary vector [1, 1, 1, 1, 1]
        BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 }, 5);

        double codelength2 = DistanceCalculator.codeLength2(vector, centroid);
        assertTrue(codelength2 <= 0.0); // Should be negative (information
                                        // content)
    }

    @Test
    public void testClassCodeLength() {
        // Create a partition with 2 classes
        Partition partition = new Partition(10);

        // Add vectors to class 1
        VectorSet class1 = partition.getElements(1);
        for (int i = 0; i < 5; i++) {
            BinaryVector vector = new BinaryVector(new int[] { 1, 0, 1, 0, 1 },
                    5);
            class1.addElement(vector);
        }

        // Add vectors to class 2
        VectorSet class2 = partition.getElements(2);
        for (int i = 0; i < 5; i++) {
            BinaryVector vector = new BinaryVector(new int[] { 0, 1, 0, 1, 0 },
                    5);
            class2.addElement(vector);
        }

        // Create centroids
        InfiniteCentroids centroids = new InfiniteCentroids(2, 5);
        Centroid centroid1 = centroids.get(0);
        for (int i = 0; i < 5; i++) {
            centroid1.set(i, 0.9);
        }

        Centroid centroid2 = centroids.get(1);
        for (int i = 0; i < 5; i++) {
            centroid2.set(i, 0.1);
        }

        double classCodeLength = DistanceCalculator.classCodeLength(partition,
                centroids, 1, 10);
        assertTrue(classCodeLength >= 0.0); // Should be positive (information
                                            // content)
    }

    @Test
    public void testAverageCodelength() {
        // Create a partition with 2 classes
        Partition partition = new Partition(10);

        // Add vectors to class 1
        VectorSet class1 = partition.getElements(1);
        for (int i = 0; i < 5; i++) {
            BinaryVector vector = new BinaryVector(new int[] { 1, 0, 1, 0, 1 },
                    5);
            class1.addElement(vector);
        }

        // Add vectors to class 2
        VectorSet class2 = partition.getElements(2);
        for (int i = 0; i < 5; i++) {
            BinaryVector vector = new BinaryVector(new int[] { 0, 1, 0, 1, 0 },
                    5);
            class2.addElement(vector);
        }

        // Create centroids
        InfiniteCentroids centroids = new InfiniteCentroids(2, 5);
        Centroid centroid1 = centroids.get(0);
        for (int i = 0; i < 5; i++) {
            centroid1.set(i, 0.9);
        }

        Centroid centroid2 = centroids.get(1);
        for (int i = 0; i < 5; i++) {
            centroid2.set(i, 0.1);
        }

        double avgCodelength = DistanceCalculator.averageCodelength(partition,
                centroids);
        assertTrue(avgCodelength >= 0.0); // Should be positive (information
                                          // content)
    }

    @Test
    public void testClassDistortion() {
        // Create a VectorSet with identical vectors
        VectorSet classVectors = new VectorSet();
        for (int i = 0; i < 5; i++) {
            BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 },
                    5);
            classVectors.addElement(vector);
        }

        // Create a centroid with all 1s
        double[] el = { 1.0, 1.0, 1.0, 1.0, 1.0 };
        Centroid centroid = new Centroid(el, 5, 0);

        double distortion = DistanceCalculator.classDistortion(classVectors,
                centroid);
        assertEquals(0.0, distortion, 1e-9);
    }

    @Test
    public void testOverallDistortion() {
        // Create a partition with 2 classes
        Partition partition = new Partition(10);

        // Add vectors to class 1
        VectorSet class1 = partition.getElements(1);
        for (int i = 0; i < 5; i++) {
            BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 },
                    5);
            class1.addElement(vector);
        }

        // Add vectors to class 2
        VectorSet class2 = partition.getElements(2);
        for (int i = 0; i < 5; i++) {
            BinaryVector vector = new BinaryVector(new int[] { 0, 0, 0, 0, 0 },
                    5);
            class2.addElement(vector);
        }

        // Create centroids
        InfiniteCentroids centroids = new InfiniteCentroids(2, 5);
        Centroid centroid1 = centroids.get(0);
        for (int i = 0; i < 5; i++) {
            centroid1.set(i, 1.0);
        }

        Centroid centroid2 = centroids.get(1);
        for (int i = 0; i < 5; i++) {
            centroid2.set(i, 0.0);
        }

        double distortion = DistanceCalculator.overallDistortion(partition,
                centroids);
        assertEquals(0.0, distortion, 1e-9);
    }

    @Test
    public void testClassMae() {
        // Create a partition with 2 classes
        Partition partition = new Partition(10);

        // Add vectors to class 1
        VectorSet class1 = partition.getElements(1);
        for (int i = 0; i < 5; i++) {
            BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 },
                    5);
            class1.addElement(vector);
        }

        // Create centroids
        InfiniteCentroids centroids = new InfiniteCentroids(2, 5);
        Centroid centroid1 = centroids.get(0);
        for (int i = 0; i < 5; i++) {
            centroid1.set(i, 1.0);
        }

        double mae = DistanceCalculator.classMae(partition, centroids, 1);
        assertEquals(0.0, mae, 1e-9);
    }

    @Test
    public void testOverallMae() {
        // Create a partition with 2 classes
        Partition partition = new Partition(10);

        // Add vectors to class 1
        VectorSet class1 = partition.getElements(1);
        for (int i = 0; i < 5; i++) {
            BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 },
                    5);
            class1.addElement(vector);
        }

        // Add vectors to class 2
        VectorSet class2 = partition.getElements(2);
        for (int i = 0; i < 5; i++) {
            BinaryVector vector = new BinaryVector(new int[] { 0, 0, 0, 0, 0 },
                    5);
            class2.addElement(vector);
        }

        // Create centroids
        InfiniteCentroids centroids = new InfiniteCentroids(2, 5);
        Centroid centroid1 = centroids.get(0);
        for (int i = 0; i < 5; i++) {
            centroid1.set(i, 1.0);
        }

        Centroid centroid2 = centroids.get(1);
        for (int i = 0; i < 5; i++) {
            centroid2.set(i, 0.0);
        }

        double mae = DistanceCalculator.overallMae(partition, centroids);
        assertEquals(0.0, mae, 1e-9);
    }

    @Test
    public void testClassMse() {
        // Create a partition with 2 classes
        Partition partition = new Partition(10);

        // Add vectors to class 1
        VectorSet class1 = partition.getElements(1);
        for (int i = 0; i < 5; i++) {
            BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 },
                    5);
            class1.addElement(vector);
        }

        // Create centroids
        InfiniteCentroids centroids = new InfiniteCentroids(2, 5);
        Centroid centroid1 = centroids.get(0);
        for (int i = 0; i < 5; i++) {
            centroid1.set(i, 1.0);
        }

        double mse = DistanceCalculator.classMse(partition, centroids, 1);
        assertEquals(0.0, mse, 1e-9);
    }

    @Test
    public void testOverallMse() {
        // Create a partition with 2 classes
        Partition partition = new Partition(10, 2);

        // Add vectors to class 1
        VectorSet class1 = partition.getElements(1);
        for (int i = 0; i < 5; i++) {
            BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 });
            class1.add(vector);
        }

        // Add vectors to class 2
        VectorSet class2 = partition.getElements(2);
        for (int i = 0; i < 5; i++) {
            BinaryVector vector = new BinaryVector(new int[] { 0, 0, 0, 0, 0 });
            class2.add(vector);
        }

        // Create centroids
        InfiniteCentroids centroids = new InfiniteCentroids(2);
        Centroid centroid1 = centroids.get(0);
        for (int i = 0; i < 5; i++) {
            centroid1.set(i, 1.0);
        }

        Centroid centroid2 = centroids.get(1);
        for (int i = 0; i < 5; i++) {
            centroid2.set(i, 0.0);
        }

        double mse = DistanceCalculator.overallMse(partition, centroids);
        assertEquals(0.0, mse, 1e-9);
    }

    @Test
    public void testUseClassWeights() {
        boolean useWeights = DistanceCalculator.useClassWeights();
        assertTrue(!useWeights); // Default should be false
    }

    @Test
    public void testNullVectorThrowsException() {
        Centroid centroid = new Centroid(new double[5], 5, 0);

        assertThrows(NullPointerException.class,
                () -> DistanceCalculator.hammingDistance(null, centroid));
    }

    @Test
    public void testNullCentroidThrowsException() {
        BinaryVector vector = new BinaryVector(new int[] { 1, 0, 1, 0, 1 });

        assertThrows(NullPointerException.class,
                () -> DistanceCalculator.hammingDistance(vector, null));
    }

    @Test
    public void testDivisionByZeroThrowsException() {
        // Create a valid partition with 1 class but no vectors in it
        Partition partition = new Partition(1);

        InfiniteCentroids centroids = new InfiniteCentroids(1, 5);

        assertThrows(ArithmeticException.class, () -> DistanceCalculator
                .averageCodelength(partition, centroids));
    }
}
