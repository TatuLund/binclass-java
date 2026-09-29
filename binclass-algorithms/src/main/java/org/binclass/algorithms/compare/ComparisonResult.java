/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.compare;

import java.util.Objects;

/**
 * Immutable result of comparing two partitions.
 * <p>
 * Mirrors the output of C {@code do_comparison()} /
 * {@code comparison_results()} from {@code compare.c}: a contingency matrix
 * counting how many vectors share each (P1-class, P2-class) pair, the nearness
 * distance derived from that matrix, and the requested print mode.
 * </p>
 */
public record ComparisonResult(
        int[][] matrix,
        double distance,
        int printMode) {

    /**
     * Creates a comparison result.
     *
     * @param matrix
     *            contingency matrix where {@code matrix[p2 - 1][p1 - 1]} holds
     *            the number of vectors assigned to class {@code p1} in P1 and
     *            class {@code p2} in P2 (1-based indices)
     * @param distance
     *            the nearness distance computed from the matrix
     * @param printMode
     *            the requested print mode (1=nearness, 2=totalfreq,
     *            3=partition)
     */
    public ComparisonResult {
        Objects.requireNonNull(matrix, "Matrix must not be null");
        if (printMode < 1 || printMode > 3) {
            throw new IllegalArgumentException(
                    "Print mode must be 1, 2, or 3, got: " + printMode);
        }
    }

    /**
     * Compares this result to another for equality.
     * <p>
     * Two results are equal when their matrices have the same shape and cell
     * values, and their distance and print mode match. Array fields use
     * {@link java.util.Arrays#deepEquals} so element content is compared rather
     * than reference identity.
     * </p>
     *
     * @param other
     *            the object to compare with
     * @return {@code true} when the objects are structurally equal
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ComparisonResult(int[][] m, double d, int pm))) {
            return false;
        }
        return Double.compare(distance, d) == 0
                && printMode == pm
                && java.util.Arrays.deepEquals(matrix, m);
    }

    /**
     * Returns a hash code for this result.
     * <p>
     * Combines the matrix content with the distance and print mode so equal
     * results produce identical codes.
     * </p>
     *
     * @return the computed hash code
     */
    @Override
    public int hashCode() {
        return java.util.Objects.hash(java.util.Arrays.deepHashCode(matrix),
                Double.hashCode(distance), printMode);
    }

    /**
     * Returns a string representation of this result.
     * <p>
     * Delegates to {@link #render()} so the textual form matches the report
     * written by the compare command.
     * </p>
     *
     * @return the rendered comparison as a string
     */
    @Override
    public String toString() {
        return render();
    }

    /**
     * Returns the number of rows in the contingency matrix.
     * <p>
     * Equal to the larger cluster count of the two partitions (clamped at one),
     * mirroring C's {@code k2} dimension used when building the matrix.
     * </p>
     *
     * @return row count (1-based class count of P2)
     */
    public int rows() {
        return matrix.length;
    }

    /**
     * Returns the number of columns in the contingency matrix.
     * <p>
     * Equal to the larger cluster count of the two partitions (clamped at one),
     * mirroring C's {@code k1} dimension used when building the matrix.
     * </p>
     *
     * @return column count (1-based class count of P1)
     */
    public int columns() {
        return matrix[0].length;
    }

    /**
     * Returns the number of vectors assigned to a given (P1-class, P2-class)
     * pair.
     *
     * @param p1Class
     *            1-based class index in partition 1
     * @param p2Class
     *            1-based class index in partition 2
     * @return the count of vectors sharing both classes
     */
    public int count(int p1Class, int p2Class) {
        return matrix[p2Class - 1][p1Class - 1];
    }

    /**
     * Returns a formatted rendering of the contingency matrix.
     * <p>
     * Mirrors C {@code print_comp_matrix()} from {@code compare.c}: prints an
     * upper-left triangle with class labels on both axes and per-cell counts,
     * followed by row sums (P1 classes) and column sums (P2 classes).
     * </p>
     *
     * @return the matrix rendered as a multi-line string
     */
    public String renderMatrix() {
        StringBuilder sb = new StringBuilder();
        int rows = rows();
        int cols = columns();

        // Column labels (P1 classes) across the top.
        sb.append("      ");
        for (int c = 1; c <= cols; c++) {
            sb.append(String.format("%6d", c));
        }
        sb.append("\n");

        // Body: each row is a P2 class with its count per P1 class.
        for (int r = 1; r <= rows; r++) {
            sb.append(String.format("%4d |", r));
            long rowSum = 0;
            for (int c = 1; c <= cols; c++) {
                int value = matrix[r - 1][c - 1];
                rowSum += value;
                sb.append(String.format("%6d", value));
            }
            sb.append(" | ").append(rowSum).append("\n");
        }

        // Row labels (P2 classes) along the bottom.
        sb.append("      ");
        for (int c = 1; c <= cols; c++) {
            sb.append(String.format("%6d", c));
        }
        sb.append("\n");

        return sb.toString();
    }

    /**
     * Returns a human-readable summary of the comparison.
     * <p>
     * Combines the distance and the rendered matrix, matching the layout C
     * writes via {@code comparison_results()} to the results file.
     * </p>
     *
     * @return the full comparison report as a string
     */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("COMPARISON RESULTS\n");
        sb.append("==================\n\n");
        sb.append(renderMatrix());
        sb.append("\nOverall distance = ")
                .append(String.format("%.4f", distance))
                .append("\n");
        return sb.toString();
    }
}
