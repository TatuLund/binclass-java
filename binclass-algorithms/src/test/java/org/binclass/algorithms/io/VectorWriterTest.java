/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.io;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link VectorWriter} covering vector serialization formats.
 */
final class VectorWriterTest {

    @Test
    void writePicFormatWithSingleVector() {
        int[] el = { 1, 0, 1 };
        String result = VectorWriter.writePicFormat(el);

        assertNotNull(result);
        assertEquals("1 0 1", result);
    }

    @Test
    void writeEmpiricalFormatWithSingleVector() {
        int[] el = { 1, 0, 1 };
        String result = VectorWriter.writeEmpiricalFormat(el);

        assertNotNull(result);
        assertEquals("1 0 1", result);
    }

    @Test
    void writeBinaryFormatWithSingleVector() {
        int[] el = { 1, 0, 1 };
        String result = VectorWriter.writeBinaryFormat(el);

        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    void writeWithStrainPrefix() {
        int[] el = { 1, 0, 1 };
        String strain = "strain_A";
        String result = VectorWriter.writeWithStrain(el, strain);

        assertNotNull(result);
        assertTrue(result.startsWith("strain_A\t"));
        assertTrue(result.contains("1 0 1"));
    }

    @Test
    void writeBatchMultipleVectors() {
        int[][] vectors = { { 1, 0 }, { 0, 1 } };
        String result = VectorWriter.writeBatch(vectors);

        assertNotNull(result);
        // Should contain both vectors separated by newline
        assertTrue(result.contains("1 0"));
        assertTrue(result.contains("0 1"));
    }

    @Test
    void writeWithHeaderIncludesMetadata() {
        int[][] vectors = { { 1, 0 }, { 0, 1 } };
        String result = VectorWriter.writeWithHeader(vectors);

        assertNotNull(result);
        // Header should contain vector count and length
        assertTrue(result.startsWith("2 2"));
    }

    @Test
    void writePicFormatNullVectorThrows() {
        assertThrows(NullPointerException.class,
                () -> VectorWriter.writePicFormat(null));
    }

    @Test
    void writeBatchNullVectorsThrows() {
        assertThrows(NullPointerException.class,
                () -> VectorWriter.writeBatch((int[][]) null));
    }

    @Test
    void writeWithHeaderEmptyVectors() {
        int[][] vectors = {};
        String result = VectorWriter.writeWithHeader(vectors);

        assertNotNull(result);
        assertEquals("", result); // Empty array produces empty string
    }

    @Test
    void writePicFormatSingleElement() {
        int[] el = { 0 };
        String result = VectorWriter.writePicFormat(el);

        assertNotNull(result);
        assertEquals("0", result);
    }

    @Test
    void writeWithStrainNullStrain() {
        int[] el = { 1, 0, 1 };
        String result = VectorWriter.writeWithStrain(el, null);

        assertNotNull(result);
        assertFalse(result.startsWith("\t")); // No prefix when strain is null
        assertEquals("1 0 1", result);
    }
}
