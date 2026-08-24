package org.binclass.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.dist.DistanceCalculator;
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.GLAEngine;
import org.binclass.algorithms.io.PartitionWriter;
import org.binclass.algorithms.util.MathUtils;
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

        int vecsToGen = parseOptionInt(opts, "-v",
                "Invalid vecs_to_gen: " + opts.get("-v"));

        boolean saveBestBoots = opts.containsKey("-P");
        boolean jeffreysPrior = opts.containsKey("-J");

        int heuristic = parseOptionInt(opts, "-r",
                "Invalid heuristic type: " + opts.get("-r"));

        int bootstrapSize = parseOptionInt(opts, "-N",
                "Invalid bootstrap_size: " + opts.get("-N"), 50);

        int bootstrapK = parseOptionInt(opts, "-K",
                "Invalid bootstrap_k: " + opts.get("-K")) + 1;

        double epsilon = 0.001;
        if (opts.containsKey("-E")) {
            try {
                epsilon = Double.parseDouble(opts.get("-E"));
                if (epsilon >= 0.5)
                    throw new IllegalArgumentException("Epsilon must be < 0.5");
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid epsilon value: " + opts.get("-E"));
            }
        }

        int bootstrapI = 0;
        if (opts.containsKey("-I")) {
            try {
                bootstrapI = Integer.parseInt(opts.get("-I"));
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid bootstrap_i: " + opts.get("-I"));
            }
        }

        setupVerboseMode(opts);
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
        log.info("  Number of trials (-N): {}", bootstrapSize);
        log.info("  Resamplings (-I): {}", bootstrapI);
        log.info("  Save best boots: {}", saveBestBoots);

        // Load vectors from data files
        VectorSet vectorSet = DataLoader.loadVectors(filebase);

        // Size the log2-factorial lookup table used by stochastic complexity.
        // Mirrors C bootstraper(): the bootstrap trials may reference factorial
        // indices up to vecs_to_gen, so the table must cover max(size(V),
        // vecs_to_gen) + k rather than only the dataset size.
        int tot = vectorSet.size();
        if (vecsToGen > tot) {
            tot = vecsToGen;
        }
        MathUtils.prepareLog2Factorials(tot + bootstrapK);

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

        // Number of bootstrap trials mirrors C bootstraper(): the number of
        // partitions generated is driven by -N (bootstrap_size), while -I
        // (bootstrap_i) controls resampling iterations for the MLE/correlation
        // analysis performed after all trials complete.
        int numTrials = Math.max(1, bootstrapSize);

        log.info("Running {} bootstrap GLA trials with k={}, size={}",
                numTrials, bootstrapK, bootstrapSize);

        // Collect results from all trials for statistical analysis
        List<Double> scValues = new ArrayList<>();
        List<Partition> partitions = new ArrayList<>();
        Partition bestPartition = null;
        double minSC = Double.MAX_VALUE;

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

            // Calculate stochastic complexity for this trial using the shared
            // implementation (Jeffreys/uniform prior per -J), matching how
            // ClassifyCommand scores partitions. Mirrors C bootstraper():
            // sc = stochastic_complexity(P1, k, l). SC is only defined when at
            // least two clusters are non-empty; a degenerate single-cluster
            // result is treated as the worst model (MAX_VALUE), mirroring how
            // ClassifyCommand scores empty partitions.
            int nonEmptyClusters = countNonEmptyClusters(partition);
            double sc = nonEmptyClusters >= 2
                    ? DistanceCalculator.stochasticComplexity(
                            partition, nonEmptyClusters,
                            vectorSet.getVectorLength(), jeffreysPrior)
                    : Double.MAX_VALUE;
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

        // Perform statistical analysis on collected results. The resampling
        // count (-I) drives the number of bootstrap iterations performed over
        // the SC values, mirroring C boots_resample(f, bootstrap_i, SC, dist).
        performStatisticalAnalysis(scValues, partitions.size(), bootstrapI);

        // Save the best partition to <filebase>.par when -P is set. Mirrors C
        // bootstraper(): inf_write_partition(p,P) after the trials complete and
        // the best-scoring partition has been selected.
        if (saveBestBoots && bestPartition != null) {
            String parFile = filebase + ".par";
            try {
                PartitionWriter.writePartition(bestPartition, parFile);
                log.info("Saved best bootstrap partition with SC={} to {}",
                        minSC, parFile);
            } catch (IOException ex) {
                throw new IOException(
                        "Failed to save best bootstrap partition: "
                                + ex.getMessage(),
                        ex);
            }
        }

        log.info("Bootstrap analysis complete");

        return 0;
    }

    /**
     * Counts the number of non-empty clusters in a partition.
     * <p>
     * Stochastic complexity is only defined for partitions with at least two
     * populated clusters, so this mirrors the actual cluster count used when
     * scoring via {@link DistanceCalculator#stochasticComplexity}.
     * </p>
     *
     * @param partition
     *            the partition to inspect (1-based cluster indices)
     * @return number of clusters containing at least one vector
     */
    private static int countNonEmptyClusters(Partition partition) {
        int nonEmpty = 0;
        for (int i = 1; i <= partition.size(); i++) {
            if (partition.getSize(i) > 0) {
                nonEmpty++;
            }
        }
        return nonEmpty;
    }

    /**
     * Performs statistical analysis on bootstrap results.
     * <p>
     * The resampling count drives the number of bootstrap iterations performed
     * over the collected stochastic-complexity values, mirroring C
     * {@code boots_resample(f, bootstrap_i, SC, dist)}.
     * </p>
     */
    private static void performStatisticalAnalysis(List<Double> scValues,
            int numTrials, int resampleCount) {
        if (scValues.isEmpty()) {
            log.info("No trials completed");
            return;
        }

        // Bootstrap resampling over the SC values. Each iteration draws a
        // sample
        // with replacement and records the correlation between trial position
        // and
        // the resampled SC value, mirroring C boots_resample().
        if (resampleCount > 0) {
            Random random = new Random();
            double sumCorrelation = 0;
            for (int r = 1; r <= resampleCount; r++) {
                double[] resampled = new double[numTrials];
                for (int i = 0; i < numTrials; i++) {
                    int idx = random.nextInt(numTrials);
                    resampled[i] = scValues.get(idx);
                }
                sumCorrelation += calculateCorrelation(toDoubleList(
                        resampled));
            }
            log.info("Bootstrap Resampling Summary:");
            log.info("  Number of resamplings: {}", resampleCount);
            log.info("  Mean correlation: "
                    + String.format("%.4f", sumCorrelation / resampleCount));
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

    /**
     * Converts a primitive {@code double[]} to an immutable {@link List} of
     * {@link Double}, boxing each element. Used so resampled SC arrays can be
     * passed to {@link #calculateCorrelation(List)}.
     *
     * @param values
     *            the primitive array to box
     * @return a list containing one entry per array element
     */
    private static List<Double> toDoubleList(double[] values) {
        List<Double> result = new ArrayList<>(values.length);
        for (double v : values) {
            result.add(v);
        }
        return result;
    }
}
