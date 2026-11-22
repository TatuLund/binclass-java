/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.gla;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.centroid.CentroidInitializer;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.events.PartitionResult;
import org.binclass.algorithms.events.SearchBestPartition;
import org.binclass.algorithms.events.SearchCompleted;
import org.binclass.algorithms.events.SearchProgress;
import org.binclass.algorithms.eventbus.EventBus;
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
@NullMarked
public final class AutomaticSearch {

    /** Maximum number of classes allowed by the C tool (const.h / vars.c). */
    public static final int MAXIMUM_CLASS_NUMBER = 200;

    /**
     * Sentinel for an uncomputed SC value, matching {@code unassigned_sc()}.
     */
    public static final double UNASSIGNED_SC = Double.MAX_VALUE;

    @SuppressWarnings("null")
    private static final Logger log = LoggerFactory
            .getLogger(AutomaticSearch.class);

    private final VectorSet vectorSet;
    private final GLAConfig config;
    /** Display identifier attached to every event this search posts. */
    private final String display;

    /** Best cluster count seen so far across the whole search (1-based). */
    private int kmin = 1;
    /** Best stochastic complexity seen so far across the whole search. */
    private double scmin = UNASSIGNED_SC;
    /**
     * Partition/centroids for the current best, refreshed on every improvement.
     */
    @Nullable
    private Partition bestPartition = null;

    /**
     * Best centroids found for each cluster count {@code k} across the whole
     * search, keyed by cluster count. Populated whenever a lower SC is recorded
     * for a given {@code k}, so it mirrors C's per-candidate
     * {@code save_centroids()} output consumed by {@code binclass function}.
     */
    private final Map<Integer, InfiniteCentroids> bestPerK = new HashMap<>();

    @Nullable
    private InfiniteCentroids bestCentroidsHolder;

    /**
     * Result of the automatic search: best partition/centroids and metadata.
     */
    public record Result(@Nullable Partition partition,
            @Nullable InfiniteCentroids centroids, double scmin, int kmin,
            int lastk, double[] scs,
            Map<Integer, InfiniteCentroids> bestPerK) {
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
        this(vectorSet, config, "gla");
    }

