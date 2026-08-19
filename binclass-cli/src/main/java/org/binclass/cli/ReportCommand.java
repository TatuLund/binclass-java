package org.binclass.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.report.ReportGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generate statistical report command.
 */
public class ReportCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(ReportCommand.class);

    @Override
    public String getName() {
        return "report";
    }

    @Override
    public String getDescription() {
        return "Generate statistical report from classification output";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        setupVerboseMode(opts);

        double epsilon = 0.001;
        if (opts.containsKey("-E")) {
            try {
                epsilon = Double.parseDouble(opts.get("-E"));
                if (epsilon >= 0.5)
                    throw new IllegalArgumentException("Epsilon must be < 0.5");
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid epsilon value: " + opts.get("-E"));
            }
        }

        boolean classWeights = opts.containsKey("-w");

        int reportParams = 0; // All by default
        if (opts.containsKey("-p")) {
            try {
                reportParams = Integer.parseInt(opts.get("-p"));
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid report params: " + opts.get("-p"));
            }
        }

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Report command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Report params: {}", reportParams);
        log.info("  Class weights: {}", classWeights);

        // Load vectors from data files
        var vectorSet = DataLoader.loadVectors(filebase);

        if (vectorSet.size() < 2) {
            throw new IllegalArgumentException(
                    "Need at least two vectors to generate report");
        }

        log.info("Generating report for {} vectors", vectorSet.size());

        // Create a partition from the loaded data
        var partition = new Partition(2);

        int idx = 0;
        for (var v : vectorSet.getElements()) {
            if (idx % 2 == 0) {
                partition.addElement(1, v);
            } else {
                partition.addElement(2, v);
            }
            idx++;
        }

        // Call ReportGenerator to generate the report
        var centroids = new InfiniteCentroids(2, vectorSet.getVectorLength());
        var report = ReportGenerator.generateReport(partition, centroids);

        log.info("Report generated successfully");
        log.info("Report size: {} bytes", report.length());

        // Write report to file if -o flag is provided
        String outputFile = opts.getOrDefault("-o", null);
        if (outputFile != null && !outputFile.isEmpty()) {
            try {
                Path path = Path.of(outputFile);
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(path, report);
                log.info("Report written to {}", outputFile);
            } catch (IOException e) {
                log.warn("Failed to write report to {}: {}", outputFile,
                        e.getMessage());
                return 1;
            }
        }

        return 0;
    }
}
