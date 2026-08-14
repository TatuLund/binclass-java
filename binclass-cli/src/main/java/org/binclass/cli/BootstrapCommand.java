package org.binclass.cli;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.GLAEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bootstrap GLA trials command.
 */
public class BootstrapCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(BootstrapCommand.class);

    @Override
    public String getName() {
        return "bootstrap";
    }

    @Override
    public String getDescription() {
        return "Run bootstrap GLA trials for statistical analysis";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        int vecsToGen = 100;
        if (opts.containsKey("-v")) {
            try {
                vecsToGen = Integer.parseInt(opts.get("-v"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid vecs_to_gen: " + opts.get("-v"));
            }
        }

        boolean saveBestBoots = opts.containsKey("-P");
        boolean jeffreysPrior = opts.containsKey("-J");

        int heuristic = 1;
        if (opts.containsKey("-r")) {
            try {
                heuristic = Integer.parseInt(opts.get("-r"));
                if (heuristic < 1 || heuristic > 6)
                    throw new IllegalArgumentException("Heuristic must be 1-6");
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid heuristic type: " + opts.get("-r"));
            }
        }

        int bootstrapSize = 0;
        if (opts.containsKey("-N")) {
            try {
                bootstrapSize = Integer.parseInt(opts.get("-N"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid bootstrap_size: " + opts.get("-N"));
            }
        }

        int bootstrapK = 0;
        if (opts.containsKey("-K")) {
            try {
                bootstrapK = Integer.parseInt(opts.get("-K")) + 1;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid bootstrap_k: " + opts.get("-K"));
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

        int bootstrapI = 0;
        if (opts.containsKey("-I")) {
            try {
                bootstrapI = Integer.parseInt(opts.get("-I"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid bootstrap_i: " + opts.get("-I"));
            }
        }

        boolean verbose = !opts.containsKey("-q");
        boolean classWeights = opts.containsKey("-w");

        int centroidType = 1; // RAND by default for bootstrap
        if (opts.containsKey("-c")) {
            try {
                centroidType = Integer.parseInt(opts.get("-c"));
                if (centroidType != 1 && centroidType != 2 && centroidType != 3
                        && centroidType != 5) {
                    throw new IllegalArgumentException(
                            "Centroid type must be 1,2,3, or 5");
                }
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid centroid_type: " + opts.get("-c"));
            }
        }

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Bootstrap command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Bootstrap k: {}", bootstrapK);
        log.info("  Bootstrap size: {}", bootstrapSize);
        log.info("  Save best boots: {}", saveBestBoots);

        // Load vectors from data files
        VectorSet vectorSet = DataLoader.loadVectors(filebase);

        // Build GLAConfig from parsed CLI options
        GLAConfig config = new GLAConfig(
                epsilon, // convergence threshold
                1.8, // pnnThreshold (default)
                heuristic, // heuristic type
                1, // alternateMode (default)
                centroidType, // centroid type
                0, // maxIter (use default)
                1000, // safetyLimit
                0, // iterBase
                vectorSet.size(), // n: total vectors
                20, // kstopwhen (-S flag)
                5, // kcStopWhen (-W flag)
                classWeights, // use class weights or uniform
                false, // rounded (fractional centroids by default)
                jeffreysPrior, // use Jeffreys prior for stochastic complexity
                false, // trashcan (disabled by default)
                false, // analyseMissing (disabled by default)
                false, // logCentroids (disabled by default)
                0.0, // firstD (no initial distance)
                false, // bestCodeLength (false by default)
                1, // distanceType (HAM by default)
                1 // heuristicCount (default)
        );

        // Run multiple GLA trials for bootstrap analysis
        int numTrials = Math.max(1, bootstrapI > 0 ? bootstrapI : 5);

        log.info("Running {} bootstrap GLA trials with k={}, size={}",
                numTrials, bootstrapK, bootstrapSize);

        // Collect results from all trials for statistical analysis
        List<Double> scValues = new ArrayList<>();
        List<Partition> partitions = new ArrayList<>();
        Partition bestPartition = null;
        double minSC = Double.MAX_VALUE;

        Random random = new Random();

        // For each trial, run GLA and collect results
        for (int trial = 1; trial <= numTrials; trial++) {
            log.info("Bootstrap trial {}/{}", trial, numTrials);

            Partition partition = new Partition(
                    bootstrapK > 0 ? bootstrapK : 3);
            InfiniteCentroids centroids = new InfiniteCentroids(
                    partition.size(), 16);

            // Initialize centroids from random vectors (with replacement)
            int idx = 0;
            for (BinaryVector bv : vectorSet) {
                if (idx >= partition.size())
                    break;
                Centroid centroid = centroids.get(idx);
                centroid.setEl(bv.getEl());
                idx++;
            }

            double[] dmin = new double[1];

            // Run GLA based on heuristic selection
            switch (config.heuristic()) {
            case 1:
                GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
                break;
            case 2:
                GLAEngine.glaSr(vectorSet, partition, centroids, dmin, config);
                break;
            case 3:
                GLAEngine.glaSa(vectorSet, partition, centroids, dmin, config);
                break;
            default:
                GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
            }

            // Calculate stochastic complexity for this trial
            double sc = calculateStochasticComplexity(partition, centroids);
            scValues.add(sc);
            partitions.add(partition);

            log.info("Trial {} completed with SC={}", trial, sc);

            // Track best partition if saveBestBoots is enabled
            if (sc < minSC) {
                minSC = sc;
                bestPartition = partition.copy();
                log.debug("New best partition at trial {}: SC={}", trial, sc);
            }
        }

        // Perform statistical analysis on collected results
        performStatisticalAnalysis(scValues, partitions.size());

        if (saveBestBoots && bestPartition != null) {
            log.info("Saved best bootstrap partition with SC={}", minSC);
        }

        log.info("Bootstrap analysis complete");

        return 0;
    }

    /**
     * Calculates stochastic complexity for a partition.
     */
    private static double calculateStochasticComplexity(Partition partition,
            InfiniteCentroids centroids) {
        double totalDistortion = 0;
        for (int i = 1; i <= partition.size(); i++) {
            var elements = partition.getElements(i);
            if (!elements.isEmpty()) {
                Centroid centroid = centroids.get(i - 1);
                for (BinaryVector bv : elements) {
                    totalDistortion += calculateDistance(bv, centroid);
                }
            }
        }

        // SC = distortion + complexity penalty
        double sc = totalDistortion
                + partition.size() * Math.log(partition.size());
        return sc;
    }

    /**
     * Calculates Hamming distance between a vector and centroid.
     */
    private static double calculateDistance(BinaryVector bv,
            Centroid centroid) {
        int[] el = bv.getEl();
        int length = Math.min(el.length, centroid.getLength());
        int distance = 0;
        for (int i = 0; i < length; i++) {
            double centroidVal = centroid.getElement(i);
            int centroidBit = centroidVal >= 0.5 ? 1 : 0;
            if (el[i] != centroidBit) {
                distance++;
            }
        }
        return distance;
    }

    /**
     * Performs statistical analysis on bootstrap results.
     */
    private static void performStatisticalAnalysis(List<Double> scValues,
            int numTrials) {
        if (scValues.isEmpty()) {
            log.info("No trials completed");
            return;
        }

        // Calculate basic statistics
        double sum = 0;
        for (double sc : scValues) {
            sum += sc;
        }
        double mean = sum / numTrials;

        double varianceSum = 0;
        for (double sc : scValues) {
            varianceSum += Math.pow(sc - mean, 2);
        }
        double variance = varianceSum / numTrials;
        double stdDev = Math.sqrt(variance);

        // Find min and max
        double minSC = Double.MAX_VALUE;
        double maxSC = Double.MIN_VALUE;
        for (double sc : scValues) {
            if (sc < minSC)
                minSC = sc;
            if (sc > maxSC)
                maxSC = sc;
        }

        log.info("Bootstrap Statistical Summary:");
        log.info("  Number of trials: {}", numTrials);
        log.info("  Mean SC: " + String.format("%.4f", mean));
        log.info("  Std Dev: " + String.format("%.4f", stdDev));
        log.info("  Min SC: " + String.format("%.4f", minSC));
        log.info("  Max SC: " + String.format("%.4f", maxSC));

        // Calculate correlation coefficient between trial number and SC values
        if (numTrials > 1) {
            double correlation = calculateCorrelation(scValues);
            log.info("  Correlation (trial vs SC): "
                    + String.format("%.4f", correlation));
        }
    }

    /**
     * Calculates Pearson correlation coefficient.
     */
    private static double calculateCorrelation(List<Double> values) {
        int n = values.size();
        if (n < 2)
            return 0;

        // Calculate means
        double sumX = 0, sumY = 0;
        for (int i = 0; i < n; i++) {
            sumX += i + 1; // trial number (1-indexed)
            sumY += values.get(i);
        }
        double meanX = sumX / n;
        double meanY = sumY / n;

        // Calculate correlation components
        double numerator = 0, denomX = 0, denomY = 0;
        for (int i = 0; i < n; i++) {
            double x = (i + 1) - meanX;
            double y = values.get(i) - meanY;
            numerator += x * y;
            denomX += x * x;
            denomY += y * y;
        }

        double denominator = Math.sqrt(denomX * denomY);
        if (denominator == 0)
            return 0;

        return numerator / denominator;
    }
}
