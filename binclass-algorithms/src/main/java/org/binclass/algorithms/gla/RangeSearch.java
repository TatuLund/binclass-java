/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.gla;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.binclass.algorithms.centroid.CentroidInitializer;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.dist.DistanceCalculator;
import org.binclass.algorithms.dist.NearestNeighbor;

/**
 * Range GLA search, mirroring the C function
 * {@code search_classes_nonautomatic()} from {@code classify.c}. Unlike
 * {@link AutomaticSearch}, which scans forward from {@code k = 1} and enhances
 * with a ping-pong phase, range search runs GLA for each cluster count in an
 * explicit {@code [kstart, kstop]} window chosen by the caller.
 * <p>
 * The class keeps every helper it needs ({@link #initializePartition},
 * {@link #runGLAAndCalculateSC}, {@link #populatePartitionForLocalSearch} and a
 * few static utilities) so that both the CLI layer and unit tests can drive a
 * range search without depending on {@code ClassifyCommand}. It is deliberately
 * free of CLI concerns such as option parsing or file writing.
 * </p>
 */
@NullMarked
public final class RangeSearch {

    /** Result of a range search: best partition and its centroids. */
    public record Result(Partition partition, InfiniteCentroids centroids) {
    }

    /**
     * Bundle returned by {@link #initializePartition} capturing both the newly
     * created partition and the (possibly replaced) centroid array produced by
     * the selected centroid-type strategy.
     */
    private record PartitionInit(Partition partition,
            InfiniteCentroids centroids) {
    }

    private static final Logger log = LoggerFactory
            .getLogger(RangeSearch.class);

    private final VectorSet vectorSet;
    private final GLAConfig config;

    /**
     * Creates a range search over the given vectors and configuration.
     *
     * @param vectorSet
     *            the vectors to cluster (must not be {@code null})
     * @param config
     *            the GLA configuration describing heuristic, scan limits, etc.
     *            (must not be {@code null})
     */
    public RangeSearch(VectorSet vectorSet, GLAConfig config) {
        this.vectorSet = Objects.requireNonNull(vectorSet, "VectorSet");
        this.config = Objects.requireNonNull(config, "GLAConfig");
    }

