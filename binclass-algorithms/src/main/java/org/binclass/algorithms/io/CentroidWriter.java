/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes centroid data to files.
 * <p>
 * Mirrors C functions from {@code report.c} for saving centroid information.
 * </p>
 */
public final class CentroidWriter {

    private static final Logger logger = LoggerFactory
            .getLogger(CentroidWriter.class);

    private CentroidWriter() {
        // Utility class — prevent instantiation
    }

    /**
     * Saves centroids to a file in the standard BinClass format.
     * <p>
     * Equivalent to C function {@code write_centroids()} from {@code report.c}.
     * </p>
     *
     * @param centroids
     *            the infinite centroids to save
     * @param outputFile
     *            path to the output file
     * @throws IOException
     *             if an I/O error occurs while writing the file
     */
    public static void save(InfiniteCentroids centroids, String outputFile)
            throws IOException {
        logger.info("Saving {} centroids to {}", centroids.size(), outputFile);

        StringBuilder sb = new StringBuilder();

        // Write header
        sb.append("# BinClass Centroid File\n");
        sb.append("# Number of centroids: ").append(centroids.size())
                .append("\n");

        // Get vector length from first centroid (all should have same length)
        int vecLen = 0;
        if (centroids.size() > 0) {
            vecLen = centroids.get(0).getLength();
        }
        sb.append("# Vector length: ").append(vecLen).append("\n\n");

        // Write each centroid
        for (int i = 0; i < centroids.size(); i++) {
            Centroid c = centroids.get(i);
            double[] el = c.getEl();

            sb.append("Centroid ").append(i + 1).append(": ");
            for (int j = 0; j < el.length; j++) {
                if (j > 0) {
                    sb.append(",");
                }
                sb.append(el[j]);
            }
            sb.append("\n");
        }

        // Write to file
        Path path = Path.of(outputFile);
        Files.writeString(path, sb.toString());

        logger.info("Successfully saved {} centroids", centroids.size());
    }
}
