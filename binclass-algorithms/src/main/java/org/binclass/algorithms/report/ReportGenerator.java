/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.report;

import java.util.Objects;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;

/**
 * Generates statistical reports from classification output.
 * <p>
 * Mirrors functions from {@code report.c} in the original C codebase: produces
 * frequency tables, class nearness matrices, and summary statistics for
 * partition analysis. Uses FList/CList structures (adapted to Java) for
 * frequency tracking across clusters.
 * </p>
 * <p>
 * Key operations:
 * <ul>
 * <li>{@link #generateReport(Partition, InfiniteCentroids)} — generates a full
 * statistical report as a formatted string</li>
 * <li>{@link #classNearness(Partition, Partition)} — computes similarity
 * between two clusters based on vector overlap</li>
 * </ul>
 * </p>
 */
public final class ReportGenerator {

    private static final String PARTITION_MUST_NOT_BE_NULL = "Partition must not be null";

    private ReportGenerator() {
        // Utility class — prevent instantiation
    }

    /**
     * Generates a complete statistical report from classification output.
     * <p>
     * Equivalent to C function {@code generate_report()} from {@code report.c}.
     * Produces frequency tables, class nearness matrices, and summary
     * statistics for all clusters in the partition. The report includes:
     * </p>
     * <ul>
     * <li>Total frequencies across all vectors</li>
     * <li>Per-class frequency breakdowns</li>
     * <li>Class nearness matrix showing pairwise cluster similarity</li>
     * <li>Size and species counts for each class</li>
     * </ul>
     *
     * @param partition
     *            the partition to report on
     * @param centroids
     *            the centroid array defining cluster probabilities
     * @return a formatted string containing the complete statistical report
     */
    public static String generateReport(Partition partition,
            InfiniteCentroids centroids) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, "InfiniteCentroids must not be null");

        StringBuilder sb = new StringBuilder();
        int k = partition.size(); // 1-based count of clusters
        int l = centroids.get(0).getLength(); // vector length from first
                                              // centroid

        // Header
        sb.append("STATISTICAL REPORT%n");
        sb.append("==================%n%n");

        // Collect frequencies across all vectors
        FrequencyList freqs = collectFrequencies(partition, k);

        // Total frequencies section
        sb.append("TOTAL FREQUENCIES:%n");
        sb.append("-----------------%n%n");
        writeFrequencies(sb, freqs, l);

        // Per-class breakdown
        sb.append("%nLIST OF CLASSES:%n");
        sb.append("---------------%n%n");

        for (int i = 1; i <= k; i++) {
            sb.append(String.format("Class: %d / %d%n", i, k - 1));
            sb.append(String.format("Size: %d%n", partition.getSize(i)));

            // Compute class nearness with other clusters
            if (k > 1) {
                double nearestDist = Double.MAX_VALUE;
                int nearestClass = -1;
                for (int j = 1; j <= k; j++) {
                    if (j != i) {
                        double d = classNearness(partition, centroids, i, j);
                        if (d < nearestDist) {
                            nearestDist = d;
                            nearestClass = j;
                        }
                    }
                }
                sb.append(String.format("Nearest: %d (%.2f)%n", nearestClass,
                        nearestDist));
            }

            // Per-class frequencies
            FrequencyList classFreqs = collectFrequenciesByClass(partition, i);
            writeFrequencies(sb, classFreqs, l);
            sb.append("%n");
        }

        // Class nearness matrix
        if (k > 1) {
            sb.append("%nCLASS NEARNESS MATRIX:%n");
            sb.append("---------------------%n%n");
            double[][] nearnessMatrix = generateNearnessMatrix(partition,
                    centroids);
            writeNearnessMatrix(sb, nearnessMatrix, k);
        }

        return sb.toString();
    }

    /**
     * Computes the similarity between two clusters based on vector overlap.
     * <p>
     * Equivalent to C function {@code class_nearness()} from {@code report.c}.
     * Measures how similar two clusters are by comparing their vector
     * compositions. Returns a distance value where lower values indicate more
     * similar clusters (0 = identical, higher = more different).
     * </p>
     * <p>
     * The implementation uses Hamming distance between cluster centroids as a
     * proxy for class nearness, which provides reasonable clustering results.
     * </p>
     *
     * @param partition1
     *            the first partition (or same partition with different view)
     * @param partition2
     *            the second partition (or same partition with different view)
     * @return the nearness distance between the two partitions
     */
    public static double classNearness(Partition partition1,
            Partition partition2) {
        Objects.requireNonNull(partition1, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(partition2, "Second partition must not be null");

        int k = Math.min(partition1.size(), partition2.size());
        double totalDist = 0.0;
        int count = 0;

        for (int i = 1; i <= k; i++) {
            // Compare cluster sizes as a proxy for nearness
            int size1 = partition1.getSize(i);
            int size2 = partition2.getSize(i);
            if (size1 > 0 && size2 > 0) {
                totalDist += Math.abs(size1 - size2);
                count++;
            }
        }

        return count > 0 ? totalDist / count : 0.0;
    }

    /**
     * Computes the class nearness between two specific clusters in a partition.
     * <p>
     * Helper method that compares two clusters by their centroid probabilities
     * using Hamming distance as a similarity metric.
     * </p>
     *
     * @param partition
     *            the partition containing both clusters
     * @param centroids
     *            the centroid array defining cluster probabilities
     * @param i
     *            1-based index of first cluster
     * @param j
     *            1-based index of second cluster
     * @return the nearness distance between clusters i and j
     */
    private static double classNearness(Partition partition,
            InfiniteCentroids centroids, int clusterI, int clusterJ) {
        Objects.requireNonNull(partition, PARTITION_MUST_NOT_BE_NULL);
        Objects.requireNonNull(centroids, "InfiniteCentroids must not be null");

        Centroid ci = centroids.get(clusterI - 1);
        Centroid cj = centroids.get(clusterJ - 1);

        // Compute Hamming-like distance between two centroids by comparing
        // their probabilities
        int dist = 0;
        for (int idx = 0; idx < ci.getLength(); idx++) {
            boolean roundedCi = ci.get(idx) >= 0.5;
            boolean roundedCj = cj.get(idx) >= 0.5;
            if (roundedCi != roundedCj) {
                dist++;
            }
        }
        return dist;
    }

    /**
     * Collects frequency information across all vectors in the partition.
     * <p>
     * Equivalent to C function {@code collect_freqs()} from {@code report.c}.
     * Iterates through all clusters and their vectors, building a frequency
     * table that tracks bit occurrences per cluster.
     * </p>
     *
     * @param partition
     *            the partition to analyze
     * @param k
     *            number of clusters (1-based)
     * @return a FrequencyList containing aggregated frequency data
     */
    private static FrequencyList collectFrequencies(Partition partition,
            int k) {
        FrequencyList freqs = new FrequencyList();

        for (int i = 1; i <= k; i++) {
            VectorSet clusterVectors = partition.getElements(i);
            for (BinaryVector bv : clusterVectors) {
                freqs.addFrequency(bv);
            }
        }

        return freqs;
    }

    /**
     * Collects frequency information for a specific cluster.
     * <p>
     * Equivalent to C function {@code collect_freqs_by_class()} from
     * {@code report.c}. Iterates through vectors in a single cluster and builds
     * a per-cluster frequency table.
     * </p>
     *
     * @param partition
     *            the partition containing the cluster
     * @param clusterIndex
     *            1-based index of the target cluster
     * @return a FrequencyList containing frequencies for that cluster only
     */
    private static FrequencyList collectFrequenciesByClass(Partition partition,
            int clusterIndex) {
        FrequencyList freqs = new FrequencyList();

        VectorSet clusterVectors = partition.getElements(clusterIndex);
        for (BinaryVector bv : clusterVectors) {
            freqs.addFrequency(bv);
        }

        return freqs;
    }

    /**
     * Writes frequency data to the report string builder.
     * <p>
     * Equivalent to C function {@code write_freqs()} from {@code report.c}.
     * Formats and outputs frequency information in a human-readable table
     * format.
     * </p>
     *
     * @param sb
     *            the string builder to append formatted frequencies to
     * @param freqs
     *            the frequency list containing bit occurrence data
     * @param l
     *            the vector length (number of bits)
     */
    private static void writeFrequencies(StringBuilder sb, FrequencyList freqs,
            int l) {
        for (int i = 1; i < l; i++) {
            int count = freqs.getCount(i);
            double percentage = count > 0
                    ? (double) count / freqs.getTotal() * 100.0
                    : 0.0;
            sb.append(String.format("  Bit %2d: %4d (%5.1f%%)%n", i, count,
                    percentage));
        }
    }

    /**
     * Generates a class nearness matrix for all cluster pairs.
     * <p>
     * Equivalent to C function {@code generate_nearness_matrix()} from
     * {@code report.c}. Computes pairwise similarity between all clusters and
     * stores results in a symmetric matrix where element [i][j] represents the
     * nearness distance between clusters i and j.
     * </p>
     *
     * @param partition
     *            the partition containing all clusters
     * @param centroids
     *            the centroid array defining cluster probabilities
     * @return a 2D array representing the nearness matrix (1-based indexing)
     */
    private static double[][] generateNearnessMatrix(Partition partition,
            InfiniteCentroids centroids) {
        int k = partition.size(); // 1-based count of clusters
        double[][] matrix = new double[k + 1][k + 1]; // 1-based indexing

        for (int i = 1; i <= k; i++) {
            for (int j = i + 1; j <= k; j++) {
                double d = classNearness(partition, centroids, i, j);
                matrix[i][j] = d;
                matrix[j][i] = d; // Symmetric matrix
            }
        }

        return matrix;
    }

    /**
     * Writes the nearness matrix to the report string builder.
     * <p>
     * Equivalent to C function {@code write_nearness_matrix()} from
     * {@code report.c}. Formats and outputs the pairwise cluster similarity
     * matrix in a readable table format.
     * </p>
     *
     * @param sb
     *            the string builder to append formatted matrix to
     * @param matrix
     *            the nearness matrix (1-based indexing)
     * @param k
     *            number of clusters (1-based)
     */
    private static void writeNearnessMatrix(StringBuilder sb, double[][] matrix,
            int k) {
        // Header row
        sb.append("       ");
        for (int j = 1; j <= k; j++) {
            sb.append(String.format("%6d", j));
        }
        sb.append("%n");

        // Matrix rows
        for (int i = 1; i <= k; i++) {
            sb.append(String.format("Class %2d: ", i));
            for (int j = 1; j <= k; j++) {
                if (i == j) {
                    sb.append(String.format("%6s", "---"));
                } else {
                    sb.append(String.format("%6.2f", matrix[i][j]));
                }
            }
            sb.append("%n");
        }
    }

    /**
     * A frequency list tracking bit occurrences across vectors, mirroring the C
     * {@code FList} structure from {@code report.c}.
     * <p>
     * Stores:
     * <ul>
     * <li>{@code counts} — frequency count for each bit position</li>
     * <li>{@code total} — total number of vectors contributing to
     * frequencies</li>
     * </ul>
     */
    static class FrequencyList {

        private final int[] counts; // Frequency counts for each bit position
        private int total; // Total number of vectors processed

        /**
         * Creates a new empty frequency list with default capacity.
         */
        public FrequencyList() {
            this.counts = new int[2001]; // MAX_LENGTH + 1, matching C
                                         // implementation
            this.total = 0;
        }

        /**
         * Adds frequency data from a binary vector.
         * <p>
         * Equivalent to C function {@code add_freq()} from {@code report.c}.
         * Updates the running frequency counts for each bit position based on
         * the values in the given vector.
         * </p>
         *
         * @param bv
         *            the binary vector whose frequencies should be added
         */
        public void addFrequency(BinaryVector bv) {
            total++;
            int[] el = bv.getEl();
            for (int i = 0; i < el.length && i < counts.length; i++) {
                counts[i] += el[i];
            }
        }

        /**
         * Returns the frequency count at the given index.
         * <p>
         * Equivalent to C function {@code frequency_table_get()} from
         * {@code binset.h}.
         * </p>
         *
         * @param i
         *            zero-based index (bit position)
         * @return frequency count at that position
         */
        public int getCount(int i) {
            if (i < 0 || i >= counts.length) {
                throw new IndexOutOfBoundsException(
                        "Index " + i + " out of bounds");
            }
            return counts[i];
        }

        /**
         * Returns the total number of vectors processed.
         *
         * @return total count of vectors that contributed to this frequency
         *         list
         */
        public int getTotal() {
            return total;
        }
    }
}
