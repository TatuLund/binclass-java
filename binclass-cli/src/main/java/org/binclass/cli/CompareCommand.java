package org.binclass.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.binclass.algorithms.compare.PartitionComparator;
import org.binclass.algorithms.io.PartitionReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compare two partitions command.
 * <p>
 * Mirrors C {@code compare_partitions()} from {@code compare.c}: reads vectors
 * and two partition files derived from a filebase, builds the comparison matrix
 * between them, computes the nearness distance, and writes both the distance
 * and the full comparison matrix to a results file.
 * </p>
 */
public class CompareCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(CompareCommand.class);

    @Override
    public String getName() {
        return "compare";
    }

    @Override
    public String getDescription() {
        return "Compare two partitions and compute nearness metrics";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        setupVerboseMode(opts);
        boolean exactMatches = opts.containsKey("-M");

        int printMode = parseOptionInt(opts, "-V",
                "Invalid print mode: " + opts.get("-V"), 1);

        // Validate -V constraint (1|2|3)
        if (printMode < 1 || printMode > 3) {
            throw new IllegalArgumentException(
                    "Print mode must be 1, 2, or 3, got: " + printMode);
        }

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Compare command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Print mode: {} (1=nearness, 2=totalfreq, 3=partition)",
                printMode);
        log.info("  Exact matches: {}", exactMatches);

        // Read the two partitions from <filebase>.partition1 and
        // <filebase>.partition2. Mirrors C compare_partitions() which derives
        // these paths from the filebase.
        var partition1 = PartitionReader.readPartition(filebase, ".partition1");
        var partition2 = PartitionReader.readPartition(filebase, ".partition2");

        log.info("Comparing partitions: P1 size={}, P2 size={}",
                partition1.size(), partition2.size());

        // Build the comparison matrix and compute the nearness distance.
        var result = PartitionComparator.comparePartitions(partition1,
                partition2, printMode, exactMatches);

        log.info("Comparison complete: distance={}", result.distance());

        // Write both the distance and the full comparison matrix to the results
        // file (<filebase>.result), matching C's comparison_results().
        String outputFile = opts.getOrDefault("-o", null);
        if (outputFile == null || outputFile.isEmpty()) {
            outputFile = filebase + ".result";
        }
        try {
            Path path = Path.of(outputFile);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, result.render());
            log.info("Comparison results written to {}", outputFile);
        } catch (IOException e) {
            throw new IOException(
                    "Failed to write comparison results: " + e.getMessage(), e);
        }

        return 0;
    }
}
