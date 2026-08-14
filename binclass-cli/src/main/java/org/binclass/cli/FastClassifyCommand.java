package org.binclass.cli;

import java.util.Map;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.SplitGLA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fast classify with Split-GLA command.
 */
public class FastClassifyCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(FastClassifyCommand.class);

    @Override
    public String getName() {
        return "fclassify";
    }

    @Override
    public String getDescription() {
        return "Fast classification using Split-GLA algorithm";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        boolean verbose = !opts.containsKey("-q");
        boolean useAbsMatch = opts.containsKey("-A");

        int kstopwhen = 0;
        if (opts.containsKey("-S")) {
            try {
                kstopwhen = Integer.parseInt(opts.get("-S"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid kstopwhen: " + opts.get("-S"));
            }
        }

        boolean jeffreysPrior = opts.containsKey("-J");

        double epsilon = 0.001;
        if (opts.containsKey("-E")) {
            try {
                epsilon = Double.parseDouble(opts.get("-E"));
                if (epsilon >= 0.5)
                    throw new IllegalArgumentException("Epsilon must be < 0.5");
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid epsilon value: " + opts.get("-E"));
            }
        }

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("FastClassify command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Use absolute match: {}", useAbsMatch);
        log.info("  Kstopwhen: {}", kstopwhen);

        // Load vectors from data files
        VectorSet vectorSet = DataLoader.loadVectors(filebase);

        // Build GLAConfig for Split-GLA
        GLAConfig config = new GLAConfig(
                epsilon, // convergence threshold
                1.8, // pnnThreshold (default)
                1, // heuristic (standard)
                1, // alternateMode (default)
                1, // centroidType (CLASSIC)
                0, // maxIter (use default)
                1000, // safetyLimit
                0, // iterBase
                vectorSet.size(), // n: total vectors
                kstopwhen, // -S flag: max clusters to search
                5, // kcStopWhen (-W flag): steps without SC improvement
                false, // weights (uniform by default)
                false, // rounded (fractional centroids by default)
                jeffreysPrior, // use Jeffreys prior for stochastic complexity
                false, // trashcan (disabled by default)
                false, // analyseMissing (disabled by default)
                false, // logCentroids (disabled by default)
                0.0, // firstD (no initial distance)
                false, // bestCodeLength (false by default)
                1, // distanceType (HAM by default)
                1 // heuristicCount (default)
        );

        log.info("Running Split-GLA algorithm on {} vectors",
                vectorSet.size());

        // Initialize centroids with first two vectors for binary split
        InfiniteCentroids centroids = new InfiniteCentroids(2, 16);

        int idx = 0;
        for (BinaryVector bv : vectorSet) {
            if (idx >= 2)
                break;
            Centroid centroid = centroids.get(idx);
            centroid.setEl(bv.getEl());
            idx++;
        }

        // Create initial partition with two classes
        Partition partition = new Partition(2);

        // Run Split-GLA algorithm to find optimal number of clusters
        double[] scmin = new double[1];
        double[] scs = new double[vectorSet.size() + 1];

        Partition result = SplitGLA.splitGLA(vectorSet, scmin, scs, config);

        log.info("Split-GLA complete: found {} clusters", result.size());

        return 0;
    }
}
