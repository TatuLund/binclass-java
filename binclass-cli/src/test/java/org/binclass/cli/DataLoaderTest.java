/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.VectorSet;

/**
 * Tests for {@link DataLoader} covering vector loading from files with various
 * header formats and data issues.
 */
final class DataLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadVectorsWithSimpleHeader() throws IOException {
        // Create a simple header file
        Files.writeString(tempDir.resolve("test.hdr"), "3 5");

        // Create data file with binary vectors
        StringBuilder sb = new StringBuilder();
        sb.append("10101\n");
        sb.append("01010\n");
        sb.append("11000\n");
        Files.writeString(tempDir.resolve("test.data"), sb.toString());

        VectorSet result = DataLoader.loadVectors(
                tempDir.resolve("test").toString());

        assertEquals(3, result.size());

        // Verify all three vectors are present (order may vary in Set)
        BinaryVector[] vectors = result.getElements()
                .toArray(new BinaryVector[0]);
        assertArrayEquals(new int[] { 1, 0, 1, 0, 1 },
                findVector(vectors, "10101"));
        assertArrayEquals(new int[] { 0, 1, 0, 1, 0 },
                findVector(vectors, "01010"));
        assertArrayEquals(new int[] { 1, 1, 0, 0, 0 },
                findVector(vectors, "11000"));
    }

    @Test
    void loadVectorsWithKeyValueHeader() throws IOException {
        // Create a key=value header file with vecoffs and veclen
        StringBuilder hdr = new StringBuilder();
        hdr.append("vecoffs=0\n");
        hdr.append("veclen=5\n");
        Files.writeString(tempDir.resolve("test.hdr"), hdr.toString());

        // Create data file - binary strings are exactly 5 characters
        StringBuilder sb = new StringBuilder();
        sb.append("10101\n");
        sb.append("01010\n");
        sb.append("11000\n");
        Files.writeString(tempDir.resolve("test.data"), sb.toString());

        VectorSet result = DataLoader.loadVectors(
                tempDir.resolve("test").toString());

        assertEquals(3, result.size());

        BinaryVector[] vectors = result.getElements()
                .toArray(new BinaryVector[0]);
        assertArrayEquals(new int[] { 1, 0, 1, 0, 1 },
                findVector(vectors, "10101"));
    }

    @Test
    void loadVectorsWithVecOffs() throws IOException {
        // Create a key=value header file with vecoffs offset
        StringBuilder hdr = new StringBuilder();
        hdr.append("vecoffs=3\n");
        hdr.append("veclen=5\n");
        Files.writeString(tempDir.resolve("test.hdr"), hdr.toString());

        // Create data file - binary portion starts at position 3
        StringBuilder sb = new StringBuilder();
        sb.append("ABC10101\n"); // "10101" starts at offset 3
        sb.append("DEF01010\n"); // "01010" starts at offset 3
        sb.append("GHI11000\n"); // "11000" starts at offset 3
        Files.writeString(tempDir.resolve("test.data"), sb.toString());

        VectorSet result = DataLoader.loadVectors(
                tempDir.resolve("test").toString());

        assertEquals(3, result.size());

        BinaryVector[] vectors = result.getElements()
                .toArray(new BinaryVector[0]);

        // Use findVector to check by content (order-independent)
        assertArrayEquals(new int[] { 1, 0, 1, 0, 1 },
                findVector(vectors, "10101"));
        assertArrayEquals(new int[] { 0, 1, 0, 1, 0 },
                findVector(vectors, "01010"));
        assertArrayEquals(new int[] { 1, 1, 0, 0, 0 },
                findVector(vectors, "11000"));
    }

    @Test
    void loadVectorsWithTrailingNewline() throws IOException {
        // Create a simple header file
        Files.writeString(tempDir.resolve("test.hdr"), "2 5");

        // Create data file with trailing newlines (common in text files)
        StringBuilder sb = new StringBuilder();
        sb.append("10101\n");
        sb.append("01010\n");
        Files.writeString(tempDir.resolve("test.data"), sb.toString());

        VectorSet result = DataLoader.loadVectors(
                tempDir.resolve("test").toString());

        assertEquals(2, result.size());

        BinaryVector[] vectors = result.getElements()
                .toArray(new BinaryVector[0]);
        assertArrayEquals(new int[] { 1, 0, 1, 0, 1 },
                findVector(vectors, "10101"));
    }

    @Test
    void loadVectorsWithTrailingCarriageReturn() throws IOException {
        // Create a simple header file
        Files.writeString(tempDir.resolve("test.hdr"), "2 5");

        // Create data file with Windows-style line endings (CRLF)
        StringBuilder sb = new StringBuilder();
        sb.append("10101\r\n");
        sb.append("01010\r\n");
        Files.writeString(tempDir.resolve("test.data"), sb.toString());

        VectorSet result = DataLoader.loadVectors(
                tempDir.resolve("test").toString());

        assertEquals(2, result.size());

        BinaryVector[] vectors = result.getElements()
                .toArray(new BinaryVector[0]);
        assertArrayEquals(new int[] { 1, 0, 1, 0, 1 },
                findVector(vectors, "10101"));
    }

    @Test
    void loadVectorsWithTrailingSpace() throws IOException {
        // Create a simple header file
        Files.writeString(tempDir.resolve("test.hdr"), "2 5");

        // Create data file with trailing spaces
        StringBuilder sb = new StringBuilder();
        sb.append("10101   \n");
        sb.append("01010   \n");
        Files.writeString(tempDir.resolve("test.data"), sb.toString());

        VectorSet result = DataLoader.loadVectors(
                tempDir.resolve("test").toString());

        assertEquals(2, result.size());

        BinaryVector[] vectors = result.getElements()
                .toArray(new BinaryVector[0]);
        assertArrayEquals(new int[] { 1, 0, 1, 0, 1 },
                findVector(vectors, "10101"));
    }

    @Test
    void loadVectorsWithTrailingTab() throws IOException {
        // Create a simple header file
        Files.writeString(tempDir.resolve("test.hdr"), "2 5");

        // Create data file with trailing tab
        StringBuilder sb = new StringBuilder();
        sb.append("10101\t\n");
        sb.append("01010\t\n");
        Files.writeString(tempDir.resolve("test.data"), sb.toString());

        VectorSet result = DataLoader.loadVectors(
                tempDir.resolve("test").toString());

        assertEquals(2, result.size());

        BinaryVector[] vectors = result.getElements()
                .toArray(new BinaryVector[0]);
        assertArrayEquals(new int[] { 1, 0, 1, 0, 1 },
                findVector(vectors, "10101"));
    }

    @Test
    void loadVectorsWithMixedWhitespace() throws IOException {
        // Create a simple header file
        Files.writeString(tempDir.resolve("test.hdr"), "2 5");

        // Create data file with mixed trailing whitespace
        StringBuilder sb = new StringBuilder();
        sb.append("10101 \t\r\n");
        sb.append("01010 \t\r\n");
        Files.writeString(tempDir.resolve("test.data"), sb.toString());

        VectorSet result = DataLoader.loadVectors(
                tempDir.resolve("test").toString());

        assertEquals(2, result.size());

        BinaryVector[] vectors = result.getElements()
                .toArray(new BinaryVector[0]);
        assertArrayEquals(new int[] { 1, 0, 1, 0, 1 },
                findVector(vectors, "10101"));
    }

    @Test
    void loadVectorsWithShorterDataPadsZeros() throws IOException {
        // Create a simple header file expecting length 8
        Files.writeString(tempDir.resolve("test.hdr"), "2 8");

        // Create data file with shorter binary strings (3 chars)
        StringBuilder sb = new StringBuilder();
        sb.append("101\n"); // Should be padded to 00000101 (5 leading zeros +
                            // "101")
        sb.append("010\n"); // Should be padded to 00000010 (5 leading zeros +
                            // "010")
        Files.writeString(tempDir.resolve("test.data"), sb.toString());

        VectorSet result = DataLoader.loadVectors(
                tempDir.resolve("test").toString());

        assertEquals(2, result.size());

        BinaryVector[] vectors = result.getElements()
                .toArray(new BinaryVector[0]);

        // Verify both padded vectors are present (order-independent)
        assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 1, 0, 1 },
                findVector(vectors, "00000101"));
        assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 0, 1, 0 },
                findVector(vectors, "00000010"));
    }

    @Test
    void loadVectorsWithLongerDataTruncates() throws IOException {
        // Create a simple header file expecting length 5
        Files.writeString(tempDir.resolve("test.hdr"), "2 5");

        // Create data file with longer binary strings (8 chars)
        StringBuilder sb = new StringBuilder();
        sb.append("10101010\n"); // Should be truncated to 10101
        sb.append("01010101\n"); // Should be truncated to 01010
        Files.writeString(tempDir.resolve("test.data"), sb.toString());

        VectorSet result = DataLoader.loadVectors(
                tempDir.resolve("test").toString());

        assertEquals(2, result.size());

        BinaryVector[] vectors = result.getElements()
                .toArray(new BinaryVector[0]);
        assertArrayEquals(new int[] { 1, 0, 1, 0, 1 },
                findVector(vectors, "10101"));
    }

    @Test
    void loadVectorsWithEmptyLinesSkipsThem() throws IOException {
        // Create a simple header file expecting 3 vectors
        Files.writeString(tempDir.resolve("test.hdr"), "3 5");

        // Create data file with empty lines between vectors
        StringBuilder sb = new StringBuilder();
        sb.append("10101\n");
        sb.append("\n"); // Empty line
        sb.append("01010\n");
        sb.append("\n"); // Empty line
        sb.append("11000\n");
        Files.writeString(tempDir.resolve("test.data"), sb.toString());

        VectorSet result = DataLoader.loadVectors(
                tempDir.resolve("test").toString());

        assertEquals(3, result.size());

        BinaryVector[] vectors = result.getElements()
                .toArray(new BinaryVector[0]);

        // Use findVector to check by content (order-independent)
        assertArrayEquals(new int[] { 1, 0, 1, 0, 1 },
                findVector(vectors, "10101"));
        assertArrayEquals(new int[] { 0, 1, 0, 1, 0 },
                findVector(vectors, "01010"));
        assertArrayEquals(new int[] { 1, 1, 0, 0, 0 },
                findVector(vectors, "11000"));
    }

    @Test
    void loadVectorsWithNVectorsLimit() throws IOException {
        // Create a header file specifying only 2 vectors
        Files.writeString(tempDir.resolve("test.hdr"), "2 5");

        // Create data file with more vectors than specified
        StringBuilder sb = new StringBuilder();
        sb.append("10101\n");
        sb.append("01010\n");
        sb.append("11000\n"); // Should be ignored (only 2 vectors requested)
        Files.writeString(tempDir.resolve("test.data"), sb.toString());

        VectorSet result = DataLoader.loadVectors(
                tempDir.resolve("test").toString());

        assertEquals(2, result.size());
    }

    @Test
    void loadVectorsWithNVectorsMinusOneUsesAllLines() throws IOException {
        // Create a header file with n_vectors=-1 (use all lines)
        StringBuilder hdr = new StringBuilder();
        hdr.append("n_vectors=-1\n");
        hdr.append("veclen=5\n");
        Files.writeString(tempDir.resolve("test.hdr"), hdr.toString());

        // Create data file with 4 vectors
        StringBuilder sb = new StringBuilder();
        sb.append("10101\n");
        sb.append("01010\n");
        sb.append("11000\n");
        sb.append("00111\n");
        Files.writeString(tempDir.resolve("test.data"), sb.toString());

        VectorSet result = DataLoader.loadVectors(
                tempDir.resolve("test").toString());

        assertEquals(4, result.size());
    }

    @Test
    void loadVectorsWithHeaderExtensionFallback() throws IOException {
        // Create data file with .data extension (primary)
        Files.writeString(tempDir.resolve("test.data"), "10101\n");

        // Create header file with .hdr extension (fallback)
        Files.writeString(tempDir.resolve("test.hdr"), "1 5");

        VectorSet result = DataLoader.loadVectors(
                tempDir.resolve("test").toString());

        assertEquals(1, result.size());
    }

    @Test
    void loadVectorsWithHeaderExtensionPriority() throws IOException {
        // Create both .header and .hdr files - should prefer .header
        Files.writeString(tempDir.resolve("test.header"), "1 5");
        Files.writeString(tempDir.resolve("test.hdr"), "2 3");

        // Also create a data file (required for loading)
        Files.writeString(tempDir.resolve("test.data"), "10101\n");

        VectorSet result = DataLoader.loadVectors(
                tempDir.resolve("test").toString());

        assertEquals(1, result.size());
    }

    @Test
    void loadVectorsWithMissingHeaderThrows() {
        assertThrows(IOException.class, () -> {
            DataLoader.loadVectors(tempDir.resolve("nonexistent").toString());
        });
    }

    @Test
    void loadVectorsWithInvalidBinaryCharacterThrows() throws IOException {
        // Create a simple header file
        Files.writeString(tempDir.resolve("test.hdr"), "1 5");

        // Create data file with invalid binary character
        StringBuilder sb = new StringBuilder();
        sb.append("10a01\n"); // 'a' is not valid binary
        Files.writeString(tempDir.resolve("test.data"), sb.toString());

        assertThrows(IllegalArgumentException.class, () -> {
            DataLoader.loadVectors(tempDir.resolve("test").toString());
        });
    }

    @Test
    void loadVectorsWithSingleVector() throws IOException {
        // Create a simple header file
        Files.writeString(tempDir.resolve("test.hdr"), "1 3");

        // Create data file with single vector
        StringBuilder sb = new StringBuilder();
        sb.append("101\n");
        Files.writeString(tempDir.resolve("test.data"), sb.toString());

        VectorSet result = DataLoader.loadVectors(
                tempDir.resolve("test").toString());

        assertEquals(1, result.size());

        BinaryVector[] vectors = result.getElements()
                .toArray(new BinaryVector[0]);
        assertArrayEquals(new int[] { 1, 0, 1 }, findVector(vectors, "101"));
    }

    @Test
    void loadVectorsWithLargeDataset() throws IOException {
        // Create a simple header file expecting many vectors
        Files.writeString(tempDir.resolve("test.hdr"), "100 5");

        // Create data file with 100 vectors
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append(String.format("%05d\n", i % 2));
        }
        Files.writeString(tempDir.resolve("test.data"), sb.toString());

        VectorSet result = DataLoader.loadVectors(
                tempDir.resolve("test").toString());

        assertEquals(100, result.size());
    }

    /**
     * Helper method to find a vector by its binary string representation.
     */
    private int[] findVector(BinaryVector[] vectors, String expected) {
        for (BinaryVector bv : vectors) {
            StringBuilder sb = new StringBuilder();
            int[] el1 = bv.getEl();
            System.err.println(
                    "[FINDVECTOR] First getEl() returned array of length "
                            + el1.length);

            int[] el2 = bv.getEl();
            System.err.println(
                    "[FINDVECTOR] Second getEl() returned array of length "
                            + el2.length);
            System.err.println("[FINDVECTOR] Same reference? " + (el1 == el2));

            System.err.print("[FINDVECTOR] Array contents: ");
            for (int i = 0; i < el1.length; i++) {
                System.err.print("[" + i + "]=" + el1[i]);
                if (i < el1.length - 1)
                    System.err.print(", ");
            }
            System.err.println();

            // Also check using get() method
            System.err.print("[FINDVECTOR] Using get(): ");
            for (int i = 0; i < bv.getLength(); i++) {
                System.err.print("[" + i + "]=" + bv.get(i));
                if (i < bv.getLength() - 1)
                    System.err.print(", ");
            }
            System.err.println();

            for (int val : el1) {
                sb.append(val);
            }
            String str = sb.toString();
            System.err.println("[FINDVECTOR] Built string: '" + str + "'");
            if (str.equals(expected)) {
                return bv.getEl();
            }
        }
        fail("Vector not found: " + expected);
        return null; // unreachable
    }
}
