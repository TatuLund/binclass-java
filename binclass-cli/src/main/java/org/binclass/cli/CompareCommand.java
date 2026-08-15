package org.binclass.cli;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compare two partitions command.
 */
public class CompareCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(CompareCommand.class);

    @Override
    public String getName() {
        return "compare";
    }

    @Override
    public String getDescription() {
        return "Compare two partitions and compute nearness metrics";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        setupVerboseMode(opts);
        boolean exactMatches = opts.containsKey("-M");

        int printMode = parseOptionInt(opts, "-V",
                "Invalid print mode: " + opts.get("-V"));

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Compare command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Print mode: {}", printMode);
        log.info("  Exact matches: {}", exactMatches);

        return 0;
    }
}
