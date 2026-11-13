/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.report;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;

/**
 * Immutable, structured view of a statistical report computed directly from a
 * {@link Partition} and its {@link InfiniteCentroids}.
 * <p>
 * Unlike {@link ReportGenerator}, which emits a flat monospace string meant for
 * terminals, the model exposes typed data so the web UI can render proper HTML
 * elements (headings, tables, per-class cards) instead of parsing text. It is
 * produced by {@link #of(Partition, InfiniteCentroids, ReportOptions)} and is
 * safe to build once per regeneration.
 * </p>
 */
@NullMarked
public final class ReportModel {

    private final @Nullable Partition partition;
    private final ReportOptions options;
    private final List<BitFrequency> totalFrequencies;
    private final List<ClassReport> classReports;
    private final double @Nullable [][] matrixValues;

    /**
     * Builds the report model from partition data.
     *
     * @param partition
     *            the partition to report on (1-based clusters)
     * @param centroids
     *            the centroid array defining cluster probabilities
     * @param options
     *            reporting options controlling which sections are emitted and
     *            how distances/digits are rendered
     * @return a computed {@link ReportModel}
     */
    @SuppressWarnings("null")
    public static ReportModel of(@Nullable Partition partition,
            InfiniteCentroids centroids, ReportOptions options) {
        Objects.requireNonNull(centroids, "InfiniteCentroids must not be null");
        if (partition == null || partition.size() <= 0) {
            return new ReportModel(partition, options,
                    List.<BitFrequency> of(), List.<ClassReport> of(),
                    options.showNearness() ? new double[1][1] : null);
        }

        int k = partition.size();
        int l = centroids.get(0).getLength();

        List<BitFrequency> totalFrequencies = computeTotalFrequencies(partition,
                k, l);
        List<ClassReport> classReports = new ArrayList<>(k);
        for (int i = 1; i <= k; i++) {
            classReports.add(computeClassReport(i, partition, centroids,
                    options, k, l));
        }

        double[][] matrix = options.showNearness()
                ? ReportGenerator.classNearnessMatrix(partition, centroids,
                        options)
                : null;

        return new ReportModel(partition, options, totalFrequencies,
                classReports, matrix);
    }

    @SuppressWarnings("null")
    private static List<BitFrequency> computeTotalFrequencies(
            Partition partition,
            int k, int l) {
        int[] counts = new int[l];
        for (int i = 1; i <= k; i++) {
            for (BinaryVector bv : partition.getElements(i)) {
                addBits(counts, bv);
            }
        }
        return buildFrequencies(counts, l, partition.getNumberOfVectors());
    }

    @SuppressWarnings("null")
    private static ClassReport computeClassReport(int i, Partition partition,
            InfiniteCentroids centroids, ReportOptions options, int k, int l) {
        List<BinaryVector> vectors = new ArrayList<>();
        for (BinaryVector bv : partition.getElements(i)) {
            vectors.add(bv);
        }
        int[] counts = new int[l];
        for (BinaryVector bv : vectors) {
            addBits(counts, bv);
        }

        int nearestClass = 0;
        double nearestDistance = 0.0;
        if (options.showNearness()) {
            nearestClass = findNearest(partition, centroids, i, k,
                    options.useHellinger());
            nearestDistance = nearestClass == 0 ? 0.0
                    : ReportGenerator.classNearness(partition, centroids, i,
                            nearestClass, options.useHellinger());
        }

        return new ClassReport(i, k, vectors.size(), nearestClass,
                nearestDistance,
                buildFrequencies(counts, l, vectors.size()));
    }

    /** Finds the closest other cluster to {@code target} by class nearness. */
    private static int findNearest(Partition partition,
            InfiniteCentroids centroids, int target, int k, boolean hellinger) {
        int nearest = 0;
        double best = Double.MAX_VALUE;
        for (int j = 1; j <= k; j++) {
            if (j == target) {
                continue;
            }
            double d = ReportGenerator.classNearness(partition, centroids,
                    target, j, hellinger);
            if (d < best) {
                best = d;
                nearest = j;
            }
        }
        return nearest;
    }

    private static void addBits(int[] counts, BinaryVector bv) {
        int len = Math.min(bv.getLength(), counts.length);
        for (int p = 1; p < len; p++) {
            counts[p] += bv.get(p);
        }
    }

    private static List<BitFrequency> buildFrequencies(int[] counts, int l,
            int total) {
        List<BitFrequency> result = new ArrayList<>();
        for (int p = 1; p < l; p++) {
            double percentage = total > 0
                    ? (double) counts[p] / total * 100.0
                    : 0.0;
            result.add(new BitFrequency(p, counts[p], percentage));
        }
        return result;
    }

    @SuppressWarnings("null")
    private ReportModel(@Nullable Partition partition,
            ReportOptions options, List<BitFrequency> totalFrequencies,
            List<ClassReport> classReports,
            double @Nullable [][] matrixValues) {
        this.partition = partition;
        this.options = options;
        this.totalFrequencies = List.<BitFrequency> copyOf(totalFrequencies);
        this.classReports = List.<ClassReport> copyOf(classReports);
        this.matrixValues = matrixValues;
    }

    /** Returns the partition this model was computed from, or {@code null}. */
    @Nullable
    public Partition partition() {
        return partition;
    }

    /** Returns the total-frequency rows (one per bit position). */
    public List<BitFrequency> totalFrequencies() {
        return totalFrequencies;
    }

    /** Returns one {@link ClassReport} per cluster, in class order. */
    public List<ClassReport> classReports() {
        return classReports;
    }

    /**
     * Returns the affinity/nearness matrix (1-based indexing), or {@code null}
     * when nearness output is disabled. The diagonal holds its default value
     * and should be rendered as a dash by callers.
     */
    public double @Nullable [][] matrixValues() {
        return matrixValues;
    }

    /** Number of clusters in the partition (1-based). */
    public int clusterCount() {
        return partition == null ? 0 : partition.size();
    }

    /** Whether the total-frequencies section should be rendered. */
    public boolean showTotalFrequencies() {
        return options.showTotalFrequencies();
    }

    /** Whether the class nearness matrix should be rendered. */
    public boolean showNearness() {
        return options.showNearness();
    }

    /** Whether per-bit digit strings (0/1/-) should accompany frequencies. */
    public boolean printDigitsEnabled() {
        return options.printDigits();
    }

    /** A single bit's frequency: its 1-based position, count and percentage. */
    public record BitFrequency(int bit, int count, double percentage) {

        /**
         * Derives the per-bit digit (0/1/-) used by {@code -d} output: '1' when
         * the probability exceeds 50%, '0' below 50%, and '-' at exactly 50%.
         */
        public char digit() {
            double probability = percentage / 100.0;
            if (probability > 0.5) {
                return '1';
            }
            if (probability < 0.5) {
                return '0';
            }
            return '-';
        }
    }

    /** Per-class summary: size, nearest neighbour and per-bit frequencies. */
    public record ClassReport(int classNumber, int totalClusters, int size,
            int nearestClass, double nearestDistance,
            List<BitFrequency> frequencies) {

        /** Whether a nearest-neighbour line should be shown for this class. */
        public boolean hasNearest() {
            return nearestClass != 0;
        }
    }
}
