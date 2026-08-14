package org.binclass.cli;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build hierarchical tree command.
 */
public class TreeCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(TreeCommand.class);

    @Override
    public String getName() {
        return "tree";
    }

    @Override
    public String getDescription() {
        return "Build hierarchical dendrogram from partition data";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        int useHellinger = 0;
        if (opts.containsKey("-H")) {
            try {
                useHellinger = Integer.parseInt(opts.get("-H"));
                if (useHellinger < 1 || useHellinger > 4)
                    throw new IllegalArgumentException(
                            "Use hellinger must be 1,2, or 4");
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid use_hellinger: " + opts.get("-H"));
            }
        }

        boolean jeffreysPrior = opts.containsKey("-J");
        boolean verbose = !opts.containsKey("-q");

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Tree command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Use hellinger: {}", useHellinger);
        log.info("  Jeffreys prior: {}", jeffreysPrior);

        return 0;
    }
}
