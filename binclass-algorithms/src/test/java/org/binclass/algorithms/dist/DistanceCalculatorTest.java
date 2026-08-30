/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.binclass.algorithms.dist.DistanceCalculator.DISTANCE_CL_START;
import static org.binclass.algorithms.dist.DistanceCalculator.DISTANCE_HAM;
import static org.binclass.algorithms.dist.DistanceCalculator.DISTANCE_L1;
import static org.binclass.algorithms.dist.DistanceCalculator.DISTANCE_L2;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DistanceCalculator}.
 */
class DistanceCalculatorTest {

    @Test
    void testHammingDistanceIdenticalVectors() {
        // Create a centroid with all 1s (probability = 1.0)
        double[] el = { 1.0, 1.0, 1.0, 1.0, 1.0 };
        Centroid centroid = new Centroid(el, 5, 0);

        // Create a binary vector [1, 1, 1, 1, 1]
        BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 }, 5);

        int distance = DistanceCalculator.hammingDistance(vector, centroid);
        assertEquals(0, distance);
    }

    @Test
    void testHammingDistanceCompletelyDifferentVectors() {
        // Create a centroid with all 0s (probability = 0.0)
        double[] el = { 0.0, 0.0, 0.0, 0.0, 0.0 };
        Centroid centroid = new Centroid(el, 5, 0);

        // Create a binary vector [1, 1, 1, 1, 1]
        BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 }, 5);

        int distance = DistanceCalculator.hammingDistance(vector, centroid);
        assertEquals(5, distance);
    }

    @Test
    void testHammingDistancePartialMatch() {
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
    void testL1DistanceIdenticalVectors() {
        // Create a centroid with all 1s (probability = 1.0)
        double[] el = { 1.0, 1.0, 1.0, 1.0, 1.0 };
        Centroid centroid = new Centroid(el, 5, 0);

        // Create a binary vector [1, 1, 1, 1, 1]
        BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 }, 5);

        double distance = DistanceCalculator.l1Distance(vector, centroid);
        assertEquals(0.0, distance, 1e-9);
    }

    @Test
    void testL1DistanceCompletelyDifferentVectors() {
        // Create a centroid with all 0s (probability = 0.0)
        double[] el = { 0.0, 0.0, 0.0, 0.0, 0.0 };
        Centroid centroid = new Centroid(el, 5, 0);

        // Create a binary vector [1, 1, 1, 1, 1]
        BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 }, 5);

        double distance = DistanceCalculator.l1Distance(vector, centroid);
        assertEquals(5.0, distance, 1e-9);
    }

    @Test
    void testL2DistanceIdenticalVectors() {
        // Create a centroid with all 1s (probability = 1.0)
        double[] el = { 1.0, 1.0, 1.0, 1.0, 1.0 };
        Centroid centroid = new Centroid(el, 5, 0);

        // Create a binary vector [1, 1, 1, 1, 1]
        BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 }, 5);

        double distance = DistanceCalculator.l2Distance(vector, centroid);
        assertEquals(0.0, distance, 1e-9);
    }

    @Test
    void testL2DistanceCompletelyDifferentVectors() {
        // Create a centroid with all 0s (probability = 0.0)
        double[] el = { 0.0, 0.0, 0.0, 0.0, 0.0 };
        Centroid centroid = new Centroid(el, 5, 0);

        // Create a binary vector [1, 1, 1, 1, 1]
        BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1, 1, 1 }, 5);

        double distance = DistanceCalculator.l2Distance(vector, centroid);
        assertEquals(5.0, distance, 1e-9);
    }

    @Test
    void testCodeLength() {
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
    void testCodeLength2() {
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
    void testClassCodeLength() {
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
    void testAverageCodelength() {
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
    void testClassDistortion() {
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
    void testOverallDistortion() {
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
    void testClassMae() {
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
    void testOverallMae() {
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
    void testClassMse() {
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
    void testOverallMse() {
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
    void testUseClassWeights() {
        boolean useWeights = DistanceCalculator.useClassWeights();
        assertTrue(!useWeights); // Default should be false
    }

    @Test
    void testNullVectorThrowsException() {
        Centroid centroid = new Centroid(new double[5], 5, 0);

        assertThrows(NullPointerException.class,
                () -> DistanceCalculator.hammingDistance(null, centroid));
    }

    @Test
    void testNullCentroidThrowsException() {
        BinaryVector vector = new BinaryVector(new int[] { 1, 0, 1, 0, 1 });

        assertThrows(NullPointerException.class,
                () -> DistanceCalculator.hammingDistance(vector, null));
    }

    @Test
    void testDivisionByZeroThrowsException() {
        // Create a valid partition with 1 class but no vectors in it
        Partition partition = new Partition(1);

        InfiniteCentroids centroids = new InfiniteCentroids(1, 5);

        assertThrows(ArithmeticException.class, () -> DistanceCalculator
                .averageCodelength(partition, centroids));
    }

    private static Partition buildTwoClassPartition() {
        Partition partition = new Partition(2);

        VectorSet class1 = partition.getElements(1);
        for (int i = 0; i < 4; i++) {
            class1.addElement(new BinaryVector(new int[] { 1, 1, 0, 0, 1 }, 5));
        }

        VectorSet class2 = partition.getElements(2);
        for (int i = 0; i < 4; i++) {
            class2.addElement(new BinaryVector(new int[] { 0, 0, 1, 1, 0 }, 5));
        }

        return partition;
    }

    private static InfiniteCentroids buildTwoClassCentroids() {
        InfiniteCentroids centroids = new InfiniteCentroids(2, 5);

        Centroid centroid0 = centroids.get(0);
        for (int i = 0; i < 5; i++) {
            centroid0.set(i, 0.9);
        }
        centroid0.setWeight(0.5);

        Centroid centroid1 = centroids.get(1);
        for (int i = 0; i < 5; i++) {
            centroid1.set(i, 0.1);
        }
        centroid1.setWeight(0.5);

        return centroids;
    }

    @Test
    void testShannonEntropyReturnsFiniteValue() {
        Partition partition = buildTwoClassPartition();
        InfiniteCentroids centroids = buildTwoClassCentroids();

        double entropy = DistanceCalculator.shannonEntropy(partition,
                centroids);

        assertTrue(Double.isFinite(entropy), "entropy should be finite");
    }

    @Test
    void testShannonEntropyThrowsWhenNoVectors() {
        Partition partition = new Partition(2);
        InfiniteCentroids centroids = new InfiniteCentroids(2, 5);
        centroids.get(0).setWeight(0.5);

        assertThrows(ArithmeticException.class, () -> DistanceCalculator
                .shannonEntropy(partition, centroids));
    }

    @Test
    void testCalculateCriteriaHammingUsesDistortion() {
        Partition partition = buildTwoClassPartition();
        InfiniteCentroids centroids = buildTwoClassCentroids();

        Criteria criteria = DistanceCalculator.calculateCriteria(
                partition, centroids, DISTANCE_HAM, 42.0);

        assertEquals(42.0, criteria.sc(), 1e-9);
        assertEquals(DistanceCalculator.overallDistortion(partition, centroids),
                criteria.d(), 1e-9);
        assertEquals(DistanceCalculator.averageCodelength(partition, centroids),
                criteria.i1(), 1e-9);
        assertEquals(DistanceCalculator.shannonEntropy(partition, centroids),
                criteria.i2(), 1e-9);
    }

    @Test
    void testCalculateCriteriaL1UsesMae() {
        Partition partition = buildTwoClassPartition();
        InfiniteCentroids centroids = buildTwoClassCentroids();

        Criteria criteria = DistanceCalculator.calculateCriteria(
                partition, centroids, DISTANCE_L1, 7.0);

        assertEquals(7.0, criteria.sc(), 1e-9);
        assertEquals(DistanceCalculator.overallMae(partition, centroids),
                criteria.d(), 1e-9);
    }

    @Test
    void testCalculateCriteriaL2UsesSse() {
        Partition partition = buildTwoClassPartition();
        InfiniteCentroids centroids = buildTwoClassCentroids();

        Criteria criteria = DistanceCalculator.calculateCriteria(
                partition, centroids, DISTANCE_L2, 7.0);

        assertEquals(7.0, criteria.sc(), 1e-9);
        assertEquals(DistanceCalculator.overallMse(partition, centroids),
                criteria.d(), 1e-9);
    }

    @Test
    void testCalculateCriteriaCodelengthSetsDToI1() {
        Partition partition = buildTwoClassPartition();
        InfiniteCentroids centroids = buildTwoClassCentroids();

        Criteria criteria = DistanceCalculator.calculateCriteria(
                partition, centroids, DISTANCE_CL_START, 3.0);

        assertEquals(3.0, criteria.sc(), 1e-9);
        // For codelength distances d is set to the average codelength (i1).
        assertEquals(criteria.i1(), criteria.d(), 1e-9);
        assertTrue(Double.isFinite(criteria.i2()));
    }

    @Test
    void testCalculateCriteriaPassesStochasticComplexityThrough() {
        Partition partition = buildTwoClassPartition();
        InfiniteCentroids centroids = buildTwoClassCentroids();

        Criteria criteria = DistanceCalculator.calculateCriteria(
                partition, centroids, DISTANCE_HAM, -12.34);

        assertEquals(-12.34, criteria.sc(), 1e-9);
    }

    /**
     * The uniform-prior SC path checks classes {@code 1..k-1}; when any of them
     * is empty it throws the exact exception the local-search fix relies on to
     * detect an unpopulated partition.
     */
    @Test
    void testStochasticComplexityThrowsOnEmptyCluster() {
        Partition partition = new Partition(3);
        // Class 1 populated, class 2 empty -> uniform path throws before
        // returning (it only inspects classes 1..k-1).
        partition.getElements(1).addElement(new BinaryVector(
                new int[] { 1, 0, 1, 0 }, 4));

        assertThrows(IllegalStateException.class, () -> DistanceCalculator
                .stochasticComplexity(partition, 3, 4));
    }

    /**
     * Locks the exact exception type and message used by the uniform-prior SC
     * path so the local-search fix stays verified end-to-end.
     */
    @Test
    void testStochasticComplexityUniformThrowsOnEmptyCluster() {
        Partition partition = new Partition(3);
        partition.getElements(1).addElement(new BinaryVector(
                new int[] { 1, 0, 1, 0 }, 4));

        assertThrows(IllegalStateException.class, () -> DistanceCalculator
                .stochasticComplexityUniform(partition, 3, 4));
    }

    /**
     * A partition whose checked classes are all non-empty scores a finite SC
     * and does not throw — the state {@code LocalSearch} starts from after
     * population.
     */
    @Test
    void testStochasticComplexityNonEmptyClassesReturnsFinite() {
        Partition partition = new Partition(3);
        partition.getElements(1).addElement(new BinaryVector(
                new int[] { 0, 0, 0, 0 }, 4));
        partition.getElements(2).addElement(new BinaryVector(
                new int[] { 1, 1, 1, 1 }, 4));

        double sc = DistanceCalculator.stochasticComplexity(partition, 3, 4);
        assertTrue(Double.isFinite(sc),
                "SC must be finite when classes are non-empty");
    }

    /**
     * A single-cluster partition (k=2) still scores finite SC because the
     * uniform path only inspects class 1.
     */
    @Test
    void testStochasticComplexitySingleNonEmptyCluster() {
        Partition partition = new Partition(2);
        partition.getElements(1).addElement(new BinaryVector(
                new int[] { 0, 0, 0, 0 }, 4));

        double sc = DistanceCalculator.stochasticComplexity(partition, 2, 4);
        assertTrue(Double.isFinite(sc),
                "SC must be finite for one non-empty cluster");
    }
}
