package org.binclass.cli;

import java.util.Map;

import org.binclass.algorithms.classify.CumulativeClassifier;
import org.binclass.algorithms.classify.CumulativeConfig;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.VectorSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cumulative classification command.
 */
public class CumulativeCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(CumulativeCommand.class);

    @Override
    public String getName() {
        return "cumulative";
    }

    @Override
    public String getDescription() {
        return "Cumulative classification with dynamic partition quality evaluation";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        boolean verbose = !opts.containsKey("-q");
        boolean cumulativeInOrder = opts.containsKey("-O");
        boolean cumulativeInputOrder = opts.containsKey("-I");
        boolean bayesianPredictive = !opts.containsKey("-S"); // -S disables it
        boolean testFeatureSignificance = opts.containsKey("-F");
        boolean cumSaveByPf = !opts.containsKey("-c"); // -c disables it
        boolean cumNoNewClasses = opts.containsKey("-n");

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

        int cumulativeAnalysis = 0;
        if (opts.containsKey("-N")) {
            try {
                cumulativeAnalysis = Integer.parseInt(opts.get("-N"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid cumulative_analysis: " + opts.get("-N"));
            }
        }

        int cumulativeSamples = 0;
        if (opts.containsKey("-s")) {
            try {
                cumulativeSamples = Integer.parseInt(opts.get("-s"));
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid cumulative_samples: " + opts.get("-s"));
            }
        }

        boolean fixedDelta = false;
        int realDeltaValue = 0;
        if (opts.containsKey("-D")) {
            try {
                realDeltaValue = Integer.parseInt(opts.get("-D"));
                if (realDeltaValue < 0)
                    throw new IllegalArgumentException("Delta must be >= 0");
                fixedDelta = true;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid fixed_delta: " + opts.get("-D"));
            }
        } else if (opts.containsKey("-d")) {
            try {
                realDeltaValue = Integer.parseInt(opts.get("-d"));
                if (realDeltaValue < 0)
                    throw new IllegalArgumentException("Delta must be >= 0");
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid delta: " + opts.get("-d"));
            }
        }

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Cumulative command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Cumulative analysis: {}", cumulativeAnalysis);
        log.info("  Cumulative samples: {}", cumulativeSamples);
        log.info("  Fixed delta: {}", fixedDelta);

        // Load vectors from data files
        VectorSet vectorSet = DataLoader.loadVectors(filebase);

        // Determine delta value for cumulative classification
        int delta = realDeltaValue > 0 ? realDeltaValue : 1;

        // Create configuration record with all parameters
        CumulativeConfig config = new CumulativeConfig(
                delta,
                cumulativeAnalysis > 0,
                cumulativeSamples,
                fixedDelta,
                bayesianPredictive,
                testFeatureSignificance,
                cumSaveByPf,
                cumNoNewClasses,
                cumulativeInOrder,
                epsilon);

        log.info(
                "Running cumulative classification with {} vectors and config={}",
                vectorSet.size(), config);

        // Run cumulative classification algorithm with configuration
        CumulativeClassifier.doCumulativeClassification(vectorSet, config);

        log.info("Cumulative classification complete");

        return 0;
    }
}
