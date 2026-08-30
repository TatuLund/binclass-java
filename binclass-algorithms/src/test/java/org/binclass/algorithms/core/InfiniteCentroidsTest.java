/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link InfiniteCentroids}.
 */
class InfiniteCentroidsTest {

    @Test
    void testCreation() {
        InfiniteCentroids ic = new InfiniteCentroids(3, 5);

        assertEquals(3, ic.size());
    }

    @Test
    void testGetSet() {
        InfiniteCentroids ic = new InfiniteCentroids(2, 5);

        double[] el1 = { 0.8, 0.2, 0.9, 0.1, 0.7 };
        Centroid c1 = new Centroid(el1, 5, 10);

        ic.set(0, c1);

        Centroid retrieved = ic.get(0);
        assertEquals(c1, retrieved);
    }

    @Test
    void testCopyFrom() {
        InfiniteCentroids source = new InfiniteCentroids(2, 5); // length 5

        double[] el1 = { 0.0, 1.0, 0.5, 0.3, 0.7 }; // 5 elements to match
                                                    // length=5
        Centroid c1 = new Centroid(el1, 5, 10);

        double[] el2 = { 0.3, 0.6, 0.2, 0.8, 0.4 }; // 5 elements to match
                                                    // length=5
        Centroid c2 = new Centroid(el2, 5, 8);

        source.set(0, c1);
        source.set(1, c2);

        InfiniteCentroids dest = new InfiniteCentroids(2, 5); // length 5 -
                                                              // compatible!
        dest.copyFrom(source);

        assertEquals(source.get(0), dest.get(0));
        assertEquals(source.get(1), dest.get(1));
    }

    @Test
    void testCalculateSC() {
        DynamicPartition dp = new DynamicPartition(2, 5);

        int[] el1 = { 1, 0, 1, 1, 0 };
        BinaryVector v1 = new BinaryVector(el1, 0, 5, 0, "v1");

        int[] el2 = { 1, 1, 0, 1, 0 };
        BinaryVector v2 = new BinaryVector(el2, 0, 5, 0, "v2");

        dp.putVector(1, v1);
        dp.putVector(1, v2);

        InfiniteCentroids ic = new InfiniteCentroids(2, 5);

        double[] elC1 = { 1.0, 0.5, 0.5, 1.0, 0.0 }; // Centroid for cluster 1
        ic.set(0, new Centroid(elC1, 5, 2));

        ic.calculateSC(dp);

        double sc = ic.getSC(0);
        // SC should be a finite number (not NaN or Infinity)
        assertFalse(Double.isNaN(sc));
        assertFalse(Double.isInfinite(sc));
    }

    @Test
    void testGetSC() {
        InfiniteCentroids ic = new InfiniteCentroids(2, 5);

        double[] el1 = { 0.8 };
        Centroid c1 = new Centroid(el1, 1, 10);

        ic.set(0, c1);

        // Before calculateSC, SC should be 0 (default)
        assertEquals(0.0, ic.getSC(0), 1e-10);
    }

    @Test
    void testBoundsChecking() {
        InfiniteCentroids ic = new InfiniteCentroids(2, 5);

        assertThrows(IndexOutOfBoundsException.class, () -> ic.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> ic.get(2));
        assertThrows(IndexOutOfBoundsException.class, () -> ic.set(-1, null));
    }

    @Test
    void testToString() {
        InfiniteCentroids ic = new InfiniteCentroids(2, 5);

        String str = ic.toString();
        assertTrue(str.contains("InfiniteCentroids{"));
    }

    @Test
    void testNegativeDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> new InfiniteCentroids(-1, 5));
        assertThrows(IllegalArgumentException.class,
                () -> new InfiniteCentroids(2, -1));
    }

    @Test
    void testInitialZeroLengthCentroids() {
        InfiniteCentroids ic = new InfiniteCentroids(3, 5);

        // All centroids should be initialized with weight 0 (uninitialized
        // state) - length is set to l during construction but can be
        // zero-length
        for (int i = 0; i < 3; i++) {
            Centroid c = ic.get(i);
            assertEquals(0, c.getWeight());
            assertEquals(5, c.getLength());
        }
    }

    @Test
    void testCopyFromIncompatibleDimensions() {
        InfiniteCentroids source = new InfiniteCentroids(2, 5);

        double[] el1 = { 0.8 }; // Length 1
        Centroid c1 = new Centroid(el1, 1, 10);

        source.set(0, c1);

        InfiniteCentroids dest = new InfiniteCentroids(2, 3); // Different
                                                              // length

        double[] el2 = { 0.8, 0.4 }; // Length 2
        Centroid c2 = new Centroid(el2, 2, 10);
        dest.set(0, c2);

        assertThrows(IllegalArgumentException.class,
                () -> dest.copyFrom(source));
    }

    @Test
    void testCopyFromNull() {
        InfiniteCentroids ic = new InfiniteCentroids(2, 5);

        assertThrows(NullPointerException.class, () -> ic.copyFrom(null));
    }

    @Test
    void testSetBoundsAgainstArrayLengthAfterRemove() {
        InfiniteCentroids ic = new InfiniteCentroids(3, 5);
        Centroid c = new Centroid(new double[] { 0.1, 0.2, 0.3, 0.4, 0.5 }, 5,
                1.0);

        // remove() shrinks k but leaves the backing array at its old capacity.
        ic.remove(0);
        assertEquals(2, ic.size());

        // index 2 is beyond k (2) but within the physical array (length 3):
        // set must accept it where a k-only bound would reject it.
        assertDoesNotThrow(() -> ic.set(2, c));
        // an index equal to the physical length remains out of bounds.
        assertThrows(IndexOutOfBoundsException.class, () -> ic.set(3, c));
    }
}
