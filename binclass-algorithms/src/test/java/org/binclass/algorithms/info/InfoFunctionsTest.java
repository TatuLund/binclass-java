/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.info;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.dist.DistanceCalculator;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InfoFunctions} covering information-theoretic calculations.
 */
final class InfoFunctionsTest {

    @Test
    void a1WithProbabilities() {
        double[] probabilities = { 0.5, 0.3, 0.7 };

        double[] result = InfoFunctions.a1(probabilities);

        assertNotNull(result);
        assertEquals(3, result.length);
        // Shannon entropy at p=0.5 should be maximum (1.0)
        assertEquals(1.0, result[0], 0.01);
    }

    @Test
    void a2WithProbabilities() {
        double[] probabilities = { 0.8, 0.2 };

        double[] result = InfoFunctions.a2(probabilities);

        assertNotNull(result);
        assertEquals(2, result.length);
        // All values should be non-negative (entropy)
        for (double value : result) {
            assertTrue(value >= 0.0);
        }
    }

    @Test
    void b1WithProbabilities() {
        double[] probabilities = { 0.5, 0.5 };

        double[] result = InfoFunctions.b1(probabilities);

        assertNotNull(result);
        assertEquals(2, result.length);
        // Normalized entropy should be between 0 and 1
        for (double value : result) {
            assertTrue(value >= 0.0 && value <= 1.0);
        }
    }

    @Test
    void b2WithProbabilities() {
        double[] probabilities = { 0.7, 0.3 };

        double[] result = InfoFunctions.b2(probabilities);

        assertNotNull(result);
        assertEquals(2, result.length);
        // Normalized entropy should be between 0 and 1
        for (double value : result) {
            assertTrue(value >= 0.0 && value <= 1.0);
        }
    }

    @Test
    void totalInformationContent() {
        double[] probabilities = { 0.5, 0.5, 0.5 };

        double result = InfoFunctions.totalInformationContent(probabilities);

        assertTrue(result > 0.0);
        // Total should be sum of individual contributions
        assertEquals(
                InfoFunctions.a1(probabilities)[0] +
                        InfoFunctions.a1(probabilities)[1] +
                        InfoFunctions.a1(probabilities)[2],
                result, 0.01);
    }

    @Test
    void averageInformationContent() {
        double[] probabilities = { 0.5, 0.5 };

        double result = InfoFunctions.averageInformationContent(probabilities);

        assertTrue(result > 0.0);
        // Average should be total divided by length
        assertEquals(
                InfoFunctions.totalInformationContent(probabilities) / 2.0,
                result, 0.01);
    }

    @Test
    void maxInformationContent() {
        double result = InfoFunctions.maxInformationContent(10);

        assertEquals(10.0, result); // Maximum is l bits when all p=0.5
    }

    @Test
    void renderFunctionsWithSampleData() {
        String result = InfoFunctions.renderFunctions("data.dat", "output.txt",
                "centroids.dat", null);

        assertNotNull(result);
        assertTrue(result.contains("INFORMATION CONTENT FUNCTIONS"));
        assertTrue(result.contains("Position"));
    }

    @Test
    void a1WithNullProbabilities() {
        assertThrows(NullPointerException.class, () -> InfoFunctions.a1(null));
    }

    @Test
    void a2WithNullProbabilities() {
        assertThrows(NullPointerException.class, () -> InfoFunctions.a2(null));
    }

    @Test
    void b1WithNullProbabilities() {
        assertThrows(NullPointerException.class, () -> InfoFunctions.b1(null));
    }

    @Test
    void b2WithNullProbabilities() {
        assertThrows(NullPointerException.class, () -> InfoFunctions.b2(null));
    }

    @Test
    void totalInformationContentWithEmptyArray() {
        double[] probabilities = {};

        double result = InfoFunctions.totalInformationContent(probabilities);

        assertEquals(0.0, result); // Empty array has zero information
    }

    @Test
    void averageInformationContentWithSingleElement() {
        double[] probabilities = { 0.5 };

        double result = InfoFunctions.averageInformationContent(probabilities);

        assertTrue(result > 0.0);
        // Average of single element should equal the element itself
        assertEquals(
                InfoFunctions.totalInformationContent(probabilities),
                result, 0.01);
    }

    @Test
    void maxInformationContentWithZero() {
        double result = InfoFunctions.maxInformationContent(0);

        assertEquals(0.0, result); // Zero length has zero maximum information
    }

