/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Vector}.
 */
class VectorTest {

    @Test
    void getBitsReturnsEmptyArrayWhenNothingSet() {
        Vector vector = new Vector();
        assertArrayEquals(new int[0], vector.getBits());
    }

    @Test
    void setBitsRoundTripsIntArray() {
        Vector vector = new Vector();
        vector.setBits(new int[] { 1, 0, 1, 1 });
        assertArrayEquals(new int[] { 1, 0, 1, 1 }, vector.getBits());
    }

    @Test
    void setBitsHandlesNull() {
        Vector vector = new Vector();
        vector.setBits(null);
        assertArrayEquals(new int[0], vector.getBits());
    }

    @Test
    void setDataPushesAllFields() {
        VectorData data = new VectorData(
                new int[] { 1, 0, 1, 0 },
                "strain-42",
                "id-7",
                3,
                new int[] { 1, 1, 1, 0 });
        Vector vector = new Vector();
        vector.setData(data);

        assertArrayEquals(new int[] { 1, 0, 1, 0 }, vector.getBits());
        assertEquals("strain-42", vector.getElement().getProperty("name"));
        assertEquals("id-7", vector.getElement().getProperty("identifier"));
        assertEquals(3, vector.getElement().getProperty("distanceToCentroid",
                Integer.MIN_VALUE));
    }

    @Test
    void setDataAcceptsNullIdentifier() {
        VectorData data = new VectorData(new int[] { 0, 1 }, "name");
        Vector vector = new Vector();
        vector.setData(data);
        assertEquals("", vector.getElement().getProperty("identifier"));
    }

    @Test
    void setDataRejectsNull() {
        Vector vector = new Vector();
        assertThrows(IllegalArgumentException.class,
                () -> vector.setData(null));
    }

    @Test
    void setRoundedCentroidRoundTripsIntArray() {
        Vector vector = new Vector();
        vector.setBits(new int[] { 1, 0, 1 });
        vector.setRoundedCentroid(new int[] { 1, 1, 0 });
        List<?> rounded = vector.getElement().getPropertyBean("roundedCentroid",
                List.class);
        assertEquals(List.of(1, 1, 0), rounded);
    }

    @Test
    void shouldShowNameInlineSwitchesAtNine() {
        Vector small = new Vector();
        small.setBits(new int[64]);
        assertFalse(small.shouldShowNameInline());

        Vector large = new Vector();
        large.setBits(new int[81]);
        assertTrue(large.shouldShowNameInline());
    }

    @Test
    void getGridSideLengthClamps() {
        Vector tiny = new Vector();
        tiny.setBits(new int[4]);
        assertEquals(2, tiny.getGridSideLength());

        Vector huge = new Vector();
        huge.setBits(new int[10_000]);
        assertEquals(12, huge.getGridSideLength());
    }

    @Test
    void getBitsReturnsDefensiveCopy() {
        Vector vector = new Vector();
        vector.setBits(new int[] { 1, 0 });
        int[] bits = vector.getBits();
        bits[0] = 0;
        assertArrayEquals(new int[] { 1, 0 }, vector.getBits());
    }
}
