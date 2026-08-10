/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.info;

import static org.junit.jupiter.api.Assertions.*;

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
    void isValidProbabilityDistributionEdgeCaseZeroSum() {
        double[] probabilities = { 0.0, 0.0, 0.0 }; // Sum = 0.0

        assertFalse(
                InfoFunctions.isValidProbabilityDistribution(probabilities));
    }
}
