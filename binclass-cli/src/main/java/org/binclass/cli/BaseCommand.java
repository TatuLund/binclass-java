package org.binclass.cli;

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
}
