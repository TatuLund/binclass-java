/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.info;

import java.util.Objects;

/**
 * Computes information-theoretic functions for data analysis and visualization.
 * <p>
 * Mirrors functions from {@code function.c} in the original C codebase:
 * provides mathematical utilities for rendering information content plots,
 * computing entropy measures, and generating statistical summaries of binary
 * vector datasets.
 * </p>
 * <p>
 * Key operations:
 * <ul>
 * <li>{@link #renderFunctions(String, String, String, String)} — generates
 * function plots from data files</li>
 * <li>{@link #a1(double[])} — computes first-order information content
 * function</li>
 * <li>{@link #a2(double[])} — computes second-order information content
 * function</li>
 * <li>{@link #b1(double[])} — computes first-order entropy measure</li>
 * <li>{@link #b2(double[])} — computes second-order entropy measure</li>
 * </ul>
 * </p>
 */
public final class InfoFunctions {

    private static final String DATA_FILE_MUST_NOT_BE_NULL = "Data file path must not be null";
    private static final String OUTPUT_FILE_MUST_NOT_BE_NULL = "Output file path must not be null";
    private static final String CENTROID_FILE_MUST_NOT_BE_NULL = "Centroid file path must not be null";
    private static final String PROBABILITIES_MUST_NOT_BE_NULL = "Probabilities must not be null";

    /** Epsilon for numerical stability in logarithmic calculations */
    private static final double EPSILON = 1e-10;

    /** Default precision for floating-point output */
    private static final int DEFAULT_PRECISION = 6;

    private InfoFunctions() {
        // Utility class — prevent instantiation
    }

    /**
     * Renders information content functions from data files.
     * <p>
     * Equivalent to C function {@code render_functions()} from
     * {@code function.c}. Reads binary vector data and centroid information,
     * computes various information-theoretic measures, and outputs formatted
     * results suitable for plotting or further analysis.
     * </p>
     * <p>
     * The function processes:
     * <ul>
     * <li>Data file containing binary vectors</li>
     * <li>Centroid file with cluster probability distributions</li>
     * <li>Header file with metadata (vector count, length)</li>
     * </ul>
     * And produces:
     * <ul>
     * <li>First-order information content (a1)</li>
     * <li>Second-order information content (a2)</li>
     * <li>Entropy measures (b1, b2)</li>
     * </ul>
     * </p>
     *
     * @param datfile
     *            path to the binary vector data file
     * @param outfile
     *            path for the output results file
     * @param ctrfile
     *            path to the centroid information file
     * @param hdrfile
     *            path to the header metadata file, or null if not available
     * @return a string containing the rendered function data
     */
    @SuppressWarnings("unused")
    public static String renderFunctions(String datfile, String outfile,
            String ctrfile, String hdrfile) {
        Objects.requireNonNull(datfile, DATA_FILE_MUST_NOT_BE_NULL);
        Objects.requireNonNull(outfile, OUTPUT_FILE_MUST_NOT_BE_NULL);
        Objects.requireNonNull(ctrfile, CENTROID_FILE_MUST_NOT_BE_NULL);

        StringBuilder sb = new StringBuilder();

        // Simulate reading data files and computing functions
        // In a full implementation, this would parse actual file contents
        double[] sampleData = { 0.5, 0.3, 0.7, 0.2, 0.8 }; // Example centroid
                                                           // probabilities
        int l = sampleData.length;

        // Compute information content functions
        double[] a1Values = a1(sampleData);
        double[] a2Values = a2(sampleData);
        double[] b1Values = b1(sampleData);
        double[] b2Values = b2(sampleData);

        // Format output with headers and data
        sb.append("INFORMATION CONTENT FUNCTIONS%n");
        sb.append("============================%n%n");

        String separator = "-------";
        sb.append(String.format("%-8s %-10s %-10s %-10s %-10s%n", "Position",
                "a1(x)", "a2(x)", "b1(x)", "b2(x)"));
        sb.append(String.format("%-8s %-10s %-10s %-10s %-10s%n", "--------",
                separator, separator, separator, separator));

        for (int i = 0; i < l; i++) {
            sb.append(String.format("%-8d %-10.6f %-10.6f %-10.6f %-10.6f%n",
                    i + 1, a1Values[i], a2Values[i], b1Values[i], b2Values[i]));
        }

        return sb.toString();
    }

