/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.gla;

import java.util.Objects;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.centroid.CentroidInitializer;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.dist.DistanceCalculator;
import org.binclass.algorithms.dist.NearestNeighbor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Automatic SC-minimizer, mirroring the C functions {@code sca_main()},
 * {@code sca_scanner()} and {@code sca_pingpong()} from {@code classify.c}.
 * <p>
 * The search runs in two phases:
 * <ol>
 * <li><b>Forward scan</b> — GLA is applied for each increasing cluster count
 * starting at {@code kstart} until there is no improvement in {@code kstopwhen}
 * consecutive steps. The least stochastic complexity (SC) seen so far
 * ({@code scmin}) and its cluster count ({@code kmin}) are kept, as well as a
 * per-{@code k} record of the best SC ({@code scs[]}).</li>
 * <li><b>Ping-pong enhancement</b> — with {@code best_code_length} forced to
 * {@code false}, the scan window is rescanned below {@code kmin} (ping) and, if
 * present, above it (pong). Each phase keeps improving until no step lowers the
 * current best SC.</li>
 * </ol>
 * The class is deliberately free of CLI concerns so that both phases can be
 * unit tested on small synthetic data.
 */
public final class AutomaticSearch {

    /** Maximum number of classes allowed by the C tool (const.h / vars.c). */
    public static final int MAXIMUM_CLASS_NUMBER = 200;

    /**
     * Sentinel for an uncomputed SC value, matching {@code unassigned_sc()}.
     */
    private static final double UNASSIGNED_SC = Double.MAX_VALUE;

    private static final Logger log = LoggerFactory
            .getLogger(AutomaticSearch.class);

    private final VectorSet vectorSet;
    private final GLAConfig config;

    /**
     * Result of the automatic search: best partition/centroids and metadata.
     */
    public record Result(Partition partition, InfiniteCentroids centroids,
            double scmin, int kmin, int lastk, double[] scs) {
    }

    /** Outcome of a single GLA run for one cluster count. */
    private record StepResult(Partition partition, InfiniteCentroids centroids,
            double sc) {
    }

    /**
     * Creates an automatic SC-minimizer over the given vectors and config.
     *
     * @param vectorSet
     *            the vectors to cluster (must not be {@code null})
     * @param config
     *            the GLA configuration describing heuristic, scan limits, etc.
     *            (must not be {@code null})
     */
    public AutomaticSearch(VectorSet vectorSet, GLAConfig config) {
        this.vectorSet = Objects.requireNonNull(vectorSet, "VectorSet");
        this.config = Objects.requireNonNull(config, "GLAConfig");
    }

    /**
     * Runs the forward scan followed by the ping-pong enhancement phase.
     *
     * @return the best partition found together with its centroids and search
     *         metadata
     */
    public Result run() {
        int s = vectorSet.size();

        // scs[k] holds the best SC seen for k clusters; unassigned entries are
        // the MAX_VALUE sentinel. Size covers every reachable cluster count.
        double[] scs = new double[Math.min(s, MAXIMUM_CLASS_NUMBER) + 1];
        for (int i = 0; i < scs.length; i++) {
            scs[i] = UNASSIGNED_SC;
        }

        int kstart = 1;
        int kcStopWhen = config.kstopwhen() > 0 ? config.kstopwhen()
                : Integer.MAX_VALUE;

        // --- Forward scan (sca_scanner) -------------------------------------
        int kmin = kstart;
        double scmin = UNASSIGNED_SC;
        int lastk = kstart;
        Partition bestPartition = null;
        InfiniteCentroids bestCentroids = null;
        int kc = 0;

        for (int k = kstart; k <= Math.min(s, MAXIMUM_CLASS_NUMBER); k++) {
            StepResult step = runAttempts(k);
            if (step == null) {
                kc++;
                continue;
            }
            double sc = step.sc();
            log.info("k={}: SC={}", k, sc);

            if (scs[k] > sc) {
                scs[k] = sc;
            }
            saveCentroidsIfEnabled();

            if (sc < scmin) {
                kmin = k;
                scmin = sc;
                lastk = k;
                kc = 0;
                bestPartition = step.partition();
                bestCentroids = step.centroids();
            } else {
                kc++;
            }

            if (kc >= kcStopWhen) {
                break;
            }
        }

        // --- Ping-pong enhancement (sca_pingpong) ---------------------------
        // best_code_length is forced to false here so the enhancement phase
        // compares raw SC values. A safety pass cap guarantees termination even
        // when GLA keeps oscillating, mirroring C's -FX safety limit.
        boolean improved = true;
        int nkm = kmin;
        double[] scminHolder = { scmin };
        int maxPongPasses = config.safetyLimit() > 0
                ? config.safetyLimit()
                : MAXIMUM_CLASS_NUMBER;
        for (int pass = 0; pass < maxPongPasses && improved; pass++) {
            improved = false;

            // Ping: rescan the window below kmin.
            nkm = ping(scs, scminHolder, kstart, nkm);
            if (nkm < kmin) {
                kmin = nkm;
                improved = true;
            }

            // Pong: rescan the window above kmin.
            int pongKmin = Math.max(kmin, nkm);
            nkm = pong(scs, scminHolder, lastk, pongKmin);
            if (nkm > kmin) {
                kmin = nkm;
                improved = true;
            }
        }

        return new Result(bestPartition, bestCentroids, scminHolder[0], kmin,
                lastk, scs);
    }

