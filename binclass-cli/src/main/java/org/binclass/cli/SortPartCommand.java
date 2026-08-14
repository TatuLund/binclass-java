package org.binclass.cli;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sort/partition operations command.
 */
public class SortPartCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(SortPartCommand.class);

    @Override
    public String getName() {
        return "sortpart";
    }

    @Override
    public String getDescription() {
        return "Sort and partition operations on binary vectors";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        boolean verbose = !opts.containsKey("-q");

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("SortPart command executed with:");
        log.info("  Filebase: {}", filebase);

        return 0;
    }
}
