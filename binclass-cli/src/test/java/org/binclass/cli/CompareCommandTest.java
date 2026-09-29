package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for CompareCommand to verify partition comparison functionality.
 */
class CompareCommandTest {

    private CompareCommand command;
    private TestCommandArgs args;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        command = new CompareCommand();
        args = TestUtils.createTestArgs("compare");
    }

    /**
     * Writes standard compare files and returns the filebase path. Uses two
     * clusters in each partition with a swapped assignment so the contingency
     * matrix is non-trivial but well defined.
     */
    private Path writeCompareFiles() throws Exception {
        List<String> strains = List.of("strain0", "strain1", "strain2",
                "strain3");
        List<String> bits = List.of("10101010", "01010101", "11001100",
                "00110011");
        List<Integer> p1 = List.of(1, 1, 2, 2);
        List<Integer> p2 = List.of(1, 2, 1, 2);
        return TestUtils.writeCompareFiles(tempDir, "data", 23, 8, 15, strains,
                bits, p1, p2);
    }

    /**
     * Writes compare files with the given class assignments and returns the
     * filebase path.
     */
    private Path writeCompareFiles(List<Integer> p1Classes,
            List<Integer> p2Classes) throws Exception {
        List<String> strains = List.of("strain0", "strain1", "strain2",
                "strain3");
        List<String> bits = List.of("10101010", "01010101", "11001100",
                "00110011");
        return TestUtils.writeCompareFiles(tempDir, "data", 23, 8, 15, strains,
                bits, p1Classes, p2Classes);
    }

    @Test
    void testExecuteWithValidNearnessMetric1() throws Exception {
        Path filebase = writeCompareFiles();
        args.setOptions(Map.of("filebase", filebase.toString(), "-V", "1"));

        int result = command.execute(args);

        assertEquals(0, result);
        assertResultWritten(filebase);
    }

    @Test
    void testExecuteWithValidNearnessMetric2() throws Exception {
        Path filebase = writeCompareFiles();
        args.setOptions(Map.of("filebase", filebase.toString(), "-V", "2"));

        int result = command.execute(args);

        assertEquals(0, result);
        assertResultWritten(filebase);
    }

    @Test
    void testExecuteWithValidNearnessMetric3() throws Exception {
        Path filebase = writeCompareFiles();
        args.setOptions(Map.of("filebase", filebase.toString(), "-V", "3"));

        int result = command.execute(args);

        assertEquals(0, result);
        assertResultWritten(filebase);
    }

    @Test
    void testExecuteWithInvalidNearnessMetric() throws Exception {
        writeCompareFiles();
        args.setOptions(Map.of("filebase", tempDir.resolve("data").toString(),
                "-V", "4"));

        assertThrows(Exception.class, () -> command.execute(args),
                "Invalid nearness metric (4) should throw exception");
    }

    @Test
    void testExecuteWithDefaultNearnessMetric() throws Exception {
        Path filebase = writeCompareFiles();
        args.setOptions(Map.of("filebase", filebase.toString()));

        int result = command.execute(args);

        assertEquals(0, result);
        assertResultWritten(filebase);
    }

    @Test
    void testExecuteWithMultipleVectors() throws Exception {
        List<String> strains = List.of("strain0", "strain1", "strain2",
                "strain3", "strain4", "strain5");
        List<String> bits = List.of("10101010", "01010101", "11001100",
                "00110011", "11110000", "00001111");
        List<Integer> p1 = List.of(1, 1, 2, 2, 3, 3);
        List<Integer> p2 = List.of(1, 2, 1, 2, 3, 3);
        Path filebase = TestUtils.writeCompareFiles(tempDir, "data", 23, 8, 15,
                strains, bits, p1, p2);
        args.setOptions(Map.of("filebase", filebase.toString(), "-V", "1"));

        int result = command.execute(args);

        assertEquals(0, result);
        assertResultWritten(filebase);
    }

    @Test
    void testExecuteWithExactMatchesFlag() throws Exception {
        // Identical assignments in both partitions yield a perfect diagonal and
        // distance 0 under exact matching.
        Path filebase = writeCompareFiles(List.of(1, 1, 2, 2),
                List.of(1, 1, 2, 2));
        args.setOptions(Map.of("filebase", filebase.toString(), "-V", "1",
                "-M", ""));

        int result = command.execute(args);

        assertEquals(0, result);
        assertResultWritten(filebase);
    }

    @Test
    void testExecuteWithCustomOutputFile() throws Exception {
        Path filebase = writeCompareFiles();
        Path outFile = tempDir.resolve("custom.result");
        args.setOptions(Map.of("filebase", filebase.toString(), "-V", "1",
                "-o", outFile.toString()));

        int result = command.execute(args);

        assertEquals(0, result);
        assertTrue(Files.exists(outFile),
                "Custom output file should be written");
        String content = Files.readString(outFile);
        assertTrue(content.contains("Overall distance"),
                "Output should contain the computed distance");
    }

    @Test
    void testResultContainsDistanceAndMatrix() throws Exception {
        Path filebase = writeCompareFiles();
        args.setOptions(Map.of("filebase", filebase.toString(), "-V", "1"));

        command.execute(args);

        String content = Files.readString(tempDir.resolve("data.result"));
        assertTrue(content.contains("COMPARISON RESULTS"),
                "Output should contain the matrix header");
        assertTrue(content.contains("Overall distance = "),
                "Output should contain the overall distance line");
    }

    private void assertResultWritten(Path filebase) throws Exception {
        Path outFile = tempDir.resolve("data.result");
        assertTrue(Files.exists(outFile), "Result file should be written");
        String content = Files.readString(outFile);
        assertTrue(content.contains("Overall distance"),
                "Output should contain the computed distance");
    }

    @Test
    void testGetName() {
        assertEquals("compare", command.getName());
    }

    @Test
    void testGetDescription() {
        String desc = command.getDescription();
        org.junit.jupiter.api.Assertions.assertNotNull(desc);
        org.junit.jupiter.api.Assertions.assertFalse(desc.isEmpty());
    }
}