    /**
     * Creates an automatic SC-minimizer over the given vectors and config.
     *
     * @param vectorSet
     *            the vectors to cluster (must not be {@code null})
     * @param config
     *            the GLA configuration describing heuristic, scan limits, etc.
     *            (must not be {@code null})
     * @param display
     *            identifier attached to every event this search posts
     */
    public AutomaticSearch(VectorSet vectorSet, GLAConfig config,
            String display) {
        this.vectorSet = Objects.requireNonNull(vectorSet, "VectorSet");
        this.config = Objects.requireNonNull(config, "GLAConfig");
        this.display = display;
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
        int lastk = kstart;
        int kc = 0;

        for (int k = kstart; k <= Math.min(s, MAXIMUM_CLASS_NUMBER); k++) {
            // Stop the forward scan promptly when a search is cancelled.
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            StepResult step = runAttempts(k);
            if (step == null) {
                kc++;
                continue;
            }
            double sc = step.sc();
            log.info("k={}: SC={}", k, sc);

            if (scs[k] > sc) {
                scs[k] = sc;
                bestPerK.put(k, step.centroids());
            }
            saveCentroidsIfEnabled();

            boolean improved = sc < scmin;
            if (improved) {
                kmin = k;
                scmin = sc;
                lastk = k;
                kc = 0;
                this.bestPartition = step.partition();
                this.bestCentroidsHolder = step.centroids();
            } else {
                kc++;
            }

            postProgress(k, scs, improved);

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
        for (int pass = 0; pass < maxPongPasses && improved
                && !Thread.currentThread().isInterrupted(); pass++) {
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

        Result result = new Result(this.bestPartition, this.bestCentroidsHolder,
                scminHolder[0], kmin, lastk, scs, bestPerK);
        postCompletion(result);
        return result;
    }

    /**
     * Posts the final {@link SearchCompleted} event carrying the search's best
     * partition and full SC curve.
     *
     * @param result
     *            the finished automatic-search result
     */
    private void postCompletion(Result result) {
        Partition partition = result.partition();
        if (partition != null) {
            EventBus.get().post(new SearchCompleted(display, buildResult(
                    result.kmin(), result.scmin(), partition,
                    result.scs(), result.centroids())));
        }
    }

    /**
     * Posts a per-{@code k} {@link SearchProgress} event and, when the step is
     * an improvement, a live {@link SearchBestPartition} snapshot so the UI can
     * refresh both the SC curve and the partition tab mid-search.
     *
     * @param k
     *            the cluster count just evaluated
     * @param scs
     *            the per-{@code k} best-SC array (used to build the completion
     *            curve)
     * @param improved
     *            whether this step lowered the running minimum SC
     */
    private void postProgress(int k, double[] scs, boolean improved) {
        EventBus.get().post(new SearchProgress(display, k, scs[k], false));
        Partition partition = bestPartition;
        if (improved && partition != null) {
            assert bestCentroidsHolder != null
                    : "Best centroids must not be null when best partition is available";
            assert scs != null
                    : "SC array must not be null when best partition is available";
            assert kmin == k
                    : "kmin must equal the current k when best partition is available";
            assert scs.length > k
                    : "SC array must have an entry for the current k when best partition is available";
            EventBus.get().post(new SearchBestPartition(
                    display, buildResult(kmin, scmin, partition, scs,
                            bestCentroidsHolder)));
        }
    }

    /**
     * Builds the {@link org.binclass.algorithms.events.PartitionResult} payload
     * from a best partition and the per-{@code k} SC array. Drops non-finite
     * points so the graph tab never renders {@code NaN}/{@code Inf}.
     *
     * @param kmin
     *            the current best cluster count
     * @param scmin
     *            the current best stochastic complexity
     * @param partition
     *            the current best partition (must not be {@code null})
     * @param scs
     *            the per-{@code k} best-SC array
     * @return the immutable payload the UI binds to
     */
    private PartitionResult buildResult(int kmin, double scmin,
            Partition partition, double[] scs,
            @Nullable InfiniteCentroids centroids) {
        List<Integer> ks = new ArrayList<>();
        List<Double> scList = new ArrayList<>();
        for (int k = 1; k < scs.length; k++) {
            if (Double.isFinite(scs[k])) {
                ks.add(k);
                scList.add(scs[k]);
            }
        }
        return new PartitionResult(kmin, scmin, partition.size(),
                toDoubleArray(scList), toIntArray(ks), partition, centroids);
    }

    private static double[] toDoubleArray(List<Double> values) {
        double[] out = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            out[i] = values.get(i);
        }
        return out;
    }

    private static int[] toIntArray(List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            out[i] = values.get(i);
        }
        return out;
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
                bestPerK.put(k, step.centroids());
            }
            saveCentroidsIfEnabled();
            if (sc < scmin[0]) {
                nkm = k;
                scmin[0] = sc;
                this.kmin = k;
                this.scmin = sc;
                this.bestPartition = step.partition();
                this.bestCentroidsHolder = step.centroids();
                postProgress(k, scs, true);
            } else {
                postProgress(k, scs, false);
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
                bestPerK.put(k, step.centroids());
            }
            saveCentroidsIfEnabled();
            if (sc < scmin[0]) {
                nkm = k;
                scmin[0] = sc;
                this.kmin = k;
                this.scmin = sc;
                this.bestPartition = step.partition();
                this.bestCentroidsHolder = step.centroids();
                postProgress(k, scs, true);
            } else {
                postProgress(k, scs, false);
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
    private @Nullable StepResult runAttempts(int kk) {
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
    private @Nullable StepResult runSingleAttempt(int kk, int attempt) {
        InfiniteCentroids centroids;
        Partition partition;
        if (attempt == 0) {
            centroids = new InfiniteCentroids(kk,
                    vectorLength());
            partition = initializePartition(kk, centroids);
        } else {
            // Different starting centroids per attempt to explore different
            // local minima. Use the same cluster count as attempt 0 (kk) so
            // every trial scores on an equal footing. Pure random values match
            // C's default normal_centroids() and work regardless of n vs k. The
            // partition must match its size or GLA's setSize() shrink path
            // nulls
            // every cluster slot and addElement() throws.
            centroids = CentroidInitializer.randomInit(kk, vectorLength());
            partition = new Partition(kk);
        }

        double[] dmin = new double[1];
        // The GLA algorithm variant is selected by the distance type (-f),
        // not by the local-search operator code (config.heuristic(), -r).
        // Mirrors C's use_gla() which branches on distance_type to pick
        // hybrid_l1 / hybrid_l2 / gla_sa / gla_sr / gla / MAE_gla / MSE_gla /
        // fast_gla.
        switch (config.distanceType()) {
        case 5 -> GLAEngine.hybridGlaL1(vectorSet, partition, centroids, dmin,
                config);
        case 6 -> GLAEngine.hybridGlaL2(vectorSet, partition, centroids, dmin,
                config);
        case 8 -> GLAEngine.glaSa(vectorSet, partition, centroids, dmin,
                config);
        case 7 -> GLAEngine.glaSr(vectorSet, partition, centroids, dmin,
                config);
        case 4 -> GLAEngine.gla(vectorSet, partition, centroids, dmin,
                config);
        case 2 -> GLAEngine.maeGla(vectorSet, partition, centroids, dmin,
                config);
        case 3 -> GLAEngine.mseGla(vectorSet, partition, centroids, dmin,
                config);
        default -> GLAEngine.fastGla(vectorSet, partition, centroids, dmin,
                config);
        }

        // Local search runs whenever -r is set (heuristic 1..8), mirroring
        // C's use_gla() which calls local_search() whenever ls_heuristic !=
        // HEUR_NONE and the cluster count exceeds 3. The single-operator modes
        // (-r1..6) and the cycler/adaptive modes (-r7/-r8) all route through
        // the same multi-operator driver; heuristicCode records which one was
        // requested. An MSE nearest-neighbor repartition seeds the partition
        // first (as range search does), then the driver keeps the best
        // partition/centroids it finds, so we score that instead of the plain
        // GLA result.
        if ((config.heuristic() >= 1 && config.heuristic() <= 8)
                && centroids.size() > 3) {
            // Clear the assignments left by the preceding GLA variant so MSE
            // nearest-neighbor repartition starts from an empty partition.
            // mseNearestNeighbor only calls setSize(k), which does not empty
            // existing clusters, so without this step every vector already
            // placed by fast GLA is duplicated, doubling the cluster counts.
            for (int i = 1; i <= partition.size(); i++) {
                partition.getElements(i).clear();
            }
            NearestNeighbor.mseNearestNeighbor(vectorSet, partition,
                    centroids);
            GLAEngine.removeEmpty(partition, centroids);
            GLAEngine.recomputeCentroids(partition, centroids, config.rounded(),
                    config.n());
            LocalSearch.localSearch(partition, centroids,
                    config.heuristicCount(), vectorLength(), config.n(),
                    config.jeffreysPrior(), new Random(), display,
                    config.heuristic(), config.rounded());
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
            if (partition.getClusterSize(i) > 0) {
                actualClusters++;
            }
        }
        if (actualClusters == 0) {
            return UNASSIGNED_SC;
        }
        // C scores a solution of `actualClusters` non-empty clusters with
        // k = realClusters + 1 (its partitions keep one spare index-0 slot).
        return DistanceCalculator.stochasticComplexity(partition,
                actualClusters + 1, vectorLength(), config.jeffreysPrior());
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
