package org.binclass.cli;

import java.util.Map;

import org.binclass.algorithms.compare.PartitionComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compare two partitions command.
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

        // Load vectors from data files
        var vectorSet = DataLoader.loadVectors(filebase);

        if (vectorSet.size() < 2) {
            throw new IllegalArgumentException(
                    "Need at least two vectors to compare partitions");
        }

        log.info("Comparing {} vectors with print mode {}",
                vectorSet.size(), printMode);

        // Create two partitions from the same data for comparison
        var partition1 = new org.binclass.algorithms.core.Partition(2);
        var partition2 = new org.binclass.algorithms.core.Partition(2);

        int idx = 0;
        for (var v : vectorSet.getElements()) {
            if (idx % 2 == 0) {
                partition1.addElement(1, v);
            } else {
                partition1.addElement(2, v);
            }

            if ((idx / 2) % 2 == 0) {
                partition2.addElement(1, v);
            } else {
                partition2.addElement(2, v);
            }
            idx++;
        }

        // Call PartitionComparator with nearness metrics
        double distance = PartitionComparator.comparePartitions(
                partition1, partition2, printMode);

        log.info("Comparison complete: distance={}", distance);
        log.info("Partition 1 size={}, Partition 2 size={}",
                partition1.size(), partition2.size());

        return 0;
    }
}
