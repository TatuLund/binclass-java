package org.binclass.cli;

import java.util.Map;

import org.binclass.algorithms.classify.Classifier;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.io.PartitionWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Identify vectors by classification command.
 */
public class IdentifyCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(IdentifyCommand.class);

    @Override
    public String getName() {
        return "identify";
    }

    @Override
    public String getDescription() {
        return "Identify vectors by classification using nearest centroid";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        setupVerboseMode(opts);

        // Parse options
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

        int distanceType = 1; // Default HAM
        if (opts.containsKey("-f")) {
            try {
                distanceType = Integer.parseInt(opts.get("-f"));
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid distance type: " + opts.get("-f"));
            }
        }

        boolean jeffreysPrior = opts.containsKey("-J");
        boolean classWeights = opts.containsKey("-w");
        boolean trashcanMode = opts.containsKey("-t");
        boolean exactMatchesOnly = opts.containsKey("-M");
        String partitionFile = opts.getOrDefault("-P", null);

        // Get filebase from options or last argument
        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Identify command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Epsilon: {}", epsilon);
        log.info("  Distance type: {}", distanceType);
        log.info("  Jeffreys prior: {}", jeffreysPrior);
        log.info("  Class weights: {}", classWeights);
        log.info("  Trashcan mode: {}", trashcanMode);
        log.info("  Exact matches only: {}", exactMatchesOnly);

        // Load vectors from data files
        VectorSet vectorSet = DataLoader.loadVectors(filebase);

        // Create centroids (using first few vectors as initial centroids)
        int numCentroids = Math.min(3, vectorSet.size());
        InfiniteCentroids centroids = new InfiniteCentroids(numCentroids, 16);

        int idx = 0;
        for (BinaryVector bv : vectorSet) {
            if (idx >= numCentroids)
                break;
            Centroid centroid = centroids.get(idx);
            centroid.setEl(bv.getEl());
            idx++;
        }

        // Create partition and identify vectors
        Partition partition = new Partition(numCentroids);

        log.info("Identifying {} vectors into {} clusters",
                vectorSet.size(), numCentroids);

        if (distanceType == 1) {
            // Use Shannon codelength distance with epsilon threshold
            Classifier.identifyVectors(vectorSet, partition, centroids,
                    epsilon);
        } else {
            // Use Hamming distance for fast identification
            Classifier.identifyVectorsFast(vectorSet, partition, centroids);
        }

        log.info(
                "Identification complete: {} vectors classified into {} clusters",
                vectorSet.size(), numCentroids);

        // Write partition to file if -P specified
        if (partitionFile != null) {
            PartitionWriter.writePartition(partition, partitionFile);
            log.info("Partition written to {}", partitionFile);
        }

        return 0;
    }
}
