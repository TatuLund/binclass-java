/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.io;

import java.util.Objects;

/**
 * Writes binary vectors to various output formats for data export.
 * <p>
 * Mirrors functions from {@code binstuff.c} in the original C codebase: handles
 * writing of binary vector data in different representations including PIC
 * format, empirical format, and custom BinClass-specific formats. Supports both
 * text-based and compact binary representations.
 * </p>
 * <p>
 * Key operations:
 * <ul>
 * <li>{@link #writePicFormat(int[])} — writes vector in PIC (Pattern
 * Information Content) format</li>
 * <li>{@link #writeEmpiricalFormat(int[])} — writes vector in empirical
 * frequency format</li>
 * <li>{@link #writeBinaryFormat(int[])} — writes compact binary
 * representation</li>
 * </ul>
 * </p>
 */
public final class VectorWriter {

    private static final String VECTOR_MUST_NOT_BE_NULL = "Vector must not be null";

    /** Default output buffer size for string operations */
    private static final int DEFAULT_BUFFER_SIZE = 1024;

    private VectorWriter() {
        // Utility class — prevent instantiation
    }

    /**
     * Writes a binary vector in PIC (Pattern Information Content) format.
     * <p>
     * Equivalent to C function {@code pic_write_bv()} from {@code binstuff.c}.
     * Outputs the vector as a sequence of 0s and 1s separated by spaces,
     * suitable for human-readable display or text-based processing pipelines.
     * </p>
     * <p>
     * Format: "0 1 1 0 ..." (space-separated binary values)
     * </p>
     *
     * @param vector
     *            the binary vector to write (array of 0s and 1s)
     * @return a string containing the PIC-formatted representation
     */
    public static String writePicFormat(int[] vector) {
        Objects.requireNonNull(vector, VECTOR_MUST_NOT_BE_NULL);

        StringBuilder sb = new StringBuilder(DEFAULT_BUFFER_SIZE);
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    /**
     * Writes a binary vector in empirical frequency format.
     * <p>
     * Equivalent to C function {@code emp_write_bv()} from {@code binstuff.c}.
     * Outputs the vector as decimal frequencies representing observed bit
     * values, useful for statistical analysis and comparison with expected
     * distributions.
     * </p>
     * <p>
     * Format: "0 1 1 0 ..." (space-separated frequency counts)
     * </p>
     *
     * @param vector
     *            the binary vector to write (array of 0s and 1s)
     * @return a string containing the empirical-formatted representation
     */
    public static String writeEmpiricalFormat(int[] vector) {
        Objects.requireNonNull(vector, VECTOR_MUST_NOT_BE_NULL);

        StringBuilder sb = new StringBuilder(DEFAULT_BUFFER_SIZE);
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            // Convert binary value to frequency representation
            int freq = vector[i] == 1 ? 1 : 0;
            sb.append(freq);
        }
        return sb.toString();
    }

    /**
     * Writes a binary vector in compact binary format.
     * <p>
     * Equivalent to C function {@code bin_write_bv()} from {@code binstuff.c}.
     * Outputs the vector as a hexadecimal string representing the bit pattern,
     * suitable for efficient storage and transmission of large datasets.
     * </p>
     * <p>
     * Format: "FF0A" (hexadecimal representation of bit pattern)
     * </p>
     *
     * @param vector
     *            the binary vector to write (array of 0s and 1s)
     * @return a string containing the compact binary-formatted representation
     */
    public static String writeBinaryFormat(int[] vector) {
        Objects.requireNonNull(vector, VECTOR_MUST_NOT_BE_NULL);

        // Convert bit array to hexadecimal representation
        StringBuilder hex = new StringBuilder();
        int currentNibble = 0;
        int bitsInNibble = 0;

        for (int i = 0; i < vector.length; i++) {
            if (vector[i] == 1) {
                currentNibble |= (1 << (3 - bitsInNibble));
            }
            bitsInNibble++;

            if (bitsInNibble == 4 || i == vector.length - 1) {
                hex.append(Integer.toHexString(currentNibble).toUpperCase());
                currentNibble = 0;
                bitsInNibble = 0;

                // Pad with zeros if needed to complete last nibble
                while (bitsInNibble < 4 && i == vector.length - 1) {
                    hex.append('0');
                    bitsInNibble++;
                }
            }
        }

        return hex.toString();
    }

    /**
     * Writes a binary vector with strain identifier prefix.
     * <p>
     * Helper method that prepends a strain name to the formatted vector output,
     * useful for datasets where each observation has an associated label or ID.
     * </p>
     *
     * @param vector
     *            the binary vector to write (array of 0s and 1s)
     * @param strain
     *            the strain identifier string, or null if not applicable
     * @return a string containing the prefixed formatted representation
     */
    public static String writeWithStrain(int[] vector, String strain) {
        Objects.requireNonNull(vector, VECTOR_MUST_NOT_BE_NULL);

        StringBuilder sb = new StringBuilder(DEFAULT_BUFFER_SIZE);
        if (strain != null && !strain.isEmpty()) {
            sb.append(strain).append('\t');
        }
        sb.append(writePicFormat(vector));
        return sb.toString();
    }

    /**
     * Writes multiple binary vectors as a batch.
     * <p>
     * Convenience method that formats an array of vectors into a single string,
     * with each vector on a separate line. Useful for generating complete
     * datasets.
     * </p>
     *
     * @param vectors
     *            the binary vectors to write (array of arrays)
     * @return a multi-line string containing all formatted vectors
     */
    public static String writeBatch(int[][] vectors) {
        Objects.requireNonNull(vectors, "Vectors array must not be null");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vectors.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(writePicFormat(vectors[i]));
        }
        return sb.toString();
    }

    /**
     * Writes a binary vector with header metadata.
     * <p>
     * Helper method that prepends header information (vector count and length)
     * to the formatted output, suitable for creating complete data files.
     * </p>
     *
     * @param vectors
     *            the binary vectors to write (array of arrays)
     * @return a string containing header followed by all formatted vectors
     */
    public static String writeWithHeader(int[][] vectors) {
        Objects.requireNonNull(vectors, "Vectors array must not be null");

        StringBuilder sb = new StringBuilder();
        if (vectors.length > 0) {
            int length = vectors[0].length;
            sb.append(vectors.length).append(' ').append(length).append('\n');
        }
        sb.append(writeBatch(vectors));
        return sb.toString();
    }
}
