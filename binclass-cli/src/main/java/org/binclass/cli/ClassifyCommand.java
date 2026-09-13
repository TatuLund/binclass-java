package org.binclass.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.gla.AutomaticSearch;
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.RangeSearch;
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
     * Detect decreasing_epsilon mode ({@code -E} without a numeric value).
     * Mirrors C {@code parse_classify()} where a bare {@code -E} sets the flag
     * and keeps the default epsilon threshold.
     */
    private boolean isDecreasingEpsilon(Map<String, String> opts) {
        return opts.containsKey("-E")
                && !opts.get("-E").matches("\\d+(\\.\\d+)?");
    }

    /**
     * Map the two boolean empty-cell fix flags to the legacy integer code used
     * by {@link GLAConfig#alternateMode()}. Mirrors C parse_classify(): 1=
     * (false,false), 2=(true,false), 3=(false,true), 4=(true,true).
     *
     * @param worstMatch
     *            alternate_worst_match flag
     * @param emptyCellFix
     *            alternate_empty_cell_fix flag
     * @return the integer mode code (1..4)
     */
    private static int toAlternateMode(boolean worstMatch,
            boolean emptyCellFix) {
        if (worstMatch && emptyCellFix) {
            return 4;
        }
        if (worstMatch) {
            return 2;
        }
        if (emptyCellFix) {
            return 3;
        }
        return 1;
    }

    /**
     * Parse and validate the convergence threshold ({@code -E}). The value must
     * satisfy {@code 0 < epsilon < 0.5}. A bare {@code -E} selects decreasing
     * epsilon mode (see {@link #isDecreasingEpsilon(Map)}).
     */
    private double parseEpsilon(Map<String, String> opts) {
        boolean decreasing = isDecreasingEpsilon(opts);

        double epsilon = 0.001;
        if (!decreasing) {
            epsilon = parseOptionDouble(opts, "-E",
                    "Invalid epsilon value: " + opts.get("-E"), 0.001);
        }
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

        int kcStopWhen = parseOptionInt(opts, "-W",
                "Invalid kcStopWhen value: " + opts.get("-W"), 5);

        boolean decreasingEpsilon = isDecreasingEpsilon(opts);
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

        // -eX selects the empty-cell / orphaned-centroid fix. Mirrors C
        // parse_classify(): 1=(false,false), 2=(true,false), 3=(false,true),
        // 4=(true,true). Default (no -e) is worst_match=TRUE, empty_cell_fix=
        // FALSE per C vars.c.
        boolean alternateWorstMatch = true;
        boolean alternateEmptyCellFix = false;
        if (opts.containsKey("-e")) {
            int tmp = parseOptionInt(opts, "-e",
                    "Invalid alternate mode: " + opts.get("-e"));
            switch (tmp) {
            case 2:
            case 4:
                alternateWorstMatch = true;
                break;
            default:
                alternateWorstMatch = false;
                break;
            }
            if (tmp == 3 || tmp == 4) {
                alternateEmptyCellFix = true;
            }
        }

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
                toAlternateMode(alternateWorstMatch, alternateEmptyCellFix),
                // alternate mode (legacy int)
                centroidType, // centroid type
                maxIter, // max iterations (0 = use default)
                safetyLimit, // safety limit
                iterBase, // iteration base
                vectorSet.size(), // n: total vectors
                kstopwhen, // -S flag: max clusters to search
                kcStopWhen, // -W flag: steps without SC improvement
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
                lsAdaptive, // local search adaptive mode (-r8)
                decreasingEpsilon, // -E two-char form
                alternateWorstMatch, // -eX worst-match fix
                alternateEmptyCellFix // -eX empty-cell-fix
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
            RangeSearch.Result rangeResult = new RangeSearch(vectorSet, config)
                    .run(kstart, kEnd);

            Partition finalPartition = rangeResult.partition();
            log.info("GLA completed with {} clusters", finalPartition.size());
            log.info("Final number of clusters: {}", finalPartition.size());
            result = new SearchResult(finalPartition, rangeResult.centroids());
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
        if (opts.containsKey("-b") || opts.containsKey("-s")) {
            // A fixed k-range was requested, so bind to range search instead
            // of the automatic scan. The automatic path ignores -b/-s and
            // always starts at k=1, which makes them ineffective when combined
            // with local search (-r/-j).
            return SearchType.NAUTO;
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
        if (config.alternateEmptyCellFix() && config.weights()) {
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
