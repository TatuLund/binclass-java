/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes centroid data to files.
 * <p>
 * Mirrors the C {@code save_centroids()} function from {@code centroid.c}: a
 * header with the number of centroids and vector length, followed by one line
 * per centroid holding space-separated values formatted as {@code %1.5f} and a
 * trailing weight formatted as {@code %1.10f}. This is the format described in
 * section 3.4 of the BinClass manual and is interchangeable with the C tool's
 * {@code -l}/{@code -L} output.
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
     * Equivalent to C function {@code save_centroids()} from
     * {@code centroid.c}.
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

        int k = centroids.size();
        // Get vector length from first centroid (all should have same length)
        int d = 0;
        if (k > 0) {
            d = centroids.get(0).getLength();
        }

        StringBuilder sb = new StringBuilder();
        // Header: number of centroids and vector length, one per line.
        sb.append(k).append('\n');
        sb.append(d).append('\n');

        // One line per centroid: space-separated values then the weight.
        for (int i = 0; i < k; i++) {
            Centroid c = centroids.get(i);
            double[] el = c.getEl();
            double weight = c.getWeight();

            for (int j = 0; j < el.length; j++) {
                sb.append(String.format(Locale.ROOT, "%1.5f", el[j]))
                        .append(' ');
            }
            sb.append(String.format(Locale.ROOT, "%1.10f", weight));
            sb.append('\n');
        }

        // Write to file
        Path path = Path.of(outputFile);
        Files.writeString(path, sb.toString());

        logger.info("Successfully saved {} centroids", k);
    }

    /**
     * Saves the best centroids found for each cluster count to a file, one
     * record per cluster count.
     * <p>
     * Each record begins with a header line holding the number of clusters and
     * a second line holding the vector length, followed by one space-separated
     * line per centroid formatted as {@code %1.5f} values and a trailing weight
     * formatted as {@code %1.10f}. Records are written in ascending cluster
     * count order so that {@code binclass function} can reproduce the SC-vs-k
     * curve from the file. This mirrors C's {@code save_centroids()} being
     * called once per candidate when the {@code -l} switch is set.
     * </p>
     *
     * @param perK
     *            map from cluster count to its best centroids (must not be
     *            {@code null} or empty)
     * @param outputFile
     *            path to the output file
     * @throws IOException
     *             if an I/O error occurs while writing the file
     */
    public static void saveBestPerK(Map<Integer, InfiniteCentroids> perK,
            String outputFile) throws IOException {
        if (perK == null || perK.isEmpty()) {
            throw new IOException("No centroids to save");
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, InfiniteCentroids> entry : new TreeMap<>(perK)
                .entrySet()) {
            InfiniteCentroids c = entry.getValue();
            int k = c.size();
            int d = k > 0 ? c.get(0).getLength() : 0;

            sb.append(k).append('\n');
            sb.append(d).append('\n');

            for (int i = 0; i < k; i++) {
                Centroid cc = c.get(i);
                double[] el = cc.getEl();
                for (int j = 0; j < el.length; j++) {
                    sb.append(String.format(Locale.ROOT, "%1.5f", el[j]))
                            .append(' ');
                }
                sb.append(String.format(Locale.ROOT, "%1.10f", cc.getWeight()))
                        .append('\n');
            }
            // Blank line separates records so each can be parsed independently.
            sb.append('\n');
        }

        Files.writeString(Path.of(outputFile), sb.toString());
        logger.info("Saved best centroids for {} cluster counts to {}",
                perK.size(), outputFile);
    }
}
