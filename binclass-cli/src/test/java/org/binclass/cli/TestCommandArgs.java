package org.binclass.cli;

import java.util.HashMap;
import java.util.Map;

/**
 * Concrete implementation of CommandArgs for testing purposes.
 */
public class TestCommandArgs implements CliParser.CommandArgs {
    private final String command;
    private final Map<String, String> options;

    public TestCommandArgs(String command) {
        this.command = command;
        this.options = new HashMap<>();
    }

    public void addOption(String key, String value) {
        options.put(key, value);
    }

    public void setOptions(Map<String, String> opts) {
        options.clear();
        options.putAll(opts);
    }

    @Override
    public String command() {
        return command;
    }

    @Override
    public Map<String, String> options() {
        return options;
    }
}
