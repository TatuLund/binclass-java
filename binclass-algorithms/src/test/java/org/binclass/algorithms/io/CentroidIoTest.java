/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.binclass.algorithms.core.InfiniteCentroids;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link CentroidWriter} and {@link CentroidReader}.
 */
public class CentroidIoTest {

    @TempDir
    Path tempDir;

    /**
     * Verifies the writer emits the C format: number of centroids on the first
     * line, vector length on the second, then one space-separated line per
     * centroid with a trailing weight.
     */
    @Test
    void testWriterEmitsCFormat() throws IOException {
        InfiniteCentroids centroids = new InfiniteCentroids(2, 4);
        centroids.get(0).setEl(new double[] { 1.0, 0.5, 0.0, 0.0 });
        centroids.get(0).setWeight(0.6);
        centroids.get(1).setEl(new double[] { 0.0, 0.0, 1.0, 1.0 });
        centroids.get(1).setWeight(0.4);

        Path file = tempDir.resolve("test.centroids");
        CentroidWriter.save(centroids, file.toString());

        String content = Files.readString(file);
        String[] lines = content.split("\\R");

        assertEquals("2", lines[0].trim(), "first line is number of centroids");
        assertEquals("4", lines[1].trim(), "second line is vector length");
        assertTrue(lines[2].startsWith("1.00000 0.50000 0.00000 0.00000 "),
                "first centroid values are space-separated at %1.5f");
        assertTrue(lines[2].endsWith("0.6000000000"),
                "first centroid weight is appended at %1.10f");
    }

    /**
     * Verifies that writing and reading a set of centroids round-trips the
     * values exactly.
     */
    @Test
    void testWriterReaderRoundTrip() throws IOException {
        InfiniteCentroids original = new InfiniteCentroids(3, 5);
        original.get(0).setEl(new double[] { 0.70000, 0.67416, 0.61866,
                0.69617, 0.77512 });
        original.get(0).setWeight(0.5102539062);
        original.get(1).setEl(new double[] { 0.28963, 0.31057, 0.36740,
                0.26570, 0.18195 });
        original.get(1).setWeight(0.4897460938);
        original.get(2).setEl(new double[] { 0.1, 0.2, 0.3, 0.4, 0.5 });
        original.get(2).setWeight(0.0);

        Path file = tempDir.resolve("roundtrip.centroids");
        CentroidWriter.save(original, file.toString());

        InfiniteCentroids loaded = CentroidReader.load(file.toString());

        assertEquals(original.size(), loaded.size(), "centroid count matches");
        for (int i = 0; i < original.size(); i++) {
            assertArrayEquals(original.get(i).getEl(), loaded.get(i).getEl(),
                    1e-9, "values match for centroid " + i);
            assertEquals(original.get(i).getWeight(), loaded.get(i).getWeight(),
                    1e-9, "weight matches for centroid " + i);
        }
    }

    /**
     * Verifies that the reader tolerates a trailing blank line and ignores an
     * absent weight field.
     */
    @Test
    void testReaderToleratesMissingWeight() throws IOException {
        Path file = tempDir.resolve("noweight.centroids");
        Files.writeString(file, "2\n3\n0.10000 0.20000 0.30000\n0.40000 "
                + "0.50000 0.60000\n");

        InfiniteCentroids loaded = CentroidReader.load(file.toString());

        assertEquals(2, loaded.size(), "two centroids parsed");
        assertArrayEquals(new double[] { 0.1, 0.2, 0.3 }, loaded.get(0).getEl(),
                1e-9);
        assertArrayEquals(new double[] { 0.4, 0.5, 0.6 }, loaded.get(1).getEl(),
                1e-9);
    }

    /**
     * Verifies that reading a file with an invalid dimension throws.
     */
    @Test
    void testReaderInvalidDimension() {
        Path file = tempDir.resolve("bad.centroids");
        try {
            Files.writeString(file, "2\n0\n0.10000 0.20000\n0.30000 0.40000\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            CentroidReader.load(file.toString());
        } catch (IOException expected) {
            assertTrue(true, "invalid dimension should raise IOException");
        }
    }
}
