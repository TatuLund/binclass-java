package org.binclass.cli;

import java.io.IOException;
import java.util.Map;

import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.gla.GLAEngine;
import org.binclass.algorithms.io.CentroidWriter;
import org.binclass.algorithms.io.PartitionReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compute centroids from a classification (partition file).
 * <p>
 * Mirrors C's {@code do_save_centroids()} and section 4.3.1 of the BinClass
 * manual: reads a partition file, computes one centroid per class as the
 * per-bit frequency average of its member vectors, and writes the result to a
 * {@code .centroids} file that can be reloaded with the classify command's
 * {@code -L} switch. The optional {@code -R} flag rounds centroids to 0/1.
 * </p>
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
        return "Compute centroids from a classification partition file";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        setupVerboseMode(opts);

        boolean roundedCentroids = opts.containsKey("-R");

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Centroids command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Rounded centroids: {}", roundedCentroids);

        Partition partition = PartitionReader.readPartition(filebase);
        int k = partition.size();
        if (k == 0) {
            throw new IOException(
                    "No classes found in partition file for: " + filebase);
        }

        // Total number of vectors across all clusters, used as the weight
        // denominator so each centroid stores class_size / total_vectors.
        int n = 0;
        for (int i = 1; i <= k; i++) {
            n += partition.getSize(i);
        }

        InfiniteCentroids centroids = new InfiniteCentroids(k,
                getVectorLength(partition));
        GLAEngine.recomputeCentroids(partition, centroids, roundedCentroids, n);

        String outputFile = filebase + ".centroids";
        CentroidWriter.save(centroids, outputFile);

        log.info("Saved {} centroids to {}", k, outputFile);
        return 0;
    }

    /**
     * Determines the bit-length of vectors stored in a partition by returning
     * the length of the first vector found in any non-empty cluster. All
     * vectors in a valid partition share the same length.
     *
     * @param partition
     *            the partition to inspect
     * @return the vector bit-length, or 0 when no vectors are present
     */
    private static int getVectorLength(Partition partition) {
        for (int i = 1; i <= partition.size(); i++) {
            var cluster = partition.getElements(i);
            if (!cluster.isEmpty()) {
                return cluster.getVectorLength();
            }
        }
        return 0;
    }
}
