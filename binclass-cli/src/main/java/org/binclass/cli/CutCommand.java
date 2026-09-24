package org.binclass.cli;

import java.io.IOException;
import java.util.Map;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.cut.CutEngine;
import org.binclass.algorithms.io.PartitionWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cut/trim partitions command.
 */
public class CutCommand implements BaseCommand {

    private static final Logger log = LoggerFactory.getLogger(CutCommand.class);

    @Override
    public String getName() {
        return "cut";
    }

    @Override
    public String getDescription() {
        return "Cut or trim partitions based on interval analysis";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        setupVerboseMode(opts);

        boolean relativeInt = opts.containsKey("-r");
        boolean minimalInt = opts.containsKey("-s");
        boolean maximalInt = opts.containsKey("-m");
        boolean analyseStab = opts.containsKey("-A");
        boolean analyseInt = opts.containsKey("-a");

        int kstart1 = 0;
        if (analyseStab) {
            kstart1 = parseOptionInt(opts, "-A",
                    "Invalid analyse_int_stab: " + opts.get("-A")) + 1;
            if (kstart1 == 0)
                throw new IllegalArgumentException("kstart must be > 0");
        }

        int kstart2 = 0;
        if (analyseInt) {
            kstart2 = parseOptionInt(opts, "-a",
                    "Invalid analyse_int: " + opts.get("-a")) + 1;
            if (kstart2 == 0)
                throw new IllegalArgumentException("kstart must be > 0");
        }

        double delta = 0.0;
        if (opts.containsKey("-D")) {
            delta = parseOptionDouble(opts, "-D",
                    "Invalid fixed_delta: " + opts.get("-D"));
        } else if (opts.containsKey("-d")) {
            delta = parseOptionDouble(opts, "-d",
                    "Invalid delta: " + opts.get("-d"));
        }
        if (delta < 0)
            throw new IllegalArgumentException("Delta must be >= 0");

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Cut command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Relative interval: {}", relativeInt);
        log.info("  Minimal interval: {}", minimalInt);
        log.info("  Maximal interval: {}", maximalInt);
        log.info("  Analyse int stab: {}", analyseStab);

        // Load vectors from data files
        VectorSet vectorSet = DataLoader.loadVectors(filebase);

        log.info("Performing cut/trim analysis on {} vectors",
                vectorSet.size());

        Partition result = runAnalysis(vectorSet, relativeInt, minimalInt,
                maximalInt, analyseStab, kstart1, kstart2);

        // Save the resulting partition to <filebase>.partition (override with
        // -o).
        // Mirrors C int_partitions(): inf_write_partition(f, P) after
        // intersection.
        String outputFile = opts.getOrDefault("-o", null);
        if (outputFile == null || outputFile.isEmpty()) {
            outputFile = filebase + ".partition";
        }
        try {
            PartitionWriter.writePartition(result, outputFile);
            log.info("Saved cut partition with {} clusters to {}",
                    result.size(), outputFile);
        } catch (IOException ex) {
            throw new IOException(
                    "Failed to save cut partition: " + ex.getMessage(), ex);
        }

        log.info("Cut/trim analysis complete");

        return 0;
    }

    /**
     * Dispatches to the requested interval strategy based on the parsed flags.
     * Keeps {@link #execute} small by isolating the branch logic here.
     *
     * @param vectorSet
     *            the vectors being clustered
     * @param relativeInt
     *            whether the relative interval switch ({@code -r}) was set
     * @param minimalInt
     *            whether the minimal interval switch ({@code -s}) was set
     * @param maximalInt
     *            whether the maximal interval switch ({@code -m}) was set
     * @param analyseStab
     *            whether the stability analysis switch ({@code -A}) was set
     * @param kstart1
     *            cluster count for stability analysis (from {@code -A})
     * @param kstart2
     *            cluster count for interval analysis (from {@code -a})
     * @return the resulting partition from the selected strategy
     */
    private Partition runAnalysis(VectorSet vectorSet, boolean relativeInt,
            boolean minimalInt, boolean maximalInt, boolean analyseStab,
            int kstart1, int kstart2) {
        if (analyseStab) {
            return performAnalyseIntStab(vectorSet, kstart1);
        } else if (relativeInt) {
            return relativeIntervalAnalysis(vectorSet);
        } else if (minimalInt) {
            return minimalCut(vectorSet);
        } else if (maximalInt) {
            return maximalCut(vectorSet);
        }
        return performStandardCut(vectorSet, kstart1, kstart2);
    }

    /**
     * Performs interval stability analysis by generating multiple partitions
     * and iteratively intersecting them. Mirrors the C function
     * do_int_analyse2() from cut.c.
     */
    private Partition performAnalyseIntStab(VectorSet vectorSet, int kstart1) {
        int k = Math.max(2, kstart1 > 0 ? kstart1 : baseK(vectorSet));
        int partitions = Math.clamp(vectorSet.size(), 2, 3);

        Partition result = generatePartition(vectorSet, k);
        for (int i = 1; i < partitions; i++) {
            Partition partition = generatePartition(
                    vectorSet, Math.min(k + i, vectorSet.size()));
            result = CutEngine.simpleIntersection(result, partition);
        }

        log.info("Interval stability analysis complete. Result has {} clusters",
                result.size());
        return result;
    }

