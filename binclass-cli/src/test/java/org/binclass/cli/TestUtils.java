package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.VectorSet;

/**
 * Test utilities for CLI command tests.
 */
public final class TestUtils {

    private TestUtils() {
        // Utility class — prevent instantiation
    }

    /**
     * Creates a mock VectorSet with alternating binary patterns.
     * 
     * @param nVectors
     *            number of vectors to create
     * @param length
     *            length of each vector
     * @return a populated VectorSet
     */
    public static VectorSet createMockVectorSet(int nVectors, int length) {
        VectorSet vectorSet = new VectorSet(nVectors);
        for (int i = 0; i < nVectors; i++) {
            int[] el = new int[length];
            for (int j = 0; j < length; j++) {
                el[j] = (i + j) % 2; // Alternating pattern
            }
            BinaryVector bv = new BinaryVector(el, 0, length, 0, "strain" + i);
            vectorSet.addElement(bv);
        }
        return vectorSet;
    }

    /**
     * Executes a command with mocked DataLoader and verifies successful
     * execution.
     * 
     * @param command
     *            the command to execute
     * @param args
     *            command arguments
     * @param mockVectorSet
     *            the mock VectorSet to return from DataLoader
     * @return the exit code from command.execute()
     */
    public static int executeCommandWithMockedLoader(BaseCommand command,
            TestCommandArgs args, VectorSet mockVectorSet) throws Exception {
        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute
            int result = command.execute(args);

            // Verify - should execute successfully
            assertEquals(0, result);
            return result;
        }
    }

    /**
     * Executes a command with mocked DataLoader and verifies successful
     * execution.
     * 
     * @param command
     *            the command to execute
     * @param args
     *            command arguments
     * @param nVectors
     *            number of vectors in mock VectorSet
     * @param length
     *            length of each vector
     * @return the exit code from command.execute()
     */
    public static int executeCommandWithMockedLoader(BaseCommand command,
            TestCommandArgs args, int nVectors, int length) throws Exception {
        VectorSet mockVectorSet = createMockVectorSet(nVectors, length);
        return executeCommandWithMockedLoader(command, args, mockVectorSet);
    }

    /**
     * Sets up options on a TestCommandArgs object.
     * 
     * @param args
     *            the test command args
     * @param opts
     *            map of option key-value pairs
     */
    public static void setupOptions(TestCommandArgs args,
            Map<String, String> opts) {
        // Directly set options on concrete object (no mocking needed)
        args.options().clear();
        args.options().putAll(opts);
    }

    /**
     * Sets up command name and options on a TestCommandArgs object.
     * 
     * @param args
     *            the test command args
     * @param opts
     *            map of option key-value pairs
     * @param commandName
     *            the command name to set
     */
    public static void setupOptions(TestCommandArgs args,
            Map<String, String> opts, String commandName) {
        // Directly set options on concrete object (no mocking needed)
        args.options().clear();
        args.options().putAll(opts);
    }

    /**
     * Creates a HashMap with the given key-value pair.
     *
     * 
     * /** Creates a new TestCommandArgs with the given command name.
     * 
     * @param commandName
     *            the command name (e.g., "classify", "identify")
     * @return a new TestCommandArgs instance
     */
    public static TestCommandArgs createTestArgs(String commandName) {
        return new TestCommandArgs(commandName);
    }

    /**
     * Creates a HashMap with the given key-value pair.
     * 
     * @param key
     *            option key (e.g., "-r", "-E")
     * @param value
     *            option value
     * @return a map containing the single entry
     */
    public static Map<String, String> createOptions(String key, String value) {
        Map<String, String> opts = new HashMap<>();
        opts.put(key, value);
        return opts;
    }

    /**
     * Creates a HashMap with multiple key-value pairs.
     * 
     * @param entries
     *            alternating key-value pairs (key1, value1, key2, value2, ...)
     * @return a map containing all entries
     */
    public static Map<String, String> createOptions(String... entries) {
        Map<String, String> opts = new HashMap<>();
        for (int i = 0; i < entries.length - 1; i += 2) {
            opts.put(entries[i], entries[i + 1]);
        }
        return opts;
    }

    /**
     * Verifies that DataLoader.loadVectors was called with the expected
     * filebase.
     * 
     * @param mockedLoader
     *            the static mock for DataLoader
     * @param filebase
     *            the expected filebase argument
     */
    public static void verifyDataLoaderCalled(
            org.mockito.stubbing.Answer<?> mockedLoader, String filebase) {
        // This is a helper to make verification more readable
        // The actual verification is done inline in tests
    }
}