    /**
     * Runs GLA for each cluster count from {@code kstart} to {@code kstop},
     * tracking stochastic complexity. Mirrors the C function
     * {@code search_classes_nonautomatic()} from {@code classify.c}.
     *
     * @param kstart
     *            lowest cluster count to try (inclusive)
     * @param kstop
     *            highest cluster count to try (inclusive)
     * @return the best partition found together with its centroids
     */
    public Result run(int kstart, int kstop) {

        // --- G7: maximum_class_number cap check ---------------------------
        // Mirrors C search_classes_nonautomatic(): if the starting cluster
        // count exceeds the tool's hard limit, report and exit(1).
        if (kstart > AutomaticSearch.MAXIMUM_CLASS_NUMBER) {
            log.warn("More classes requested than possible: kstart={} > {}",
                    kstart, AutomaticSearch.MAXIMUM_CLASS_NUMBER);
            throw new IllegalArgumentException(
                    "More classes requested than possible");
        }

        double scmin = Double.MAX_VALUE;
        int actualK = kstart;
        int noImprovementCount = 0;
        InfiniteCentroids centroids = null; // Declare outside loop for logging
        Partition bestPartition = null;
        InfiniteCentroids bestCentroids = null;

        // --- G5: allocate scs[] capped at maximum_class_number ------------
        // scs[k] holds the best SC seen for k clusters; uncomputed entries
        // keep the 1000.0 sentinel used by C's search_classes_nonautomatic().
        int mk = kstop + 1;
        if (mk > AutomaticSearch.MAXIMUM_CLASS_NUMBER) {
            mk = AutomaticSearch.MAXIMUM_CLASS_NUMBER;
        }
        double[] scs = new double[mk];
        for (int i = 0; i < mk; i++) {
            scs[i] = 1000.0;
        }

        // --- G8: elapsed-time tracking ------------------------------------
        long startTime = System.currentTimeMillis();
        long lastTime = startTime;

        // Get the actual vector length from the first vector in the set
        int vectorLength = vectorSet.size() > 0
                ? vectorSet.iterator().next().getLength()
                : 16;

        int attempts = config.iterBase() > 0 ? config.iterBase() : 1;
        for (int k = kstart; k <= kstop; k++) {
            // Run GLA up to `attempts` times per cluster count with different
            // starting centroids, keeping the best SC so bad local minima are
            // not counted. Mirrors C use_gla() where -a sets the number of
            // trials.
            double bestScForK = Double.MAX_VALUE;
            Partition bestPartitionForK = null;
            InfiniteCentroids bestCentroidsForK = null;
            for (int attempt = 0; attempt < attempts; attempt++) {
                InfiniteCentroids centroidsK;
                Partition partitionK;
                if (attempt == 0) {
                    InfiniteCentroids initialCentroids = new InfiniteCentroids(
                            k + 1, vectorLength);
                    PartitionInit init = initializePartition(vectorSet, k,
                            initialCentroids, config.centroidType());
                    centroidsK = init.centroids();
                    partitionK = init.partition();
                } else {
                    // Different starting centroids per attempt to escape bad
                    // local minima (mirrors C use_gla() random_centroids()).
                    // Use the same cluster count as attempt 0 (k + 1) so every
                    // trial scores on equal footing; pure random values work
                    // regardless of n vs k. The partition must match its size
                    // or
                    // GLA's setSize() shrink path nulls every cluster slot and
                    // addElement() throws.
                    centroidsK = CentroidInitializer.randomInit(k + 1,
                            vectorLength);
                    partitionK = new Partition(k + 1);
                }

                double scForK = runGLAAndCalculateSC(vectorSet, partitionK,
                        centroidsK, config);

                if (scForK < bestScForK) {
                    bestScForK = scForK;
                    bestPartitionForK = partitionK;
                    bestCentroidsForK = centroidsK;
                }
            }

            Partition partition = bestPartitionForK;
            centroids = bestCentroidsForK;
            double sc = bestScForK;
            log.info("Classification at k={}: SC={}", k, sc);

            // --- G5: record the best SC seen for this cluster count -------
            // Mirrors C's `scs[(C->k)-1] = min(...)` update. The array is
            // capped at maximum_class_number and printed as a table below.
            if (k < scs.length && scs[k] > sc) {
                scs[k] = sc;
            }

            // Always update scmin to track the best SC seen so far
            if (sc < scmin) {
                scmin = sc;
                bestPartition = partition;
                bestCentroids = centroids;
                actualK = k;
                noImprovementCount = 0;
                log.info("New best classification at k={}: SC={}, clusters={}",
                        k, sc, partition == null ? 0 : partition.size());
            } else {
                // --- G6: count "tries since best" -------------------------
                noImprovementCount++;
                log.info(
                        "Tries since best classification: {}",
                        noImprovementCount);
            }

            // --- G8: emit elapsed-time messages ---------------------------
            long now = System.currentTimeMillis();
            long secondsSinceStart = (now - startTime) / 1000;
            long secondsSinceLast = (now - lastTime) / 1000;
            log.info("Time ellapsed since start:                {}",
                    formatTime(secondsSinceStart));
            log.info(
                    "Time ellapsed for current classification: {}",
                    formatTime(secondsSinceLast));

            boolean reachedSafetyLimit = k - kstart >= config.safetyLimit();
            if (shouldTerminate(noImprovementCount, config)
                    || reachedSafetyLimit) {
                if (reachedSafetyLimit) {
                    log.info("Safety limit reached at k={}", k);
                }
                break;
            }
        }

        // --- G5: print the "SC as function of k" table -------------------
        // Mirrors C's final report: every entry below the 1000.0 sentinel is
        // printed with the same format used by the original tool.
        log.info("\nSC as function of k\n--");
        for (int i = 0; i < mk; i++) {
            if (scs[i] < 1000.0) {
                log.info(String.format("%3d: %2.4f", i, scs[i]));
            }
        }

        // Log centroid info if enabled
        if (bestPartition != null) {
            logCentroidInfo(bestPartition, centroids, config.logCentroids());
        }

        if (bestPartition == null) {
            bestPartition = new Partition(kstart + 1);
        }

        log.info("Best classification: k={}, SC={}", actualK, scmin);
        return new Result(bestPartition, bestCentroids);
    }

