package org.binclass.cli;

import java.util.Map;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.dist.DistanceCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Render SC/Shannon functions vs k command.
 */
public class FunctionCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(FunctionCommand.class);

    @Override
    public String getName() {
        return "function";
    }

    @Override
    public String getDescription() {
        return "Render information-theoretic functions (SC, Shannon entropy) as function of k";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        setupVerboseMode(opts);
        boolean classWeights = opts.containsKey("-w");

        int distanceType = parseOptionInt(opts, "-f",
                "Invalid distance type: " + opts.get("-f"));

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Function command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Distance type: {}", distanceType);
        log.info("  Class weights: {}", classWeights);

        // Load vectors from data files
        VectorSet vectorSet = DataLoader.loadVectors(filebase);

        log.info("Computing information-theoretic functions for {} vectors",
                vectorSet.size());

        // Compute Shannon entropy and SC function as a function of k
        int maxK = Math.min(10, vectorSet.size());

        for (int k = 1; k <= maxK; k++) {
            Partition partition = new Partition(k);
            InfiniteCentroids centroids = new InfiniteCentroids(k, 16);

            // Initialize centroids from first k vectors and assign all vectors
            int idx = 0;
            for (BinaryVector bv : vectorSet) {
                if (idx < k) {
                    Centroid centroid = centroids.get(idx);
                    centroid.setEl(bv.getEl());
                }
                partition.getElements((idx % k) + 1).add(bv);
                idx++;
            }

            // Compute average codelength for this k value
            double avgCodelength = DistanceCalculator.averageCodelength(
                    partition, centroids);

            log.info("k={}: average_codelength={}", k, avgCodelength);
        }

        log.info("Function computation complete");

        return 0;
    }
}