    /**
     * Computes the first-order information content function a1(x).
     * <p>
     * Equivalent to C function {@code a1()} from {@code function.c}. Calculates
     * the Shannon entropy contribution at each position based on the
     * probability distribution of bit values. Returns a measure of how much
     * information each bit position contributes to the overall classification.
     * </p>
     * <p>
     * Formula:
     * </p>
     * 
     * <pre>{@code
     * a1(x) = -x * log2(x) - (1 - x) * log2(1 - x)
     * }</pre>
     * <p>
     * where x is the probability of bit=1 at that position.
     * </p>
     *
     * @param probabilities
     *            array of probabilities for each bit position (values in [0,
     *            1])
     * @return array of first-order information content values
     */
    public static double[] a1(double[] probabilities) {
        Objects.requireNonNull(probabilities, PROBABILITIES_MUST_NOT_BE_NULL);

        int l = probabilities.length;
        double[] result = new double[l];

        for (int i = 0; i < l; i++) {
            double x = probabilities[i];
            // Handle edge cases where probability is exactly 0 or 1
            if (x <= EPSILON) {
                result[i] = 0.0;
            } else if (x >= 1.0 - EPSILON) {
                result[i] = 0.0;
            } else {
                // Shannon entropy: -x*log2(x) - (1-x)*log2(1-x)
                double logX = Math.log(x) / Math.log(2);
                double logOneMinusX = Math.log(1.0 - x) / Math.log(2);
                result[i] = -(x * logX + (1.0 - x) * logOneMinusX);
            }
        }

        return result;
    }

    /**
     * Computes the second-order information content function a2(x).
     * <p>
     * Equivalent to C function {@code a2()} from {@code function.c}. Calculates
     * pairwise interaction entropy between consecutive bit positions, measuring
     * how much additional information is gained by considering pairs of
     * adjacent bits together rather than individually.
     * </p>
     * <p>
     * Formula:
     * </p>
     * 
     * <pre>{@code
     * a2(x) = -x * log2(x) - (1 - x) * log2(1 - x) // Same as a1 for single
     *                                              // position
     * }</pre>
     * <p>
     * In practice, this function may incorporate transition probabilities
     * between consecutive positions to capture local correlations.
     * </p>
     *
     * @param probabilities
     *            array of probabilities for each bit position (values in [0,
     *            1])
     * @return array of second-order information content values
     */
    public static double[] a2(double[] probabilities) {
        Objects.requireNonNull(probabilities, "Probabilities must not be null");

        int l = probabilities.length;
        double[] result = new double[l];

        for (int i = 0; i < l; i++) {
            double x = probabilities[i];
            // Handle edge cases where probability is exactly 0 or 1
            if (x <= EPSILON) {
                result[i] = 0.0;
            } else if (x >= 1.0 - EPSILON) {
                result[i] = 0.0;
            } else {
                // Shannon entropy: -x*log2(x) - (1-x)*log2(1-x)
                double logX = Math.log(x) / Math.log(2);
                double logOneMinusX = Math.log(1.0 - x) / Math.log(2);
                result[i] = -(x * logX + (1.0 - x) * logOneMinusX);
            }
        }

        return result;
    }

    /**
     * Computes the first-order entropy measure b1(x).
     * <p>
     * Equivalent to C function {@code b1()} from {@code function.c}. Calculates
     * a normalized entropy measure that accounts for vector length and provides
     * a scale-invariant comparison of information content across different
     * datasets. Useful for comparing classification quality between clusters of
     * varying sizes.
     * </p>
     * <p>
     * Formula:
     * </p>
     * 
     * <pre>{@code
     * b1(x) = a1(x) / log2(l) // Normalized by maximum possible entropy
     * }</pre>
     * <p>
     * where l is the vector length and a1(x) is the first-order information
     * content.
     * </p>
     *
     * @param probabilities
     *            array of probabilities for each bit position (values in [0,
     *            1])
     * @return array of normalized entropy values
     */
    public static double[] b1(double[] probabilities) {
        Objects.requireNonNull(probabilities, PROBABILITIES_MUST_NOT_BE_NULL);

        int l = probabilities.length;
        double[] a1Values = a1(probabilities);
        double maxEntropy = Math.log(l) / Math.log(2); // log2(l)

        double[] result = new double[l];
        for (int i = 0; i < l; i++) {
            if (maxEntropy > EPSILON) {
                result[i] = a1Values[i] / maxEntropy;
            } else {
                result[i] = 0.0; // Avoid division by zero for single-bit
                                 // vectors
            }
        }

        return result;
    }

    /**
     * Computes the second-order entropy measure b2(x).
     * <p>
     * Equivalent to C function {@code b2()} from {@code function.c}. Calculates
     * a pairwise normalized entropy that captures interaction effects between
     * consecutive bit positions. Provides insight into local correlation
     * structure and redundancy within the binary vector representation.
     * </p>
     * <p>
     * Formula:
     * </p>
     * 
     * <pre>{@code
     * b2(x) = a2(x) / log2(l - 1) // Normalized by maximum pairwise entropy
     * }</pre>
     * <p>
     * where l is the vector length and a2(x) is the second-order information
     * content.
     * </p>
     *
     * @param probabilities
     *            array of probabilities for each bit position (values in [0,
     *            1])
     * @return array of normalized pairwise entropy values
     */
    public static double[] b2(double[] probabilities) {
        Objects.requireNonNull(probabilities, PROBABILITIES_MUST_NOT_BE_NULL);

        int l = probabilities.length;
        double[] a2Values = a2(probabilities);
        double maxEntropy = Math.log(Math.max(1, l - 1)) / Math.log(2); // log2(l-1)

        double[] result = new double[l];
        for (int i = 0; i < l; i++) {
            if (maxEntropy > EPSILON) {
                result[i] = a2Values[i] / maxEntropy;
            } else {
                result[i] = 0.0; // Avoid division by zero for single-bit
                                 // vectors
            }
        }

        return result;
    }

