package org.binclass.cli;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import java.util.Map;
import org.slf4j.LoggerFactory;

/**
 * Base interface for all CLI commands.
 */
public interface BaseCommand {
    /** Get the command name (e.g., "identify", "classify"). */
    String getName();

    /** Get a brief description of what this command does. */
    String getDescription();

    /**
     * Execute the command with the given arguments. Returns exit code (0 =
     * success).
     */
    int execute(CliParser.CommandArgs args) throws Exception;

    /**
     * Set up logging based on verbose mode. When quiet mode (-q) is enabled,
     * suppresses INFO/DEBUG messages by setting log level to WARN.
     * 
     * @param options
     *            the parsed CLI options map
     */
    default void setupVerboseMode(Map<String, String> options) {
        Logger logger = (Logger) LoggerFactory.getLogger("org.binclass");
        if (options.containsKey("-q")) {
            logger.setLevel(Level.WARN);
        }
    }

    /**
     * Parse an integer option value with validation.
     * 
     * @param opts
     *            the parsed CLI options map
     * @param option
     *            the option key (e.g., "-s")
     * @param message
     *            exception message template for invalid values
     * @return the parsed int value, or 0 if option not present
     */
    default int parseOptionInt(Map<String, String> opts, String option,
            String message) {
        return parseOptionInt(opts, option, message, 0);
    }

    /**
     * Parse an integer option value with validation and custom default.
     * 
     * @param opts
     *            the parsed CLI options map
     * @param option
     *            the option key (e.g., "-s")
     * @param message
     *            exception message template for invalid values
     * @param defaultValue
     *            the value to return if option is not present
     * @return the parsed int value, or defaultValue if option not present
     */
    default int parseOptionInt(Map<String, String> opts, String option,
            String message, int defaultValue) {
        if (!opts.containsKey(option))
            return defaultValue;
        try {
            return Integer.parseInt(opts.get(option));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Parse a double option value with validation.
     * 
     * @param opts
     *            the parsed CLI options map
     * @param option
     *            the option key (e.g., "-E")
     * @param message
     *            exception message template for invalid values
     * @return the parsed double value, or 0.0 if option not present
     */
    default double parseOptionDouble(Map<String, String> opts, String option,
            String message) {
        return parseOptionDouble(opts, option, message, 0.0);
    }

    /**
     * Parse a double option value with validation and custom default.
     * 
     * @param opts
     *            the parsed CLI options map
     * @param option
     *            the option key (e.g., "-E")
     * @param message
     *            exception message template for invalid values
     * @param defaultValue
     *            the value to return if option is not present
     * @return the parsed double value, or defaultValue if option not present
     */
    default double parseOptionDouble(Map<String, String> opts, String option,
            String message, double defaultValue) {
        if (!opts.containsKey(option))
            return defaultValue;
        try {
            return Double.parseDouble(opts.get(option));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(message);
        }
    }
}
