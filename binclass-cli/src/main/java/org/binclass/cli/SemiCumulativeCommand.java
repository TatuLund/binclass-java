package org.binclass.cli;

import java.util.Map;

import org.binclass.algorithms.classify.CumulativeClassifier;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.VectorSet;
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

        boolean verbose = !opts.containsKey("-q");
        boolean useAbsMatch = opts.containsKey("-A");

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

        boolean jeffreysPrior = opts.containsKey("-J");

        int joinTarget = 2;
        if (opts.containsKey("-j")) {
            try {
                joinTarget = Integer.parseInt(opts.get("-j"));
                if (joinTarget < 2)
                    throw new IllegalArgumentException(
                            "Join target must be >= 2");
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid join_target: " + opts.get("-j"));
            }
        }

        double glaThreshold = 1.0;
        if (opts.containsKey("-T")) {
            try {
                glaThreshold = Double.parseDouble(opts.get("-T"));
                if (!(glaThreshold > 1.0))
                    throw new IllegalArgumentException(
                            "GLA threshold must be > 1.0");
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid gla_treshold: " + opts.get("-T"));
            }
        }

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("SemiCumulative command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Join target: {}", joinTarget);
        log.info("  GLA threshold: {}", glaThreshold);

        // Load vectors from data files
        VectorSet vectorSet = DataLoader.loadVectors(filebase);

        log.info("Running semi-cumulative classification with {} vectors, " +
                "join_target={}, gla_threshold={}",
                vectorSet.size(), joinTarget, glaThreshold);

        // Run cumulative classification algorithm (semi-cumulative variant)
        CumulativeClassifier.doCumulativeClassification(vectorSet, 0);

        log.info("Semi-cumulative classification complete");

        return 0;
    }
}
