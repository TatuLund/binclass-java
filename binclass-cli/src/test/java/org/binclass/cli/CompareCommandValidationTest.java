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
 * Integration tests for CompareCommand validation of -V constraint.
 */
class CompareCommandValidationTest {

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
     * Writes standard compare files and returns the filebase path.
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

    @Test
    void testValidNearnessMetric1() throws Exception {
        Path filebase = writeCompareFiles();
        args.setOptions(Map.of("filebase", filebase.toString(), "-V", "1"));

        int result = command.execute(args);

        assertEquals(0, result);
    }

    @Test
    void testValidNearnessMetric2() throws Exception {
        Path filebase = writeCompareFiles();
        args.setOptions(Map.of("filebase", filebase.toString(), "-V", "2"));

        int result = command.execute(args);

        assertEquals(0, result);
    }

    @Test
    void testValidNearnessMetric3() throws Exception {
        Path filebase = writeCompareFiles();
        args.setOptions(Map.of("filebase", filebase.toString(), "-V", "3"));

        int result = command.execute(args);

        assertEquals(0, result);
    }

    @Test
    void testInvalidNearnessMetric0() throws Exception {
        Path filebase = writeCompareFiles();
        args.setOptions(Map.of("filebase", filebase.toString(), "-V", "0"));

        assertThrows(Exception.class, () -> command.execute(args),
                "Invalid nearness metric 0 should throw exception");
    }

    @Test
    void testInvalidNearnessMetric4() throws Exception {
        Path filebase = writeCompareFiles();
        args.setOptions(Map.of("filebase", filebase.toString(), "-V", "4"));

        assertThrows(Exception.class, () -> command.execute(args),
                "Invalid nearness metric 4 should throw exception");
    }

    @Test
    void testInvalidNearnessMetric5() throws Exception {
        Path filebase = writeCompareFiles();
        args.setOptions(Map.of("filebase", filebase.toString(), "-V", "5"));

        assertThrows(Exception.class, () -> command.execute(args),
                "Invalid nearness metric 5 should throw exception");
    }

    @Test
    void testDefaultNearnessMetric() throws Exception {
        Path filebase = writeCompareFiles();
        args.setOptions(Map.of("filebase", filebase.toString()));

        int result = command.execute(args);

        assertEquals(0, result);
        assertTrue(Files.exists(tempDir.resolve("data.result")),
                "Result file should be written with default metric");
    }
}