    /**
     * Runs the ping phase of the enhancement loop. Mirrors C
     * {@code sca_pingpong()} rescan from {@code kstart} up to {@code kmin},
     * applying GLA only where SC is still decreasing toward the current best.
     *
     * @param scs
     *            the per-{@code k} best-SC array (updated in place)
     * @param scminHolder
     *            a single-element holder for the current best SC, updated in
     *            place when an improvement is found
     * @param kstart
     *            lowest cluster count to try
     * @param kmin
     *            current best cluster count
     * @return the improved cluster count ({@code nkm})
     */
    private int ping(double[] scs, double[] scminHolder, int kstart,
            int kmin) {
        double[] scmin = { scminHolder[0] };
        int nkm = kmin;
        for (int k = kstart; k < kmin; k++) {
            if (scs[k] > scmin[0]) {
                continue;
            }
            StepResult step = runAttempts(k);
            if (step == null) {
                continue;
            }
            double sc = step.sc();
            if (scs[k] > sc) {
                scs[k] = sc;
            }
            saveCentroidsIfEnabled();
            if (sc < scmin[0]) {
                nkm = k;
                scmin[0] = sc;
            }
        }
        return nkm;
    }

    /**
     * Runs the pong phase of the enhancement loop. Mirrors C
     * {@code sca_pingpong()} rescan from {@code lastk} down to {@code kmin},
     * applying GLA only where SC is still decreasing toward the current best.
     *
     * @param scs
     *            the per-{@code k} best-SC array (updated in place)
     * @param scminHolder
     *            a single-element holder for the current best SC, updated in
     *            place when an improvement is found
     * @param lastk
     *            highest cluster count to try
     * @param kmin
     *            current best cluster count
     * @return the improved cluster count ({@code nkm})
     */
    private int pong(double[] scs, double[] scminHolder, int lastk,
            int kmin) {
        double[] scmin = { scminHolder[0] };
        int nkm = kmin;
        for (int k = Math.min(lastk, scs.length - 1); k > kmin; k--) {
            if (scs[k] > scmin[0]) {
                continue;
            }
            StepResult step = runAttempts(k);
            if (step == null) {
                continue;
            }
            double sc = step.sc();
            if (scs[k] > sc) {
                scs[k] = sc;
            }
            saveCentroidsIfEnabled();
            if (sc < scmin[0]) {
                nkm = k;
                scmin[0] = sc;
            }
        }
        return nkm;
    }

    /**
     * Runs GLA for a single cluster count up to the configured number of
     * attempts ({@code -a}), keeping the result with the best SC. Each attempt
     * starts from a different centroid set so bad local minima are not counted.
     *
     * @param kk
     *            the target number of clusters (1-based)
     * @return the resulting partition, centroids and SC, or {@code null} when
     *         no valid clustering was produced across all attempts
     */
    private StepResult runAttempts(int kk) {
        int attempts = config.iterBase() > 0 ? config.iterBase() : 1;
        StepResult best = null;
        for (int attempt = 0; attempt < attempts; attempt++) {
            StepResult step = runSingleAttempt(kk, attempt);
            if (step != null && (best == null || step.sc() < best.sc())) {
                best = step;
            }
        }
        return best;
    }

