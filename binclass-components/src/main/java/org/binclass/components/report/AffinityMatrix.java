/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.report;

import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.html.Table;
import com.vaadin.flow.component.html.TableCell;
import com.vaadin.flow.component.html.TableRow;
import org.jspecify.annotations.NullMarked;

/**
 * A styled HTML table that renders the class-nearness (or affinity) matrix as a
 * square grid: a header row and column numbered {@code 1..k}, a left label
 * column naming each class, and one cell per cluster pair. The diagonal is
 * shown as a dash. Instead of inline cell styles, every logical cell carries a
 * CSS class (see {@link #CLASS_NAME} and the {@code matrix-*} hooks) so all
 * styling lives in {@code affinitymatrix.css} (imported via
 * {@code @StyleSheet}). Cells do not wrap so wide matrices stay compact.
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
     * Creates an empty affinity matrix carrying its {@code affinity-matrix}
     * class and the {@code report-affinity} id used by tests. Call
     * {@link #render(double[][], int)} to populate it.
     */
    public AffinityMatrix() {
        addClassName(CLASS_NAME);
        setId("report-affinity");
    }

    /**
     * Renders the given symmetric matrix, clearing any previous content. The
     * array uses 1-based indexing; {@code matrix[i][j]} is the distance between
     * clusters {@code i} and {@code j}.
     *
     * @param matrix
     *            the nearness/affinity matrix (1-based), may be {@code null}
     * @param k
     *            number of clusters (1-based)
     */
    @SuppressWarnings("null")
    public void render(double[][] matrix, int k) {
        removeAllRows();
        if (matrix == null || k <= 0) {
            return;
        }

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
            row[0] = "Class " + i;
            for (int j = 1; j <= k; j++) {
                row[j] = i == j ? DIAGONAL_CELL : formatValue(matrix[i][j]);
            }
            TableRow dataRow = addRow(row);
            List<TableCell> cells = dataRow.getCells();
            cells.get(0).addClassName("matrix-label");
            for (int j = 1; j <= k; j++) {
                cells.get(j)
                        .addClassName(
                                i == j ? "matrix-diagonal" : "matrix-cell");
            }
        }
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
