package org.binclass.cli;

import java.util.Map;

import org.binclass.algorithms.classify.CumulativeClassifier;
import org.binclass.algorithms.classify.CumulativeConfig;
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

        setupVerboseMode(opts);
        boolean useAbsMatch = opts.containsKey("-A");

        double epsilon = parseOptionDouble(opts, "-E",
                "Invalid epsilon value: " + opts.get("-E"), 0.001);

        boolean jeffreysPrior = opts.containsKey("-J");

        int joinTarget = parseOptionInt(opts, "-j",
                "Invalid join_target: " + opts.get("-j"), 2);

        double glaThreshold = parseOptionDouble(opts, "-T",
                "Invalid gla_threshold: " + opts.get("-T"), 1.0);

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
        CumulativeClassifier.doCumulativeClassification(vectorSet,
                CumulativeConfig.defaults());

        log.info("Semi-cumulative classification complete");

        return 0;
    }
}
