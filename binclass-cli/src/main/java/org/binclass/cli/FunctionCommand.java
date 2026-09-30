package org.binclass.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.binclass.algorithms.info.InfoFunctions;
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

        int distanceType = parseOptionInt(opts, "-f",
                "Invalid distance type: " + opts.get("-f"));

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Function command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Distance type: {}", distanceType);
        log.info("  Class weights: {}", classWeights);

        // Use InfoFunctions to render information-theoretic functions.
        // Mirrors C's render_functions(): the results are written to an
        // .output file (spec section 4.3.2), honouring an optional -o override.
        String datfile = filebase + ".data";
        String outfile = filebase + ".out";
        String ctrfile = filebase + ".centroids";

        log.info(
                "Computing information-theoretic functions using InfoFunctions");
        String result = InfoFunctions.renderFunctions(datfile, outfile,
                ctrfile, null);

        if (result == null || result.isEmpty()) {
            log.warn("Function computation produced no data");
            return 1;
        }

        // Persist the rendered functions to disk. Mirrors ReportCommand:
        // an explicit -o path is honoured, otherwise the results are written
        // next to the input as <filebase>.output.
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
