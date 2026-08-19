package org.binclass.cli;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BinClass CLI Application - main entry point.
 */
public class BinClass {

    private static final Logger log = LoggerFactory
            .getLogger(BinClass.class);
    private static final String VERSION = "3.0-SNAPSHOT";
    private static final String APP_NAME = "BinClass";

    public static void main(String[] args) {
        // Check for "command help" pattern first (e.g., "classify help")
        if (args.length >= 3 && "help".equalsIgnoreCase(args[args.length - 1])) {
            String command = args[1].toLowerCase();
            CommandRegistry registry = new CommandRegistry();

            if (!registry.hasCommand(command)) {
                log.error("Unknown command: {}", command);
                System.exit(1);
            }

            String helpText = new CliParser().helpTextForCommand(command);
            System.out.println();
            System.out.println("Usage: binclass " + command + " [options] <filebase>");
            System.out.println();
            System.out.println(helpText);
            System.exit(0);
        }

        // General help or no args
        if (args.length < 1 || isHelpRequested(args)) {
            printUsage();
            return;
        }

        CliParser parser = new CliParser();
        CommandRegistry registry = new CommandRegistry();

        try {
            // Parse command and options
            CliParser.CommandArgs cmdArgs = parser.parse(args);
            String command = cmdArgs.command().toLowerCase();

            // Check if command is registered
            if (!registry.hasCommand(command)) {
                log.error("Unknown command: {}", command);
                System.exit(1);
            }

            // Validate arguments
            List<String> errors = parser.validate(cmdArgs);
            if (!errors.isEmpty()) {
                log.error("Validation errors:");
                for (String error : errors) {
                    log.error("  - {}", error);
                }
                System.exit(1);
            }

            // Get command implementation and execute
            BaseCommand commandImpl = instantiateCommand(registry,
                    cmdArgs.command());
            int exitCode = commandImpl.execute(cmdArgs);
            System.exit(exitCode);

        } catch (Exception e) {
            log.error("Error: {}", e.getMessage());
            System.exit(1);
        }
    }

    private static BaseCommand instantiateCommand(CommandRegistry registry,
            String command) throws Exception {
        Class<? extends BaseCommand> cmdClass = registry
                .getCommandClass(command);
        if (cmdClass == null) {
            throw new IllegalStateException(
                    "No implementation for command: " + command);
        }

        if ("test1".equals(command) || "test2".equals(command)) {
            // Special handling for TestAlgorithmsCommand which needs test name
            return new TestAlgorithmsCommand(command);
        } else {
            try {
                return cmdClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                log.error("Failed to instantiate {}: {}",
                        cmdClass.getSimpleName(), e.getMessage());
                System.exit(1);
                throw e;
            }
        }
    }

    private static boolean isHelpRequested(String[] args) {
        for (String arg : args) {
            if ("help".equalsIgnoreCase(arg) || "--help".equalsIgnoreCase(arg)
                    || "-h".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void printUsage() {
        log.info("{} - Software Package For Classifying Binary Vectors",
                APP_NAME);
        log.info("Version: {}", VERSION);
        log.info("");
        log.info("Usage: {} <command> [options] <filebase>",
                APP_NAME.toLowerCase());
        log.info("");

        CommandRegistry registry = new CommandRegistry();

        log.info("Available commands:");
        for (String cmd : registry.registeredCommands()) {
            String desc = switch (cmd) {
            case "identify" -> "Identify vectors by classification";
            case "classify" -> "Classify vectors with GLA";
            case "compare" -> "Compare two partitions";
            case "report" -> "Generate statistical report";
            case "generate" -> "Generate synthetic data";
            case "bootstrap" -> "Bootstrap GLA trials";
            case "fclassify", "splitgla" -> "Fast classify with Split-GLA";
            case "cumulative" -> "Cumulative classification";
            case "sclassify", "joingla" -> "Semi-cumulative classification";
            case "tree" -> "Build hierarchical tree";
            case "centroids" -> "Save/load centroids";
            case "sortpart" -> "Sort/partition operations";
            case "mixture" -> "Mixture classifier (EM)";
            case "cut" -> "Cut/trim partitions";
            case "function" -> "Render SC/Shannon functions vs k";
            case "test1" -> "Test algorithm 1 (distortion minimizer)";
            case "test2" -> "Test algorithm 2 (semi-cumulative)";
            default -> "";
            };
            log.info(String.format("  %-15s %s", cmd, desc));
        }

        log.info("");
        log.info("Run '{}' <command> help' for command-specific help.",
                APP_NAME.toLowerCase());
    }
}
