package org.binclass.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.binclass.algorithms.centroid.CentroidInitializer;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.dist.DistanceCalculator;
import org.binclass.algorithms.dist.NearestNeighbor;
import org.binclass.algorithms.gla.AutomaticSearch;
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.GLAEngine;
import org.binclass.algorithms.gla.LocalSearch;
import org.binclass.algorithms.gla.SearchType;
import org.binclass.algorithms.io.CentroidWriter;
import org.binclass.algorithms.io.PartitionWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classify vectors with GLA command.
 */
public class ClassifyCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(ClassifyCommand.class);

    /** Result of a range search: best partition and its centroids. */
    private record SearchResult(Partition partition,
            InfiniteCentroids centroids) {
    }

    /**
     * Bundle returned by {@link #initializePartition} capturing both the newly
     * created partition and the (possibly replaced) centroid array produced by
     * the selected centroid-type strategy.
     */
    private record PartitionInit(Partition partition,
            InfiniteCentroids centroids) {
    }

    @Override
    public String getName() {
        return "classify";
    }

    @Override
    public String getDescription() {
        return "Classify vectors using Generalized Lloyd Algorithm (GLA)";
    }

    /**
     * Parse and validate the starting number of clusters ({@code -b}). Defaults
     * to 1 when not provided.
     *
     * @param opts
     *            command options
     * @return a positive cluster count
     */
    private int parseKStart(Map<String, String> opts) {
        if (!opts.containsKey("-b")) {
            return 1;
        }
        try {
            int kstart = Integer.parseInt(opts.get("-b"));
            if (kstart < 1) {
                throw new IllegalArgumentException("kstart must be >= 1");
            }
            return kstart;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid kstart value: " + opts.get("-b"), e);
        }
    }

    /**
     * Parse and validate the convergence threshold ({@code -E}). The value must
     * satisfy {@code 0 < epsilon < 0.5}.
     *
     * @param opts
     *            command options
     * @return a valid epsilon value, defaulting to {@code 0.001}
     */
    private double parseEpsilon(Map<String, String> opts) {
        double epsilon = parseOptionDouble(opts, "-E",
                "Invalid epsilon value: " + opts.get("-E"), 0.001);
        if (epsilon <= 0 || epsilon >= 0.5) {
            throw new IllegalArgumentException(
                    "Invalid epsilon value: " + opts.get("-E"));
        }
        return epsilon;
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        // Parse options
        int kstart = parseKStart(opts);

        int kstop = parseOptionInt(opts, "-s",
                "Invalid kstop value: " + opts.get("-s"));

        int kstopwhen = parseOptionInt(opts, "-S",
                "Invalid kstopwhen value: " + opts.get("-S"));

        double epsilon = parseEpsilon(opts);

        setupVerboseMode(opts);

        int heuristic = parseOptionInt(opts, "-r",
                "Invalid heuristic type: " + opts.get("-r"), 1);

        // -r7 selects the local-search cycler strategy and -r8 selects the
        // adaptive strategy. Both keep a base heuristic (SPLITJOIN1) but route
        // through LocalSearch.localSearch() instead of a single GLA variant.
        boolean lsCycler = heuristic == 7;
        boolean lsAdaptive = heuristic == 8;

        boolean trashcan = opts.containsKey("-t");
        int alternateMode = parseOptionInt(opts, "-e",
                "Invalid alternate mode: " + opts.get("-e"));

        boolean analyseMissing = opts.containsKey("-m");
        int centroidType = parseOptionInt(opts, "-c",
                "Invalid centroid type: " + opts.get("-c"));

        boolean logCentroids = opts.containsKey("-l");
        boolean jeffreysPrior = opts.containsKey("-J");
        boolean classWeights = opts.containsKey("-w");

        // -B sets require_better (require improving distance for k+1) and an
        // optional numeric value sets first_d. Mirrors C parse_classify().
        boolean requireBetter = opts.containsKey("-B");
        double firstD = parseOptionDouble(opts, "-B",
                "Invalid first_d value: " + opts.get("-B"));

        boolean bestCodeLength = opts.containsKey("-C");
        boolean roundedCentroids = opts.containsKey("-R");

        int distanceType = parseOptionInt(opts, "-f",
                "Invalid distance type: " + opts.get("-f"));

        int maxIter = parseOptionInt(opts, "-n",
                "Invalid max_iter: " + opts.get("-n"));

        int heuristicCount = parseOptionInt(opts, "-j",
                "Invalid heuristic count: " + opts.get("-j")) + 1;

        int safetyLimit = maxIter > 0 ? maxIter
                : parseOptionInt(opts, "-F",
                        "Invalid safety limit: " + opts.get("-F"),
                        500); // Default matches C code (safety_limit=500)

        int iterBase = parseOptionInt(opts, "-a",
                "Invalid iter_base: " + opts.get("-a"));

        String centroidFile = null;
        if (opts.containsKey("-L")) {
            centroidFile = opts.get("-L");
        }

        String partitionFile = opts.getOrDefault("-P", null);

        // Get filebase from options or last argument
        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Classify command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  kstart: {}", kstart);
        log.info("  kstop: {}", kstop);
        log.info("  Epsilon: {}", epsilon);
        log.info("  Heuristic: {}", heuristic);
        log.info("  Centroid type: {}", centroidType);
        log.info("  Trashcan mode: {}", trashcan);
        log.info("  Analyse missing: {}", analyseMissing);
        log.info("  Log centroids: {}", logCentroids);
        log.info("  First distance: {}", firstD);
        log.info("  Best code length: {}", bestCodeLength);
        log.info("  Distance type: {}", distanceType);
        log.info("  Heuristic count: {}", heuristicCount);

        // Load vectors from data files. This also pre-builds the log2-factorial
        // lookup table, sized to cover every stochastic-complexity index
        // (mirrors C read_set(): prepare_log2_factorials((n+n)) in binset.c).
        VectorSet vectorSet = DataLoader.loadVectors(filebase);

        // Determine search range (kstart..kstop)
        int kEnd = kstop > 0 ? kstop : kstart;
        if (kstopwhen > 0 && kstop == 0) {
            // Automatic search: scan forward until no improvement in kstopwhen
            // steps
            kEnd = Integer.MAX_VALUE; // Will be limited by convergence check
        }

        SearchType searchType = determineSearchType(opts);
        log.info("Running range search from k={} to k={}", kstart, kEnd);
        log.info("Search type: {}", searchType);

        // Build GLAConfig from parsed CLI options
        GLAConfig config = new GLAConfig(
                epsilon, // convergence threshold
                1.8, // pnnThreshold (default)
                heuristic, // heuristic type
                alternateMode, // alternate mode
                centroidType, // centroid type
                maxIter, // max iterations (0 = use default)
                safetyLimit, // safety limit
                iterBase, // iteration base
                vectorSet.size(), // n: total vectors
                kstopwhen, // -S flag: max clusters to search
                5, // kcStopWhen (-W flag): steps without SC improvement
                classWeights, // use class weights or uniform
                roundedCentroids, // round centroids to binary values
                jeffreysPrior, // use Jeffreys prior for stochastic complexity
                trashcan, // enable trashcan mode (-t flag)
                analyseMissing, // analyse missing bits (-m flag)
                logCentroids, // log centroid information (-l flag)
                firstD, // first distance value (-B flag)
                bestCodeLength, // use best code length criterion (-C flag)
                distanceType, // distance type: 1=HAM (int)
                heuristicCount, // heuristic count parameter (-j flag)
                false, // filterExactK (disabled by default)
                requireBetter, // require better distance (-B flag)
                lsCycler, // local search cycler mode (-r7)
                lsAdaptive // local search adaptive mode (-r8)
        );

        // Emit the human-readable "Methods:" summary block derived from every
        // flag (G9), mirroring C's methods() in classify.c.
        methods(config, searchType);

        // Dispatch to the requested search strategy. Mirrors C
        // classify_vectors():
        // ST_AUTO -> AutomaticSearch.run(), everything else uses the range
        // search.
        SearchResult result;
        if (searchType == SearchType.AUTO) {
            AutomaticSearch.Result autoResult = new AutomaticSearch(vectorSet,
                    config).run();
            log.info("GLA completed with {} clusters", autoResult.kmin());
            log.info("Final number of clusters: {}", autoResult.kmin());
            Partition bestPartition = autoResult.partition();
            if (bestPartition == null) {
                bestPartition = new Partition(kstart + 1);
            }
            result = new SearchResult(bestPartition,
                    autoResult.centroids());
        } else {
            result = runRangeSearch(vectorSet, kstart, kEnd, config);

            Partition finalPartition = result.partition();
            log.info("GLA completed with {} clusters", finalPartition.size());
            log.info("Final number of clusters: {}", finalPartition.size());
        }

        if (result.partition() == null) {
            log.warn("No partition found during search");
            return 1;
        }

        if (centroidFile != null) {
            int code = writeCentroids(result.centroids(), centroidFile);
            if (code != 0) {
                return code;
            }
        }

        if (partitionFile != null) {
            int code = writePartition(result.partition(), partitionFile);
            if (code != 0) {
                return code;
            }
        }

        return 0;
    }

    /**
     * Determines the search strategy from parsed CLI options, mirroring C
     * {@code parse_classify()}. The default is automatic
     * ({@link SearchType#AUTO}); {@code -nXX} selects non-automatic range
     * search and {@code -Lfilename} selects loaded centroids.
     *
     * @param opts
     *            the parsed command options
     * @return the resolved search type
     */
    private SearchType determineSearchType(Map<String, String> opts) {
        if (opts.containsKey("-n")) {
            return SearchType.NAUTO;
        }
        if (opts.containsKey("-L")) {
            return SearchType.LCENT;
        }
        return SearchType.AUTO;
    }

    /**
     * Save the best centroids to a file.
     *
     * @param centroids
     *            the centroids to persist, may be {@code null}
     * @param centroidFile
     *            destination path
     * @return exit code ({@code 0} on success, {@code 1} on failure)
     */
    private int writeCentroids(InfiniteCentroids centroids,
            String centroidFile) {
        try {
            Path path = Path.of(centroidFile);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            if (centroids == null) {
                log.warn("No centroids available for writing");
                return 1;
            }

            CentroidWriter.save(centroids, centroidFile);
            log.info("Best partition written to {}", centroidFile);
            return 0;
        } catch (IOException e) {
            log.warn("Failed to write best partition to {}: {}", centroidFile,
                    e.getMessage());
            return 1;
        }
    }

    /**
     * Write a partition to a file.
     *
     * @param partition
     *            the partition to persist
     * @param partitionFile
     *            destination path
     * @return exit code ({@code 0} on success, {@code 1} on failure)
     */
    private int writePartition(Partition partition, String partitionFile) {
        try {
            Path path = Path.of(partitionFile);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            PartitionWriter.writePartition(partition, partitionFile);
            log.info("Partition written to {}", partitionFile);
            return 0;
        } catch (IOException e) {
            log.warn("Failed to write partition to {}: {}", partitionFile,
                    e.getMessage());
            return 1;
        }
    }

    /**
     * Runs GLA for each k value from kstart to kstop, tracking stochastic
     * complexity. Mirrors the C function search_classes_nonautomatic() from
     * classify.c.
     */
    private SearchResult runRangeSearch(
            VectorSet vectorSet,
            int kstart,
            int kstop,
            GLAConfig config) {

        // --- G7: maximum_class_number cap check ---------------------------
        // Mirrors C search_classes_nonautomatic(): if the starting cluster
        // count exceeds the tool's hard limit, report and exit(1).
        if (kstart > AutomaticSearch.MAXIMUM_CLASS_NUMBER) {
            log.warn("More classes requested than possible: kstart={} > {}",
                    kstart, AutomaticSearch.MAXIMUM_CLASS_NUMBER);
            throw new IllegalArgumentException(
                    "More classes requested than possible");
        }

        double scmin = Double.MAX_VALUE;
        int actualK = kstart;
        int noImprovementCount = 0;
        InfiniteCentroids centroids = null; // Declare outside loop for logging
        Partition bestPartition = null;
        InfiniteCentroids bestCentroids = null;

        // --- G5: allocate scs[] capped at maximum_class_number ------------
        // scs[k] holds the best SC seen for k clusters; uncomputed entries
        // keep the 1000.0 sentinel used by C's search_classes_nonautomatic().
        int mk = kstop + 1;
        if (mk > AutomaticSearch.MAXIMUM_CLASS_NUMBER) {
            mk = AutomaticSearch.MAXIMUM_CLASS_NUMBER;
        }
        double[] scs = new double[mk];
        for (int i = 0; i < mk; i++) {
            scs[i] = 1000.0;
        }

        // --- G8: elapsed-time tracking ------------------------------------
        long startTime = System.currentTimeMillis();
        long lastTime = startTime;

        // Get the actual vector length from the first vector in the set
        int vectorLength = vectorSet.size() > 0
                ? vectorSet.iterator().next().getLength()
                : 16;

        int attempts = config.iterBase() > 0 ? config.iterBase() : 1;
        for (int k = kstart; k <= kstop; k++) {
            // Run GLA up to `attempts` times per cluster count with different
            // starting centroids, keeping the best SC so bad local minima are
            // not
            // counted. Mirrors C use_gla() where -a sets the number of trials.
            double bestScForK = Double.MAX_VALUE;
            Partition bestPartitionForK = null;
            InfiniteCentroids bestCentroidsForK = null;
            for (int attempt = 0; attempt < attempts; attempt++) {
                InfiniteCentroids centroidsK;
                Partition partitionK;
                if (attempt == 0) {
                    InfiniteCentroids initialCentroids = new InfiniteCentroids(
                            k + 1, vectorLength);
                    PartitionInit init = initializePartition(vectorSet, k,
                            initialCentroids, config.centroidType());
                    centroidsK = init.centroids();
                    partitionK = init.partition();
                } else {
                    // Different starting centroids per attempt to escape bad
                    // local minima (mirrors C use_gla() random_centroids()).
                    // Use the same cluster count as attempt 0 (k + 1) so every
                    // trial scores on equal footing; pure random values work
                    // regardless of n vs k. The partition must match its size
                    // or
                    // GLA's setSize() shrink path nulls every cluster slot and
                    // addElement() throws.
                    centroidsK = CentroidInitializer.randomInit(k + 1,
                            vectorLength);
                    partitionK = new Partition(k + 1);
                }

                double scForK = runGLAAndCalculateSC(vectorSet, partitionK,
                        centroidsK, config);

                if (scForK < bestScForK) {
                    bestScForK = scForK;
                    bestPartitionForK = partitionK;
                    bestCentroidsForK = centroidsK;
                }
            }

            Partition partition = bestPartitionForK;
            centroids = bestCentroidsForK;
            double sc = bestScForK;
            log.info("Classification at k={}: SC={}", k, sc);

            // --- G5: record the best SC seen for this cluster count -------
            // Mirrors C's `scs[(C->k)-1] = min(...)` update. The array is
            // capped at maximum_class_number and printed as a table below.
            if (k < scs.length && scs[k] > sc) {
                scs[k] = sc;
            }

            // Always update scmin to track the best SC seen so far
            if (sc < scmin) {
                scmin = sc;
                bestPartition = partition;
                bestCentroids = centroids;
                actualK = k;
                noImprovementCount = 0;
                log.info("New best classification at k={}: SC={}, clusters={}",
                        k, sc, partition == null ? 0 : partition.size());
            } else {
                // --- G6: count "tries since best" -------------------------
                noImprovementCount++;
                log.info(
                        "Tries since best classification: {}",
                        noImprovementCount);
            }

            // --- G8: emit elapsed-time messages ---------------------------
            long now = System.currentTimeMillis();
            long secondsSinceStart = (now - startTime) / 1000;
            long secondsSinceLast = (now - lastTime) / 1000;
            log.info("Time ellapsed since start:                {}",
                    formatTime(secondsSinceStart));
            log.info(
                    "Time ellapsed for current classification: {}",
                    formatTime(secondsSinceLast));

            boolean reachedSafetyLimit = k - kstart >= config.safetyLimit();
            if (shouldTerminate(noImprovementCount, config)
                    || reachedSafetyLimit) {
                if (reachedSafetyLimit) {
                    log.info("Safety limit reached at k={}", k);
                }
                break;
            }
        }

        // --- G5: print the "SC as function of k" table -------------------
        // Mirrors C's final report: every entry below the 1000.0 sentinel is
        // printed with the same format used by the original tool.
        log.info("\nSC as function of k\n--");
        for (int i = 0; i < mk; i++) {
            if (scs[i] < 1000.0) {
                log.info(String.format("%3d: %2.4f", i, scs[i]));
            }
        }

        // Log centroid info if enabled
        if (bestPartition != null) {
            logCentroidInfo(bestPartition, centroids, config.logCentroids());
        }

        if (bestPartition == null) {
            bestPartition = new Partition(kstart + 1);
        }

        log.info("Best classification: k={}, SC={}", actualK, scmin);
        return new SearchResult(bestPartition, bestCentroids);
    }

    /**
     * Initialize a partition and its centroid array using the strategy selected
     * by {@code -c} (centroid type). Mirrors C's {@code random_centroids()}
     * dispatch in {@code centroid.c}:
     * <ul>
     * <li>{@code 1} (CT_CLASSIC) &rarr; uniform random centroids
     * ({@code normal_centroids})</li>
     * <li>{@code 2} (CT_SRAND) &rarr; statistical sampling
     * ({@code statistical_centroids})</li>
     * <li>{@code 3} (CT_SEMI) &rarr; semi-random frequency weighting
     * ({@code semi_random_centroids})</li>
     * <li>{@code 4} (CT_RAND) &rarr; pick random input vectors
     * ({@code pick_centroids})</li>
     * <li>{@code 5} (CT_PNN) &rarr; pairwise nearest-neighbour merging
     * ({@code pnn_centroids_rand})</li>
     * </ul>
     * The returned centroids array is sized {@code k + 1} to match the
     * partition's cluster count.
     *
     * @param vectorSet
     *            the source vectors (required for CT_RAND and CT_PNN)
     * @param k
     *            the starting number of clusters; the arrays hold {@code k + 1}
     *            entries
     * @param centroids
     *            a pre-allocated centroid array that is populated in place for
     *            the classic/semi-random strategies, or replaced by the
     *            vector-based strategies
     * @return a partition sized {@code k + 1}; cluster assignment happens later
     *         during GLA
     */
    private PartitionInit initializePartition(VectorSet vectorSet, int k,
            InfiniteCentroids centroids, int centroidType) {
        Partition partition = new Partition(k + 1);
        int l = vectorSet.getVectorLength();
        InfiniteCentroids initialized;
        switch (centroidType) {
        case 2: // CT_SRAND - statistical_centroids
            initialized = CentroidInitializer.semiRandomInit(centroids.size(),
                    l);
            break;
        case 3: // CT_SEMI - semi_random_centroids
            initialized = CentroidInitializer.semiRandomInit(centroids.size(),
                    l);
            break;
        case 4: // CT_RAND - pick_centroids
            initialized = CentroidInitializer.pickInit(centroids.size(), l,
                    vectorSet);
            break;
        case 5: // CT_PNN - pnn_centroids_rand
            initialized = CentroidInitializer.pnnInit(centroids.size(), l,
                    vectorSet);
            break;
        default: // CT_CLASSIC - normal_centroids (uniform random)
            initialized = CentroidInitializer.randomInit(centroids.size(), l);
            break;
        }
        return new PartitionInit(partition, initialized);
    }

    /**
     * Run GLA and calculate stochastic complexity in one step.
     */
    private double runGLAAndCalculateSC(VectorSet vectorSet,
            Partition partition,
            InfiniteCentroids centroids, GLAConfig config) {
        // Calculate minimum distortion array
        double[] dmin = new double[1];

        // Initialization hint: skip if firstD indicates already converged
        if (config.firstD() > 0 && dmin[0] < config.firstD()) {
            log.debug("Initial distortion below firstD={}, skipping",
                    config.firstD());
        }

        // Run GLA based on heuristic selection
        switch (config.heuristic()) {
        case 1:
            log.info("Using standard GLA");
            GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
            break;
        case 2:
            log.info("Using stochastic relaxation GLA");
            GLAEngine.glaSr(vectorSet, partition, centroids, dmin, config);
            break;
        case 3:
            log.info("Using simulated annealing GLA");
            GLAEngine.glaSa(vectorSet, partition, centroids, dmin, config);
            break;
        case 4:
            log.info("Using hybrid L1 GLA");
            GLAEngine.hybridGlaL1(vectorSet, partition, centroids, dmin,
                    config);
            break;
        case 5:
            log.info("Using hybrid L2 GLA");
            GLAEngine.hybridGlaL2(vectorSet, partition, centroids, dmin,
                    config);
            break;
        case 6:
            log.info("Using MAE GLA");
            GLAEngine.maeGla(vectorSet, partition, centroids, dmin, config);
            break;
        case 7:
            // Local search cycler mode (-r7): run the multi-operator local
            // search driver that cycles through every strategy. Mirrors C's
            // local_search() with ls_heuristic_cycler = TRUE.
            log.info("Using local search (cycling all strategies)");
            populatePartitionForLocalSearch(vectorSet, partition,
                    centroids, config);
            LocalSearch.localSearch(partition, centroids,
                    config.heuristicCount(), vectorSet.getVectorLength(),
                    config.n(), config.jeffreysPrior(), new java.util.Random());
            break;
        case 8:
            // Local search adaptive mode (-r8): run the multi-operator local
            // driver that adapts operator selection probabilities. Mirrors C's
            // local_search() with ls_adaptive_heuristic = TRUE.
            log.info("Using local search (adaptive strategies)");
            populatePartitionForLocalSearch(vectorSet, partition,
                    centroids, config);
            LocalSearch.localSearch(partition, centroids,
                    config.heuristicCount(), vectorSet.getVectorLength(),
                    config.n(), config.jeffreysPrior(), new java.util.Random());
            break;
        default:
            log.info("Defaulting to standard GLA");
            GLAEngine.gla(vectorSet, partition, centroids, dmin, config);
        }

        // Calculate stochastic complexity or best code length based on flag
        double sc;
        int numClusters = partition.size();

        // Propagate GLAConfig flags to DistanceCalculator for codelength
        // calculations
        DistanceCalculator.setUseClassWeights(config.weights());
        DistanceCalculator.setUseRoundedCentroids(config.rounded());

        if (config.bestCodeLength()) {
            try {
                sc = DistanceCalculator.averageCodelength(partition, centroids);
            } catch (ArithmeticException ex) {
                log.debug("averageCodelength threw ArithmeticException: {}",
                        ex.getMessage());
                sc = 0.0; // Fallback for empty partitions
            }
        } else {
            // Always use proper stochastic complexity calculation
            // Use actual number of non-empty clusters for accurate SC
            int actualClusters = 0;
            for (int i = 1; i <= numClusters; i++) {
                if (partition.getSize(i) > 0) {
                    actualClusters++;
                }
            }

            // If all clusters are empty, return a large SC value as fallback
            if (actualClusters == 0) {
                sc = Double.MAX_VALUE;
                log.debug(
                        "SC calculation: empty partition, returning MAX_VALUE");
                return sc;
            }

            sc = DistanceCalculator.stochasticComplexity(
                    partition, actualClusters, vectorSet.getVectorLength(),
                    config.jeffreysPrior());

            log.debug(
                    "SC calculation: k={}, actualClusters={}, l={}, jeffreys={}",
                    numClusters, actualClusters, vectorSet.getVectorLength(),
                    config.jeffreysPrior());
        }

        return sc;
    }

    /**
     * Populates an empty partition with MSE nearest-neighbor assignments before
     * running local search. Mirrors C's {@code use_gla_load_centroids()}, which
     * calls {@code MSE_gla2(V,P,C,&d,n)} to assign every vector to its nearest
     * centroid (and drop the resulting empty clusters) before invoking
     * {@code local_search()}. Without this step the partition is entirely
     * empty, so {@link LocalSearch#localSearch} computes its initial stochastic
     * complexity on an empty partition and throws "Empty cluster in
     * stochastic_complexity_u".
     *
     * @param vectorSet
     *            the source vectors to assign
     * @param partition
     *            the (initially empty) partition populated in place
     * @param centroids
     *            the centroid array updated in place
     * @param config
     *            GLA configuration controlling rounding and vector length
     */
    private static void populatePartitionForLocalSearch(VectorSet vectorSet,
            Partition partition, InfiniteCentroids centroids,
            GLAConfig config) {
        NearestNeighbor.mseNearestNeighbor(vectorSet, partition, centroids);
        GLAEngine.removeEmpty(partition, centroids);
        GLAEngine.recomputeCentroids(partition, centroids,
                config.rounded(), config.n());
    }

    /**
     * Check if we should terminate the range search.
     */
    private boolean shouldTerminate(int noImprovementCount, GLAConfig config) {
        int maxSteps = config.kstopwhen() > 0 ? config.kstopwhen()
                : Integer.MAX_VALUE;
        return noImprovementCount >= maxSteps;
    }

    /**
     * Logs centroid information when logCentroids flag is enabled.
     */
    private static void logCentroidInfo(Partition partition,
            InfiniteCentroids centroids, boolean logCentroids) {
        if (!logCentroids)
            return;
        for (int i = 0; i < centroids.size(); i++) {
            Centroid centroid = centroids.get(i);
            int clusterIdx = Math.min(i + 1, partition.size()); // 1-based,
                                                                // bounded
            int clusterSize = partition.getSize(clusterIdx);
            double entropy = calculateEntropy(centroid);
            log.debug("Cluster {}: size={}, entropy={}", i + 1, clusterSize,
                    entropy);
        }
    }

    /**
     * Calculates Shannon entropy for a centroid.
     */
    private static double calculateEntropy(Centroid centroid) {
        double[] el = centroid.getArray();
        double entropy = 0.0;
        for (double val : el) {
            if (val > 0 && val < 1) {
                entropy -= val * Math.log(val) + (1 - val) * Math.log(1 - val);
            }
        }
        return entropy / el.length; // Normalize by vector length
    }

    /**
     * Formats a duration in seconds as days/hours/minutes/seconds, mirroring
     * C's {@code print_time()} from bottom.c used by the range-search loop.
     *
     * @param totalSeconds
     *            elapsed time expressed in whole seconds
     * @return a formatted string such as {@code " 0d 0h 0m 3s"}
     */
    private static String formatTime(long totalSeconds) {
        long days = totalSeconds / 86400;
        long rem = Math.floorMod(totalSeconds, 86400);
        long hours = rem / 3600;
        rem %= 3600;
        long minutes = rem / 60;
        long seconds = rem % 60;
        return String.format("%3dd %2dh %2dm %2ds", days, hours, minutes,
                seconds);
    }

    /**
     * Logs the human-readable "Methods:" summary block derived from every flag,
     * mirroring C's {@code methods()} function in {@code classify.c}. The
     * output covers prior type, distance-type to method name mapping, selection
     * mode (SC vs codelength), class weights, filter-exact-k, alternate
     * empty-cell fix + weights, require-better, rounded centroids, local-search
     * strategy, search type, centroid type, and trashcan.
     *
     * @param config
     *            the resolved GLA configuration
     * @param searchType
     *            the resolved search strategy (used for range/adaptive
     *            messages)
     */
    private void methods(GLAConfig config, SearchType searchType) {
        log.info("\nMethods:");
        // Prior type.
        if (config.jeffreysPrior()) {
            log.info("  Using stochastic complexity with Jeffrey's prior");
        } else {
            log.info("  Using stochastic complexity with uniform prior");
        }

        // Distance-type to method name mapping. Mirrors C's switch on
        // distance_type (DT_HAM=1, DT_L1=2, DT_L2=3, DT_CL=4, DT_L1_CL=5,
        // DT_L2_CL=6, DT_SR=7, DT_SA=8).
        int dt = config.distanceType();
        if (dt == 5) {
            log.info("  Hybrid L1/Codelength minimization");
        } else if (dt == 6) {
            log.info("  Hybrid L2/Codelength minimization");
        } else if (dt == 8) {
            log.info("  Codelength minimization with simulated annealing (SA)");
        } else if (dt == 7) {
            log.info(
                    "  Codelength minimization with stochastic relaxation (SR)");
        } else if (dt == 4) {
            log.info("  Codelength minimization");
        } else if (dt == 2) {
            log.info("  Mean absolute error minimization (L1/MAE)");
        } else if (dt == 3) {
            log.info("  Mean square error minimization (L2/MSE)");
        } else {
            log.info(
                    "  Average hamming distance minimzation (Gower)");
        }

        // Selection mode.
        if (searchType != SearchType.LCENT) {
            if (config.bestCodeLength()) {
                log.info("  Choosing by codelength");
            } else {
                log.info("  Choosing by stochatic complexity (SC)");
            }
        }

        // Class-size weighted codelength.
        boolean codelengthDistance = dt == 4 || dt == 5 || dt == 6 || dt == 7
                || dt == 8;
        if (config.weights() && codelengthDistance) {
            log.info("  Using class size weighted version of codelength");
        }

        // filter_exact_k.
        if (config.filterExactK()) {
            log.info("  Filter ak=k");
        }

        // Alternate empty-cell fix combined with class weights.
        if ((config.alternateMode() == 3 || config.alternateMode() == 4)
                && config.weights()) {
            log.info("  Using extra iteration in orphaned centroids fix");
        }

        // require_better.
        if (config.requireBetter()) {
            log.info("  Better codelength for k+1 required");
        }

        // Rounded centroids.
        if (config.rounded()) {
            log.info("  Rounded centroids are used");
        }

        // Local search strategy. Mirrors C's ls_heuristic dispatch where 1..6
        // map to operators and cycler/adaptive modes override the single-line
        // description.
        if (config.lsCycler()) {
            log.info("  Cycling all strategies for Local Search");
        } else if (config.heuristic() == 2) {
            log.info(
                    "  Using split and join (variation 1) strategy for Local Search");
        } else if (config.heuristic() == 3) {
            log.info(
                    "  Using split and join (variation 2) strategy for Local Search");
        } else if (config.heuristic() == 4) {
            log.info("  Using replace worst strategy for Local Search");
        } else if (config.heuristic() == 5) {
            log.info("  Using replace smallest strategy for Local Search");
        } else if (config.heuristic() == 6) {
            log.info("  Using random swap strategy for Local Search");
        }

        // Search type.
        if (searchType == SearchType.AUTO) {
            log.info("  Automatic search");
        } else if (searchType == SearchType.NAUTO) {
            log.info("  Search in arbitrary range {}..{}", config.kstopwhen(),
                    config.n());
        } else if (searchType == SearchType.ADAP) {
            log.info("  Adaptive search with trshold: {}",
                    String.format("%.4f", config.epsilon()));
        }

        // Loaded centroids vs centroid-type selection.
        if (searchType == SearchType.LCENT) {
            log.info("  Loading predefined centroids");
        } else if (config.centroidType() == 3) {
            log.info("  Semirandom initial centroids");
        } else if (config.centroidType() == 1) {
            log.info("  Random initial centroids");
        } else if (config.centroidType() == 2) {
            log.info("  Statistically cointoshed initial centroids");
        } else if (config.centroidType() == 5) {
            log.info("  Using PNN algorithm for initial centroids");
        } else if (config.centroidType() == 4) {
            log.info("  Picking random vectors for initial centroids");
        }

        // Trashcan.
        if (config.trashcan()) {
            log.info("  Trash class is used");
        }
    }

}
