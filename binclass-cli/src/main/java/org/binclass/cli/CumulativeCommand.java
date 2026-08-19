package org.binclass.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.binclass.algorithms.classify.CumulativeClassifier;
import org.binclass.algorithms.classify.CumulativeConfig;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.DynamicPartition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.io.DynamicPartitionWriter;
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

        setupVerboseMode(opts);
        boolean cumulativeInOrder = opts.containsKey("-O");
        boolean cumulativeInputOrder = opts.containsKey("-I");
        boolean bayesianPredictive = !opts.containsKey("-S"); // -S disables it
        boolean testFeatureSignificance = opts.containsKey("-F");
        boolean cumSaveByPf = !opts.containsKey("-c"); // -c disables it
        boolean cumNoNewClasses = opts.containsKey("-n");

        double epsilon = parseOptionDouble(opts, "-E",
                "Invalid epsilon value: " + opts.get("-E"), 0.001);

        int cumulativeAnalysis = parseOptionInt(opts, "-N",
                "Invalid cumulative_analysis: " + opts.get("-N"));

        int cumulativeSamples = parseOptionInt(opts, "-s",
                "Invalid cumulative_samples: " + opts.get("-s"));

        int realDeltaValue = parseOptionInt(opts, "-D",
                "Invalid fixed_delta: " + opts.get("-D"));
        boolean fixedDelta = realDeltaValue > 0;

        // Also check for lowercase -d flag (delta without fixed mode)
        if (!opts.containsKey("-D") && opts.containsKey("-d")) {
            int deltaFromLowercase = parseOptionInt(opts, "-d",
                    "Invalid delta value: " + opts.get("-d"));
            realDeltaValue = deltaFromLowercase;
            fixedDelta = false; // -d doesn't set fixed mode
        } else if (opts.containsKey("-D") && opts.containsKey("-d")) {
            // If both flags present, lowercase -d overrides for non-fixed delta
            int deltaFromLowercase = parseOptionInt(opts, "-d",
                    "Invalid delta value: " + opts.get("-d"));
            realDeltaValue = deltaFromLowercase;
            fixedDelta = false;
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

        String partitionFile = opts.getOrDefault("-P", null);

        log.info(
                "Running cumulative classification with {} vectors and config={}",
                vectorSet.size(), config);

        // Run cumulative classification algorithm with configuration
        DynamicPartition dynamicPartition = CumulativeClassifier
                .doCumulativeClassification(vectorSet, config);

        if (dynamicPartition != null) {
            log.info("Cumulative classification complete with {} clusters",
                    dynamicPartition.size());

            // Write partition to file if -P flag is provided
            if (partitionFile != null && !partitionFile.isEmpty()) {
                try {
                    Path path = Path.of(partitionFile);
                    Path parent = path.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    DynamicPartitionWriter.writeDynamicPartition(
                            dynamicPartition, partitionFile);
                    log.info("Dynamic partition written to {}", partitionFile);
                } catch (IOException e) {
                    log.warn("Failed to write dynamic partition to {}: {}",
                            partitionFile, e.getMessage());
                    return 1;
                }
            }
        } else {
            log.warn("Cumulative classification returned null partition");
        }

        return 0;
    }
}