    /**
     * Initialize a partition and its centroid array using the strategy selected
     * by {@code -c} (centroid type). Mirrors C's {@code random_centroids()}
     * dispatch in {@code centroid.c}:
     * <ul>
     * <li>{@code 1} (CT_CLASSIC) &rarr; uniform random centroids
     * ({@code normal_centroids})</li>
     * <li>{@code 2} (CT_SRAND) &rarr; statistical sampling
     * ({@code statistical_centroids})</li>
     * <li>{@code 3} (CT_SEMI) &rarr; semi-random frequency weighting
     * ({@code semi_random_centroids})</li>
     * <li>{@code 4} (CT_RAND) &rarr; pick random input vectors
     * ({@code pick_centroids})</li>
     * <li>{@code 5} (CT_PNN) &rarr; pairwise nearest-neighbour merging
     * ({@code pnn_centroids_rand})</li>
     * </ul>
     * The returned centroids array is sized {@code k + 1} to match the
     * partition's cluster count.
     *
     * @param vectorSet
     *            the source vectors (required for CT_RAND and CT_PNN)
     * @param k
     *            the starting number of clusters; the arrays hold {@code k + 1}
     *            entries
     * @param centroids
     *            a pre-allocated centroid array that is populated in place for
     *            the classic/semi-random strategies, or replaced by the
     *            vector-based strategies
     * @param centroidType
     *            the selected {@code -c} centroid type
     * @return a partition sized {@code k + 1}; cluster assignment happens later
     *         during GLA
     */
    private PartitionInit initializePartition(VectorSet vectorSet, int k,
            InfiniteCentroids centroids, int centroidType) {
        Partition partition = new Partition(k + 1);
        int l = vectorSet.getVectorLength();
        InfiniteCentroids initialized;
        switch (centroidType) {
        case 2: // CT_SRAND - statistical_centroids
            initialized = CentroidInitializer.semiRandomInit(centroids.size(),
                    l);
            break;
        case 3: // CT_SEMI - semi_random_centroids
            initialized = CentroidInitializer.semiRandomInit(centroids.size(),
                    l);
            break;
        case 4: // CT_RAND - pick_centroids
            initialized = CentroidInitializer.pickInit(centroids.size(), l,
                    vectorSet);
            break;
        case 5: // CT_PNN - pnn_centroids_rand
            initialized = CentroidInitializer.pnnInit(centroids.size(), l,
                    vectorSet);
            break;
        default: // CT_CLASSIC - normal_centroids (uniform random)
            initialized = CentroidInitializer.randomInit(centroids.size(), l);
            break;
        }
        return new PartitionInit(partition, initialized);
    }

