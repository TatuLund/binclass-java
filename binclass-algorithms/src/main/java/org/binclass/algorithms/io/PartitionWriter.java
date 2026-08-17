/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.binclass.algorithms.core.Partition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes partition data to files.
 * <p>
 * Mirrors C functions from {@code binset.c} for saving partition information.
 * </p>
 */
public final class PartitionWriter {

    private static final Logger logger = LoggerFactory
            .getLogger(PartitionWriter.class);

    private PartitionWriter() {
        // Utility class — prevent instantiation
    }

    /**
     * Writes a partition to a file in the standard BinClass format.
     * <p>
     * Equivalent to C function {@code inf_write_partition()} from
     * {@code binset.c}. Partitions are written in descending order by size
     * (largest cluster first).
     * </p>
     *
     * @param partition
     *            the partition to write
     * @param outputFile
     *            path to the output file
     * @throws IOException
     *             if an I/O error occurs while writing the file
     */
    public static void writePartition(Partition partition, String outputFile)
            throws IOException {
        logger.info("Writing partition with {} clusters to {}",
                partition.size(), outputFile);

        StringBuilder sb = new StringBuilder();

        // Sort partition by size (descending) using bubble sort like C
        // implementation
        Partition sorted = sortPartBySize(partition);

        int k = sorted.size();
        for (int i = 1; i <= k; i++) {
            var cluster = sorted.getElements(i);
            if (!cluster.isEmpty()) {
                sb.append("Class ").append(i).append("\n");
                // Write elements - using their indices or values
                int idx = 0;
                for (var element : cluster) {
                    sb.append(element.toString()).append("\n");
                    idx++;
                }
            }
        }

        Path path = Path.of(outputFile);
        Files.writeString(path, sb.toString());
        logger.info("Partition written to {}", outputFile);
    }

    /**
     * Sorts a partition by cluster size in descending order.
     * <p>
     * Equivalent to C function {@code inf_write_partition()} sorting logic.
     * </p>
     *
     * @param partition
     *            the partition to sort
     * @return a new sorted partition (original is not modified)
     */
    private static Partition sortPartBySize(Partition partition) {
        int k = partition.size();

        // Create arrays for sizes and references
        int[] sizes = new int[k + 1];
        var clusters = new org.binclass.algorithms.core.BinaryVector[k + 1][];

        for (int i = 1; i <= k; i++) {
            sizes[i] = partition.getElements(i).size();
            clusters[i] = partition.getElements(i)
                    .toArray(new org.binclass.algorithms.core.BinaryVector[0]);
        }

        // Bubble sort by size (descending)
        for (int i = 1; i < k; i++) {
            for (int j = i + 1; j <= k; j++) {
                if (sizes[i] < sizes[j]) {
                    // Swap sizes
                    int tempSize = sizes[i];
                    sizes[i] = sizes[j];
                    sizes[j] = tempSize;

                    // Swap clusters
                    var tempCluster = clusters[i];
                    clusters[i] = clusters[j];
                    clusters[j] = tempCluster;
                }
            }
        }

        // Create new partition with sorted clusters
        Partition sorted = new Partition(k);
        for (int i = 1; i <= k; i++) {
            for (var bv : clusters[i]) {
                sorted.addElement(i, bv);
            }
        }

        return sorted;
    }
}
