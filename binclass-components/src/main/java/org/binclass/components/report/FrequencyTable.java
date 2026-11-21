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
import org.jspecify.annotations.Nullable;
import org.binclass.algorithms.report.ReportModel.BitFrequency;

/**
 * A small, styled HTML table that renders per-bit frequency data. Each bit
 * position is a column (headed by its 1-based index) and each metric — count,
 * percentage and optionally the {@code -d} digit — is a row. Swapping the old
 * tall layout (one row per bit) into a short wide one keeps the table compact
 * when many bits are present. Used both for the total frequencies section and
 * inside each {@link ClassInfoComponent}. Each metric row carries a style hook
 * ({@code frequency-count-row}, {@code frequency-percentage-row} and, when the
 * digit column is shown, {@code frequency-digit-row}) so CSS can border it like
 * the affinity matrix and right-align the numeric cells (see PRD §17.4). The
 * leading empty corner cell keeps bit-position headers aligned with their data.
 */
@NullMarked
public class FrequencyTable extends Table {

    /** CSS class applied to every instance for styling and test selection. */
    public static final String CLASS_NAME = "frequency-table";

    /** Label of the count row, placed in the first (metric) column. */
    private static final String COUNT_ROW = "Count";

    /** Label of the percentage row, placed in the first (metric) column. */
    private static final String PERCENTAGE_ROW = "Percentage";

    /** Label of the digit row, placed in the first (metric) column. */
    private static final String DIGIT_ROW = "Digit";

    /** CSS class hook for the count metric row, used by CSS styling. */
    public static final String COUNT_ROW_CLASS = "frequency-count-row";

    /** CSS class hook for the percentage metric row, used by CSS styling. */
    public static final String PERCENTAGE_ROW_CLASS = "frequency-percentage-row";

    /**
     * CSS class hook for the optional digit metric row, used by CSS styling.
     */
    public static final String DIGIT_ROW_CLASS = "frequency-digit-row";

    /**
     * Builds a frequency table without a digit column. Bit positions become
     * columns; count and percentage are rows. An empty or {@code null} list
     * still renders the header so callers can show an empty section.
     *
     * @param frequencies
     *            the per-bit frequency rows, may be {@code null}
     */
    public FrequencyTable(@Nullable List<BitFrequency> frequencies) {
        this(frequencies, false);
    }

    /**
     * Builds a frequency table. Bit positions become columns; count, percentage
     * and optionally the digit are rows. An empty or {@code null} list still
     * renders the header so callers can show an empty section.
     *
     * @param frequencies
     *            the per-bit frequency rows, may be {@code null}
     * @param showDigit
     *            when {@code true}, append a digit row (0/1/-) derived from
     *            each bit's probability, mirroring the {@code -d} flag
     */
    @SuppressWarnings("null")
    public FrequencyTable(@Nullable List<BitFrequency> frequencies,
            boolean showDigit) {
        addClassName(CLASS_NAME);

        int bits = frequencies == null ? 0 : frequencies.size();

        // Header row: an empty corner cell keeps the bit-position headers
        // aligned with their data columns, then one column per bit position.
        String[] header = new String[bits + 1];
        for (int b = 0; b < bits; b++) {
            header[b + 1] = Integer.toString(frequencies.get(b).bit());
        }
        TableRow headerRow = addHeaderRow(header);
        for (TableCell cell : headerRow.getCells()) {
            cell.addClassName("frequency-header-cell");
        }

        if (bits == 0) {
            return;
        }

        // Count row.
        String[] counts = new String[bits];
        for (int b = 0; b < bits; b++) {
            counts[b] = Integer.toString(frequencies.get(b).count());
        }
        addStyledRow(COUNT_ROW, COUNT_ROW_CLASS, counts);

        // Percentage row.
        String[] percentages = new String[bits];
        for (int b = 0; b < bits; b++) {
            percentages[b] = String.format(Locale.ROOT, "%.1f%%",
                    frequencies.get(b).percentage());
        }
        addStyledRow(PERCENTAGE_ROW, PERCENTAGE_ROW_CLASS, percentages);

        // Optional digit row.
        if (showDigit) {
            String[] digits = new String[bits];
            for (int b = 0; b < bits; b++) {
                digits[b] = Character.toString(frequencies.get(b).digit());
            }
            addStyledRow(DIGIT_ROW, DIGIT_ROW_CLASS, digits);
        }
    }

    /**
     * Prepends a metric label to the given column values, producing the single
     * {@code String[]} that {@link Table#addRow(String[])} expects. Index zero
     * holds the leading label; each bit value follows in its own column so the
     * header row stays aligned with the data below it.
     *
     * @param label
     *            the leading metric name
     * @param columns
     *            one value per bit column
     * @return a combined row array of length {@code columns.length + 1}
     */
    private static String[] concat(String label, String[] columns) {
        String[] row = new String[columns.length + 1];
        row[0] = label;
        System.arraycopy(columns, 0, row, 1, columns.length);
        return row;
    }

    /**
     * Adds a metric body row with the leading label and applies its style hook.
     * The label occupies the first column; each bit value follows in its own
     * column so headers stay aligned with their data.
     *
     * @param label
     *            the leading metric name (count, percentage or digit)
     * @param styleClass
     *            the CSS class hook applied to every cell in the row
     * @param columns
     *            one value per bit column
     */
    private void addStyledRow(String label, String styleClass,
            String[] columns) {
        TableRow row = addRow(concat(label, columns));
        for (TableCell cell : row.getCells()) {
            cell.addClassName(styleClass);
        }
        for (int b = 0; b < columns.length; b++) {
            row.getCells().get(b + 1).addClassName("frequency-number");
        }
    }
}
