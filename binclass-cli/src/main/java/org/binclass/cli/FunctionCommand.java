package org.binclass.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.info.InfoFunctions;
import org.binclass.algorithms.io.CentroidReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Render SC/Shannon functions vs k command.
 */
public class FunctionCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(FunctionCommand.class);

    @Override
    public String getName() {
        return "function";
    }

    @Override
    public String getDescription() {
        return "Render information-theoretic functions (SC, Shannon entropy) as function of k";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        setupVerboseMode(opts);
        boolean classWeights = opts.containsKey("-w");

        // Default matches C's global `eDist distance_type = DT_L1_CL` (vars.c):
        // when -f is omitted the function uses codelength-based L1 hybrid GLA.
        int distanceType = parseOptionInt(opts, "-f",
                "Invalid distance type: " + opts.get("-f"), 5);

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Function command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Distance type: {}", distanceType);
        log.info("  Class weights: {}", classWeights);

        // Locate the input files next to the filebase. Mirrors C's
        // render_functions(): the .data/.dat file holds the vectors, the
        // .centroids file holds one saved candidate per cluster count.
        String ctrfile = filebase + ".centroids";

        VectorSet vectors = DataLoader.loadVectors(filebase);

        List<InfiniteCentroids> records;
        try {
            records = CentroidReader.loadAll(ctrfile);
        } catch (IOException e) {
            log.warn("Could not read centroid file {}: {}", ctrfile,
                    e.getMessage());
            records = List.of();
        }

        log.info("Computing information-theoretic functions for {} vectors and "
                + "{} centroid records", vectors.size(), records.size());
        String result = InfoFunctions.calculateFunctions(records, vectors,
                distanceType, classWeights);

        if (result == null || result.isEmpty()) {
            log.warn("Function computation produced no data");
            return 1;
        }

        // Persist the rendered functions to disk. Mirrors ReportCommand: an
        // explicit -o path is honoured, otherwise the results are written next
        // to the input as <filebase>.output.
        String outputFile = opts.getOrDefault("-o", null);
        if (outputFile == null || outputFile.isEmpty()) {
            outputFile = filebase + ".output";
        }
        try {
            Path path = Path.of(outputFile);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, result);
            log.info("Function results written to {}", outputFile);
        } catch (IOException e) {
            log.warn("Failed to write function results to {}: {}",
                    outputFile, e.getMessage());
            return 1;
        }

        return 0;
    }
}
