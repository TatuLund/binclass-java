/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads centroid files into {@link InfiniteCentroids} instances.
 * <p>
 * Mirrors the C {@code load_centroids()} function from {@code centroid.c}: the
 * first two lines hold the number of centroids and the vector dimension, then
 * each following line holds that many space-separated values formatted as
 * {@code %1.5f} followed by a weight formatted as {@code %1.10f}. This is the
 * format described in section 3.4 of the BinClass manual and is interchangeable
 * with the C tool's {@code -l}/{@code -L} output.
 * </p>
 */
public final class CentroidReader {

    private static final Logger logger = LoggerFactory
            .getLogger(CentroidReader.class);

    private CentroidReader() {
        // Utility class — prevent instantiation
    }

    /**
     * Reads centroids from a file in the standard BinClass format.
     * <p>
     * Equivalent to C function {@code load_centroids()} from
     * {@code centroid.c}.
     * </p>
     *
     * @param inputFile
     *            path to the centroid file to read
     * @return the parsed centroids, or an empty array when the file is empty
     * @throws IOException
     *             if an I/O error occurs or the format is invalid
     */
    public static InfiniteCentroids load(String inputFile) throws IOException {
        logger.info("Loading centroids from {}", inputFile);

        String[] lines = Files.readAllLines(Path.of(inputFile))
                .toArray(new String[0]);
        int pos = 0;

        // Skip leading blank/comment lines before the header.
        while (pos < lines.length && isBlankOrComment(lines[pos])) {
            pos++;
        }
        if (pos >= lines.length) {
            throw new IOException("Empty centroid file: " + inputFile);
        }

        int k = parseIntToken(lines[pos++]);
        if (k <= 0) {
            throw new IOException(
                    "Number of centroids must be positive in: " + inputFile);
        }

        while (pos < lines.length && isBlankOrComment(lines[pos])) {
            pos++;
        }
        if (pos >= lines.length) {
            throw new IOException("Missing vector length in: " + inputFile);
        }

        int d = parseIntToken(lines[pos++]);
        if (d <= 0) {
            throw new IOException(
                    "Vector length must be positive in: " + inputFile);
        }

        InfiniteCentroids centroids = new InfiniteCentroids(k, d);
        for (int i = 0; i < k; i++) {
            while (pos < lines.length && isBlankOrComment(lines[pos])) {
                pos++;
            }
            if (pos >= lines.length) {
                throw new IOException(
                        "Unexpected end of file at centroid " + (i + 1)
                                + " in: "
                                + inputFile);
            }

            Centroid c = centroids.get(i);
            double[] el = c.getArray();
            StringTokenizer tokens = new StringTokenizer(lines[pos++]);

            for (int j = 0; j < el.length; j++) {
                if (!tokens.hasMoreTokens()) {
                    throw new IOException(
                            "Missing value at position " + (j + 1)
                                    + " of centroid "
                                    + (i + 1) + " in: " + inputFile);
                }
                el[j] = parseDoubleToken(tokens.nextToken());
            }
            // Optional trailing weight; ignore if absent.
            if (tokens.hasMoreTokens()) {
                c.setWeight(parseDoubleToken(tokens.nextToken()));
            }
        }

        logger.info("Successfully loaded {} centroids", k);
        return centroids;
    }

