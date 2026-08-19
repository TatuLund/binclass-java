package org.binclass.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.dist.DistanceCalculator;
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.GLAEngine;
import org.binclass.algorithms.io.CentroidWriter;
import org.binclass.algorithms.io.PartitionWriter;
import org.binclass.algorithms.util.MathUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classify vectors with GLA command.
 */
public class ClassifyCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(ClassifyCommand.class);

    /** Best partition found during range search */
    private Partition bestPartition;

    /** Centroids from the best classification */
    private InfiniteCentroids bestCentroids;

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
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid kstart value: " + opts.get("-b"));
            }
        }

        int kstop = parseOptionInt(opts, "-s",
                "Invalid kstop value: " + opts.get("-s"));

        int kstopwhen = parseOptionInt(opts, "-S",
                "Invalid kstopwhen value: " + opts.get("-S"));

        double epsilon = parseOptionDouble(opts, "-E",
                "Invalid epsilon value: " + opts.get("-E"), 0.001);

        // Validate epsilon is within reasonable range (0 < epsilon < 0.5)
        if (epsilon <= 0 || epsilon >= 0.5) {
            throw new IllegalArgumentException(
                    "Invalid epsilon value: " + opts.get("-E"));
        }

        setupVerboseMode(opts);

        int heuristic = parseOptionInt(opts, "-r",
                "Invalid heuristic type: " + opts.get("-r"), 1);

        boolean trashcan = opts.containsKey("-t");
        int alternateMode = parseOptionInt(opts, "-e",
                "Invalid alternate mode: " + opts.get("-e"));

        boolean analyseMissing = opts.containsKey("-m");
        int centroidType = parseOptionInt(opts, "-c",
                "Invalid centroid type: " + opts.get("-c"));

        boolean logCentroids = opts.containsKey("-l");
        boolean jeffreysPrior = opts.containsKey("-J");
        boolean classWeights = opts.containsKey("-w");

        double firstD = parseOptionDouble(opts, "-B",
                "Invalid first_d value: " + opts.get("-B"));

        boolean bestCodeLength = opts.containsKey("-C");
        boolean roundedCentroids = opts.containsKey("-R");

        int distanceType = parseOptionInt(opts, "-f",
                "Invalid distance type: " + opts.get("-f"));

        int maxIter = parseOptionInt(opts, "-n",
                "Invalid max_iter: " + opts.get("-n"));

        int heuristicCount = parseOptionInt(opts, "-j",
                "Invalid heuristic count: " + opts.get("-j")) + 1;

        int safetyLimit = maxIter > 0 ? maxIter
                : parseOptionInt(opts, "-F",
                        "Invalid safety limit: " + opts.get("-F"),
                        500); // Default matches C code (safety_limit=500)

        int iterBase = parseOptionInt(opts, "-a",
                "Invalid iter_base: " + opts.get("-a"));

        String centroidFile = null;
        if (opts.containsKey("-L")) {
            centroidFile = opts.get("-L");
        }

        String partitionFile = opts.getOrDefault("-P", null);

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
        Partition finalPartition = runRangeSearch(vectorSet, kstart, kEnd,
                config);

        if (finalPartition == null) {
            log.warn("No partition found during range search");
            return 1;
        }

        log.info("GLA completed with {} clusters", finalPartition.size());
        log.info("Final number of clusters: {}", finalPartition.size());

        // Write centroids to file if -L specified
        if (centroidFile != null) {
            try {
                Path path = Path.of(centroidFile);
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                if (bestCentroids != null) {
                    CentroidWriter.save(bestCentroids, centroidFile);
                    log.info("Best partition written to {}", centroidFile);
                } else {
                    log.warn("No centroids available for writing");
                }
            } catch (IOException e) {
                log.warn("Failed to write best partition to {}: {}",
                        centroidFile,
                        e.getMessage());
                return 1;
            }
        }

        // Write partition data to file if -P specified
        if (partitionFile != null && finalPartition != null) {
            try {
                Path path = Path.of(partitionFile);
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }

                PartitionWriter.writePartition(finalPartition, partitionFile);
                log.info("Partition written to {}", partitionFile);
            } catch (IOException e) {
                log.warn("Failed to write partition to {}: {}", partitionFile,
                        e.getMessage());
                return 1;
            }
        }

        return 0;
    }

    /**
     * Runs GLA for each k value from kstart to kstop, tracking stochastic
     * complexity. Mirrors the C function search_classes_nonautomatic() from
     * classify.c.
     */
    private Partition runRangeSearch(
            VectorSet vectorSet,
            int kstart,
            int kstop,
            GLAConfig config) {

        double scmin = Double.MAX_VALUE;
        int actualK = kstart;
        int noImprovementCount = 0;
        InfiniteCentroids centroids = null; // Declare outside loop for logging

        bestPartition = null;
        bestCentroids = null;

        // Get the actual vector length from the first vector in the set
        int vectorLength = vectorSet.size() > 0
                ? vectorSet.iterator().next().getLength()
                : 16;

        for (int k = kstart; k <= kstop; k++) {
            centroids = new InfiniteCentroids(k + 1, vectorLength);
            Partition partition = initializePartition(vectorSet, k, centroids);

            double sc = runGLAAndCalculateSC(vectorSet, partition, centroids,
                    config, k);

            // Always update scmin to track the best SC seen so far
            if (sc < scmin) {
                scmin = sc;
                this.bestPartition = partition;
                this.bestCentroids = centroids;
                actualK = k;
                noImprovementCount = 0;
                log.debug("New best classification at k={}: SC={}, clusters={}",
                        k, sc, partition.size());
            } else {
                noImprovementCount++;
                if (shouldTerminate(noImprovementCount, config)) {
                    break;
                }
            }

            if (k - kstart >= config.safetyLimit()) {
                log.info("Safety limit reached at k={}", k);
                break;
            }
        }

        // Log centroid info if enabled
        if (this.bestPartition != null) {
            logCentroidInfo(this.bestPartition, centroids,
                    config.logCentroids());
        }

        if (this.bestPartition == null) {
            this.bestPartition = new Partition(kstart + 1);
        }

        log.info("Best classification: k={}, SC={}", actualK, scmin);
        return this.bestPartition;
    }

    /**
     * Initialize partition with first k vectors as centroids.
     */
    private Partition initializePartition(VectorSet vectorSet, int k,
            InfiniteCentroids centroids) {
        Partition partition = new Partition(k + 1);
        int idx = 0;
        for (BinaryVector bv : vectorSet) {
            if (idx >= k)
                break;
            Centroid centroid = centroids.get(idx);
            centroid.setEl(bv.getEl());
            idx++;
        }
        return partition;
    }

    /**
     * Run GLA and calculate stochastic complexity in one step.
     */
    private double runGLAAndCalculateSC(VectorSet vectorSet,
            Partition partition,
            InfiniteCentroids centroids, GLAConfig config, int k) {
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
     * Check if we should terminate the range search.
     */
    private boolean shouldTerminate(int noImprovementCount, GLAConfig config) {
        int maxSteps = config.kstopwhen() > 0 ? config.kstopwhen()
                : Integer.MAX_VALUE;
        return noImprovementCount >= maxSteps;
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
            log.debug("Cluster {}: size={}, entropy={}", i + 1, clusterSize,
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
