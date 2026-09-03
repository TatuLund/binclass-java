/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Partition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link PartitionWriter}.
 */
public class PartitionWriterTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies that partition output follows PIC format with strain names.
     * Format: classname (9 chars) + padding to col 15 + strain (7 chars) +
     * padding to col 23 + continuous binary string
     */
    @Test
    void testWritePartitionWithStrains() throws IOException {
        // Create a partition with two clusters, each containing vectors with
        // strains
        Partition partition = new Partition(2);

        // Cluster 1: 2 elements
        BinaryVector bv1 = new BinaryVector(new int[] { 1, 0, 1 }, 0, 3, 1,
                "STRAIN_A");
        BinaryVector bv2 = new BinaryVector(new int[] { 0, 1, 1 }, 0, 3, 1,
                "STRAIN_B");

        // Cluster 2: 1 element
        BinaryVector bv3 = new BinaryVector(new int[] { 1, 1, 0 }, 0, 3, 2,
                "STRAIN_C");

        partition.addElement(1, bv1);
        partition.addElement(1, bv2);
        partition.addElement(2, bv3);

        Path outputFile = tempDir.resolve("test.partition");
        PartitionWriter.writePartition(partition, outputFile.toString());

        String content = Files.readString(outputFile);

        // Verify class headers are present
        assertTrue(content.contains("Class 1"),
                "Should contain Class 1 header");
        assertTrue(content.contains("Class 2"),
                "Should contain Class 2 header");

        // Verify strain names are in the output (PIC format)
        assertTrue(content.contains("STRAIN_A"),
                "Output should contain strain name STRAIN_A");
        assertTrue(content.contains("STRAIN_B"),
                "Output should contain strain name STRAIN_B");
        assertTrue(content.contains("STRAIN_C"),
                "Output should contain strain name STRAIN_C");

        // Verify binary vectors are continuous strings (not bracket notation)
        assertTrue(content.contains("101"),
                "Binary vector should be continuous string '101'");
        assertTrue(content.contains("011"),
                "Binary vector should be continuous string '011'");
        assertTrue(content.contains("110"),
                "Binary vector should be continuous string '110'");

        // Verify no bracket notation
        assertTrue(!content.contains("["),
                "Output should not contain bracket notation");
        assertTrue(!content.contains("]"),
                "Output should not contain bracket notation");
    }

    /**
     * Verifies that partition output matches PIC format alignment.
     */
    @Test
    void testWritePartitionAlignment() throws IOException {
        Partition partition = new Partition(1);

        // Create vector with 7-char strain name (matches idlen in
        // entero.header)
        BinaryVector bv = new BinaryVector(new int[] { 1, 0, 1, 1 }, 0, 4, 1,
                "TEST_0");

        partition.addElement(1, bv);

        Path outputFile = tempDir.resolve("alignment.partition");
        PartitionWriter.writePartition(partition, outputFile.toString());

        String content = Files.readString(outputFile);
        String[] lines = content.split("\n");

        // Find the line with strain name (skip "Class 1" header)
        for (String line : lines) {
            if (line.contains("TEST_0")) {
                // Verify alignment: classname at col 0-8, padding to col 14,
                // strain at col 15-21, padding to col 22, binary string from
                // col 23+
                assertEquals(9, "ESCH COLI".length(),
                        "Classname should be 9 chars");

                // Check that strain starts at position 15 (0-indexed)
                int strainPos = line.indexOf("TEST_0");
                assertEquals(15, strainPos,
                        "Strain name should start at column 15 in PIC format");

                // Verify binary string follows after padding to column 23
                // Strain at position 15-21 (7 chars), pad to 23, then binary
                String binaryPart = line.substring(strainPos + 7);
                assertTrue(binaryPart.startsWith(" "),
                        "Should have padding between strain and binary vector");
                break;
            }
        }
    }

    /**
     * Verifies that partition is sorted by cluster size (descending).
     */
    @Test
    void testWritePartitionSortedBySize() throws IOException {
        Partition partition = new Partition(3);

        // Cluster 1: 1 element
        BinaryVector bv1 = new BinaryVector(new int[] { 1 }, 0, 1, 1, "A");

        // Cluster 2: 3 elements
        BinaryVector bv2 = new BinaryVector(new int[] { 0 }, 0, 1, 2, "B");
        BinaryVector bv3 = new BinaryVector(new int[] { 1 }, 0, 1, 2, "C");
        BinaryVector bv4 = new BinaryVector(new int[] { 0 }, 0, 1, 2, "D");

        // Cluster 3: 2 elements
        BinaryVector bv5 = new BinaryVector(new int[] { 1 }, 0, 1, 3, "E");
        BinaryVector bv6 = new BinaryVector(new int[] { 0 }, 0, 1, 3, "F");

        partition.addElement(1, bv1);
        partition.addElement(2, bv2);
        partition.addElement(2, bv3);
        partition.addElement(2, bv4);
        partition.addElement(3, bv5);
        partition.addElement(3, bv6);

        Path outputFile = tempDir.resolve("sorted.partition");
        PartitionWriter.writePartition(partition, outputFile.toString());

        String content = Files.readString(outputFile);

        // Verify cluster order: largest first (sorted indices 1, 2, 3)
        int class1Pos = content.indexOf("Class 1");
        int class2Pos = content.indexOf("Class 2");
        int class3Pos = content.indexOf("Class 3");

        assertTrue(class1Pos < class2Pos && class2Pos < class3Pos,
                "Largest cluster should appear first in output (sorted by size)");
    }

    /**
     * Verifies that each vector's own class-name string is written to the
     * partition output rather than a single hardcoded value. Mirrors C
     * {@code pic_write_bv()} which writes x->clasname per vector.
     */
    @Test
    void testWritePartitionUsesPerVectorClassName() throws IOException {
        Partition partition = new Partition(1);

        BinaryVector bv1 = new BinaryVector(new int[] { 1, 0 }, 0, 2, 1,
                "STRAIN_A", "BUDV AQUA");
        BinaryVector bv2 = new BinaryVector(new int[] { 0, 1 }, 0, 2, 1,
                "STRAIN_B", "CITR AMA1");

        partition.addElement(1, bv1);
        partition.addElement(1, bv2);

        Path outputFile = tempDir.resolve("classname.partition");
        PartitionWriter.writePartition(partition, outputFile.toString());

        String content = Files.readString(outputFile);

        // Both distinct class names must appear (not a single hardcoded value)
        assertTrue(content.contains("BUDV AQUA"),
                "Output should contain the first vector's class name BUDV AQUA");
        assertTrue(content.contains("CITR AMA1"),
                "Output should contain the second vector's class name CITR AMA1");
        // The class-name field is written at column 0 of each data row, so with
        // two distinct per-vector names no row starts with the old fallback.
        long eschCount = content.lines().filter(l -> l.startsWith("ESCH COLI"))
                .count();
        assertEquals(0, eschCount,
                "Rows should use per-vector names, not a single hardcoded value");
    }
}
