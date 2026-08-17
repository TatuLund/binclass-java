package org.binclass.cli;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sort/partition operations command.
 */
public class SortPartCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(SortPartCommand.class);

    @Override
    public String getName() {
        return "sortpart";
    }

    @Override
    public String getDescription() {
        return "Sort and partition operations on binary vectors";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        setupVerboseMode(opts);

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("SortPart command executed with:");
        log.info("  Filebase: {}", filebase);

        // Load vectors from data files
        var vectorSet = DataLoader.loadVectors(filebase);

        if (vectorSet.size() < 2) {
            throw new IllegalArgumentException(
                    "Need at least two vectors to sort/partition");
        }

        log.info("Sorting {} vectors", vectorSet.size());

        // Create a partition with sorted classes by size (descending)
        var partition = new org.binclass.algorithms.core.Partition(2);

        int idx = 0;
        for (var v : vectorSet.getElements()) {
            if (idx % 2 == 0) {
                partition.addElement(1, v);
            } else {
                partition.addElement(2, v);
            }
            idx++;
        }

        // Sort classes by size (largest first)
        var sortedPartition = sortClassesBySize(partition);

        log.info("Sorted partition: {} classes", sortedPartition.size());
        for (int i = 1; i <= sortedPartition.size(); i++) {
            log.info("Class {}: {} vectors", i,
                    sortedPartition.getElements(i).size());
        }

        return 0;
    }

    /**
     * Sorts partition classes by size in descending order.
     * <p>
     * Equivalent to C function {@code sort_partition()} from the original
     * codebase.
     * </p>
     *
     * @param partition
     *            the input partition
     * @return a new partition with classes sorted by size (largest first)
     */
    static org.binclass.algorithms.core.Partition sortClassesBySize(
            org.binclass.algorithms.core.Partition partition) {

        int k = partition.size();
        var sortedPartition = new org.binclass.algorithms.core.Partition(k);

        // Collect class sizes and original indices
        int[] classSizes = new int[k + 1];
        for (int i = 1; i <= k; i++) {
            classSizes[i] = partition.getElements(i).size();
        }

        // Create array of class indices [1, 2, ..., k]
        Integer[] classIndices = new Integer[k];
        for (int i = 0; i < k; i++) {
            classIndices[i] = i + 1;
        }

        // Sort class indices by their sizes in descending order using bubble
        // sort
        boolean swapped;
        do {
            swapped = false;
            for (int i = 0; i < k - 1; i++) {
                if (classSizes[classIndices[i]] < classSizes[classIndices[i
                        + 1]]) {
                    // Swap indices to put larger class first
                    Integer tempIdx = classIndices[i];
                    classIndices[i] = classIndices[i + 1];
                    classIndices[i + 1] = tempIdx;
                    swapped = true;
                }
            }
        } while (swapped);

        // Copy vectors from original partition to sorted partition in the new
        // order
        for (int i = 0; i < k; i++) {
            int originalClassIndex = classIndices[i];
            var classVectors = partition.getElements(originalClassIndex);
            for (var v : classVectors) {
                sortedPartition.addElement(i + 1, v);
            }
        }

        return sortedPartition;
    }
}
