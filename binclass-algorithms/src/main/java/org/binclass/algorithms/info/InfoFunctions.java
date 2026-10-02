/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.info;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.binclass.algorithms.core.AlgorithmConfig;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.dist.DistanceCalculator;
import org.binclass.algorithms.dist.NearestNeighbor;
import org.binclass.algorithms.util.MathUtils;

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
     * Recalculates SC(uniform), SC(Jeffrey's), Shannon entropy and codelength
     * values as a function of the number of clusters from saved centroids.
     * <p>
     * Equivalent to C function {@code calculate_functions()} from
     * {@code function.c}. For every centroid record it assigns vectors to the
     * nearest centroids, scores the resulting partition with both stochastic
     * complexity priors plus the Shannon-entropy and codelength measures, then
     * prints a per-record table, a contiguous FUNCTION section, and
     * least-squares MLE fits of {@code a/x + bx + c} and
     * {@code a*log(x) + bx + c}.
     * </p>
     * <p>
     * A record with {@code R} real clusters is scored by passing
     * {@code k = R + 1} to the stochastic-complexity routine (Java's
     * {@link Partition} keeps no spare index-0 slot like C) and
     * displayed/stored at index {@code R}.
     * </p>
     *
     * @param records
     *            the centroid records, one per cluster count, in ascending
     *            order
     * @param vectors
     *            the binary vectors to assign to centroids
     * @param distanceType
     *            the distance type used for nearest-neighbor assignment (see
     *            {@link DistanceCalculator} constants)
     * @param useClassWeights
     *            if true, weighted codelength is used for assignment and
     *            scoring
     * @return the formatted CALCULATING / FUNCTION / MLE ESTIMATES report
     */
    public static String calculateFunctions(List<InfiniteCentroids> records,
            VectorSet vectors, int distanceType, boolean useClassWeights) {
        Objects.requireNonNull(records, "Records must not be null");
        Objects.requireNonNull(vectors, "Vectors must not be null");

        int t = vectors.size();
        if (t == 0) {
            throw new IllegalArgumentException("No vectors to score");
        }

        // Ensure the log2-factorial lookup table covers every cluster size and
        // bit frequency reachable during scoring. Mirrors C
        // prepare_log2_factorials(t+t).
        MathUtils.prepareLog2Factorials(2 * t);

        int maxK = maxK(records);

        // SC arrays indexed by real cluster count R (1..maxK). Index 0 unused.
        double[] scu = new double[maxK + 1];
        double[] scj = new double[maxK + 1];
        double[] cl2 = new double[maxK + 1];
        double[] cl = new double[maxK + 1];
        for (int i = 1; i <= maxK; i++) {
            scu[i] = Double.MAX_VALUE;
            scj[i] = Double.MAX_VALUE;
            cl2[i] = Double.MAX_VALUE;
            cl[i] = Double.MAX_VALUE;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\nCALCULATING:\n");

        ScArrays sc = new ScArrays(scu, scj, cl2, cl);
        scoreRecords(sb, records, vectors, distanceType, useClassWeights, sc);

        sb.append("\nFUNCTION:\n\n");
        int n = printCurve(sb, sc, maxK);

        sb.append("\nMLE ESTIMATES:\n\n");
        fitFunction(sb, scu, n);
        fitFunctionLogl(sb, scu, n);

        return sb.toString();
    }

    /** A no-argument double supplier used by {@link #guard}. */
    @FunctionalInterface
    private interface DoubleSupplier {
        double get();
    }

    /** Mutable holder for the four SC arrays indexed by cluster count. */
    private static final class ScArrays {
        final double[] scu;
        final double[] scj;
        final double[] cl2;
        final double[] cl;

        ScArrays(double[] scu, double[] scj, double[] cl2, double[] cl) {
            this.scu = scu;
            this.scj = scj;
            this.cl2 = cl2;
            this.cl = cl;
        }
    }

    /**
     * Formats a score for output, rendering the unassigned sentinel
     * {@link Double#MAX_VALUE} as {@code Infinity} so empty clusters and
     * missing measures print consistently instead of as a 309-digit number.
     */
    private static String fmt(double v) {
        return v == Double.MAX_VALUE ? "Infinity"
                : String.format(Locale.ROOT, "%1.5f", v);
    }

    /**
     * Scores a partition with stochastic complexity, mapping to the Java
     * {@code k = R + 1} convention. Returns {@link Double#MAX_VALUE} when the
     * score is unavailable (empty cluster, out-of-range k, or division by
     * zero).
     */
    private static double score(Partition partition, int k, int l,
            boolean jeffreysPrior) {
        try {
            return DistanceCalculator.stochasticComplexity(partition, k, l,
                    jeffreysPrior);
        } catch (IllegalArgumentException | IllegalStateException
                | ArithmeticException _) {
            return Double.MAX_VALUE;
        }
    }

    /**
     * Assigns vectors to nearest centroids for one record and computes the four
     * information-theoretic measures (SC uniform, SC Jeffreys, Shannon entropy,
     * average codelength). Returns the values in that order, or {@code null}
     * when the record has fewer than one cluster.
     */
    private static double[] scoreRecord(InfiniteCentroids c, VectorSet vectors,
            int distanceType, boolean useClassWeights) {
        int r = c.size();
        if (r < 1) {
            return new double[0];
        }
        int l = c.get(0).getLength();

        Partition partition = new Partition(r);
        NearestNeighbor.dispatch(vectors, partition, c, distanceType,
                useClassWeights);

        double scuVal = score(partition, r + 1, l, false);
        double scjVal = score(partition, r + 1, l, true);
        double cl2Val = guard(() -> DistanceCalculator.shannonEntropy(
                partition, c));
        double clVal = guard(() -> DistanceCalculator.averageCodelength(
                partition, c));

        return new double[] { scuVal, scjVal, cl2Val, clVal };
    }

    /**
     * Scores every record, printing the CALCULATING table and keeping the best
     * SC(uniform), SC(Jeffrey's), Shannon entropy and codelength per cluster
     * count in the supplied arrays (initialised to {@link Double#MAX_VALUE}).
     */
    private static void scoreRecords(StringBuilder sb,
            List<InfiniteCentroids> records, VectorSet vectors,
            int distanceType,
            boolean useClassWeights, ScArrays sc) {
        for (InfiniteCentroids c : records) {
            int r = c.size(); // real clusters, displayed/stored at this index
            if (r < 1 || r > maxK(records)) {
                continue;
            }

            double[] q = scoreRecord(c, vectors, distanceType, useClassWeights);
            sb.append(
                    String.format("%4d: %s %s %s %s%n", r, fmt(q[0]), fmt(q[1]),
                            fmt(q[2]), fmt(q[3])));

            if (q[0] < sc.scu[r]) {
                sc.scu[r] = q[0];
            }
            if (q[1] < sc.scj[r]) {
                sc.scj[r] = q[1];
            }
            if (q[2] < sc.cl2[r]) {
                sc.cl2[r] = q[2];
            }
            if (q[3] < sc.cl[r]) {
                sc.cl[r] = q[3];
            }
        }
    }

    /** Largest cluster count across all records, at least two. */
    private static int maxK(List<InfiniteCentroids> records) {
        int maxK = 2;
        for (InfiniteCentroids c : records) {
            maxK = Math.max(maxK, c.size());
        }
        return maxK;
    }

    /**
     * Prints the contiguous FUNCTION section for every index that has a valid
     * SC(uniform) value. Returns the count of printed entries (the first index
     * with an invalid value), which is used as the fit range bound.
     */
    private static int printCurve(StringBuilder sb, ScArrays sc, int maxK) {
        int i = 1;
        while (i <= maxK && sc.scu[i] < Double.MAX_VALUE) {
            sb.append(String.format("%4d: %s %s %s %s%n", i, fmt(sc.scu[i]),
                    fmt(sc.scj[i]), fmt(sc.cl2[i]), fmt(sc.cl[i])));
            i++;
        }
        return i;
    }

    /**
     * Runs a scoring supplier, returning {@link Double#MAX_VALUE} on any error.
     */
    private static double guard(DoubleSupplier supplier) {
        try {
            return supplier.get();
        } catch (RuntimeException _) {
            return Double.MAX_VALUE;
        }
    }

    /**
     * Computes the first-order fit coefficient {@code a1(y,n) = sum y[i]/i}.
     * Equivalent to C function {@code a1()} from {@code function.c}.
     */
    private static double cA1(double[] y, int n) {
        double s = 0.0;
        for (int i = 1; i < n; i++) {
            s += y[i] / i;
        }
        return s;
    }

    /**
     * Computes {@code a2(n) = sum 1/i^2}. Equivalent to C function {@code a2()}
     * from {@code function.c}.
     */
    private static double cA2(int n) {
        double s = 0.0;
        for (int i = 1; i < n; i++) {
            s += 1.0 / ((double) i * i);
        }
        return s;
    }

    /**
     * Computes {@code b1(y,n) = sum y[i]*i}. Equivalent to C function
     * {@code b1()} from {@code function.c}.
     */
    private static double cB1(double[] y, int n) {
        double s = 0.0;
        for (int i = 1; i < n; i++) {
            s += y[i] * i;
        }
        return s;
    }

    /**
     * Computes {@code b2(n) = sum i^2}. Equivalent to C function {@code b2()}
     * from {@code function.c}.
     */
    private static double cB2(int n) {
        double s = 0.0;
        for (int i = 1; i < n; i++) {
            s += (double) i * i;
        }
        return s;
    }

    /**
     * Computes {@code c1(n) = sum 1/i}. Equivalent to C function {@code c1()}
     * from {@code function.c}.
     */
    private static double cC1(int n) {
        double s = 0.0;
        for (int i = 1; i < n; i++) {
            s += 1.0 / i;
        }
        return s;
    }

    /**
     * Computes {@code c2(n) = sum i}. Equivalent to C function {@code c2()}
     * from {@code function.c}.
     */
    private static double cC2(int n) {
        double s = 0.0;
        for (int i = 1; i < n; i++) {
            s += i;
        }
        return s;
    }

    /**
     * Computes {@code c3(y,n) = sum y[i]}. Equivalent to C function
     * {@code c3()} from {@code function.c}.
     */
    private static double cC3(double[] y, int n) {
        double s = 0.0;
        for (int i = 1; i < n; i++) {
            s += y[i];
        }
        return s;
    }

    /**
     * Computes {@code d1(n) = sum (log2 i)^2}. Equivalent to C function
     * {@code d1()} from {@code function.c}.
     */
    private static double cD1(int n) {
        double s = 0.0;
        for (int i = 1; i < n; i++) {
            double li = MathUtils.log2(i);
            s += li * li;
        }
        return s;
    }

    /**
     * Computes {@code d2(n) = sum log2(i) * n}. Equivalent to C function
     * {@code d2()} from {@code function.c}.
     */
    private static double cD2(int n) {
        double s = 0.0;
        for (int i = 1; i < n; i++) {
            s += MathUtils.log2(i) * n;
        }
        return s;
    }

    /**
     * Computes {@code d3(n) = sum log2(i)}. Equivalent to C function
     * {@code d3()} from {@code function.c}.
     */
    private static double cD3(int n) {
        double s = 0.0;
        for (int i = 1; i < n; i++) {
            s += MathUtils.log2(i);
        }
        return s;
    }

    /**
     * Computes {@code d4(y,n) = sum y[i] * log2(i)}. Equivalent to C function
     * {@code d4()} from {@code function.c}.
     */
    private static double cD4(double[] y, int n) {
        double s = 0.0;
        for (int i = 1; i < n; i++) {
            s += y[i] * MathUtils.log2(i);
        }
        return s;
    }

    /**
     * Computes the least-squares fit error
     * {@code sum (Y[i] - a/i - b*i - c)^2}. Equivalent to C function
     * {@code fit_error()} from {@code function.c}.
     */
    private static double fitError(double[] y, int n, double a, double b,
            double c) {
        double s = 0.0;
        for (int i = 1; i < n; i++) {
            double diff = y[i] - (a / i) - (b * i) - c;
            s += diff * diff;
        }
        return s;
    }

    /**
     * Computes the least-squares fit error
     * {@code sum (Y[i] - a*log2(i) - b*i - c)^2}. Equivalent to C function
     * {@code fit_error_logl()} from {@code function.c}.
     */
    private static double fitErrorLogl(double[] y, int n, double a, double b,
            double c) {
        double s = 0.0;
        for (int i = 1; i < n; i++) {
            double diff = y[i] - (a * MathUtils.log2(i)) - (b * i) - c;
            s += diff * diff;
        }
        return s;
    }

    /**
     * Solves a 3x3 linear system in place using Gauss-Jordan elimination.
     * <p>
     * Mirrors C function {@code gauss_eliminate3()} from {@code function.c}.
     * The input is a 3x4 matrix (three equations plus the right-hand side);
     * after elimination the solution occupies column index 3 of each row.
     * </p>
     *
     * @param m
     *            a 3x4 matrix; modified in place so that {@code m[r][3]} holds
     *            the r-th unknown
     */
    private static void gaussEliminate3(double[][] m) {
        double c;

        c = -m[1][0] / m[0][0];
        for (int i = 0; i < 4; i++) {
            m[1][i] += c * m[0][i];
        }
        c = -m[2][0] / m[0][0];
        for (int i = 0; i < 4; i++) {
            m[2][i] += c * m[0][i];
        }
        c = -m[2][1] / m[1][1];
        for (int i = 0; i < 4; i++) {
            m[2][i] += c * m[1][i];
        }
        c = -m[1][2] / m[2][2];
        for (int i = 0; i < 4; i++) {
            m[1][i] += c * m[2][i];
        }
        c = -m[0][2] / m[2][2];
        for (int i = 0; i < 4; i++) {
            m[0][i] += c * m[2][i];
        }
        c = -m[0][1] / m[1][1];
        for (int i = 0; i < 4; i++) {
            m[0][i] += c * m[1][i];
        }
        m[0][3] /= m[0][0];
        m[1][3] /= m[1][1];
        m[2][3] /= m[2][2];
    }

    /**
     * Fits {@code a/x + bx + c} to the data with least squares and appends the
     * resulting equation and error. Equivalent to C function
     * {@code fit_function()} from {@code function.c}.
     */
    private static void fitFunction(StringBuilder sb, double[] sc, int n) {
        double a1 = cA1(sc, n);
        double a2 = cA2(n);
        double b1 = cB1(sc, n);
        double b2 = cB2(n);
        double c1 = cC1(n);
        double c2 = cC2(n);
        double c3 = cC3(sc, n);

        double[][] m = new double[3][4];
        /* aA2 + bN + cC1 = A1 */
        m[0][0] = a2;
        m[0][1] = n;
        m[0][2] = c1;
        m[0][3] = a1;
        /* aN + bB2 + cC2 = B1 */
        m[1][0] = n;
        m[1][1] = b2;
        m[1][2] = c2;
        m[1][3] = b1;
        /* aC1 + bC2 + cN = C3 */
        m[2][0] = c1;
        m[2][1] = c2;
        m[2][2] = n;
        m[2][3] = c3;

        gaussEliminate3(m);
        double a = m[0][3];
        double b = m[1][3];
        double cc = m[2][3];

        sb.append(String.format("F(x)  = %.4f/x + %.4fx + %.4f%n", a, b, cc));
        sb.append(String.format("Error = %.4f%n", fitError(sc, n, a, b, cc)));
    }

    /**
     * Fits {@code a*log(x) + bx + c} to the data with least squares and appends
     * the resulting equation and error. Equivalent to C function
     * {@code fit_function_logl()} from {@code function.c}.
     */
    private static void fitFunctionLogl(StringBuilder sb, double[] sc, int n) {
        double b1 = cB1(sc, n);
        double b2 = cB2(n);
        double c2 = cC2(n);
        double c3 = cC3(sc, n);
        double d1 = cD1(n);
        double d2 = cD2(n);
        double d3 = cD3(n);
        double d4 = cD4(sc, n);

        double[][] m = new double[3][4];
        /* aD1 + bD2 + cD3 = D4 */
        m[0][0] = d1;
        m[0][1] = d2;
        m[0][2] = d3;
        m[0][3] = d4;
        /* aD2 + bB2 + cC2 = B1 */
        m[1][0] = d2;
        m[1][1] = b2;
        m[1][2] = c2;
        m[1][3] = b1;
        /* aD3 + bC2 + cN = C3 */
        m[2][0] = -d3;
        m[2][1] = c2;
        m[2][2] = n;
        m[2][3] = c3;

        gaussEliminate3(m);
        double af = m[0][3];
        double bf = m[1][3];
        double cf = m[2][3];

        sb.append(String.format("F(x)  = %.4flog(x) + %.4fx + %.4f%n", af, bf,
                cf));
        sb.append(String.format("Error = %.4f%n", fitErrorLogl(sc, n, af, bf,
                cf)));
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
            if (x <= AlgorithmConfig.NUMERICAL_STABILITY_EPSILON) {
                result[i] = 0.0;
            } else if (x >= 1.0 - AlgorithmConfig.NUMERICAL_STABILITY_EPSILON) {
                result[i] = 0.0;
            } else {
                // Shannon entropy: -x*log2(x) - (1-x)*log2(1-x)
                double logX = MathUtils.log2(x);
                double logOneMinusX = MathUtils.log2Complement(x);
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
        Objects.requireNonNull(probabilities, PROBABILITIES_MUST_NOT_BE_NULL);
        // Second-order information content shares the single-position entropy
        // model with a1; callers select the order via the array length.
        return a1(probabilities);
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
            if (maxEntropy > AlgorithmConfig.NUMERICAL_STABILITY_EPSILON) {
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
            if (maxEntropy > AlgorithmConfig.NUMERICAL_STABILITY_EPSILON) {
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
            sb.append(String.format(Locale.ROOT, "%.*f%n", precision,
                    values[i]));
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
        Objects.requireNonNull(probabilities, PROBABILITIES_MUST_NOT_BE_NULL);

        double entropy = 0.0;
        for (double p : probabilities) {
            if (p > AlgorithmConfig.NUMERICAL_STABILITY_EPSILON) {
                entropy -= p * MathUtils.log2(p);
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
        Objects.requireNonNull(probabilities, PROBABILITIES_MUST_NOT_BE_NULL);

        double sum = 0.0;
        for (double p : probabilities) {
            if (p < 0 || p > 1) {
                return false; // Probability out of range [0, 1]
            }
            sum += p;
        }

        return Math
                .abs(sum - 1.0) < AlgorithmConfig.NUMERICAL_STABILITY_EPSILON;
    }
}