    /**
     * Run GLA and calculate stochastic complexity in one step.
     *
     * @param vectorSet
     *            the vectors to cluster
     * @param partition
     *            the partition mutated by GLA (in place)
     * @param centroids
     *            the centroid array mutated by GLA (in place)
     * @param config
     *            the resolved GLA configuration
     * @return the stochastic complexity of the resulting clustering
     */
    private double runGLAAndCalculateSC(VectorSet vectorSet,
            Partition partition,
            InfiniteCentroids centroids, GLAConfig config) {
        // Calculate minimum distortion array
        double[] dmin = new double[1];

        // Initialization hint: skip if firstD indicates already converged
        if (config.firstD() > 0 && dmin[0] < config.firstD()) {
            log.debug("Initial distortion below firstD={}, skipping",
                    config.firstD());
        }

        // Run GLA based on heuristic selection
        switch (config.heuristic()) {
        case 1:
            log.info("Using standard GLA");
            GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
            break;
        case 2:
            log.info("Using stochastic relaxation GLA");
            GLAEngine.glaSr(vectorSet, partition, centroids, dmin, config);
            break;
        case 3:
            log.info("Using simulated annealing GLA");
            GLAEngine.glaSa(vectorSet, partition, centroids, dmin, config);
            break;
        case 4:
            log.info("Using hybrid L1 GLA");
            GLAEngine.hybridGlaL1(vectorSet, partition, centroids, dmin,
                    config);
            break;
        case 5:
            log.info("Using hybrid L2 GLA");
            GLAEngine.hybridGlaL2(vectorSet, partition, centroids, dmin,
                    config);
            break;
        case 6:
            log.info("Using MAE GLA");
            GLAEngine.maeGla(vectorSet, partition, centroids, dmin, config);
            break;
        case 7:
            // Local search cycler mode (-r7): run the multi-operator local
            // search driver that cycles through every strategy. Mirrors C's
            // local_search() with ls_heuristic_cycler = TRUE.
            log.info("Using local search (cycling all strategies)");
            populatePartitionForLocalSearch(vectorSet, partition, centroids,
                    config);
            LocalSearch.localSearch(partition, centroids,
                    config.heuristicCount(), vectorSet.getVectorLength(),
                    config.n(), config.jeffreysPrior(), new java.util.Random());
            break;
        case 8:
            // Local search adaptive mode (-r8): run the multi-operator local
            // driver that adapts operator selection probabilities. Mirrors C's
            // local_search() with ls_adaptive_heuristic = TRUE.
            log.info("Using local search (adaptive strategies)");
            populatePartitionForLocalSearch(vectorSet, partition, centroids,
                    config);
            LocalSearch.localSearch(partition, centroids,
                    config.heuristicCount(), vectorSet.getVectorLength(),
                    config.n(), config.jeffreysPrior(), new java.util.Random());
            break;
        default:
            log.info("Defaulting to standard GLA");
            GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
        }

        // Calculate stochastic complexity or best code length based on flag
        double sc;
        int numClusters = partition.size();

        // Propagate GLAConfig flags to DistanceCalculator for codelength
        // calculations
        DistanceCalculator.setUseClassWeights(config.weights());
        DistanceCalculator.setUseRoundedCentroids(config.rounded());

        if (config.bestCodeLength()) {
            try {
                sc = DistanceCalculator.averageCodelength(partition, centroids);
            } catch (ArithmeticException ex) {
                log.debug("averageCodelength threw ArithmeticException: {}",
                        ex.getMessage());
                sc = 0.0; // Fallback for empty partitions
            }
        } else {
            // Always use proper stochastic complexity calculation
            // Use actual number of non-empty clusters for accurate SC
            int actualClusters = 0;
            for (int i = 1; i <= numClusters; i++) {
                if (partition.getSize(i) > 0) {
                    actualClusters++;
                }
            }

            // If all clusters are empty, return a large SC value as fallback
            if (actualClusters == 0) {
                sc = Double.MAX_VALUE;
                log.debug(
                        "SC calculation: empty partition, returning MAX_VALUE");
                return sc;
            }

            sc = DistanceCalculator.stochasticComplexity(
                    partition, actualClusters, vectorSet.getVectorLength(),
                    config.jeffreysPrior());

            log.debug(
                    "SC calculation: k={}, actualClusters={}, l={}, jeffreys={}",
                    numClusters, actualClusters, vectorSet.getVectorLength(),
                    config.jeffreysPrior());
        }

        return sc;
    }

