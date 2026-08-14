package org.binclass.cli;

import java.util.Map;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.VectorSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Save/load centroids command.
 */
public class CentroidCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(CentroidCommand.class);

    @Override
    public String getName() {
        return "centroids";
    }

    @Override
    public String getDescription() {
        return "Save or load centroid files";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        boolean verbose = !opts.containsKey("-q");

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Centroids command executed with:");
        log.info("  Filebase: {}", filebase);

        // Load vectors from data files to create centroids
        VectorSet vectorSet = DataLoader.loadVectors(filebase);

        log.info("Processing {} vectors for centroid computation",
                vectorSet.size());

        // Create initial centroids from the loaded vectors
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

        log.info("Created {} initial centroids", numCentroids);

        return 0;
    }
}
