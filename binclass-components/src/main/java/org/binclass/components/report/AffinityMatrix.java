/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.report;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import com.vaadin.flow.component.html.Table;
import com.vaadin.flow.component.html.TableCell;
import com.vaadin.flow.component.html.TableRow;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;

/**
 * A styled HTML table that renders the class-nearness (or affinity) matrix as a
 * square grid: a header row and column numbered {@code 1..k}, a left label
 * column naming each class, and one cell per cluster pair. The diagonal is
 * shown as a dash. Each data cell carries an inline background color
 * interpolated between green (the smallest observed distance), orange (a
 * mid-range distance) and red (the largest observed distance), so the whole
 * matrix reads as a heat map. Row labels use the {@code X: NAME} form where
 * {@code NAME} is the most common vector name in that cluster. Cells do not
 * wrap so wide matrices stay compact.
 */
@NullMarked
public class AffinityMatrix extends Table {

    /**
     * CSS class applied to the table element for styling and test selection.
     */
    public static final String CLASS_NAME = "affinity-matrix";

    /** Text for a diagonal matrix cell (self-distance). */
    private static final String DIAGONAL_CELL = "-";

    /**
     * Background color for the smallest distance: light green {@code #a0f0a0}.
     */
    private static final int COLOR_MIN_R = 0xa0;
    private static final int COLOR_MIN_G = 0xf0;
    private static final int COLOR_MIN_B = 0xa0;

    /**
     * Background color for a mid-range distance: light orange {@code #f0f0a0}.
     */
    private static final int COLOR_MID_R = 0xf0;
    private static final int COLOR_MID_G = 0xf0;
    private static final int COLOR_MID_B = 0xa0;

    /** Background color for the largest distance: light red {@code #f0a0a0}. */
    private static final int COLOR_MAX_R = 0xf0;
    private static final int COLOR_MAX_G = 0xa0;
    private static final int COLOR_MAX_B = 0xa0;

    /**
     * Creates an empty affinity matrix carrying its {@code affinity-matrix}
     * class and the {@code report-affinity} id used by tests. Call
     * {@link #render(double[][], int, org.binclass.algorithms.core.Partition)}
     * to populate it.
     */
    public AffinityMatrix() {
        addClassName(CLASS_NAME);
        setId("report-affinity");
    }

    /**
     * Renders the given symmetric matrix, clearing any previous content. The
     * array uses 1-based indexing; {@code matrix[i][j]} is the distance between
     * clusters {@code i} and {@code j}. Every data cell gets a background color
     * interpolated from the observed minimum to maximum distance in the matrix.
     *
     * @param matrix
     *            the nearness/affinity matrix (1-based), may be {@code null}
     * @param k
     *            number of clusters (1-based)
     * @param partition
     *            the partition whose vectors supply each cluster's name, or
     *            {@code null} to fall back to bare indices as row labels
     */
    @SuppressWarnings("null")
    public void render(double[][] matrix, int k,
            @Nullable Partition partition) {
        removeAllRows();
        if (matrix == null || k <= 0) {
            return;
        }

        String[] names = clusterNames(partition, k);

        MatrixRange range = matrixRange(matrix, k);
        double min = range.min();
        double max = range.max();

        String[] header = new String[k + 1];
        for (int j = 0; j <= k; j++) {
            header[j] = j == 0 ? "" : Integer.toString(j);
        }
        TableRow headerRow = addHeaderRow(header);
        headerRow.getCells().get(0).addClassName("matrix-corner");
        for (TableCell cell : headerRow.getCells()) {
            cell.addClassName("matrix-header-cell");
        }

        for (int i = 1; i <= k; i++) {
            String[] row = new String[k + 1];
            row[0] = i + ": " + names[i];
            for (int j = 1; j <= k; j++) {
                row[j] = i == j ? DIAGONAL_CELL : formatValue(matrix[i][j]);
            }
            TableRow dataRow = addRow(row);
            List<TableCell> cells = dataRow.getCells();
            cells.get(0).addClassName("matrix-label");
            for (int j = 1; j <= k; j++) {
                TableCell cell = cells.get(j);
                if (i == j) {
                    // Diagonal is a self-distance shown as a dash and keeps its
                    // default background rather than being color-coded.
                    cell.addClassName("matrix-diagonal");
                } else {
                    cell.addClassName("matrix-cell");
                    cell.getStyle().set("background-color",
                            colorFor(matrix[i][j], min, max));
                }
            }
        }
    }

    /**
     * Derives the most common vector name for each cluster (1-based), used as
     * the row label. Prefers each vector's class-name, falls back to its strain
     * identifier, and finally to the one-based index when a cluster carries no
     * names at all. Mirrors {@code GlaViewTranslator.mostFrequentName}.
     *
     * @param partition
     *            the partition whose clusters supply the vectors; may be
     *            {@code null} to fall back to bare indices
     * @param k
     *            number of clusters (1-based)
     * @return a 1-based array of cluster names, never {@code null}
     */
    private static String[] clusterNames(@Nullable Partition partition, int k) {
        String[] names = new String[k + 1];
        for (int i = 1; i <= k; i++) {
            VectorSet cluster = partition == null ? null
                    : partition.getCluster(i - 1);
            names[i] = dominantName(cluster, i);
        }
        return names;
    }

