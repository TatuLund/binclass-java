/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.report;

/**
 * Options controlling how {@link ReportGenerator} renders a statistical report.
 * <p>
 * Mirrors the reporting switches parsed by {@code parse_report()} in the
 * original C codebase ({@code parser.c}) and honoured inside
 * {@code generate_report()} ({@code report.c}):
 * </p>
 * <ul>
 * <li>{@code printDigits} — {@code -d}: emit per-bit digit strings (0/1/-)
 * alongside percentages.</li>
 * <li>{@code affinityMatrix} — {@code -a}: transform nearness distances as
 * {@code (l - d) / l} and additionally report the farest class.</li>
 * <li>{@code useHellinger} — {@code -h}: compute the nearness matrix with
 * Hellinger distance instead of Hamming-based class nearness.</li>
 * <li>{@code reportParams} — {@code -p}: bitmask selecting which sections are
 * emitted. A value of {@code 0} selects every section, matching C's {@code -p0}
 * behaviour.</li>
 * </ul>
 */
public record ReportOptions(
        /** Emit per-bit digit strings in frequency output ({@code -d}). */
        boolean printDigits,

        /**
         * Transform nearness as affinity matrix and show farest class
         * ({@code -a}).
         */
        boolean affinityMatrix,

        /** Use Hellinger distance for the nearness matrix ({@code -h}). */
        boolean useHellinger,

        /**
         * Reporting-parameters bitmask selecting sections ({@code -p}, 0 =
         * all).
         */
        int reportParams) {

    /** Default options: plain Hamming report with every section enabled. */
    public static final ReportOptions DEFAULT = new ReportOptions(false, false,
            false, 0);

    /** Reporting parameter flags; values match {@code original/vars.c}. */
    public static final int RP_TOTALFREQ = 1;
    public static final int RP_NEARNESS = 2;
    public static final int RP_PARTITION = 4;
    public static final int RP_MATCH = 8;
    public static final int RP_NEIGHBOR = 16;
    public static final int RP_FREQ = 32;
    public static final int RP_MATRIX = 64;

    /**
     * Whether the given reporting-parameter bit is enabled. A {@code 0}
     * {@code reportParams} value enables every section, matching C's
     * {@code -p0} default of all sections.
     *
     * @param flag
     *            one of the {@code RP_*} constants
     * @return {@code true} when the section should be emitted
     */
    private boolean has(int flag) {
        return reportParams == 0 || (reportParams & flag) != 0;
    }

    /**
     * Whether the total frequencies section is enabled.
     *
     * @return {@code true} when the total frequencies section should be emitted
     */
    public boolean showTotalFrequencies() {
        return has(RP_TOTALFREQ);
    }

    /**
     * Whether per-class frequency listings are enabled.
     *
     * @return {@code true} when per-class frequency sections should be emitted
     */
    public boolean showPerClassFrequencies() {
        return has(RP_FREQ);
    }

    /**
     * Whether the class nearness matrix (and nearest/farest class info) is
     * enabled.
     *
     * @return {@code true} when nearness information should be emitted
     */
    public boolean showNearness() {
        return has(RP_NEARNESS);
    }
}