    /**
     * Reads all centroid records from a file written by
     * {@link CentroidWriter#saveBestPerK}.
     * <p>
     * Each record is blank-line separated and has the same layout as a single
     * {@link #load(String)} record: a header line with the number of clusters,
     * a second line with the vector length, then one space-separated line per
     * centroid. This mirrors C's {@code calculate_functions()} which loops over
     * every saved candidate when reproducing the SC-vs-k curve.
     * </p>
     *
     * @param inputFile
     *            path to the multi-record centroid file
     * @return the parsed records in file order, or an empty list when none are
     *         present
     * @throws IOException
     *             if an I/O error occurs or a record is malformed
     */
    public static List<InfiniteCentroids> loadAll(String inputFile)
            throws IOException {
        logger.info("Loading all centroid records from {}", inputFile);

        String[] lines = Files.readAllLines(Path.of(inputFile))
                .toArray(new String[0]);
        List<InfiniteCentroids> records = new ArrayList<>();
        int pos = 0;

        while (pos < lines.length) {
            // Skip leading blank/comment lines before a record header.
            while (pos < lines.length && isBlankOrComment(lines[pos])) {
                pos++;
            }
            if (pos >= lines.length) {
                break;
            }

            int k = parseIntToken(lines[pos++]);
            if (k <= 0) {
                throw new IOException(
                        "Number of centroids must be positive in: "
                                + inputFile);
            }

            while (pos < lines.length && isBlankOrComment(lines[pos])) {
                pos++;
            }
            if (pos >= lines.length) {
                throw new IOException("Missing vector length in: " + inputFile);
            }

            int d = parseIntToken(lines[pos++]);
            if (d <= 0) {
                throw new IOException(
                        "Vector length must be positive in: " + inputFile);
            }

            InfiniteCentroids centroids = new InfiniteCentroids(k, d);
            for (int i = 0; i < k; i++) {
                while (pos < lines.length && isBlankOrComment(lines[pos])) {
                    pos++;
                }
                if (pos >= lines.length) {
                    throw new IOException(
                            "Unexpected end of file at centroid " + (i + 1)
                                    + " in: " + inputFile);
                }

                Centroid c = centroids.get(i);
                double[] el = c.getArray();
                StringTokenizer tokens = new StringTokenizer(lines[pos++]);

                for (int j = 0; j < el.length; j++) {
                    if (!tokens.hasMoreTokens()) {
                        throw new IOException(
                                "Missing value at position " + (j + 1)
                                        + " of centroid " + (i + 1)
                                        + " in: " + inputFile);
                    }
                    el[j] = parseDoubleToken(tokens.nextToken());
                }
                // Optional trailing weight; ignore if absent.
                if (tokens.hasMoreTokens()) {
                    c.setWeight(parseDoubleToken(tokens.nextToken()));
                }
            }

            records.add(centroids);
        }

        logger.info("Successfully loaded {} centroid records", records.size());
        return records;
    }

    private static boolean isBlankOrComment(String line) {
        String trimmed = line.trim();
        return trimmed.isEmpty() || trimmed.startsWith("#");
    }

    private static int parseIntToken(String line) throws IOException {
        StringTokenizer tokens = new StringTokenizer(line);
        if (!tokens.hasMoreTokens()) {
            throw new IOException("Missing header value in centroid file");
        }
        try {
            return Integer.parseInt(tokens.nextToken().trim());
        } catch (NumberFormatException e) {
            throw new IOException("Invalid integer token in centroid file: "
                    + tokens.nextToken(), e);
        }
    }

    private static double parseDoubleToken(String token) throws IOException {
        try {
            return Double.parseDouble(token.trim());
        } catch (NumberFormatException e) {
            throw new IOException("Invalid number token: " + token, e);
        }
    }

    /**
     * Reads centroids from a file with an explicit suffix appended to a
     * filebase.
     * <p>
     * Convenience for callers that locate centroid files next to other dataset
     * files using a shared base name.
     * </p>
     *
     * @param filebase
     *            the base name of the data files (without extension)
     * @param suffix
     *            the suffix appended to the filebase (e.g.
     *            {@code ".centroids"})
     * @return the parsed centroids, or an empty array when the file is empty
     * @throws IOException
     *             if an I/O error occurs or the format is invalid
     */
    public static InfiniteCentroids load(String filebase, String suffix)
            throws IOException {
        return load(filebase + suffix);
    }

    /**
     * Formats a centroid value using the same precision as {@code %1.5f}.
     *
     * @param value
     *            the value to format
     * @return the formatted value with five decimal places
     */
    static String formatValue(double value) {
        return String.format(Locale.ROOT, "%1.5f", value);
    }

    /**
     * Formats a centroid weight using the same precision as {@code %1.10f}.
     *
     * @param weight
     *            the weight to format
     * @return the formatted weight with ten decimal places
     */
    static String formatWeight(double weight) {
        return String.format(Locale.ROOT, "%1.10f", weight);
    }
}