    /**
     * Returns the most common non-blank label among a cluster's vectors,
     * preferring class-names over strain identifiers and falling back to the
     * one-based index when none are present. Ties keep whichever label was seen
     * first in insertion order.
     *
     * @param cluster
     *            the cluster's vectors; may be {@code null} or empty
     * @param fallbackIndex
     *            the 1-based cluster index used as name when no label exists
     * @return the dominant name, or {@code String.valueOf(fallbackIndex)}
     */
    @SuppressWarnings("null")
    private static String dominantName(@Nullable VectorSet cluster,
            int fallbackIndex) {
        if (cluster != null) {
            String className = dominantLabel(cluster,
                    BinaryVector::getClassName);
            if (className != null) {
                return className;
            }
            String strain = dominantLabel(cluster, BinaryVector::getStrain);
            if (strain != null) {
                return strain;
            }
        }
        return Integer.toString(fallbackIndex);
    }

    /**
     * Returns the most frequent non-blank label produced by {@code labelOf} for
     * the given vectors, or {@code null} when every vector carries a blank
     * label. Ties keep whichever label was seen first in insertion order.
     *
     * @param cluster
     *            the cluster's binary vectors (may be empty)
     * @param labelOf
     *            extracts a label from each vector
     * @return the dominant label, or {@code null} when none is present
     */
    @SuppressWarnings("null")
    private static @Nullable String dominantLabel(@Nullable VectorSet cluster,
            Function<BinaryVector, String> labelOf) {
        if (cluster == null) {
            return null;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BinaryVector bv : cluster) {
            String label = labelOf.apply(bv);
            if (label != null && !label.isBlank()) {
                counts.merge(label, 1, Integer::sum);
            }
        }
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    /**
     * Maps a distance to an interpolated background color between green (the
     * observed minimum), orange (the midpoint) and red (the observed maximum).
     * When the range collapses every value maps to the midpoint color.
     *
     * @param value
     *            the cell's distance
     * @param min
     *            the smallest observed distance in the matrix
     * @param max
     *            the largest observed distance in the matrix
     * @return an {@code #rrggbb} color string
     */
    @SuppressWarnings("null")
    protected static String colorFor(double value, double min, double max) {
        double t = (max == min) ? 0.5 : (value - min) / (max - min);
        t = Math.max(0.0, Math.min(1.0, t));
        int r, g, b;
        if (t <= 0.5) {
            double f = t / 0.5;
            r = clamp((int) Math
                    .round(COLOR_MIN_R + (COLOR_MID_R - COLOR_MIN_R) * f));
            g = clamp((int) Math
                    .round(COLOR_MIN_G + (COLOR_MID_G - COLOR_MIN_G) * f));
            b = clamp((int) Math
                    .round(COLOR_MIN_B + (COLOR_MID_B - COLOR_MIN_B) * f));
        } else {
            double f = (t - 0.5) / 0.5;
            r = clamp((int) Math
                    .round(COLOR_MID_R + (COLOR_MAX_R - COLOR_MID_R) * f));
            g = clamp((int) Math
                    .round(COLOR_MID_G + (COLOR_MAX_G - COLOR_MID_G) * f));
            b = clamp((int) Math
                    .round(COLOR_MID_B + (COLOR_MAX_B - COLOR_MID_B) * f));
        }
        return String.format("#%02x%02x%02x", r, g, b);
    }

    /** Clamps a channel value to the valid {@code 0..255} range. */
    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /**
     * Smallest and largest distance observed across a matrix's data region,
     * used to scale the per-cell background colors of the class-nearness grid.
     *
     * @param min
     *            the smallest distance found
     * @param max
     *            the largest distance found
     */
    record MatrixRange(double min, double max) {
    }

    /**
     * Scans the {@code 1..k} data region of a symmetric matrix and returns its
     * observed minimum and maximum distance. Diagonal cells (self-distance)
     * and any cell whose distance is exactly zero are excluded from the range,
     * so the color scale reflects only real pairwise separations.
     *
     * @param matrix
     *            the nearness/affinity matrix (1-based), may be {@code null}
     * @param k
     *            number of clusters (1-based)
     * @return the observed {@link MatrixRange}, or a zero range when empty
     */
    static MatrixRange matrixRange(double[][] matrix, int k) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (int i = 1; i <= k; i++) {
            for (int j = 1; j <= k; j++) {
                double v = matrix[i][j];
                if (v == 0.0) {
                    continue; // skip diagonal / zero distances from the range
                }
                if (v < min) {
                    min = v;
                }
                if (v > max) {
                    max = v;
                }
            }
        }
        return new MatrixRange(min, max);
    }

    /**
     * Formats a matrix value compactly: integers render without a decimal point
     * (the default Hamming distance is an integer count of differing
     * positions), while Hellinger or affinity-transform distances keep two
     * decimals with trailing zeros stripped.
     *
     * @param value
     *            the cell value to format
     * @return a compact, locale-independent string representation
     */
    @SuppressWarnings("null")
    private static String formatValue(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        String formatted = String.format(Locale.ROOT, "%.2f", value);
        int end = formatted.length();
        while (end > 0 && formatted.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && formatted.charAt(end - 1) == '.') {
            end--;
        }
        return formatted.substring(0, end);
    }
}
