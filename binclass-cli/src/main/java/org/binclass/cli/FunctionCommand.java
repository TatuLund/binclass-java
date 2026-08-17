package org.binclass.cli;

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

        // Use InfoFunctions to render information-theoretic functions
        String datfile = filebase + ".dat";
        String outfile = filebase + ".out";
        String ctrfile = filebase + ".centroids";
        String hdrfile = filebase + ".hdr";

        log.info(
                "Computing information-theoretic functions using InfoFunctions");
        String result = InfoFunctions.renderFunctions(datfile, outfile, ctrfile,
                hdrfile);

        log.info("Function computation complete: {}",
                result != null ? "success" : "no data");

        return 0;
    }
}