    /**
     * Performs relative interval analysis using simple set intersection.
     * Mirrors the C function do_simple_int() from cut.c.
     */
    private Partition relativeIntervalAnalysis(VectorSet vectorSet) {
        int k = baseK(vectorSet);
        Partition partition1 = generatePartition(vectorSet, k);
        Partition partition2 = generatePartition(
                vectorSet, Math.min(k + 1, vectorSet.size()));

        Partition result = CutEngine.simpleIntersection(partition1, partition2);
        log.info("Relative interval analysis complete. Result has {} clusters",
                result.size());
        return result;
    }

    /**
     * Performs minimal interval cut using best-match intersection. Mirrors the
     * C function do_min_int() from cut.c.
     */
    private Partition minimalCut(VectorSet vectorSet) {
        int k = baseK(vectorSet);
        Partition partition1 = generatePartition(vectorSet, k);
        Partition partition2 = generatePartition(
                vectorSet, Math.min(k + 1, vectorSet.size()));

        Partition result = CutEngine.minimalInterval(partition1, partition2);
        log.info("Minimal interval cut complete. Result has {} clusters",
                result.size());
        return result;
    }

    /**
     * Performs maximal interval cut using all-maximum-match intersection.
     * Mirrors the C function do_max_int() from cut.c.
     */
    private Partition maximalCut(VectorSet vectorSet) {
        int k = baseK(vectorSet);
        Partition partition1 = generatePartition(vectorSet, k);
        Partition partition2 = generatePartition(
                vectorSet, Math.min(k + 1, vectorSet.size()));

        Partition result = CutEngine.maximalInterval(partition1, partition2);
        log.info("Maximal interval cut complete. Result has {} clusters",
                result.size());
        return result;
    }

    /**
     * Performs standard cut/trim analysis across a range of cluster counts.
     * Mirrors the C int_partitions() default path (do_min_int).
     */
    private Partition performStandardCut(VectorSet vectorSet, int kstart1,
            int kstart2) {
        int startK = Math.max(2, kstart1 > 0 ? kstart1 : baseK(vectorSet));
        int endK = Math.min(kstart2 > 0 ? kstart2 : startK + 2,
                vectorSet.size());

        Partition partition1 = generatePartition(vectorSet, startK);
        Partition partition2 = generatePartition(vectorSet, endK);

        log.info("Analyzing partitions from k={} to k={}", startK, endK);

        Partition result = CutEngine.minimalInterval(partition1, partition2);
        log.info("Standard cut analysis complete. Result has {} clusters",
                result.size());
        return result;
    }

    /**
     * Derives a sensible base cluster count from the data size so that two
     * distinct partitions can be generated for interval analysis.
     *
     * @param vectorSet
     *            the vectors being clustered
     * @return a base cluster count between 2 and half the vector count
     */
    private static int baseK(VectorSet vectorSet) {
        return Math.max(2, Math.clamp(vectorSet.size() / 2, 1,
                vectorSet.size() - 1));
    }

    /**
     * Creates a partition by assigning each vector to its nearest centroid.
     * Centroids are seeded from the first k vectors for deterministic output.
     *
     * @param vectorSet
     *            the vectors being clustered
     * @param k
     *            the number of clusters (1-based)
     * @return a populated partition with the requested cluster count
     */
    private Partition generatePartition(VectorSet vectorSet, int k) {
        int length = vectorSet.getVectorLength();
        Partition partition = new Partition(k);
        InfiniteCentroids centroids = new InfiniteCentroids(k, length);

        int idx = 0;
        for (BinaryVector bv : vectorSet) {
            if (idx >= k)
                break;
            centroids.get(idx).setEl(bv.getEl());
            idx++;
        }

        for (BinaryVector bv : vectorSet) {
            int bestCluster = 1;
            double minDistance = Double.MAX_VALUE;

            for (int i = 0; i < k; i++) {
                Centroid centroid = centroids.get(i);
                double distance = calculateDistance(bv, centroid);
                if (distance < minDistance) {
                    minDistance = distance;
                    bestCluster = i + 1;
                }
            }

            partition.getElements(bestCluster).add(bv);
        }

        return partition;
    }

    /**
     * Calculates Hamming distance between a vector and centroid.
     *
     * @param bv
     *            the binary vector to measure
     * @param centroid
     *            the reference centroid
     * @return the number of differing bit positions
     */
    private double calculateDistance(BinaryVector bv, Centroid centroid) {
        int[] el = bv.getEl();
        int length = Math.min(el.length, centroid.getLength());
        int distance = 0;
        for (int i = 0; i < length; i++) {
            int centroidBit = centroid.getElement(i) >= 0.5 ? 1 : 0;
            if (el[i] != centroidBit) {
                distance++;
            }
        }
        return distance;
    }
}