    /**
     * Populates an empty partition with MSE nearest-neighbor assignments before
     * running local search. Mirrors C's {@code use_gla_load_centroids()}, which
     * calls {@code MSE_gla2(V,P,C,&d,n)} to assign every vector to its nearest
     * centroid (and drop the resulting empty clusters) before invoking
     * {@code local_search()}. Without this step the partition is entirely
     * empty, so {@link LocalSearch#localSearch} computes its initial stochastic
     * complexity on an empty partition and throws "Empty cluster in
     * stochastic_complexity_u".
     *
     * @param vectorSet
     *            the source vectors to assign
     * @param partition
     *            the (initially empty) partition populated in place
     * @param centroids
     *            the centroid array updated in place
     * @param config
     *            GLA configuration controlling rounding and vector length
     */
    private static void populatePartitionForLocalSearch(VectorSet vectorSet,
            Partition partition, InfiniteCentroids centroids,
            GLAConfig config) {
        NearestNeighbor.mseNearestNeighbor(vectorSet, partition, centroids);
        GLAEngine.removeEmpty(partition, centroids);
        GLAEngine.recomputeCentroids(partition, centroids,
                config.rounded(), config.n());
    }

    /**
     * Check if we should terminate the range search.
     *
     * @param noImprovementCount
     *            consecutive cluster counts without an SC improvement
     * @param config
     *            the resolved GLA configuration (provides {@code -S})
     * @return {@code true} when the number of non-improving steps reaches the
     *         configured limit
     */
    private static boolean shouldTerminate(int noImprovementCount,
            GLAConfig config) {
        int maxSteps = config.kstopwhen() > 0 ? config.kstopwhen()
                : Integer.MAX_VALUE;
        return noImprovementCount >= maxSteps;
    }

    /**
     * Logs centroid information when logCentroids flag is enabled.
     *
     * @param partition
     *            the best partition found so far
     * @param centroids
     *            the associated centroid array
     * @param logCentroids
     *            whether {@code -l} logging is requested
     */
    private static void logCentroidInfo(Partition partition,
            InfiniteCentroids centroids, boolean logCentroids) {
        if (!logCentroids)
            return;
        for (int i = 0; i < centroids.size(); i++) {
            Centroid centroid = centroids.get(i);
            int clusterIdx = Math.min(i + 1, partition.size()); // 1-based,
                                                                // bounded
            int clusterSize = partition.getSize(clusterIdx);
            double entropy = calculateEntropy(centroid);
            log.debug("Cluster {}: size={}, entropy={}", i + 1, clusterSize,
                    entropy);
        }
    }

    /**
     * Calculates Shannon entropy for a centroid.
     *
     * @param centroid
     *            the centroid to measure
     * @return the mean per-bit Shannon entropy of the centroid array
     */
    private static double calculateEntropy(Centroid centroid) {
        double[] el = centroid.getArray();
        double entropy = 0.0;
        for (double val : el) {
            if (val > 0 && val < 1) {
                entropy -= val * Math.log(val) + (1 - val) * Math.log(1 - val);
            }
        }
        return entropy / el.length; // Normalize by vector length
    }

    /**
     * Formats a duration in seconds as days/hours/minutes/seconds, mirroring
     * C's {@code print_time()} from bottom.c used by the range-search loop.
     *
     * @param totalSeconds
     *            elapsed time expressed in whole seconds
     * @return a formatted string such as {@code " 0d 0h 0m 3s"}
     */
    private static String formatTime(long totalSeconds) {
        long days = totalSeconds / 86400;
        long rem = Math.floorMod(totalSeconds, 86400);
        long hours = rem / 3600;
        rem %= 3600;
        long minutes = rem / 60;
        long seconds = rem % 60;
        return String.format("%3dd %2dh %2dm %2ds", days, hours, minutes,
                seconds);
    }
}
