package org.binclass.cli;

import java.util.Map;

import org.binclass.algorithms.classify.CumulativeConfig;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.JoinGLA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Semi-cumulative classification command.
 */
public class SemiCumulativeCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(SemiCumulativeCommand.class);

    @Override
    public String getName() {
        return "sclassify";
    }

    @Override
    public String getDescription() {
        return "Semi-cumulative classification with join-GLA";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        setupVerboseMode(opts);
        boolean useAbsMatch = opts.containsKey("-A");

        double epsilon = parseOptionDouble(opts, "-E",
                "Invalid epsilon value: " + opts.get("-E"), 0.001);

        boolean decreasingEpsilon = false;
        if (opts.containsKey("-E")
                && !opts.get("-E").matches("\\d+(\\.\\d+)?")) {
            // -E without numeric value means decreasing_epsilon mode
            decreasingEpsilon = true;
        }

        boolean jeffreysPrior = opts.containsKey("-J");

        int joinTarget = parseOptionInt(opts, "-j",
                "Invalid join_target: " + opts.get("-j"), 2);

        double glaThreshold = parseOptionDouble(opts, "-T",
                "Invalid gla_threshold: " + opts.get("-T"), 1.0);

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Semi-cumulative classification with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Use abs match: {}", useAbsMatch);
        log.info("  Epsilon: {}", epsilon);
        log.info("  Jeffreys prior: {}", jeffreysPrior);
        log.info("  Join target: {}", joinTarget);
        log.info("  GLA threshold: {}", glaThreshold);

        // Load vectors from data files
        VectorSet vectorSet = DataLoader.loadVectors(filebase);

        log.info("Running semi-cumulative classification with {} vectors",
                vectorSet.size());

        // Build GLA config from parsed options
        int n = vectorSet.size();
        GLAConfig glaConfig = new GLAConfig(
                epsilon, // epsilon threshold
                1.8, // pnnThreshold (default)
                1, // heuristic (standard)
                1, // alternateMode
                1, // centroidType (CLASSIC)
                0, // maxIter (use default)
                1000, // safetyLimit
                0, // iterBase
                n, // total vectors
                20, // kstopwhen (-S flag, default 20)
                5, // kcStopWhen (-W flag, default 5)
                false, // weights (not used in semi-cumulative)
                true, // rounded (binary vectors)
                jeffreysPrior, // Jeffreys prior from -J flag
                false, // trashcan mode not enabled
                false, // analyseMissing disabled
                false, // logCentroids disabled
                0.0, // firstD default
                false, // bestCodeLength disabled
                1, // distanceType (HAM)
                joinTarget, // heuristicCount from -j flag
                false, // filterExactK (disabled by default)
                false, // requireBetter (disabled by default)
                false, // lsCycler (disabled by default)
                false, // lsAdaptive (disabled by default)
                decreasingEpsilon, // -E two-char form
                true, // alternateWorstMatch (default per C vars.c)
                false // alternateEmptyCellFix
        );

        // Initialize SC arrays for Join-GLA search
        double[] scmin = new double[1]; // minimum SC value found
        double[] scs = new double[n + 1]; // SC values indexed by k (1-based)

        // Run Join-GLA algorithm (matches C source joingla.c)
        Partition partition = JoinGLA.joinGLA(vectorSet, scmin, scs, glaConfig);

        log.info("Join-GLA complete: optimal k={}, minimum SC={}",
                partition.size(), scmin[0]);

        // Output results (would write to files in full implementation)
        log.info("Semi-cumulative classification complete with {} clusters",
                partition.size());

        return 0;
    }
}
