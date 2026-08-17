package org.binclass.cli;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
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

        boolean analyseIntStab = false;
        int kstart1 = 0;
        if (opts.containsKey("-A")) {
            try {
                kstart1 = Integer.parseInt(opts.get("-A")) + 1;
                if (kstart1 == 0)
                    throw new IllegalArgumentException("kstart must be > 0");
                analyseIntStab = true;
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid analyse_int_stab: " + opts.get("-A"));
            }
        }

        int kstart2 = 0;
        if (opts.containsKey("-a")) {
            try {
                kstart2 = Integer.parseInt(opts.get("-a")) + 1;
                if (kstart2 == 0)
                    throw new IllegalArgumentException("kstart must be > 0");
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid analyse_int: " + opts.get("-a"));
            }
        }

        boolean fixedDelta = false;
        double realDeltaValue = 0.0;
        if (opts.containsKey("-D")) {
            try {
                realDeltaValue = Double.parseDouble(opts.get("-D"));
                if (realDeltaValue < 0)
                    throw new IllegalArgumentException("Delta must be >= 0");
                fixedDelta = true;
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid fixed_delta: " + opts.get("-D"));
            }
        } else if (opts.containsKey("-d")) {
            try {
                realDeltaValue = Double.parseDouble(opts.get("-d"));
                if (realDeltaValue < 0)
                    throw new IllegalArgumentException("Delta must be >= 0");
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid delta: " + opts.get("-d"));
            }
        }

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Cut command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Relative interval: {}", relativeInt);
        log.info("  Analyse int stab: {}", analyseIntStab);
        log.info("  Fixed delta: {}", fixedDelta);

        // Load vectors from data files
        VectorSet vectorSet = DataLoader.loadVectors(filebase);

        log.info("Performing cut/trim analysis on {} vectors",
                vectorSet.size());

        if (analyseIntStab) {
            performAnalyseIntStab(vectorSet, kstart1, realDeltaValue);
        } else if (relativeInt) {
            performRelativeIntervalAnalysis(vectorSet, kstart2, realDeltaValue);
        } else {
            performStandardCutAnalysis(vectorSet, kstart1, kstart2, fixedDelta,
                    realDeltaValue);
        }

        log.info("Cut/trim analysis complete");

        return 0;
    }

    /**
     * Performs interval stability analysis by generating multiple partitions
     * and intersecting them. Mirrors the C function do_int_analyse2() from
     * cut.c.
     */
    private void performAnalyseIntStab(VectorSet vectorSet, int kstart1,
            double delta) {
        log.info("Performing interval stability analysis");

        List<Partition> partitions = new ArrayList<>();

        // Generate multiple random partitions (simplified - using deterministic
        // initialization)
        // Limit number of partitions to avoid creating more clusters than
        // vectors
        int numPartitions = Math.min(3, vectorSet.size());
        for (int i = 0; i < numPartitions; i++) {
            int k = Math.max(1, Math.min(kstart1 + i, vectorSet.size()));
            Partition partition = createRandomPartition(vectorSet, k);
            partitions.add(partition);
            log.info("Generated partition {} with k={}", i + 1, k);
        }

        // Perform iterative intersection to find stable clusters
        Partition result = performIterativeIntersection(partitions, delta);

        log.info("Interval stability analysis complete. Result has {} clusters",
                result.size());
    }

    /**
     * Performs relative interval analysis using simple set intersection.
     * Mirrors the C function do_simple_int() from cut.c.
     */
    private void performRelativeIntervalAnalysis(VectorSet vectorSet,
            int kstart2, double delta) {
        log.info("Performing relative interval analysis");

        // Create two partitions for comparison
        Partition partition1 = createRandomPartition(vectorSet,
                Math.max(2, kstart2));
        Partition partition2 = createRandomPartition(vectorSet,
                Math.max(2, kstart2 + 1));

        // Perform simple intersection
        Partition result = performSimpleIntersection(partition1, partition2);

        log.info("Relative interval analysis complete. Result has {} clusters",
                result.size());
    }

    /**
     * Performs standard cut/trim analysis with minimal and maximal intervals.
     */
    private void performStandardCutAnalysis(VectorSet vectorSet, int kstart1,
            int kstart2,
            boolean fixedDelta, double delta) {
        log.info("Performing standard cut/trim analysis");

        // Determine k range for interval analysis
        int startK = Math.max(1, kstart1 > 0 ? kstart1 : 2);
        int endK = Math.max(startK + 1, kstart2 > 0 ? kstart2 : startK + 5);

        log.info("Analyzing partitions from k={} to k={}", startK, endK);

        // Create partitions for each k value and perform interval analysis
        List<Partition> partitions = new ArrayList<>();
        for (int k = startK; k <= endK; k++) {
            Partition partition = createRandomPartition(vectorSet, k);
            partitions.add(partition);
            log.info("k={}: created partition with {} vectors", k,
                    vectorSet.size());
        }

        // Perform minimal interval analysis (do_min_int)
        if (partitions.size() >= 2) {
            Partition minInterval = performMinimalIntersection(
                    partitions.get(0), partitions.get(1));
            log.info(
                    "Minimal interval analysis complete. Result has {} clusters",
                    minInterval.size());

            // Perform maximal interval analysis (do_max_int)
            Partition maxInterval = performMaximalIntersection(
                    partitions.get(0), partitions.get(1));
            log.info(
                    "Maximal interval analysis complete. Result has {} clusters",
                    maxInterval.size());
        }
    }

    /**
     * Creates a partition with random initialization from the vector set.
     */
    private Partition createRandomPartition(VectorSet vectorSet, int k) {
        Partition partition = new Partition(k);
        InfiniteCentroids centroids = new InfiniteCentroids(k, 16);

        // Initialize centroids from vectors (deterministic for now)
        int idx = 0;
        for (BinaryVector bv : vectorSet) {
            if (idx >= k)
                break;
            Centroid centroid = centroids.get(idx);
            centroid.setEl(bv.getEl());
            idx++;
        }

        // Distribute vectors across clusters based on nearest centroid
        for (BinaryVector bv : vectorSet) {
            int bestCluster = 1;
            double minDistance = Double.MAX_VALUE;

            for (int i = 0; i < k; i++) {
                Centroid centroid = centroids.get(i);
                double distance = calculateDistance(bv, centroid);
                if (distance < minDistance) {
                    minDistance = distance;
                    bestCluster = i + 1; // 1-indexed
                }
            }

            partition.getElements(bestCluster).add(bv);
        }

        return partition;
    }

    /**
     * Performs iterative intersection of multiple partitions to find stable
     * clusters.
     */
    private Partition performIterativeIntersection(List<Partition> partitions,
            double delta) {
        if (partitions.isEmpty()) {
            return new Partition(1);
        }

        Partition result = partitions.get(0);

        // Iteratively intersect with each partition
        for (int i = 1; i < partitions.size(); i++) {
            result = performSimpleIntersection(result, partitions.get(i));
        }

        return result;
    }

    /**
     * Performs simple set intersection of two partitions.
     */
    private Partition performSimpleIntersection(Partition p1, Partition p2) {
        int k1 = p1.size();
        int k2 = p2.size();

        // Create result partition with all combinations (k1 * k2 clusters)
        Partition result = new Partition(k1 * k2);

        // For each cluster in p1, find matching clusters in p2 and create
        // intersections
        for (int i = 1; i <= k1; i++) {
            var elementsP1 = p1.getElements(i);

            Set<String> elementsP1Set = new HashSet<>();
            for (BinaryVector bv : elementsP1) {
                elementsP1Set.add(bv.toString());
            }

            for (int j = 1; j <= k2; j++) {
                var elementsP2 = p2.getElements(j);

                int resultCluster = (i - 1) * k2 + j; // Unique cluster ID
                                                      // (1-based)
                var resultElements = result.getElements(resultCluster);

                for (BinaryVector bv : elementsP2) {
                    if (elementsP1Set.contains(bv.toString())) {
                        resultElements.add(bv);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Performs minimal interval analysis - finds best matching clusters between
     * partitions.
     */
    private Partition performMinimalIntersection(Partition p1, Partition p2) {
        int k1 = p1.size();
        int k2 = p2.size();

        // Create result partition with max(k1, k2) clusters
        Partition result = new Partition(Math.max(k1, k2));

        // For each cluster in p1, find the best matching cluster in p2 (max
        // overlap)
        for (int i = 1; i <= k1; i++) {
            var elementsP1 = p1.getElements(i);

            int bestMatch = -1;
            int maxOverlap = 0;

            // Find cluster in p2 with maximum overlap
            for (int j = 1; j <= k2; j++) {
                var elementsP2 = p2.getElements(j);

                Set<String> elementsP1Set = new HashSet<>();
                for (BinaryVector bv : elementsP1) {
                    elementsP1Set.add(bv.toString());
                }

                int overlap = 0;
                for (BinaryVector bv : elementsP2) {
                    if (elementsP1Set.contains(bv.toString())) {
                        overlap++;
                    }
                }

                if (overlap > maxOverlap) {
                    maxOverlap = overlap;
                    bestMatch = j;
                }
            }

            // Add overlapping elements to result cluster
            if (bestMatch > 0 && maxOverlap > 0) {
                var elementsP2 = p2.getElements(bestMatch);
                Set<String> elementsP1Set = new HashSet<>();
                for (BinaryVector bv : elementsP1) {
                    elementsP1Set.add(bv.toString());
                }

                int resultCluster = i;
                var resultElements = result.getElements(resultCluster);

                for (BinaryVector bv : elementsP2) {
                    if (elementsP1Set.contains(bv.toString())) {
                        resultElements.add(bv);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Performs maximal interval analysis - creates intersections for all
     * maximal matches.
     */
    private Partition performMaximalIntersection(Partition p1, Partition p2) {
        int k1 = p1.size();
        int k2 = p2.size();

        // Create result partition with k1 * k2 clusters (all combinations)
        Partition result = new Partition(k1 * k2);

        for (int i = 1; i <= k1; i++) {
            var elementsP1 = p1.getElements(i);

            Set<String> elementsP1Set = new HashSet<>();
            for (BinaryVector bv : elementsP1) {
                elementsP1Set.add(bv.toString());
            }

            for (int j = 1; j <= k2; j++) {
                var elementsP2 = p2.getElements(j);

                int resultCluster = (i - 1) * k2 + j; // Unique cluster ID
                var resultElements = result.getElements(resultCluster);

                for (BinaryVector bv : elementsP2) {
                    if (elementsP1Set.contains(bv.toString())) {
                        resultElements.add(bv);
                    }
                }
            }
        }

        return result;
    }

    /**
     * Calculates Hamming distance between a vector and centroid.
     */
    private double calculateDistance(BinaryVector bv, Centroid centroid) {
        int[] el = bv.getEl();
        int length = Math.min(el.length, centroid.getLength());
        int distance = 0;
        for (int i = 0; i < length; i++) {
            double centroidVal = centroid.getElement(i);
            int centroidBit = centroidVal >= 0.5 ? 1 : 0;
            if (el[i] != centroidBit) {
                distance++;
            }
        }
        return distance;
    }
}