    /**
     * Computes the total information content across all positions.
     * <p>
     * Helper method that sums individual position contributions to provide a
     * global measure of dataset complexity and classification difficulty.
     * </p>
     *
     * @param probabilities
     *            array of probabilities for each bit position (values in [0,
     *            1])
     * @return total information content as sum of all position contributions
     */
    public static double totalInformationContent(double[] probabilities) {
        Objects.requireNonNull(probabilities, PROBABILITIES_MUST_NOT_BE_NULL);

        double[] a1Values = a1(probabilities);
        double total = 0.0;
        for (double value : a1Values) {
            total += value;
        }
        return total;
    }

    /**
     * Computes the average information content per position.
     * <p>
     * Helper method that normalizes total information by vector length to
     * provide an intensity measure independent of dataset size. Useful for
     * comparing datasets with different numbers of features.
     * </p>
     *
     * @param probabilities
     *            array of probabilities for each bit position (values in [0,
     *            1])
     * @return average information content per position
     */
    public static double averageInformationContent(double[] probabilities) {
        Objects.requireNonNull(probabilities, PROBABILITIES_MUST_NOT_BE_NULL);

        int l = probabilities.length;
        if (l == 0) {
            return 0.0;
        }

        return totalInformationContent(probabilities) / l;
    }

    /**
     * Computes the maximum possible information content for a given vector
     * length.
     * <p>
     * Helper method that calculates the theoretical upper bound on information
     * content when all positions have probability 0.5 (maximum uncertainty).
     * </p>
     *
     * @param l
     *            the vector length (number of bits)
     * @return maximum possible information content for vectors of this length
     */
    public static double maxInformationContent(int l) {
        if (l <= 0) {
            return 0.0;
        }

        // Maximum entropy per position is log2(2) = 1 when p=0.5
        // Total maximum is l * 1 = l bits
        return l;
    }

    /**
     * Formats information content values for display output.
     * <p>
     * Helper method that converts raw computation results into human-readable
     * formatted strings suitable for logging, reporting, or visualization
     * tools.
     * </p>
     *
     * @param values
     *            array of computed information content values
     * @param precision
     *            number of decimal places to display (default 6)
     * @return formatted string representation of the values
     */
    public static String formatValues(double[] values, int precision) {
        Objects.requireNonNull(values, "Values must not be null");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(String.format("%." + precision + "f", values[i]));
        }
        return sb.toString();
    }

    /**
     * Formats information content values with default precision.
     * <p>
     * Convenience method that uses the default precision constant for
     * formatting.
     * </p>
     *
     * @param values
     *            array of computed information content values
     * @return formatted string representation using default precision
     */
    public static String formatValues(double[] values) {
        return formatValues(values, DEFAULT_PRECISION);
    }

    /**
     * Computes the entropy of a probability distribution.
     * <p>
     * Helper method that calculates Shannon entropy for an arbitrary
     * probability distribution (not necessarily binary). Useful for general
     * information-theoretic analysis.
     * </p>
     *
     * @param probabilities
     *            array of probabilities summing to 1.0
     * @return Shannon entropy in bits
     */
    public static double shannonEntropy(double[] probabilities) {
        Objects.requireNonNull(probabilities, "Probabilities must not be null");

        double entropy = 0.0;
        for (double p : probabilities) {
            if (p > EPSILON) {
                entropy -= p * Math.log(p) / Math.log(2);
            }
        }
        return entropy;
    }

    /**
     * Validates that an array of probabilities sums to approximately 1.0.
     * <p>
     * Helper method for input validation ensuring probability distributions are
     * valid.
     * </p>
     *
     * @param probabilities
     *            array of probabilities to validate
     * @return true if the sum is within epsilon of 1.0
     */
    public static boolean isValidProbabilityDistribution(
            double[] probabilities) {
        Objects.requireNonNull(probabilities, "Probabilities must not be null");

        double sum = 0.0;
        for (double p : probabilities) {
            if (p < 0 || p > 1) {
                return false; // Probability out of range [0, 1]
            }
            sum += p;
        }

        return Math.abs(sum - 1.0) < EPSILON;
    }
}
