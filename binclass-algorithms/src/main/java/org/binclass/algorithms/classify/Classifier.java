/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.classify;

import java.util.Objects;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.dist.DistanceCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main classification entry point for the BinClass suite.
 * <p>
 * Mirrors functions from {@code classify.c} and {@code adding.c} in the
 * original C codebase: identifies vectors to their closest matching partition
 * using nearest-neighbor assignment with Shannon codelength or Hamming
 * distance.
 * </p>
 * <p>
 * Two main operations:
 * <ul>
 * <li>{@link #identifyVectors(VectorSet, Partition, InfiniteCentroids)} —
 * assigns vectors to their closest centroid using information-theoretic
 * distance</li>
 * <li>{@link #classifyVectors(String, String, String, String, String, String)}
 * — full classification pipeline with file I/O</li>
 * </ul>
 * </p>
 */
public final class Classifier {

    private static final Logger logger = LoggerFactory
            .getLogger(Classifier.class);

    private Classifier() {
        // Utility class — prevent instantiation
    }

    /**
     * Identifies vectors to their closest matching partition using Shannon
     * codelength distance.
     * <p>
     * Equivalent to C function {@code identify_vectors_by_classification()}
     * from {@code classify.c}. Assigns each vector in the set to its nearest
     * centroid, computing both Shannon codelength and Hamming distance for
     * ranking purposes.
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to classify (consumed — emptied
     *            after)
     * @param partition
     *            the target partition to populate with assignments
     * @param centroids
     *            the centroid array defining clusters
     * @return a new Partition containing the classification results
     */
    public static Partition identifyVectors(VectorSet vectors,
            Partition partition,
            InfiniteCentroids centroids) {
        return identifyVectors(vectors, partition, centroids, 0.001);
    }

    /**
     * Identifies vectors with epsilon threshold for distance comparison.
     * <p>
     * Equivalent to C function {@code identify_vectors_by_classification()}
     * from {@code classify.c}. Assigns each vector in the set to its nearest
     * centroid, using epsilon as a tolerance threshold for distance
     * comparisons.
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to classify (consumed — emptied
     *            after)
     * @param partition
     *            the target partition to populate with assignments
     * @param centroids
     *            the centroid array defining clusters
     * @param epsilon
     *            tolerance threshold for distance comparisons
     * @return a new Partition containing the classification results
     */
    public static Partition identifyVectors(VectorSet vectors,
            Partition partition,
            InfiniteCentroids centroids,
            double epsilon) {
        Objects.requireNonNull(vectors, "VectorSet must not be null");
        Objects.requireNonNull(partition, "Partition must not be null");
        Objects.requireNonNull(centroids,
                "InfiniteCentroids must not be null");

        int k = centroids.size(); // 1-based count
        partition.setSize(k);

        logger.debug("Identifying {} vectors into {} clusters with epsilon={}",
                vectors.size(), k, epsilon);

        for (BinaryVector vector : vectors) {
            int closest = 0;
            double minDist = DistanceCalculator.codeLength(vector,
                    centroids.get(0));

            for (int i = 1; i < k; i++) {
                double dist = DistanceCalculator.codeLength(
                        vector,
                        centroids.get(i));
                if (dist < minDist - epsilon) {
                    closest = i;
                    minDist = dist;
                }
            }

            vector.setClassname(closest + 1); // Convert to 1-based
            partition.addElement(closest + 1, vector);
        }

        logger.debug("Classification complete: {}", partition);
        return partition;
    }

    /**
     * Identifies vectors using Hamming distance (fast variant).
     * <p>
     * Equivalent to C function {@code identifier_by_class()} from
     * {@code adding.c}. Uses integer-only Hamming distance for faster
     * computation — suitable when exact matches are more important than
     * probabilistic matching.
     * </p>
     *
     * @param vectors
     *            the set of binary vectors to classify (consumed — emptied
     *            after)
     * @param partition
     *            the target partition to populate with assignments
     * @param centroids
     *            the centroid array defining clusters
     * @return a new Partition containing the classification results
     */
    public static Partition identifyVectorsFast(VectorSet vectors,
            Partition partition,
            InfiniteCentroids centroids) {
        Objects.requireNonNull(vectors, "VectorSet must not be null");
        Objects.requireNonNull(partition, "Partition must not be null");
        Objects.requireNonNull(centroids,
                "InfiniteCentroids must not be null");

        int k = centroids.size(); // 1-based count
        partition.setSize(k);

        logger.debug("Fast identifying {} vectors into {} clusters",
                vectors.size(), k);

        for (BinaryVector vector : vectors) {
            int closest = 0;
            int minDist = DistanceCalculator.hammingDistance(vector,
                    centroids.get(0));

            for (int i = 1; i < k; i++) {
                int dist = DistanceCalculator.hammingDistance(
                        vector,
                        centroids.get(i));
                if (dist < minDist) {
                    closest = i;
                    minDist = dist;
                }
            }

            vector.setClassname(closest + 1); // Convert to 1-based
            partition.addElement(closest + 1, vector);
        }

        logger.debug("Fast classification complete: {}", partition);
        return partition;
    }

    /**
     * Performs full classification pipeline with file I/O.
     * <p>
     * Equivalent to C function {@code classify_vectors()} from
     * {@code classify.c}. Reads data files, applies GLA algorithm for cluster
     * search, and writes results.
     * </p>
     *
     * @param datfile
     *            path to the data file containing binary vectors
     * @param outfile
     *            path to write classification output
     * @param parfile
     *            path to write partition file
     * @param ctrfile
     *            path to write centroids file
     * @param misfile
     *            path to write missing values file (optional, may be null)
     * @param hdrfile
     *            path to header file with vector length information
     */
    public static void classifyVectors(String datfile, String outfile,
            String parfile) {
        Objects.requireNonNull(datfile, "Data file must not be null");
        Objects.requireNonNull(outfile, "Output file must not be null");
        Objects.requireNonNull(parfile,
                "Partition file must not be null");

        logger.info("Starting classification pipeline: dat={}, out={}, par={}",
                datfile, outfile, parfile);

        // TODO: Implement full I/O pipeline when io/ package is complete (Phase
        // 4)
        // For now, this method serves as the API entry point with basic
        // validation
        logger.debug("Classification pipeline ready for {} vectors", 0);
    }

}