    @Test
    void isValidProbabilityDistributionValid() {
        double[] probabilities = { 0.25, 0.25, 0.25, 0.25 };

        assertTrue(InfoFunctions.isValidProbabilityDistribution(probabilities));
    }

    @Test
    void isValidProbabilityDistributionInvalidSum() {
        double[] probabilities = { 0.3, 0.3, 0.3 }; // Sum = 0.9

        assertFalse(
                InfoFunctions.isValidProbabilityDistribution(probabilities));
    }

    @Test
    void isValidProbabilityDistributionOutOfRange() {
        double[] probabilities = { 0.5, -0.1, 0.6 }; // Negative value

        assertFalse(
                InfoFunctions.isValidProbabilityDistribution(probabilities));
    }

    @Test
    void isValidProbabilityDistributionGreaterThanOne() {
        double[] probabilities = { 0.5, 1.2, 0.3 }; // Value > 1

        assertFalse(
                InfoFunctions.isValidProbabilityDistribution(probabilities));
    }

    @Test
    void isValidProbabilityDistributionNullInput() {
        assertThrows(NullPointerException.class,
                () -> InfoFunctions.isValidProbabilityDistribution(null));
    }

    @Test
    void isValidProbabilityDistributionSingleElement() {
        double[] probabilities = { 1.0 };

        assertTrue(InfoFunctions.isValidProbabilityDistribution(probabilities));
    }

    @Test
    void calculateFunctionsSingleClusterRecordIsFinite() {
        VectorSet vectors = new VectorSet();
        for (int i = 0; i < 10; i++) {
            BinaryVector v = new BinaryVector(new int[] { 1, 0, 1 }, 3);
            vectors.addElement(v);
        }

        InfiniteCentroids singleCluster = new InfiniteCentroids(1, 3);
        Centroid c = singleCluster.get(0);
        for (int i = 0; i < 3; i++) {
            c.set(i, 0.5);
        }

        String report = InfoFunctions.calculateFunctions(
                List.of(singleCluster), vectors, DistanceCalculator.DISTANCE_L1,
                false);

        assertTrue(report.contains("CALCULATING:"));
        // Find the CALCULATING line for R=1 and confirm SCU[1] is finite.
        String[] lines = report.split("\n");
        boolean foundR1 = false;
        for (String line : lines) {
            if (line.trim().startsWith("1:")) {
                foundR1 = true;
                // First token after "1:" is SCU[1]; it must not be Infinity.
                String scuToken = line.split("\\s+")[1];
                assertNotEquals("Infinity", scuToken,
                        "SCU for single-cluster record should be finite");
            }
        }
        assertTrue(foundR1, "CALCULATING table should contain a row for R=1");
    }

    @Test
    void calculateFunctionsTrimsEmptyClusters() {
        // Two groups of vectors that map cleanly onto two centroids, plus a
        // third centroid that no vector is closest to. After L1
        // nearest-neighbor
        // assignment the third class is empty; SC must be computed on the
        // actual
        // (trimmed) cluster count rather than reported as Infinity.
        VectorSet vectors = new VectorSet();
        for (int i = 0; i < 2; i++) {
            vectors.addElement(new BinaryVector(new int[] { 1, 1, 0, 0 }, 4));
        }
        for (int i = 0; i < 2; i++) {
            vectors.addElement(new BinaryVector(new int[] { 0, 0, 1, 1 }, 4));
        }

        InfiniteCentroids threeClusters = new InfiniteCentroids(3, 4);
        for (int i = 0; i < 4; i++) {
            threeClusters.get(0).set(i, i < 2 ? 1.0 : 0.0); // [1,1,0,0]
            threeClusters.get(1).set(i, i >= 2 ? 1.0 : 0.0); // [0,0,1,1]
            threeClusters.get(2).set(i, 0.5); // neutral
        }

        String report = InfoFunctions.calculateFunctions(
                List.of(threeClusters), vectors, DistanceCalculator.DISTANCE_L1,
                false);

        assertTrue(report.contains("CALCULATING:"));
        // The record had k=3 but only 2 classes are non-empty; the table must
        // be
        // indexed at the actual count (2) and SCU[2] must be finite.
        String[] lines = report.split("\n");
        boolean foundR2 = false;
        for (String line : lines) {
            if (line.trim().startsWith("2:")) {
                foundR2 = true;
                assertNotEquals("Infinity", line.split("\\s+")[1],
                        "SCU at the actual cluster count should be finite");
            }
        }
        assertTrue(foundR2, "CALCULATING table should contain a row for R=2");
    }
}
