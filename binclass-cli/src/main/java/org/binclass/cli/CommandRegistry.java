package org.binclass.cli;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry mapping command names to their implementation classes.
 */
public class CommandRegistry {

    private static final Map<String, Class<? extends BaseCommand>> COMMANDS = new HashMap<>();

    static {
        COMMANDS.put("identify", IdentifyCommand.class);
        COMMANDS.put("classify", ClassifyCommand.class);
        COMMANDS.put("compare", CompareCommand.class);
        COMMANDS.put("report", ReportCommand.class);
        COMMANDS.put("generate", GenerateCommand.class);
        COMMANDS.put("bootstrap", BootstrapCommand.class);
        COMMANDS.put("fclassify", FastClassifyCommand.class);
        COMMANDS.put("splitgla", FastClassifyCommand.class);
        COMMANDS.put("cumulative", CumulativeCommand.class);
        COMMANDS.put("sclassify", SemiCumulativeCommand.class);
        COMMANDS.put("joingla", SemiCumulativeCommand.class);
        COMMANDS.put("tree", TreeCommand.class);
        COMMANDS.put("centroids", CentroidCommand.class);
        COMMANDS.put("sortpart", SortPartCommand.class);
        COMMANDS.put("mixture", MixtureCommand.class);
        COMMANDS.put("cut", CutCommand.class);
        COMMANDS.put("function", FunctionCommand.class);
        COMMANDS.put("test1", TestAlgorithmsCommand.class);
        COMMANDS.put("test2", TestAlgorithmsCommand.class);
    }

    /** Get all registered command names. */
    public Set<String> registeredCommands() {
        return Collections.unmodifiableSet(COMMANDS.keySet());
    }

    /** Get the implementation class for a command name. */
    public Class<? extends BaseCommand> getCommandClass(String name) {
        return COMMANDS.get(name.toLowerCase());
    }

    /** Check if a command is registered. */
    public boolean hasCommand(String name) {
        return COMMANDS.containsKey(name.toLowerCase());
    }

    /** Get help text for a specific command. */
    public String helpTextFor(String command) {
        CliParser parser = new CliParser();
        return parser.helpTextForCommand(command);
    }
}
