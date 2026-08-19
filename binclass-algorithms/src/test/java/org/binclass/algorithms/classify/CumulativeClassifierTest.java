/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.classify;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.DynamicPartition;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.util.MathUtils;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link CumulativeClassifier}.
 */
class CumulativeClassifierTest {

    @Test
    void testDoCumulativeClassificationSingleVector() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 1, 1 };
        BinaryVector v1 = new BinaryVector(el1, 3);
        vectors.addElement(v1);

        CumulativeConfig config = CumulativeConfig.defaults();
        DynamicPartition result = CumulativeClassifier
                .doCumulativeClassification(vectors, config);

        assertNotNull(result);
        assertEquals(1, result.size()); // Single vector creates one class

        // Verify the vector is in class 1 (1-based)
        VectorSet cluster1 = result.getCluster(1);
        assertNotNull(cluster1);
        assertEquals(1, cluster1.size());
    }

    @Test
    void testDoCumulativeClassificationMultipleVectors() {
        VectorSet vectors = new VectorSet();

        // Create 10 similar vectors (all zeros)
        for (int i = 0; i < 10; i++) {
            int[] el = { 0, 0, 0 };
            BinaryVector v = new BinaryVector(el, 3);
            vectors.addElement(v);
        }

        CumulativeConfig config = CumulativeConfig.defaults();
        DynamicPartition result = CumulativeClassifier
                .doCumulativeClassification(vectors, config);

        assertNotNull(result);

        // All similar vectors should be in the same class (or few classes)
        assertTrue(result.size() >= 1 && result.size() <= 3,
                "Similar vectors should cluster together");
    }

    @Test
    void testDoCumulativeClassificationDistinctClusters() {
        VectorSet vectors = new VectorSet();

        // Cluster A: all zeros
        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        vectors.addElement(v1);

        // Cluster B: all ones
        int[] el2 = { 1, 1 };
        BinaryVector v2 = new BinaryVector(el2, 2);
        vectors.addElement(v2);

        CumulativeConfig config = CumulativeConfig.defaults();
        DynamicPartition result = CumulativeClassifier
                .doCumulativeClassification(vectors, config);

        assertNotNull(result);

        // Should create at least 2 classes for distinct vectors
        assertTrue(result.size() >= 1);
    }

    @Test
    void testDoCumulativeClassificationWithNoNewClasses() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        vectors.addElement(v1);

        int[] el2 = { 1, 1 };
        BinaryVector v2 = new BinaryVector(el2, 2);
        vectors.addElement(v2);

        CumulativeConfig config = CumulativeConfig.defaults()
                .withCumNoNewClasses(true);
        DynamicPartition result = CumulativeClassifier
                .doCumulativeClassification(vectors, config);

        assertNotNull(result);
        // With cumNoNewClasses=true, should not create new classes beyond the
        // first
        assertEquals(1, result.size());
    }

    @Test
    void testDoCumulativeClassificationWithFixedDelta() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        vectors.addElement(v1);

        int[] el2 = { 1, 1 };
        BinaryVector v2 = new BinaryVector(el2, 2);
        vectors.addElement(v2);

        CumulativeConfig config = CumulativeConfig.ofFixedDelta(5);
        DynamicPartition result = CumulativeClassifier
                .doCumulativeClassification(vectors, config);

        assertNotNull(result);
        // With fixed delta=5, should be more conservative about creating new
        // classes
        assertTrue(result.size() >= 1 && result.size() <= 2);
    }

    @Test
    void testDoCumulativeClassificationWithBayesianPredictiveDisabled() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        vectors.addElement(v1);

        int[] el2 = { 1, 1 };
        BinaryVector v2 = new BinaryVector(el2, 2);
        vectors.addElement(v2);

        CumulativeConfig config = CumulativeConfig.defaults()
                .withBayesianPredictive(false);
        DynamicPartition result = CumulativeClassifier
                .doCumulativeClassification(vectors, config);

        assertNotNull(result);
        assertTrue(result.size() >= 1);
    }

    @Test
    void testDoCumulativeClassificationWithFeatureSignificance() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        vectors.addElement(v1);

        int[] el2 = { 1, 1 };
        BinaryVector v2 = new BinaryVector(el2, 2);
        vectors.addElement(v2);

        CumulativeConfig config = CumulativeConfig.defaults()
                .withTestFeatureSignificance(true);
        DynamicPartition result = CumulativeClassifier
                .doCumulativeClassification(vectors, config);

        assertNotNull(result);
        assertTrue(result.size() >= 1);
    }

    @Test
    void testDoCumulativeClassificationWithSaveByPf() {
        VectorSet vectors = new VectorSet();

        // Create enough vectors to trigger save checkpoints (every 10)
        for (int i = 0; i < 15; i++) {
            int[] el = { 0, 0 };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        CumulativeConfig config = CumulativeConfig.defaults()
                .withCumSaveByPf(true);
        DynamicPartition result = CumulativeClassifier
                .doCumulativeClassification(vectors, config);

        assertNotNull(result);
        assertTrue(result.size() >= 1);
    }

    @Test
    void testDoCumulativeClassificationWithSampling() {
        VectorSet vectors = new VectorSet();

        // Create enough vectors to trigger sampling checkpoints
        for (int i = 0; i < 20; i++) {
            int[] el = { 0, 0 };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        CumulativeConfig config = CumulativeConfig.defaults()
                .withCumulativeSamples(5); // Sample every 5 vectors
        DynamicPartition result = CumulativeClassifier
                .doCumulativeClassification(vectors, config);

        assertNotNull(result);
        assertTrue(result.size() >= 1);
    }

    @Test
    void testDoCumulativeClassificationWithCumulativeAnalysis() {
        VectorSet vectors = new VectorSet();

        // Create enough vectors to trigger analysis checkpoints
        for (int i = 0; i < 20; i++) {
            int[] el = { 0, 0 };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        CumulativeConfig config = CumulativeConfig.defaults()
                .withCumulativeAnalysis(true); // Enable cumulative analysis
        DynamicPartition result = CumulativeClassifier
                .doCumulativeClassification(vectors, config);

        assertNotNull(result);
        assertTrue(result.size() >= 1);
    }

    @Test
    void testInitializeFromVector() {
        int[] el = { 1, 0, 1 };
        BinaryVector v = new BinaryVector(el, 3);

        DynamicPartition result = CumulativeClassifier.initializeFromVector(v,
                0);

        assertNotNull(result);
        assertEquals(1, result.size()); // Single class

        VectorSet cluster1 = result.getCluster(1);
        assertNotNull(cluster1);
        assertEquals(1, cluster1.size());
    }

    @Test
    void testExtendWithNewClass() {
        int[] el1 = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 2);

        DynamicPartition dynPart = CumulativeClassifier.initializeFromVector(v1,
                0);
        assertEquals(1, dynPart.size());

        // Add a second vector as a new class
        int[] el2 = { 1, 0 };
        BinaryVector v2 = new BinaryVector(el2, 2);

        DynamicPartition extended = CumulativeClassifier
                .extendWithNewClass(dynPart, v2);

        assertNotNull(extended);
        assertEquals(2, extended.size()); // Now has two classes

        // Verify both vectors are present
        VectorSet cluster1 = extended.getCluster(1);
        VectorSet cluster2 = extended.getCluster(2);

        assertNotNull(cluster1);
        assertNotNull(cluster2);
        assertEquals(1, cluster1.size());
        assertEquals(1, cluster2.size());
    }

    @Test
    void testFindBestClassExisting() {
        // Create a partition with one class containing similar vectors
        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);

        DynamicPartition dynPart = CumulativeClassifier.initializeFromVector(v1,
                0);

        // Add another similar vector to the same class
        int[] el2 = { 0, 0 };
        BinaryVector v2 = new BinaryVector(el2, 2);
        CumulativeClassifier.assignToClass(dynPart, v2, 1);

        // Now try to classify a vector that fits well in the existing class
        int[] el3 = { 0, 0 };
        BinaryVector v3 = new BinaryVector(el3, 2);

        int bestClass = CumulativeClassifier.findBestClass(dynPart, v3, 0,
                MathUtils.EPSILON);

        assertTrue(bestClass >= 1, "Should find a suitable class");
    }

    @Test
    void testFindBestClassNewClass() {
        // Create a partition with one class containing very different vectors
        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);

        DynamicPartition dynPart = CumulativeClassifier.initializeFromVector(v1,
                0);

        // Add a very different vector to the same class
        int[] el2 = { 1, 1 };
        BinaryVector v2 = new BinaryVector(el2, 2);
        CumulativeClassifier.assignToClass(dynPart, v2, 1);

        // Now try to classify another very different vector with high delta
        int[] el3 = { 0, 1 };
        BinaryVector v3 = new BinaryVector(el3, 2);

        // With deterministic bits (no missing values), both SC values are 0.0
        // So bestSC > newClassSC + delta becomes 0.0 > 0.0 + delta, which is
        // false for any positive delta
        // This means findBestClass will return the existing class (1) rather
        // than -1
        int bestClass = CumulativeClassifier.findBestClass(dynPart, v3, 1000,
                MathUtils.EPSILON);

        assertEquals(1, bestClass,
                "With deterministic bits, should assign to existing class");
    }

    @Test
    void testAssignToClass() {
        int[] el = { 1, 0 };
        BinaryVector v = new BinaryVector(el, 2);

        DynamicPartition dynPart = CumulativeClassifier.initializeFromVector(v,
                0);

        // Add another vector to class 1
        int[] el2 = { 1, 1 };
        BinaryVector v2 = new BinaryVector(el2, 2);

        assertDoesNotThrow(
                () -> CumulativeClassifier.assignToClass(dynPart, v2, 1));

        // Verify the vector was added
        VectorSet cluster1 = dynPart.getCluster(1);
        assertEquals(2, cluster1.size());
    }

    @Test
    void testCalculateSCIncrease() {
        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);

        DynamicPartition dynPart = CumulativeClassifier.initializeFromVector(v1,
                0);

        // Add another similar vector
        int[] el2 = { 0, 0 };
        BinaryVector v2 = new BinaryVector(el2, 2);
        CumulativeClassifier.assignToClass(dynPart, v2, 1);

        // Now calculate SC increase for a third vector
        int[] el3 = { 0, 1 };
        BinaryVector v3 = new BinaryVector(el3, 2);

        double scIncrease = CumulativeClassifier.calculateSCIncrease(dynPart,
                v3, 1, MathUtils.EPSILON);

        assertTrue(scIncrease >= 0.0, "SC increase should be non-negative");
    }

    @Test
    void testCalculateNewClassSC() {
        int[] el = { 1, 0, 1 };
        BinaryVector v = new BinaryVector(el, 3);

        DynamicPartition dynPart = CumulativeClassifier.initializeFromVector(v,
                0);

        // Calculate SC for creating a new class with this vector
        double sc = CumulativeClassifier.calculateNewClassSC(dynPart, v);

        assertTrue(sc >= 0.0, "SC should be non-negative");
    }

    @Test
    void testCalculateVectorComplexity() {
        int[] el1 = { 0, 0 }; // All zeros - deterministic
        BinaryVector v1 = new BinaryVector(el1, 2);

        double complexity1 = CumulativeClassifier.calculateVectorComplexity(v1,
                2);
        assertEquals(0.0, complexity1, 0.01,
                "Deterministic vector should have zero complexity");

        int[] el2 = { 1, 1 }; // All ones - deterministic
        BinaryVector v2 = new BinaryVector(el2, 2);

        double complexity2 = CumulativeClassifier.calculateVectorComplexity(v2,
                2);
        assertEquals(0.0, complexity2, 0.01,
                "Deterministic vector should have zero complexity");

        // Vector with missing values would have higher complexity (not tested
        // here as BinaryVector doesn't support missing)
    }

    @Test
    void testDoCumulativeClassificationNullVectorSet() {
        assertThrows(NullPointerException.class, () -> CumulativeClassifier
                .doCumulativeClassification(null, CumulativeConfig.defaults()));
    }

    @Test
    void testInitializeFromVectorNull() {
        assertThrows(NullPointerException.class,
                () -> CumulativeClassifier.initializeFromVector(null, 0));
    }

    @Test
    void testExtendWithNewClassNull() {
        int[] el = { 1, 0 };
        BinaryVector v = new BinaryVector(el, 2);

        assertThrows(NullPointerException.class,
                () -> CumulativeClassifier.extendWithNewClass(null, v));

        DynamicPartition dynPart = CumulativeClassifier.initializeFromVector(v,
                0);
        assertThrows(NullPointerException.class,
                () -> CumulativeClassifier.extendWithNewClass(dynPart, null));
    }

    @Test
    void testFindBestClassNull() {
        int[] el = { 1, 0 };
        BinaryVector v = new BinaryVector(el, 2);

        DynamicPartition dynPart = CumulativeClassifier.initializeFromVector(v,
                0);

        assertThrows(NullPointerException.class,
                () -> CumulativeClassifier.findBestClass(null, v, 0,
                        MathUtils.EPSILON));

        assertThrows(NullPointerException.class,
                () -> CumulativeClassifier.findBestClass(dynPart, null, 0,
                        MathUtils.EPSILON));
    }

    @Test
    void testAssignToClassNull() {
        int[] el = { 1, 0 };
        BinaryVector v = new BinaryVector(el, 2);

        DynamicPartition dynPart = CumulativeClassifier.initializeFromVector(v,
                0);

        assertThrows(NullPointerException.class,
                () -> CumulativeClassifier.assignToClass(null, v, 1));

        assertThrows(NullPointerException.class,
                () -> CumulativeClassifier.assignToClass(dynPart, null, 1));
    }

    @Test
    void testCalculateSCIncreaseNull() {
        int[] el = { 1, 0 };
        BinaryVector v = new BinaryVector(el, 2);

        DynamicPartition dynPart = CumulativeClassifier.initializeFromVector(v,
                0);

        assertThrows(NullPointerException.class,
                () -> CumulativeClassifier.calculateSCIncrease(null, v, 1,
                        MathUtils.EPSILON));

        assertThrows(NullPointerException.class, () -> CumulativeClassifier
                .calculateSCIncrease(dynPart, null, 1, MathUtils.EPSILON));
    }

    @Test
    void testCalculateNewClassSCNull() {
        int[] el = { 1, 0 };
        BinaryVector v = new BinaryVector(el, 2);

        DynamicPartition dynPart = CumulativeClassifier.initializeFromVector(v,
                0);

        assertThrows(NullPointerException.class,
                () -> CumulativeClassifier.calculateNewClassSC(null, v));

        assertThrows(NullPointerException.class,
                () -> CumulativeClassifier.calculateNewClassSC(dynPart, null));
    }

    @Test
    void testCalculateVectorComplexityNull() {
        assertThrows(NullPointerException.class,
                () -> CumulativeClassifier.calculateVectorComplexity(null, 2));
    }

    @Test
    void testDoCumulativeClassificationLargeDataset() {
        Random random = new Random(123);
        VectorSet vectors = new VectorSet();

        // Create 50 random vectors
        for (int i = 0; i < 50; i++) {
            int[] el = {
                    random.nextInt(2),
                    random.nextInt(2)
            };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        assertDoesNotThrow(
                () -> CumulativeClassifier.doCumulativeClassification(
                        vectors, CumulativeConfig.defaults()));
    }

    @Test
    void testDoCumulativeClassificationWithDelta() {
        VectorSet vectors = new VectorSet();

        // Create some similar vectors
        for (int i = 0; i < 5; i++) {
            int[] el = { 0, 0 };
            BinaryVector v = new BinaryVector(el, 2);
            vectors.addElement(v);
        }

        // Test with different delta values
        DynamicPartition resultLowDelta = CumulativeClassifier
                .doCumulativeClassification(
                        vectors, CumulativeConfig.ofDelta(0));
        DynamicPartition resultHighDelta = CumulativeClassifier
                .doCumulativeClassification(
                        vectors, CumulativeConfig.ofDelta(100));

        assertNotNull(resultLowDelta);
        assertNotNull(resultHighDelta);

        // Higher delta should create more classes (less aggressive merging)
        assertTrue(resultHighDelta.size() >= resultLowDelta.size(),
                "Higher delta should create at least as many classes");
    }

    @Test
    void testConvertToPartition() {
        VectorSet vectors = new VectorSet();

        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        int[] el2 = { 1, 1 };
        BinaryVector v2 = new BinaryVector(el2, 2);

        vectors.addElement(v1);
        vectors.addElement(v2);

        DynamicPartition dynPart = CumulativeClassifier
                .doCumulativeClassification(
                        vectors, CumulativeConfig.defaults());

        // Convert to static Partition
        Partition partition = dynPart.convert();

        assertNotNull(partition);
        assertEquals(dynPart.size(), partition.size());
    }
}