    /**
     * Runs GLA once for a single cluster count. The first attempt seeds the
     * first {@code kk} input vectors (deterministic); later attempts use random
     * starting centroids to explore different local minima.
     *
     * @param kk
     *            the target number of clusters (1-based)
     * @param attempt
     *            zero-based attempt index
     * @return the resulting partition, centroids and SC, or {@code null} when
     *         no valid clustering was produced
     */
    private StepResult runSingleAttempt(int kk, int attempt) {
        InfiniteCentroids centroids;
        Partition partition;
        if (attempt == 0) {
            centroids = new InfiniteCentroids(kk + 1,
                    vectorLength());
            partition = initializePartition(kk, centroids);
        } else {
            // Different starting centroids per attempt to explore different
            // local minima. Use the same cluster count as attempt 0 (kk + 1) so
            // every trial scores on an equal footing. Pure random values match
            // C's default normal_centroids() and work regardless of n vs k. The
            // partition must match its size or GLA's setSize() shrink path
            // nulls
            // every cluster slot and addElement() throws.
            centroids = CentroidInitializer.randomInit(kk + 1, vectorLength());
            partition = new Partition(kk + 1);
        }

        double[] dmin = new double[1];
        switch (config.heuristic()) {
        case 2 -> GLAEngine.glaSr(vectorSet, partition, centroids, dmin,
                config);
        case 3 -> GLAEngine.glaSa(vectorSet, partition, centroids, dmin,
                config);
        case 4 -> GLAEngine.hybridGlaL1(vectorSet, partition, centroids, dmin,
                config);
        case 5 -> GLAEngine.hybridGlaL2(vectorSet, partition, centroids, dmin,
                config);
        case 6 -> GLAEngine.maeGla(vectorSet, partition, centroids, dmin,
                config);
        default -> GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
        }

        // Local search cycler (-r7) and adaptive (-r8) modes run the
        // multi-operator local_search() driver after GLA, mirroring C's
        // use_gla() which calls local_search() only when the cluster count
        // exceeds 3. An MSE nearest-neighbor repartition seeds the partition
        // first (as range search does), then the driver keeps the best
        // partition/centroids it finds, so we score that instead of the plain
        // GLA result.
        if ((config.heuristic() == 7 || config.heuristic() == 8)
                && centroids.size() > 3) {
            NearestNeighbor.mseNearestNeighbor(vectorSet, partition,
                    centroids);
            GLAEngine.removeEmpty(partition, centroids);
            GLAEngine.recomputeCentroids(partition, centroids, config.rounded(),
                    config.n());
            LocalSearch.localSearch(partition, centroids,
                    config.heuristicCount(), vectorLength(), config.n(),
                    config.jeffreysPrior(), new java.util.Random());
        }

        double sc = score(partition, centroids);
        if (Double.isNaN(sc) || sc == UNASSIGNED_SC) {
            return null;
        }
        return new StepResult(partition, centroids, sc);
    }

    /** Seeds the first {@code kk} input vectors as initial centroids. */
    private Partition initializePartition(int kk, InfiniteCentroids centroids) {
        Partition partition = new Partition(kk + 1);
        int idx = 0;
        for (BinaryVector bv : vectorSet) {
            if (idx >= kk) {
                break;
            }
            centroids.get(idx).setEl(bv.getEl());
            idx++;
        }
        return partition;
    }

    /** Scores a clustering using SC or best-code-length, per the config. */
    private double score(Partition partition, InfiniteCentroids centroids) {
        DistanceCalculator.setUseClassWeights(config.weights());
        DistanceCalculator.setUseRoundedCentroids(config.rounded());

        if (config.bestCodeLength()) {
            try {
                return DistanceCalculator.averageCodelength(partition,
                        centroids);
            } catch (ArithmeticException ex) {
                return UNASSIGNED_SC;
            }
        }

        int actualClusters = 0;
        for (int i = 1; i <= partition.size(); i++) {
            if (partition.getSize(i) > 0) {
                actualClusters++;
            }
        }
        if (actualClusters == 0) {
            return UNASSIGNED_SC;
        }
        return DistanceCalculator.stochasticComplexity(partition,
                actualClusters, vectorLength(), config.jeffreysPrior());
    }

    /** No-op centroid logger hook kept for parity with the C scan loop. */
    private void saveCentroidsIfEnabled() {
        // Centroid logging is handled by the CLI layer when -l is set; the
        // algorithm keeps no side state so it stays pure and testable.
    }

    /** Returns the vector length from the first stored vector (or 16). */
    private int vectorLength() {
        return vectorSet.size() > 0 ? vectorSet.iterator().next().getLength()
                : 16;
    }

    // Kept for API symmetry with C helpers that reference centroid weights.
    @SuppressWarnings("unused")
    private static double centroidWeight(Centroid c) {
        return c.getWeight();
    }
}
