/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.DynamicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes dynamic partition data to files.
 * <p>
 * Mirrors C functions from {@code binset.c} for saving dynamic partition
 * information including frequency tables and Hamming distance matrices.
 * </p>
 */
public final class DynamicPartitionWriter {

    private static final Logger logger = LoggerFactory
            .getLogger(DynamicPartitionWriter.class);

    private DynamicPartitionWriter() {
        // Utility class — prevent instantiation
    }

    /**
     * Writes a dynamic partition to a file in the standard BinClass format.
     * <p>
     * Includes cluster assignments, frequency tables, and Hamming distance
     * matrix.
     * </p>
     *
     * @param dynPartition
     *            the dynamic partition to write
     * @param outputFile
     *            path to the output file
     * @throws IOException
     *             if an I/O error occurs while writing the file
     */
    public static void writeDynamicPartition(DynamicPartition dynPartition,
            String outputFile) throws IOException {
        logger.info("Writing dynamic partition with {} clusters to {}",
                dynPartition.size(), outputFile);

        StringBuilder sb = new StringBuilder();

        // Write header information
        sb.append("# Dynamic Partition\n");
        sb.append("# Number of clusters: ").append(dynPartition.size())
                .append("\n");
        sb.append("# Vector length: ").append(dynPartition.getVectorLength())
                .append("\n");
        sb.append("\n");

        // Write cluster assignments and frequencies
        int k = dynPartition.size();

        for (int i = 1; i <= k; i++) {
            Collection<BinaryVector> clusterElements = dynPartition
                    .getCluster(i).getElements();
            sb.append("Class ").append(i).append("\n");
            sb.append("Size: ").append(clusterElements.size()).append("\n");

            // Write frequency table for this cluster
            int[][] freqs = dynPartition.getFrequencies();
            if (freqs != null && i <= freqs.length) {
                sb.append("Frequencies:\n");
                for (int bitPos = 0; bitPos < freqs[i - 1].length; bitPos++) {
                    sb.append("  Bit ").append(bitPos).append(": ")
                            .append(freqs[i - 1][bitPos]).append("\n");
                }
            }

            // Write cluster elements (binary vectors)
            for (BinaryVector bv : clusterElements) {
                sb.append(bv.toString()).append("\n");
            }
            sb.append("\n");
        }

        // Write Hamming distance matrix if available
        double[][] hammingDistances = dynPartition.getHammingDistances();
        if (hammingDistances != null && k > 0) {
            sb.append("Hamming Distance Matrix:\n");
            for (int i = 0; i < k; i++) {
                StringBuilder row = new StringBuilder();
                for (int j = 0; j < k; j++) {
                    if (j > 0) {
                        row.append(", ");
                    }
                    row.append(String.format("%.4f", hammingDistances[i][j]));
                }
                sb.append("Cluster ").append(i + 1).append(": ")
                        .append(row.toString()).append("\n");
            }
        }

        // Write to file
        Path path = Path.of(outputFile);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Files.writeString(path, sb.toString());
        logger.info("Dynamic partition written successfully");
    }
}
