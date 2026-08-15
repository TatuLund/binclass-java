package org.binclass.cli;

import java.io.IOException;
import java.util.Map;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.dist.DistanceCalculator;
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.GLAEngine;
import org.binclass.algorithms.util.MathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classify vectors with GLA command.
 */
public class ClassifyCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(ClassifyCommand.class);

    @Override
    public String getName() {
        return "classify";
    }

    @Override
    public String getDescription() {
        return "Classify vectors using Generalized Lloyd Algorithm (GLA)";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        // Parse options
        int kstart = 1;
        if (opts.containsKey("-b")) {
            try {
                kstart = Integer.parseInt(opts.get("-b"));
                if (kstart < 1)
                    throw new IllegalArgumentException("kstart must be >= 1");
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid kstart value: " + opts.get("-b"));
            }
        }

        int kstop = 0;
        if (opts.containsKey("-s")) {
            try {
                kstop = Integer.parseInt(opts.get("-s"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid kstop value: " + opts.get("-s"));
            }
        }

        int kstopwhen = 0;
        if (opts.containsKey("-S")) {
            try {
                kstopwhen = Integer.parseInt(opts.get("-S"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid kstopwhen value: " + opts.get("-S"));
            }
        }

        double epsilon = 0.001;
        if (opts.containsKey("-E")) {
            try {
                epsilon = Double.parseDouble(opts.get("-E"));
                if (epsilon >= 0.5)
                    throw new IllegalArgumentException("Epsilon must be < 0.5");
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid epsilon value: " + opts.get("-E"));
            }
        }

        boolean verbose = !opts.containsKey("-q");

        // Set log level based on verbose mode: quiet mode suppresses INFO/DEBUG
        // messages
        if (!verbose) {
            ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory
                    .getLogger(ClassifyCommand.class))
                    .setLevel(ch.qos.logback.classic.Level.WARN);
        }

        int heuristic = 1;
        if (opts.containsKey("-r")) {
            try {
                heuristic = Integer.parseInt(opts.get("-r"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid heuristic type: " + opts.get("-r"));
            }
        }

        boolean trashcan = opts.containsKey("-t");
        int alternateMode = 1;
        if (opts.containsKey("-e")) {
            try {
                alternateMode = Integer.parseInt(opts.get("-e"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid alternate mode: " + opts.get("-e"));
            }
        }

        boolean analyseMissing = opts.containsKey("-m");
        int centroidType = 1; // CLASSIC
        if (opts.containsKey("-c")) {
            try {
                centroidType = Integer.parseInt(opts.get("-c"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid centroid type: " + opts.get("-c"));
            }
        }

        boolean logCentroids = opts.containsKey("-l");
        boolean jeffreysPrior = opts.containsKey("-J");
        boolean classWeights = opts.containsKey("-w");

        double firstD = 0;
        if (opts.containsKey("-B")) {
            try {
                firstD = Double.parseDouble(opts.get("-B"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid first_d value: " + opts.get("-B"));
            }
        }

        boolean bestCodeLength = opts.containsKey("-C");
        boolean roundedCentroids = opts.containsKey("-R");

        int distanceType = 1;
        if (opts.containsKey("-f")) {
            try {
                distanceType = Integer.parseInt(opts.get("-f"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid distance type: " + opts.get("-f"));
            }
        }

        int maxIter = 0;
        if (opts.containsKey("-n")) {
            try {
                maxIter = Integer.parseInt(opts.get("-n"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid max_iter: " + opts.get("-n"));
            }
        }

        int heuristicCount = 1;
        if (opts.containsKey("-j")) {
            try {
                heuristicCount = Integer.parseInt(opts.get("-j")) + 1;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid heuristic count: " + opts.get("-j"));
            }
        }

        int safetyLimit = maxIter > 0 ? maxIter : 1000;
        if (opts.containsKey("-F")) {
            try {
                safetyLimit = Integer.parseInt(opts.get("-F"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid safety limit: " + opts.get("-F"));
            }
        }

        int iterBase = 0;
        if (opts.containsKey("-a")) {
            try {
                iterBase = Integer.parseInt(opts.get("-a"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid iter_base: " + opts.get("-a"));
            }
        }

        String dumpfile = null;
        if (opts.containsKey("-d")) {
            dumpfile = opts.get("-d");
        }

        String centroidFile = null;
        if (opts.containsKey("-L")) {
            centroidFile = opts.get("-L");
        }

        // Get filebase from options or last argument
        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Classify command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  kstart: {}", kstart);
        log.info("  kstop: {}", kstop);
        log.info("  Epsilon: {}", epsilon);
        log.info("  Heuristic: {}", heuristic);
        log.info("  Centroid type: {}", centroidType);
        log.info("  Trashcan mode: {}", trashcan);
        log.info("  Analyse missing: {}", analyseMissing);
        log.info("  Log centroids: {}", logCentroids);
        log.info("  First distance: {}", firstD);
        log.info("  Best code length: {}", bestCodeLength);
        log.info("  Distance type: {}", distanceType);
        log.info("  Heuristic count: {}", heuristicCount);

        // Load vectors from data files
        VectorSet vectorSet = DataLoader.loadVectors(filebase);

        // Initialize log factorials for stochastic complexity calculations
        // (mirrors C code: prepare_log2_factorials(n+n) in binset.c)
        int n = vectorSet.size();
        MathUtils.prepareLog2Factorials((n + n));

        // Determine search range (kstart..kstop)
        int kEnd = kstop > 0 ? kstop : kstart;
        if (kstopwhen > 0 && kstop == 0) {
            // Automatic search: scan forward until no improvement in kstopwhen
            // steps
            kEnd = Integer.MAX_VALUE; // Will be limited by convergence check
        }

        log.info("Running range search from k={} to k={}", kstart, kEnd);

        // Build GLAConfig from parsed CLI options
        GLAConfig config = new GLAConfig(
                epsilon, // convergence threshold
                1.8, // pnnThreshold (default)
                heuristic, // heuristic type
                alternateMode, // alternate mode
                centroidType, // centroid type
                maxIter, // max iterations (0 = use default)
                safetyLimit, // safety limit
                iterBase, // iteration base
                vectorSet.size(), // n: total vectors
                kstopwhen, // -S flag: max clusters to search
                5, // kcStopWhen (-W flag): steps without SC improvement
                classWeights, // use class weights or uniform
                roundedCentroids, // round centroids to binary values
                jeffreysPrior, // use Jeffreys prior for stochastic complexity
                trashcan, // enable trashcan mode (-t flag)
                analyseMissing, // analyse missing bits (-m flag)
                logCentroids, // log centroid information (-l flag)
                firstD, // first distance value (-B flag)
                bestCodeLength, // use best code length criterion (-C flag)
                distanceType, // distance type: 1=HAM (int)
                heuristicCount // heuristic count parameter (-j flag)
        );

        // Run GLA for each k value and track best classification
        Partition bestPartition = runRangeSearch(vectorSet, kstart, kEnd,
                config);

        log.info("GLA completed with {} clusters", bestPartition.size());
        log.info("Final number of clusters: {}", bestPartition.size());

        return 0;
    }

    /**
     * Runs GLA for each k value from kstart to kstop, tracking stochastic
     * complexity. Mirrors the C function search_classes_nonautomatic() from
     * classify.c.
     */
    private static Partition runRangeSearch(
            VectorSet vectorSet,
            int kstart,
            int kstop,
            GLAConfig config) {

        Partition bestPartition = null;
        double scmin = Double.MAX_VALUE;
        int actualK = kstart;
        int noImprovementCount = 0;
        InfiniteCentroids centroids = null; // Declare outside loop for logging

        for (int k = kstart; k <= kstop; k++) {
            // Create new partition with (k+1) clusters (C uses 1-indexed)
            Partition partition = new Partition(k + 1);
            centroids = new InfiniteCentroids(k + 1, 16);

            // Initialize centroids from first k vectors
            int idx = 0;
            for (BinaryVector bv : vectorSet) {
                if (idx >= k)
                    break;
                Centroid centroid = centroids.get(idx);
                centroid.setEl(bv.getEl());
                idx++;
            }

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
            default:
                log.info("Defaulting to standard GLA");
                GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
            }

            // Calculate stochastic complexity or best code length based on flag
            double sc;
            int numClusters = Math.min(k + 1, partition.size());

            // Propagate GLAConfig flags to DistanceCalculator for codelength
            // calculations
            DistanceCalculator.setUseClassWeights(config.weights());
            DistanceCalculator.setUseRoundedCentroids(config.rounded());

            if (config.bestCodeLength()) {
                // Use best code length criterion instead of SC
                try {
                    sc = DistanceCalculator.averageCodelength(partition,
                            centroids);
                } catch (ArithmeticException e) {
                    log.debug("averageCodelength threw ArithmeticException: {}",
                            e.getMessage());
                    sc = 0.0; // Fallback for empty partitions
                }
            } else {
                // Check if we have enough non-empty clusters for full SC
                // calculation
                boolean hasEnoughClusters = true;
                for (int i = 1; i <= numClusters && hasEnoughClusters; i++) {
                    if (partition.getSize(i) == 0) {
                        hasEnoughClusters = false;
                    }
                }

                if (!hasEnoughClusters || numClusters < 2) {
                    // Not enough non-empty clusters: use simplified
                    // distortion-based SC
                    sc = calculateStochasticComplexity(partition, centroids,
                            config.distanceType());
                } else {
                    sc = DistanceCalculator.stochasticComplexity(
                            partition, numClusters, vectorSet.getVectorLength(),
                            config.jeffreysPrior());
                }
            }

            // Convergence check: stop if distortion change is below epsilon
            double prevScmin = scmin;
            scmin = Math.min(scmin, sc);
            boolean converged = (config.epsilon() > 0)
                    && (Math.abs(sc - prevScmin) < config.epsilon());

            log.info("k={}: SC={}, clusters={}, distType={}{}",
                    k, sc, partition.size(), config.distanceType(),
                    converged ? " [CONVERGED]" : "");

            if (sc < scmin || !converged) {
                scmin = Math.min(scmin, sc);
                bestPartition = partition;
                actualK = k;
                noImprovementCount = 0;
                log.debug("New best classification at k={}: SC={}", k, sc);
            } else {
                noImprovementCount++;
                // Early termination if no improvement for kstopwhen consecutive
                // steps or convergence reached
                int maxSteps = config.kstopwhen() > 0 ? config.kstopwhen()
                        : Integer.MAX_VALUE;
                if (noImprovementCount >= maxSteps || converged) {
                    log.info("Terminating: {}", converged ? "converged"
                            : "no improvement for " + noImprovementCount
                                    + " steps");
                    break;
                }
                log.debug("No improvement at k={}, trying next", k);
            }

            // Safety limit on range search iterations
            if (k - kstart >= config.safetyLimit()) {
                log.info("Safety limit reached at k={}", k);
                break;
            }

            // Update vectorSet for next iteration (C code does
            // partition_to_set)
            // For simplicity, we'll reuse the same vectorSet in this
            // implementation
        }

        // Log centroid info if enabled
        if (bestPartition != null) {
            logCentroidInfo(bestPartition, centroids,
                    config.logCentroids());
        }

        if (bestPartition == null) {
            bestPartition = new Partition(kstart + 1);
        }

        log.info("Best classification: k={}, SC={}", actualK, scmin);
        return bestPartition;
    }

    /**
     * Calculates stochastic complexity for single-cluster case using the
     * configured distance metric.
     */
    private static double calculateStochasticComplexity(Partition partition,
            InfiniteCentroids centroids, int distanceType) {
        double totalDistortion = 0;
        for (int i = 1; i <= partition.size(); i++) {
            var elements = partition.getElements(i);
            if (!elements.isEmpty()) {
                Centroid centroid = centroids.get(i - 1);
                for (BinaryVector bv : elements) {
                    totalDistortion += calculateDistance(bv, centroid,
                            distanceType);
                }
            }
        }
        return totalDistortion;
    }

    /**
     * Calculates distance between a vector and centroid using the configured
     * distance type. Dispatches to DistanceCalculator methods.
     *
     * @param bv
     *            the binary vector
     * @param centroid
     *            the centroid
     * @param distanceType
     *            1=Hamming, 2=L1 (Manhattan), 3=L2 (Euclidean squared),
     *            4=Shannon codelength
     * @return the computed distance
     */
    private static double calculateDistance(BinaryVector bv, Centroid centroid,
            int distanceType) {
        return switch (distanceType) {
        case 1 -> DistanceCalculator.hammingDistance(bv, centroid);
        case 2 -> DistanceCalculator.l1Distance(bv, centroid);
        case 3 -> DistanceCalculator.l2Distance(bv, centroid);
        case 4 -> DistanceCalculator.codeLength(bv, centroid);
        default -> DistanceCalculator.hammingDistance(bv, centroid);
        };
    }

    /**
     * Logs centroid information when logCentroids flag is enabled.
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
            log.debug("Cluster {}: size={}, entropy={:.4f}", i + 1, clusterSize,
                    entropy);
        }
    }

    /**
     * Calculates Shannon entropy for a centroid.
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

}
