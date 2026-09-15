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
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.SplitGLA;
import org.binclass.algorithms.io.PartitionWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fast classify with Split-GLA command.
 */
public class FastClassifyCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(FastClassifyCommand.class);

    @Override
    public String getName() {
        return "fclassify";
    }

    @Override
    public String getDescription() {
        return "Fast classification using Split-GLA algorithm";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        setupVerboseMode(opts);
        boolean useAbsMatch = opts.containsKey("-A");

        int kstopwhen = parseOptionInt(opts, "-S",
                "Invalid kstopwhen: " + opts.get("-S"));

        boolean jeffreysPrior = opts.containsKey("-J");

        double epsilon = parseOptionDouble(opts, "-E",
                "Invalid epsilon value: " + opts.get("-E"), 0.001);

        // Validate epsilon is within reasonable range (0 < epsilon < 0.5)
        if (epsilon <= 0 || epsilon >= 0.5) {
            throw new IllegalArgumentException(
                    "Invalid epsilon value: " + opts.get("-E"));
        }

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("FastClassify command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Use absolute match: {}", useAbsMatch);
        log.info("  Kstopwhen: {}", kstopwhen);

        // Load vectors from data files
        VectorSet vectorSet = DataLoader.loadVectors(filebase);

        // Build GLAConfig for Split-GLA
        GLAConfig config = new GLAConfig(
                epsilon, // convergence threshold
                1.8, // pnnThreshold (default)
                1, // heuristic (standard)
                1, // alternateMode (default)
                1, // centroidType (CLASSIC)
                0, // maxIter (use default)
                1000, // safetyLimit
                0, // iterBase
                vectorSet.size(), // n: total vectors
                kstopwhen, // -S flag: max clusters to search
                5, // kcStopWhen (-W flag): steps without SC improvement
                false, // weights (uniform by default)
                false, // rounded (fractional centroids by default)
                jeffreysPrior, // use Jeffreys prior for stochastic complexity
                false, // trashcan (disabled by default)
                false, // analyseMissing (disabled by default)
                false, // logCentroids (disabled by default)
                0.0, // firstD (no initial distance)
                false, // bestCodeLength (false by default)
                1, // distanceType (HAM by default)
                1, // heuristicCount (default)
                false, // filterExactK (disabled by default)
                false, // requireBetter (disabled by default)
                false, // lsCycler (disabled by default)
                false, // lsAdaptive (disabled by default)
                false, // decreasingEpsilon
                true, // alternateWorstMatch (default per C vars.c)
                false // alternateEmptyCellFix
        );

        log.info("Running Split-GLA algorithm on {} vectors",
                vectorSet.size());

        // Get actual vector length from first vector in set
        int vectorLength = vectorSet.size() > 0
                ? vectorSet.iterator().next().getLength()
                : 16;

        // Initialize centroids with first two vectors for binary split
        InfiniteCentroids centroids = new InfiniteCentroids(2, vectorLength);

        int idx = 0;
        for (BinaryVector bv : vectorSet) {
            if (idx >= 2)
                break;
            Centroid centroid = centroids.get(idx);
            centroid.setEl(bv.getEl());
            idx++;
        }

        // Run Split-GLA algorithm to find optimal number of clusters
        double[] scmin = new double[1];
        double[] scs = new double[vectorSet.size() + 1];

        Partition result = SplitGLA.splitGLA(vectorSet, scmin, scs, config);

        log.info("Split-GLA complete: found {} clusters", result.size());

        // Persist the resulting partition (default <filebase>.partition,
        // override with -P)
        String partitionFile = opts.getOrDefault("-P", null);
        if (partitionFile == null || partitionFile.isEmpty()) {
            partitionFile = filebase + ".partition";
        }
        int code = writePartition(result, partitionFile);
        if (code != 0) {
            return code;
        }

        return 0;
    }

    /**
     * Writes a partition to the given path, creating parent directories as
     * needed.
     *
     * @param partition
     *            the partition to persist
     * @param partitionFile
     *            destination file path
     * @return 0 on success, 1 if writing failed
     */
    private int writePartition(Partition partition, String partitionFile) {
        try {
            Path path = Path.of(partitionFile);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            PartitionWriter.writePartition(partition, partitionFile);
            log.info("Partition written to {}", partitionFile);
            return 0;
        } catch (IOException e) {
            log.warn("Failed to write partition to {}: {}", partitionFile,
                    e.getMessage());
            return 1;
        }
    }
}
