package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
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

    /**
     * Writes a header file and two partition files for compare-command tests.
     * <p>
     * The two partitions share the same underlying vectors (identical strain
     * ids and bit patterns) but may assign them to different classes, mirroring
     * C's {@code compare_partitions()} which reads both assignments from a
     * single dataset. Returns the filebase path used by
     * {@link org.binclass.algorithms.io.PartitionReader}.
     * </p>
     *
     * @param dir
     *            directory in which to write the files
     * @param filebaseName
     *            base name of the data files (without extension)
     * @param vecOffs
     *            offset to the start of the binary portion
     * @param veclen
     *            number of bits per vector
     * @param idOffs
     *            offset to the start of the strain identifier
     * @param strains
     *            unique strain identifier for each shared vector
     * @param bits
     *            binary string (0/1) for each shared vector, same order as
     *            {@code strains}
     * @param p1Classes
     *            1-based class index of each vector in partition 1
     * @param p2Classes
     *            1-based class index of each vector in partition 2
     * @return the filebase path (without extension) used to locate the files
     * @throws IOException
     *             if writing a file fails
     */
    public static Path writeCompareFiles(Path dir, String filebaseName,
            int vecOffs, int veclen, int idOffs, List<String> strains,
            List<String> bits, List<Integer> p1Classes,
            List<Integer> p2Classes) throws IOException {
        Path filebase = dir.resolve(filebaseName);
        String header = "vecoffs=" + vecOffs + "\n"
                + "veclen=" + veclen + "\n" + "idoffs=" + idOffs + "\n";
        Files.writeString(dir.resolve(filebaseName + ".header"), header);

        writePartitionFile(dir.resolve(filebaseName + ".partition1"), vecOffs,
                idOffs, strains, bits, p1Classes);
        writePartitionFile(dir.resolve(filebaseName + ".partition2"), vecOffs,
                idOffs, strains, bits, p2Classes);

        return filebase;
    }

    /**
     * Writes a single partition file grouping vectors by class.
     * <p>
     * Mirrors the on-disk layout read by {@code PartitionReader}: a
     * {@code Class N} header precedes each run of vectors belonging to that
     * cluster, and each vector line places the strain identifier at
     * {@code idOffs} and the binary bits at {@code vecOffs}.
     * </p>
     *
     * @param path
     *            destination file path
     * @param vecOffs
     *            offset to the start of the binary portion
     * @param idOffs
     *            offset to the start of the strain identifier
     * @param strains
     *            unique strain identifier for each vector
     * @param bits
     *            binary string for each vector, same order as {@code strains}
     * @param classes
     *            1-based class index of each vector
     * @throws IOException
     *             if writing the file fails
     */
    public static void writePartitionFile(Path path, int vecOffs, int idOffs,
            List<String> strains, List<String> bits, List<Integer> classes)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < strains.size(); i++) {
            if (i == 0 || !classes.get(i).equals(classes.get(i - 1))) {
                sb.append("Class ").append(classes.get(i)).append('\n');
            }
            sb.append(formatVectorLine(strains.get(i), bits.get(i), idOffs,
                    vecOffs)).append('\n');
        }
        Files.writeString(path, sb.toString());
    }

    /**
     * Formats a single partition vector line so the strain identifier lands at
     * {@code idOffs} and the binary bits land at {@code vecOffs}.
     *
     * @param strain
     *            the strain identifier of the vector
     * @param bitString
     *            the binary representation (0/1) of the vector
     * @param idOffs
     *            offset to the start of the strain identifier
     * @param vecOffs
     *            offset to the start of the binary portion
     * @return the formatted line
     */
    public static String formatVectorLine(String strain, String bitString,
            int idOffs, int vecOffs) {
        StringBuilder sb = new StringBuilder();
        // Column 0 must be non-space for PartitionReader to treat the line as
        // a vector (mirrors C's read_partition: buf[0] != ' '). A placeholder
        // is placed there so lines whose strain starts later (at idOffs) are
        // still detected.
        sb.append('X');
        while (sb.length() < idOffs) {
            sb.append(' ');
        }
        sb.append(strain);
        while (sb.length() < vecOffs) {
            sb.append(' ');
        }
        sb.append(bitString);
        return sb.toString();
    }
}
