/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.report;

import java.util.List;

import com.vaadin.flow.component.html.Table;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.binclass.algorithms.report.ReportModel.BitFrequency;

/**
 * A small, styled HTML table that renders per-bit frequency data as three
 * columns: bit position, count and percentage. Used both for the total
 * frequencies section and inside each {@link ClassInfoComponent}. The
 * {@code frequency-table} class hook lets CSS style it consistently with the
 * affinity matrix (see PRD §18.5).
 */
@NullMarked
public class FrequencyTable extends Table {

    /** CSS class applied to every instance for styling and test selection. */
    public static final String CLASS_NAME = "frequency-table";

    /**
     * Builds a frequency table from the given rows without a digit column.
     *
     * @param frequencies
     *            the per-bit frequency rows, may be {@code null}
     */
    public FrequencyTable(@Nullable List<BitFrequency> frequencies) {
        this(frequencies, false);
    }

    /**
     * Builds a frequency table from the given rows. An empty or {@code null}
     * list still renders the header so callers can show an empty section.
     *
     * @param frequencies
     *            the per-bit frequency rows, may be {@code null}
     * @param showDigit
     *            when {@code true}, append a digit column (0/1/-) derived from
     *            each bit's probability, mirroring the {@code -d} flag
     */
    public FrequencyTable(@Nullable List<BitFrequency> frequencies,
            boolean showDigit) {
        addClassName(CLASS_NAME);

        String[] header = { "Bit", "Count", "Percentage" };
        if (showDigit) {
            header = new String[] { "Bit", "Count", "Percentage", "Digit" };
        }
        addHeaderRow(header);

        if (frequencies == null) {
            return;
        }
        for (BitFrequency row : frequencies) {
            if (showDigit) {
                addRow(Integer.toString(row.bit()),
                        Integer.toString(row.count()),
                        String.format(java.util.Locale.ROOT, "%.1f%%",
                                row.percentage()),
                        Character.toString(row.digit()));
            } else {
                addRow(Integer.toString(row.bit()),
                        Integer.toString(row.count()),
                        String.format(java.util.Locale.ROOT, "%.1f%%",
                                row.percentage()));
            }
        }
    }
}
