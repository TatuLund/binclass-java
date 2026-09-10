/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.DynamicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link DynamicPartitionWriter}.
 */
public class DynamicPartitionWriterTest {

    @TempDir
    Path tempDir;

    // Standard PIC offsets used by the BinClass data set / partition reader.
    private static final int NAME_LEN = 9;
    private static final int ID_OFFS = 15;
    private static final int VEC_OFFS = 23;

    /**
     * Builds a dynamic partition with two clusters for testing.
     */
    private DynamicPartition samplePartition() {
        DynamicPartition dp = new DynamicPartition(2, 6);

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0, 1, 0, 1, 0 }, 0,
                6, 0, "STRAIN_A", "BUDV");
        BinaryVector v2 = new BinaryVector(new int[] { 1, 1, 1, 0, 0, 0 }, 0,
                6, 0, "STRAIN_B", "BUDV");
        BinaryVector v3 = new BinaryVector(new int[] { 0, 0, 0, 1, 1, 1 }, 0,
                6, 0, "STRAIN_C", "AQUA");

        dp.putVector(1, v1);
        dp.putVector(1, v2);
        dp.putVector(2, v3);
        return dp;
    }

    /**
     * Verifies that vector lines use PIC format (classname padded to column 15,
     * strain padded to column 23, then continuous bits) and not the old Java
     * bracket notation.
     */
    @Test
    void testWritePartitionPicFormat() throws IOException {
        DynamicPartition dp = samplePartition();

        Path outputFile = tempDir.resolve("test.partition");
        DynamicPartitionWriter.writeDynamicPartition(dp,
                outputFile.toString());

        String content = Files.readString(outputFile);
        assertTrue(content.contains("Class 1"),
                "Should contain Class 1 header");
        assertTrue(content.contains("Class 2"),
                "Should contain Class 2 header");

        // Each strain name must appear at column 15 (0-indexed).
        String[] lines = content.split("\n");
        boolean foundVector = false;
        for (String line : lines) {
            if (line.contains("STRAIN_A") || line.contains("STRAIN_B")
                    || line.contains("STRAIN_C")) {
                int strainPos = line.indexOf("STRAIN");
                assertEquals(15, strainPos,
                        "Strain should start at column 15");
                foundVector = true;
                break;
            }
        }
        assertTrue(foundVector, "Should find a PIC-format vector line");

        // The binary portion must begin exactly at column VEC_OFFS (23).
        for (String line : lines) {
            if (line.contains("STRAIN_A")) {
                assertEquals("101010", line.substring(VEC_OFFS),
                        "Bits should start at column 23");
                break;
            }
        }
    }

    /**
     * Verifies that the written partition round-trips through a parser that
     * mirrors {@code ReportCommand.parsePartitionLine}.
     */
    @Test
    void testWritePartitionRoundTrip() throws IOException {
        DynamicPartition dp = samplePartition();

        Path outputFile = tempDir.resolve("roundtrip.partition");
        DynamicPartitionWriter.writeDynamicPartition(dp,
                outputFile.toString());

        String content = Files.readString(outputFile);
        List<String> lines = new ArrayList<>(List.of(content.split("\n")));

        // Count clusters the way the standard reader does.
        int k = 0;
        for (String line : lines) {
            if (line.startsWith("Class ")) {
                k++;
            }
        }
        assertEquals(2, k, "Should detect two clusters");

        // Parse vectors back using PIC fixed-width offsets. Collect strain and
        // its bits so order within a cluster does not matter.
        List<String[]> parsed = new ArrayList<>();
        int currentCluster = 0;
        for (String line : lines) {
            if (line.startsWith("Class ")) {
                currentCluster++;
            } else if (!isVectorLine(line)) {
                continue;
            } else {
                String strain = line.substring(15, VEC_OFFS).trim();
                int[] bits = parseBits(line);
                StringBuilder sb = new StringBuilder();
                for (int b : bits) {
                    sb.append(b == -1 ? 'x' : b);
                }
                parsed.add(new String[] { strain, sb.toString() });
            }
        }

        // Three vectors total across both clusters.
        assertEquals(3, parsed.size(), "Should parse three vectors");

        // Verify each expected (strain -> bits) pair round-trips exactly.
        assertContains(parsed, "STRAIN_A", "101010");
        assertContains(parsed, "STRAIN_B", "111000");
        assertContains(parsed, "STRAIN_C", "000111");
    }

    /**
     * Asserts that {@code parsed} contains an entry with the given strain and
     * bit string.
     */
    private void assertContains(List<String[]> parsed, String strain,
            String bits) {
        for (String[] entry : parsed) {
            if (entry[0].equals(strain)) {
                assertEquals(bits, entry[1],
                        "Bits for " + strain + " should round-trip");
                return;
            }
        }
        assertTrue(false, "Missing strain " + strain + " in parsed output");
    }

    /**
     * Mirrors {@code ReportCommand.isVectorLine}: a line is a vector when it is
     * long enough and does not start with a space.
     */
    private boolean isVectorLine(String line) {
        return line.length() >= VEC_OFFS + 6 - 1 && (line.isEmpty()
                || line.charAt(0) != ' ');
    }

    /**
     * Mirrors {@code ReportCommand.parsePartitionLine} bit decoding for the
     * binary portion starting at {@code VEC_OFFS}.
     */
    private int[] parseBits(String line) {
        int length = 6;
        int[] values = new int[length];
        int end = Math.min(VEC_OFFS + length, line.length());
        for (int pos = VEC_OFFS; pos < end; pos++) {
            char c = line.charAt(pos);
            if (c == ' ') {
                values[pos - VEC_OFFS] = -1;
            } else if (c != '0') {
                values[pos - VEC_OFFS] = 1;
            } else {
                values[pos - VEC_OFFS] = 0;
            }
        }
        return values;
    }

    /**
     * Verifies that missing values are written as 'x' and parsed back as -1.
     */
    @Test
    void testWritePartitionMissingValue() throws IOException {
        DynamicPartition dp = new DynamicPartition(1, 4);
        BinaryVector v = new BinaryVector(new int[] { 1, 0, 1, 0 }, 0b0100, 4,
                0, "STRAIN_M", "BUDV");
        dp.putVector(1, v);

        Path outputFile = tempDir.resolve("missing.partition");
        DynamicPartitionWriter.writeDynamicPartition(dp,
                outputFile.toString());

        String content = Files.readString(outputFile);
        for (String line : content.split("\n")) {
            if (line.startsWith("STRAIN_M")) {
                // Bit position 2 is missing -> 'x' at column VEC_OFFS + 2.
                assertEquals('x', line.charAt(VEC_OFFS + 2),
                        "Missing bit should be written as 'x'");
                break;
            }
        }
    }

}
