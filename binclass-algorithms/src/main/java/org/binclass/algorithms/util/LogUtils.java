/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.util;

import java.util.Objects;

/**
 * Logging utilities for the BinClass algorithm suite.
 * <p>
 * Provides statistical profiling and function logging originally implemented in
 * C as {@code log_profile()} and {@code log_function()} in {@code logfile.c}.
 * These methods operate on byte arrays (no file I/O) so they can be used from
 * both the algorithms module and CLI layer.
 * </p>
 * <p>
 * All logging uses SLF4J + Logback (configured via parent POM). The original C
 * verbose mode maps to log levels: {@code DEBUG} = verbose, {@code INFO} =
 * normal, {@code WARN}/{@code ERROR} = errors.
 * </p>
 */
public final class LogUtils {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(LogUtils.class);

    /**
     * Total frequency counts across all vectors — mirrors C's global
     * {@code total_freqs[]} array
     */
    private static final double[] TOTAL_FREQS = new double[2001]; // MAX_LENGTH
                                                                  // + 1

    private LogUtils() {
        // Utility class — prevent instantiation
    }

    /**
     * Logs a statistical profile of frequency distributions across vector
     * lengths.
     * <p>
     * Equivalent to C function {@code log_profile()} from {@code logfile.c}:
     * 
     * <pre>{@code
     * void log_profile(FILE *o, int l, int s) {
     *   for (i=1; i<l; i++) {
     *     p = total_freqs[i] / (double)s;
     *     fprintf(o, "%4d: %4d, %1.5f\n", i, (int)total_freqs[i], p);
     *     total_freqs[i] = p;  // normalize for next call
     *   }
     * }
     * }</pre>
     * </p>
     * <p>
     * The frequency counts are normalized in-place after each call, so
     * subsequent calls show relative proportions rather than absolute counts.
     * </p>
     *
     * @param l
     *            the maximum vector length to profile (exclusive upper bound)
     * @param s
     *            the total number of samples (denominator for normalization)
     * @return formatted string representation of the statistical profile
     */
    public static String logProfile(int l, int s) {
        if (s <= 0) {
            throw new IllegalArgumentException(
                    "Total samples must be positive");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\nStatistical profile:\n");

        for (int i = 1; i < l; i++) {
            double p = TOTAL_FREQS[i] / (double) s;
            sb.append(String.format("%4d: %4d, %1.5f%n", i,
                    (int) TOTAL_FREQS[i], p));
            TOTAL_FREQS[i] = p; // Normalize for next call
        }
        sb.append("--\n\n");

        logger.debug("Statistical profile logged for l={}, s={}", l, s);
        return sb.toString();
    }

    /**
     * Logs function values (e.g., SC values) across cluster counts.
     * <p>
     * Equivalent to C function {@code log_function()} from {@code logfile.c}:
     * 
     * <pre>{@code
     * void log_function(FILE *o, double *scs, int lastk) {
     *   for (i=0; i<=(lastk+1); i++) {
     *     if (scs[i] < unassigned_sc()) fprintf(o, "%4d: %1.5f\n", i, scs[i]);
     *   }
     * }
     * }</pre>
     * </p>
     *
     * @param scs
     *            the array of function values (e.g., SC values for each k)
     * @param lastK
     *            the maximum cluster count index to log
     * @return formatted string representation of the logged function values
     */
    public static String logFunction(double[] scs, int lastK) {
        Objects.requireNonNull(scs, "SC array must not be null");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= lastK + 1; i++) {
            if (scs[i] < Double.MAX_VALUE) { // unassigned_sc() equivalent:
                                             // MAX_VALUE
                sb.append(String.format("%4d: %1.5f%n", i, scs[i]));
            }
        }

        logger.debug("Function values logged for lastK={}", lastK);
        return sb.toString();
    }

    /**
     * Resets the total frequency counts to zero.
     * <p>
     * Useful between independent profiling runs to avoid cross-contamination of
     * frequency data.
     * </p>
     */
    public static void resetTotalFreqs() {
        for (int i = 0; i < TOTAL_FREQS.length; i++) {
            TOTAL_FREQS[i] = 0.0;
        }
    }

    /**
     * Sets a total frequency count at the given index.
     *
     * @param index
     *            the vector length index (1-based)
     * @param value
     *            the frequency count to set
     */
    public static void setTotalFreq(int index, double value) {
        if (index < 0 || index >= TOTAL_FREQS.length) {
            throw new IllegalArgumentException("Index out of bounds: " + index
                    + ", max=" + (TOTAL_FREQS.length - 1));
        }
        TOTAL_FREQS[index] = value;
    }

    /**
     * Gets the total frequency count at the given index.
     *
     * @param index
     *            the vector length index (1-based)
     * @return the frequency count at that index
     */
    public static double getTotalFreq(int index) {
        if (index < 0 || index >= TOTAL_FREQS.length) {
            throw new IllegalArgumentException("Index out of bounds: " + index
                    + ", max=" + (TOTAL_FREQS.length - 1));
        }
        return TOTAL_FREQS[index];
    }
}
