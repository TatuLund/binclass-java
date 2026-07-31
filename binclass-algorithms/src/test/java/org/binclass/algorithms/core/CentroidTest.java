/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Centroid}.
 */
public class CentroidTest {

    @Test
    public void testCreation() {
        double[] el = { 0.5, 0.3, 0.8 };
        Centroid c = new Centroid(el, 3, 10);

        assertEquals(3, c.getLength());
        assertEquals(10, c.getWeight());
    }

    @Test
    public void testGet() {
        double[] el = { 0.5, 0.3, 0.8 };
        Centroid c = new Centroid(el, 3, 10);

        assertEquals(0.5, c.get(0), 1e-10);
        assertEquals(0.3, c.get(1), 1e-10);
        assertEquals(0.8, c.get(2), 1e-10);
    }

    @Test
    public void testGetLog1() {
        double[] el = { 0.5 };
        Centroid c = new Centroid(el, 1, 10);

        // log2(0.5) = -1.0 (information content, positive for prob < 1)
        assertEquals(-1.0, c.getLog1(0), 1e-10);
    }

    @Test
    public void testGetLog0() {
        double[] el = { 0.5 };
        Centroid c = new Centroid(el, 1, 10);

        // log2(1 - 0.5) = log2(0.5) = -1.0 (information content, positive for
        // prob < 1)
        assertEquals(-1.0, c.getLog0(0), 1e-10);
    }

    @Test
    public void testCopy() {
        double[] el = { 0.5, 0.3 };
        Centroid original = new Centroid(el, 2, 10);

        Centroid copy = original.copy();

        assertEquals(original, copy);
        assertNotSame(original.getArray(), copy.getArray()); // Different array
                                                             // instances

        // Modify copy shouldn't affect original
        double[] modifiedEl = { 0.9, 0.3 };
        Centroid modifiedCopy = new Centroid(modifiedEl, 2, 10);
        assertNotEquals(copy, modifiedCopy);
    }

    @Test
    public void testGetArray() {
        double[] el = { 0.5, 0.3 };
        Centroid c = new Centroid(el, 2, 10);

        double[] arr = c.getArray();
        assertEquals(2, arr.length);
        assertEquals(0.5, arr[0], 1e-10);
        assertEquals(0.3, arr[1], 1e-10);
    }

    @Test
    public void testBoundsChecking() {
        double[] el = { 0.5 };
        Centroid c = new Centroid(el, 1, 10);

        assertThrows(IndexOutOfBoundsException.class, () -> c.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> c.get(1));
    }

    @Test
    public void testZeroLengthCentroid() {
        Centroid c = new Centroid(5); // Length 5, weight 0 (default)

        assertEquals(5, c.getLength());
        assertEquals(0, c.getWeight());
    }

    @Test
    public void testEquals() {
        double[] el1 = { 0.5, 0.3 };
        double[] el2 = { 0.5, 0.3 };

        Centroid c1 = new Centroid(el1, 2, 10);
        Centroid c2 = new Centroid(el2, 2, 10);

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
    }

    @Test
    public void testToString() {
        double[] el = { 0.5, 0.3 };
        Centroid c = new Centroid(el, 2, 10);

        String str = c.toString();
        assertTrue(str.contains("Centroid{"));
        assertTrue(str.contains("weight=10"));
    }

    @Test
    public void testLogProbabilities() {
        // Test with probability values that have known log probabilities
        double[] el = { 0.25, 0.75 };
        Centroid c = new Centroid(el, 2, 10);

        // log2(0.25) = -2.0 (information content for bit=1 probability)
        assertEquals(-2.0, c.getLog1(0), 1e-10);

        // log2(1 - 0.75) = log2(0.25) = -2.0 (information content for bit=0
        // probability)
        assertEquals(-2.0, c.getLog0(1), 1e-10);
    }

    @Test
    public void testEdgeCaseProbabilityOne() {
        // Edge case: probability of 1.0 should give -log2(1) = 0
        double[] el = { 1.0 };
        Centroid c = new Centroid(el, 1, 10);

        assertEquals(0.0, c.getLog1(0), 1e-10); // -log2(1.0) = 0

        // Edge case: probability of 0.0 should give -log2(0) = +Infinity
        double[] elZero = { 0.0 };
        Centroid cZero = new Centroid(elZero, 1, 10);

        assertEquals(Double.NEGATIVE_INFINITY, cZero.getLog1(0), 1e-10); // log2(0)
                                                                         // =
                                                                         // -Inf
    }
}
