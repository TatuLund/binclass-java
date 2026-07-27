/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FrequencyTable}.
 */
public class FrequencyTableTest {

    @Test
    public void testCreation() {
        int[] freq = { 5, 3, 7 };
        FrequencyTable ft = new FrequencyTable(freq, 10);

        assertEquals(3, ft.getLength());
        assertEquals(10, ft.getSize());
    }

    @Test
    public void testGet() {
        int[] freq = { 5, 3, 7 };
        FrequencyTable ft = new FrequencyTable(freq, 10);

        assertEquals(5, ft.get(0));
        assertEquals(3, ft.get(1));
        assertEquals(7, ft.get(2));
    }

    @Test
    public void testGetSize() {
        int[] freq = { 5, 3 };
        FrequencyTable ft = new FrequencyTable(freq, 10);

        assertEquals(10, ft.getSize());
    }

    @Test
    public void testIsLinked() {
        int[] freq = { 5 };
        FrequencyTable ft = new FrequencyTable(freq, 10);

        assertFalse(ft.isLinked()); // Default is not linked
    }

    @Test
    public void testGetLinkage() {
        int[] freq = { 5 };
        FrequencyTable ft = new FrequencyTable(freq, 10);

        assertNull(ft.getLinkage()); // No linkage by default
    }

    @Test
    public void testCopy() {
        int[] freq = { 5, 3 };
        FrequencyTable original = new FrequencyTable(freq, 10);

        FrequencyTable copy = original.copy();

        assertEquals(original, copy);
        assertNotSame(original.getArray(), copy.getArray()); // Different array
                                                             // instances

        // Modify copy shouldn't affect original
        int[] modifiedFreq = { 9, 3 };
        FrequencyTable modifiedCopy = new FrequencyTable(modifiedFreq, 10);
        assertNotEquals(copy, modifiedCopy);
    }

    @Test
    public void testGetArray() {
        int[] freq = { 5, 3 };
        FrequencyTable ft = new FrequencyTable(freq, 10);

        int[] arr = ft.getArray();
        assertEquals(2, arr.length);
        assertEquals(5, arr[0]);
        assertEquals(3, arr[1]);
    }

    @Test
    public void testBoundsChecking() {
        int[] freq = { 5 };
        FrequencyTable ft = new FrequencyTable(freq, 10);

        assertThrows(IndexOutOfBoundsException.class, () -> ft.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> ft.get(1));
    }

    @Test
    public void testToString() {
        int[] freq = { 5, 3 };
        FrequencyTable ft = new FrequencyTable(freq, 10);

        String str = ft.toString();
        assertTrue(str.contains("FrequencyTable{"));
        assertTrue(str.contains("size=10"));
    }

    @Test
    public void testEquals() {
        int[] freq1 = { 5, 3 };
        int[] freq2 = { 5, 3 };

        FrequencyTable ft1 = new FrequencyTable(freq1, 10);
        FrequencyTable ft2 = new FrequencyTable(freq2, 10);

        assertEquals(ft1, ft2);
        assertEquals(ft1.hashCode(), ft2.hashCode());
    }

    @Test
    public void testNotEquals() {
        int[] freq1 = { 5, 3 };
        int[] freq2 = { 5, 4 }; // Different frequency

        FrequencyTable ft1 = new FrequencyTable(freq1, 10);
        FrequencyTable ft2 = new FrequencyTable(freq2, 10);

        assertNotEquals(ft1, ft2);
    }

    @Test
    public void testLinkedFrequencyTable() {
        int[] freq = { 5 };
        FrequencyTable linkedFt = new FrequencyTable(freq, 10, true,
                "partition_ref");

        assertTrue(linkedFt.isLinked());
        assertEquals("partition_ref", linkedFt.getLinkage());
    }

    @Test
    public void testHashCodeConsistency() {
        int[] freq = { 5, 3 };
        FrequencyTable ft = new FrequencyTable(freq, 10);

        int hash1 = ft.hashCode();
        int hash2 = ft.hashCode(); // Should be consistent

        assertEquals(hash1, hash2);
    }

    @Test
    public void testLinkedFrequencyTableEquality() {
        int[] freq = { 5 };

        FrequencyTable linkedFt1 = new FrequencyTable(freq, 10, true, "ref");
        FrequencyTable linkedFt2 = new FrequencyTable(freq, 10, true, "ref");

        assertEquals(linkedFt1, linkedFt2); // Should be equal (same freq and
                                            // size)
    }

    @Test
    public void testLinkedFrequencyTableNotEquals() {
        int[] freq = { 5 };

        FrequencyTable linkedFt1 = new FrequencyTable(freq, 10, true, "ref");
        FrequencyTable unlinkedFt = new FrequencyTable(freq, 10, false, null);

        assertNotEquals(linkedFt1, unlinkedFt); // Different linkage state
    }

    @Test
    public void testLargeFrequencyValues() {
        int[] freq = { Integer.MAX_VALUE / 2, Integer.MIN_VALUE + 1 };
        FrequencyTable ft = new FrequencyTable(freq, 10);

        assertEquals(Integer.MAX_VALUE / 2, ft.get(0));
        assertEquals(Integer.MIN_VALUE + 1, ft.get(1));
    }

    @Test
    public void testZeroSize() {
        int[] freq = { 5 };
        FrequencyTable ft = new FrequencyTable(freq, 0); // Size 0

        assertEquals(0, ft.getSize());
        assertEquals(5, ft.get(0)); // Frequency can still be non-zero
    }

    @Test
    public void testSingleElement() {
        int[] freq = { 42 };
        FrequencyTable ft = new FrequencyTable(freq, 1);

        assertEquals(1, ft.getLength());
        assertEquals(42, ft.get(0));
    }
}
