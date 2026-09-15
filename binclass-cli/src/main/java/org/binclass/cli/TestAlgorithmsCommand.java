package org.binclass.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.binclass.algorithms.classify.CumulativeClassifier;
import org.binclass.algorithms.centroid.CentroidManager;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.DynamicPartition;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.dist.DistanceCalculator;
import org.binclass.algorithms.gla.GLAEngine;
import org.binclass.algorithms.io.PartitionWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test algorithm commands (test1 and test2).
 */
public class TestAlgorithmsCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(TestAlgorithmsCommand.class);

    private final Random random = new Random();

    private final String testName;

    public TestAlgorithmsCommand(String testName) {
        this.testName = testName;
    }

    @Override
    public String getName() {
        return testName;
    }

    @Override
    public String getDescription() {
        if ("test1".equals(testName)) {
            return "Test algorithm 1 (distortion minimizer)";
        } else {
            return "Test algorithm 2 (semi-cumulative)";
        }
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        setupVerboseMode(opts);

        if ("test1".equals(testName)) {
            return executeTest1(opts, args);
        } else if ("test2".equals(testName)) {
            return executeTest2(opts, args);
        } else {
            throw new IllegalArgumentException("Unknown test: " + testName);
        }
    }

    private int executeTest1(Map<String, String> opts,
            CliParser.CommandArgs args) throws NumberFormatException {
        int kstart = 0;
        if (opts.containsKey("-k")) {
            try {
                kstart = Integer.parseInt(opts.get("-k")) + 1;
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid kstart: " + opts.get("-k"));
            }
        }

        int t1RsCount = 0;
        if (opts.containsKey("-r")) {
            try {
                t1RsCount = Integer.parseInt(opts.get("-r")) + 1;
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid t1_rs_count: " + opts.get("-r"));
            }
        }

        int t1Trials = 0;
        if (opts.containsKey("-t")) {
            try {
                t1Trials = Integer.parseInt(opts.get("-t")) + 1;
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid t1_trials: " + opts.get("-t"));
            }
        }

        boolean extraIter = opts.containsKey("-e");

        if (kstart == 0) {
            throw new IllegalArgumentException("test1 requires -k option");
        }

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Test1 command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Kstart: {}", kstart);
        log.info("  RS count: {}", t1RsCount);
        log.info("  Trials: {}", t1Trials);
        log.info("  Extra iterations: {}", extraIter);

        try {
            return runTest1(opts, filebase, kstart, t1RsCount, t1Trials,
                    extraIter);
        } catch (IOException e) {
            log.warn("test1 failed: {}", e.getMessage());
            return 1;
        }
    }

    /**
     * Runs the distortion-minimizer algorithm (C {@code apply_alg1}).
     * <p>
     * For each trial it builds a random partition with {@code kstart} clusters,
     * refines it, applies random swaps that keep stochastic-complexity gains,
     * and saves the best partition found to the output file. Mirrors
     * {@code t_alg_1.c}.
     * </p>
     *
     * @param filebase
     *            base name of the data files
     * @param kstart
     *            number of clusters (1-based)
     * @param t1RsCount
     *            number of random swaps per trial
     * @param t1Trials
     *            number of trials to run
     * @param extraIter
     *            whether to refine each swap candidate with step 2
     * @return exit code (0 = success)
     */
    private int runTest1(Map<String, String> opts, String filebase,
            int kstart, int t1RsCount, int t1Trials, boolean extraIter)
            throws IOException {
        VectorSet vectorSet = DataLoader.loadVectors(filebase);
        int length = vectorSet.getVectorLength();

        List<BinaryVector> vectors = vectorSetToList(vectorSet);

        Partition bestPartition = null;
        double scmin = Double.MAX_VALUE;

        // Run each stochastic-complexity trial.
        for (int trial = 1; trial < t1Trials; trial++) {
            Partition partition = alg1(vectors, kstart, random);
            partition = alg1Enhance(partition, kstart, t1RsCount, extraIter,
                    random);

            double sc = stochasticComplexitySafe(partition, kstart, length);
            if (!Double.isNaN(sc) && sc < scmin) {
                scmin = sc;
                bestPartition = partition.copy();
            }

            // Reuse the vectors for the next trial.
            vectors = vectorSetToList(GLAEngine.partitionToSet(partition));
        }

        String outputFile = opts.getOrDefault("-P", null);
        if (outputFile == null || outputFile.isEmpty()) {
            outputFile = filebase + ".partition";
        }

        if (bestPartition != null) {
            PartitionWriter.writePartition(bestPartition, outputFile);
            log.info("test1 best partition written to {}", outputFile);
        }

        return 0;
    }

    /**
     * Builds a random {@code k}-cluster partition from the given vectors.
     * <p>
     * Mirrors C {@code alg1_init} + {@code alg1_step1}: picks {@code k - 1}
     * distinct vectors as initial centroids, then assigns every remaining
     * vector to its nearest centroid by average Hamming distance.
     * </p>
     *
     * @param vectors
     *            the candidate vectors (initial cluster members are removed)
     * @param kstart
     *            number of clusters (1-based)
     * @param length
     *            vector length
     * @param random
     *            RNG for selecting initial centroids
     * @return a partition with {@code kstart} clusters
     */
    private Partition alg1(List<BinaryVector> vectors, int kstart,
            Random random) {
        // Pick k - 1 distinct seed vectors as the initial clusters.
        List<BinaryVector> seeds = new ArrayList<>();
        while (seeds.size() < kstart - 1 && !vectors.isEmpty()) {
            BinaryVector candidate = vectors
                    .get(random.nextInt(vectors.size()));
            if (!seeds.contains(candidate)) {
                seeds.add(candidate);
                vectors.remove(candidate);
            }
        }

        Partition partition = new Partition(kstart);
        for (int i = 0; i < seeds.size(); i++) {
            partition.addElement(i + 1, seeds.get(i));
        }

        // Assign every remaining vector to its nearest seed.
        for (BinaryVector bv : vectors) {
            int bestCluster = 1;
            int bestDist = Integer.MAX_VALUE;
            for (int i = 0; i < seeds.size(); i++) {
                int dist = DistanceCalculator.hammingDistanceVectors(bv,
                        seeds.get(i));
                if (dist < bestDist) {
                    bestDist = dist;
                    bestCluster = i + 1;
                }
            }
            partition.addElement(bestCluster, bv);
        }

        alg1Step2(partition, kstart);
        return partition;
    }

    /**
     * Refines a partition by moving vectors to a better-scoring cluster.
     * <p>
     * Mirrors C {@code alg1_step2}: for each non-trivial cluster it tries to
     * relocate every member to the nearest other cluster and repeats until no
     * moves remain.
     * </p>
     *
     * @param partition
     *            the partition to refine (1-based clusters)
     * @param kstart
     *            number of clusters (1-based)
     */
    private void alg1Step2(Partition partition, int kstart) {
        boolean moved = true;
        while (moved) {
            moved = false;
            List<int[]> centroids = computeCentroids(partition, kstart);
            for (int cluster = 1; cluster < kstart; cluster++) {
                if (refineCluster(partition, cluster, centroids)) {
                    moved = true;
                }
            }
        }
    }

    /**
     * Computes the rounded-mean centroid of each cluster 1..k-1 as the
     * position-wise mean vector.
     *
     * @param partition
     *            the source partition
     * @param kstart
     *            number of clusters (1-based)
     * @return a list holding one rounded-mean centroid per cluster index 1..k-1
     */
    private List<int[]> computeCentroids(Partition partition, int kstart) {
        List<int[]> centroids = new ArrayList<>();
        for (int c = 1; c < kstart; c++) {
            centroids.add(roundedCentroid(partition, c));
        }
        return centroids;
    }

    /**
     * Relocates members of a cluster to their nearest centroid and repeats the
     * scan until no moves remain.
     *
     * @param partition
     *            the partition to refine (1-based clusters)
     * @param cluster
     *            the 1-based cluster index to refine
     * @param centroids
     *            precomputed rounded-mean centroids for clusters 1..k-1
     * @return true if any member was relocated during this scan
     */
    private boolean refineCluster(Partition partition, int cluster,
            List<int[]> centroids) {
        if (partition.getSize(cluster) <= 2) {
            return false;
        }
        List<BinaryVector> members = vectorSetToList(
                partition.getElements(cluster));
        boolean moved = false;
        for (BinaryVector bv : members) {
            int bestCluster = cluster;
            int bestDist = Integer.MAX_VALUE;
            for (int c = 0; c < centroids.size(); c++) {
                int dist = DistanceCalculator.hammingDistanceVectors(
                        bv, toBinaryVector(centroids.get(c)));
                if (dist < bestDist) {
                    bestDist = dist;
                    bestCluster = c + 1;
                }
            }
            // Keep at least one member in the source cluster.
            if (bestCluster != cluster && partition.getSize(cluster) > 1) {
                partition.removeElement(cluster, bv);
                partition.addElement(bestCluster, bv);
                moved = true;
            }
        }
        return moved;
    }

    /**
     * Computes the rounded-mean centroid of a cluster as the position-wise mean
     * vector.
     *
     * @param partition
     *            the source partition
     * @param cluster
     *            1-based cluster index
     * @return an int array holding the rounded mean bit at each position
     */
    private int[] roundedCentroid(Partition partition, int cluster) {
        VectorSet members = partition.getElements(cluster);
        if (members.isEmpty()) {
            return new int[0];
        }
        int length = members.iterator().next().getLength();
        long[] sum = new long[length];
        for (BinaryVector bv : members) {
            int[] el = bv.getEl();
            for (int i = 0; i < length; i++) {
                sum[i] += el[i];
            }
        }
        int count = members.size();
        int[] centroid = new int[length];
        for (int i = 0; i < length; i++) {
            centroid[i] = (sum[i] * 2) >= count ? 1 : 0;
        }
        return centroid;
    }

    /**
     * Applies random swaps that keep stochastic-complexity gains.
     * <p>
     * Mirrors C {@code alg1_enhance}: performs {@code t1RsCount} swaps, each
     * moving a random vector between two clusters and refining with step 2 when
     * requested, retaining the swap only if it lowers SC.
     * </p>
     *
     * @param partition
     *            the current partition (may be replaced)
     * @param kstart
     *            number of clusters (1-based)
     * @param t1RsCount
     *            number of swap attempts
     * @param extraIter
     *            whether to refine each candidate with step 2
     * @param random
     *            RNG for selecting swaps
     * @return the best partition found
     */
    private Partition alg1Enhance(Partition partition, int kstart,
            int t1RsCount, boolean extraIter, Random random) {
        Partition current = partition;
        int length = vectorLength(current);
        double bestSc = stochasticComplexitySafe(current, kstart, length);
        if (Double.isNaN(bestSc)) {
            bestSc = Double.MAX_VALUE;
        }

        for (int swap = 1; swap < t1RsCount; swap++) {
            Partition candidate = alg1Swap(current, kstart, random);
            if (extraIter) {
                alg1Step2(candidate, kstart);
            }
            double sc = stochasticComplexitySafe(candidate, kstart, length);
            if (!Double.isNaN(sc) && sc < bestSc) {
                bestSc = sc;
                current = candidate;
            }
        }
        return current;
    }

    /**
     * Returns the vector length of a partition by inspecting its first cluster.
     *
     * @param partition
     *            the source partition
     * @return the bit-length of vectors in the partition, or 0 if empty
     */
    private int vectorLength(Partition partition) {
        for (int c = 1; c <= partition.size(); c++) {
            VectorSet members = partition.getElements(c);
            if (!members.isEmpty()) {
                return members.iterator().next().getLength();
            }
        }
        return 0;
    }

    /**
     * Converts a {@link VectorSet} into an ordered list of its vectors.
     *
     * @param set
     *            the vector set to convert
     * @return a new ArrayList containing every vector in iteration order
     */
    private static List<BinaryVector> vectorSetToList(VectorSet set) {
        List<BinaryVector> list = new ArrayList<>();
        for (BinaryVector bv : set) {
            list.add(bv);
        }
        return list;
    }

    /**
     * Wraps an int array as a {@link BinaryVector}.
     *
     * @param bits
     *            the bit values (0/1)
     * @return a binary vector with those bits and no missing values
     */
    private static BinaryVector toBinaryVector(int[] bits) {
        return new BinaryVector(bits, 0, bits.length, 0, "centroid");
    }

    /**
     * Performs a single random swap: moves one vector from a random cluster to
     * another and refines the assignment.
     *
     * @param partition
     *            the source partition
     * @param kstart
     *            number of clusters (1-based)
     * @param random
     *            RNG for selecting the swap
     * @return a new partition with the swap applied
     */
    private Partition alg1Swap(Partition partition, int kstart,
            Random random) {
        Partition candidate = partition.copy();
        int from = 1 + random.nextInt(kstart - 1);
        int to = 1 + random.nextInt(kstart - 1);
        if (from == to) {
            return candidate;
        }
        VectorSet source = candidate.getElements(from);
        if (source.isEmpty()) {
            return candidate;
        }
        BinaryVector moved = vectorSetToList(source).get(
                random.nextInt(source.size()));
        candidate.removeElement(from, moved);
        candidate.addElement(to, moved);
        alg1Step2(candidate, kstart);
        return candidate;
    }

    private int executeTest2(Map<String, String> opts,
            CliParser.CommandArgs args) throws NumberFormatException {
        int t2Threshold = 0;
        if (opts.containsKey("-t")) {
            try {
                t2Threshold = Integer.parseInt(opts.get("-t"));
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid t2_treshold: " + opts.get("-t"));
            }
        }

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Test2 command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Threshold: {}", t2Threshold);

        try {
            return runTest2(opts, filebase, t2Threshold);
        } catch (IOException e) {
            log.warn("test2 failed: {}", e.getMessage());
            return 1;
        }
    }

    /**
     * Runs the semi-cumulative algorithm (C {@code apply_alg2}).
     * <p>
     * Repeats 100 times: builds a dynamic partition with threshold-based
     * growth, converts it to a static partition, recomputes centroids and
     * measures overall distortion, then saves the best partition found. Mirrors
     * {@code t_alg_2.c}.
     * </p>
     *
     * @param filebase
     *            base name of the data files
     * @param t2Threshold
     *            growth threshold (dist &lt; vec_len + threshold)
     * @return exit code (0 = success)
     */
    private int runTest2(Map<String, String> opts, String filebase,
            int t2Threshold) throws IOException {
        VectorSet vectorSet = DataLoader.loadVectors(filebase);
        int length = vectorSet.getVectorLength();

        List<BinaryVector> vectors = vectorSetToList(vectorSet);

        Partition bestPartition = null;
        double dmin = Double.MAX_VALUE;

        StringBuilder report = new StringBuilder();
        report.append("Treshold: ").append(t2Threshold).append('\n');

        for (int iteration = 1; iteration <= 100; iteration++) {
            // Shuffle the vectors so each trial starts from a different order.
            List<BinaryVector> shuffled = new ArrayList<>(vectors);
            Collections.shuffle(shuffled, random);

            DynamicPartition dynamic = CumulativeClassifier
                    .initializeFromVector(shuffled.get(0));
            for (int i = 1; i < shuffled.size(); i++) {
                assignToCluster(dynamic, shuffled.get(i), t2Threshold,
                        length);
            }

            Partition partition = dynamic.convert();
            int k = partition.size();

            InfiniteCentroids centroids = CentroidManager.allocateCentroids(k,
                    length);
            GLAEngine.recomputeCentroids(partition, centroids, true,
                    vectors.size());
            double distortion = DistanceCalculator.overallDistortion(partition,
                    centroids);
            double sc = stochasticComplexitySafe(partition, k, length);

            report.append(String.format("%3d: %3d, %2.4f %2.4f%n", iteration,
                    k - 1, sc, distortion));

            if (distortion < dmin) {
                dmin = distortion;
                bestPartition = partition.copy();
            }
        }

        String outputFile = opts.getOrDefault("-P", null);
        if (outputFile == null || outputFile.isEmpty()) {
            outputFile = filebase + ".partition";
        }

        if (bestPartition != null) {
            PartitionWriter.writePartition(bestPartition, outputFile);
            log.info("test2 best partition written to {}", outputFile);
        }

        Path reportFile = Path.of(filebase + ".output");
        Files.writeString(reportFile, report.toString());
        log.info("test2 report written to {}", reportFile);

        return 0;
    }

    /**
     * Assigns a vector to the nearest cluster of a dynamic partition.
     * <p>
     * Mirrors C {@code alg2_init}: if the best Hamming distance is below
     * {@code vec_len + threshold} the vector joins that cluster, otherwise a
     * new cluster is created.
     * </p>
     *
     * @param dynamic
     *            the dynamic partition to update
     * @param bv
     *            the vector to assign
     * @param t2Threshold
     *            growth threshold
     * @param length
     *            vector length
     */
    private void assignToCluster(DynamicPartition dynamic, BinaryVector bv,
            int t2Threshold, int length) {
        int bestCluster = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 1; i <= dynamic.size(); i++) {
            double dist = DistanceCalculator.hammingDistanceVectors(bv,
                    nearestVector(dynamic, i));
            if (dist < bestDist) {
                bestDist = (int) dist;
                bestCluster = i;
            }
        }

        if (bestCluster == -1 || bestDist >= length + t2Threshold) {
            DynamicPartition extended = CumulativeClassifier
                    .extendWithNewClass(dynamic, bv);
            assignToCluster(extended, bv, t2Threshold, length);
        } else {
            dynamic.putVector(bestCluster, bv);
        }
    }

    /**
     * Returns a representative vector for a cluster (its first member).
     *
     * @param dynamic
     *            the dynamic partition
     * @param i
     *            1-based cluster index
     * @return a binary vector from that cluster, or null if empty
     */
    private BinaryVector nearestVector(DynamicPartition dynamic, int i) {
        VectorSet members = dynamic.getCluster(i);
        Iterator<BinaryVector> it = members.iterator();
        return it.hasNext() ? it.next() : null;
    }

    /**
     * Computes stochastic complexity for a partition, tolerating empty
     * clusters.
     * <p>
     * {@code stochasticComplexity} requires every cluster 1..k-1 to be
     * non-empty, so this skips empty clusters before evaluating. Returns
     * {@code NaN} when no usable cluster remains.
     * </p>
     *
     * @param partition
     *            the source partition (1-based clusters)
     * @param k
     *            number of clusters (1-based)
     * @param length
     *            vector length
     * @return the stochastic complexity, or NaN if it cannot be computed
     */
    private double stochasticComplexitySafe(Partition partition, int k,
            int length) {
        for (int c = 1; c < k; c++) {
            if (partition.getSize(c) == 0) {
                return Double.NaN;
            }
        }
        try {
            return DistanceCalculator.stochasticComplexity(partition, k,
                    length);
        } catch (RuntimeException _) {
            return Double.NaN;
        }
    }
}
