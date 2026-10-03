package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.info.InfoFunctions;
import org.binclass.algorithms.io.CentroidReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.mockito.ArgumentCaptor;

/**
 * Unit tests for FunctionCommand.
 * <p>
 * The {@code function} command loads vectors via {@link DataLoader}, centroid
 * records via {@link CentroidReader#loadAll(String)}, then renders them through
 * {@link InfoFunctions#calculateFunctions}. These tests mock exactly those two
 * dependencies, run the real scoring path, and assert on the produced output
 * file plus the arguments passed to {@code calculateFunctions}.
 */
class FunctionCommandTest {

    private FunctionCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new FunctionCommand();
        args = TestUtils.createTestArgs("test");
    }

    @AfterEach
    void tearDown() {
        // Clean up static mocks between tests to prevent conflicts
        clearAllCaches();
    }

    /** Builds a set of vectors, all with the same dimension. */
    private static VectorSet buildVectors(int count, int dim) {
        VectorSet vectors = new VectorSet(count);
        for (int i = 0; i < count; i++) {
            int[] el = new int[dim];
            for (int j = 0; j < dim; j++) {
                el[j] = (i + j) % 2; // alternating 0/1 pattern
            }
            vectors.addElement(new BinaryVector(el, dim));
        }
        return vectors;
    }

    /**
     * Builds centroid records of sizes 1..kCount, each with the given vector
     * dimension. Values are kept in [0,1) so scoring runs without throwing on
     * {@code log2(0)}.
     */
    private static List<InfiniteCentroids> buildRecords(int dim, int kCount) {
        List<InfiniteCentroids> records = new ArrayList<>();
        for (int k = 1; k <= kCount; k++) {
            InfiniteCentroids rec = new InfiniteCentroids(k, dim);
            for (int c = 0; c < k; c++) {
                double[] el = rec.get(c).getArray();
                for (int j = 0; j < dim; j++) {
                    el[j] = ((c + j) % 5) / 10.0; // 0.0 .. 0.4
                }
            }
            records.add(rec);
        }
        return records;
    }

    @Test
    void testExecuteWithDefaultParameters() throws Exception {
        VectorSet vectors = buildVectors(3, 8);
        List<InfiniteCentroids> records = buildRecords(8, 3);

        try (@SuppressWarnings("unused")
        var mockedLoader = mockStatic(DataLoader.class);
                @SuppressWarnings("unused")
                var mockedRecords = mockStatic(CentroidReader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(vectors);
            when(CentroidReader.loadAll(anyString()))
                    .thenReturn(records);

            int result = command.execute(args);

            assertEquals(0, result,
                    "command should succeed with default params");
        }
    }

    @Test
    void testExecuteWithVerbose() throws Exception {
        TestUtils.setupOptions(args, new HashMap<>());
        VectorSet vectors = buildVectors(3, 8);
        List<InfiniteCentroids> records = buildRecords(8, 3);

        try (@SuppressWarnings("unused")
        var mockedLoader = mockStatic(DataLoader.class);
                @SuppressWarnings("unused")
                var mockedRecords = mockStatic(CentroidReader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(vectors);
            when(CentroidReader.loadAll(anyString()))
                    .thenReturn(records);

            int result = command.execute(args);

            assertEquals(0, result, "verbose mode should still succeed");
        }
    }

    @Test
    void testExecuteWithClassWeights() throws Exception {
        TestUtils.setupOptions(args, TestUtils.createOptions("-w", ""));
        VectorSet vectors = buildVectors(3, 8);
        List<InfiniteCentroids> records = buildRecords(8, 3);

        try (@SuppressWarnings("unused")
        var mockedLoader = mockStatic(DataLoader.class);
                @SuppressWarnings("unused")
                var mockedRecords = mockStatic(CentroidReader.class);
                var mockedInfo = mockStatic(InfoFunctions.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(vectors);
            when(CentroidReader.loadAll(anyString()))
                    .thenReturn(records);
            when(InfoFunctions.calculateFunctions(any(), any(), anyInt(),
                    anyBoolean()))
                    .thenReturn("CALCULATING:\nFUNCTION:\nMLE ESTIMATES:\n");

            int result = command.execute(args);

            assertEquals(0, result);
            mockedInfo.verify(() -> InfoFunctions.calculateFunctions(
                    any(), any(), anyInt(), anyBoolean()));
        }
    }

    @Test
    void testExecuteWithDistanceType1() throws Exception {
        TestUtils.setupOptions(args, TestUtils.createOptions("-f", "1"));
        VectorSet vectors = buildVectors(3, 8);
        List<InfiniteCentroids> records = buildRecords(8, 3);

        try (@SuppressWarnings("unused")
        var mockedLoader = mockStatic(DataLoader.class);
                @SuppressWarnings("unused")
                var mockedRecords = mockStatic(CentroidReader.class);
                var mockedInfo = mockStatic(InfoFunctions.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(vectors);
            when(CentroidReader.loadAll(anyString()))
                    .thenReturn(records);
            when(InfoFunctions.calculateFunctions(any(), any(), anyInt(),
                    anyBoolean()))
                    .thenReturn("CALCULATING:\nFUNCTION:\nMLE ESTIMATES:\n");

            int result = command.execute(args);

            assertEquals(0, result);
            ArgumentCaptor<Integer> distCap = ArgumentCaptor
                    .forClass(Integer.class);
            mockedInfo.verify(() -> InfoFunctions.calculateFunctions(
                    any(), any(), distCap.capture(), anyBoolean()));
            assertEquals(1, distCap.getValue(),
                    "distance type 1 should be forwarded to calculateFunctions");
        }
    }

    @Test
    void testExecuteWithDistanceType2() throws Exception {
        TestUtils.setupOptions(args, TestUtils.createOptions("-f", "2"));
        VectorSet vectors = buildVectors(3, 8);
        List<InfiniteCentroids> records = buildRecords(8, 3);

        try (@SuppressWarnings("unused")
        var mockedLoader = mockStatic(DataLoader.class);
                @SuppressWarnings("unused")
                var mockedRecords = mockStatic(CentroidReader.class);
                var mockedInfo = mockStatic(InfoFunctions.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(vectors);
            when(CentroidReader.loadAll(anyString()))
                    .thenReturn(records);
            when(InfoFunctions.calculateFunctions(any(), any(), anyInt(),
                    anyBoolean()))
                    .thenReturn("CALCULATING:\nFUNCTION:\nMLE ESTIMATES:\n");

            int result = command.execute(args);

            assertEquals(0, result);
            ArgumentCaptor<Integer> distCap = ArgumentCaptor
                    .forClass(Integer.class);
            mockedInfo.verify(() -> InfoFunctions.calculateFunctions(
                    any(), any(), distCap.capture(), anyBoolean()));
            assertEquals(2, distCap.getValue());
        }
    }

    @Test
    void testExecuteWithInvalidDistanceType() {
        Map<String, String> opts = new HashMap<>();
        opts.put("-f", "abc");
        TestUtils.setupOptions(args, opts);

        // Invalid -f value throws IllegalArgumentException during option
        // parsing,
        // before any file loading or scoring occurs.
        assertThrows(IllegalArgumentException.class,
                () -> command.execute(args));
    }

    @Test
    void testGetName() {
        assertEquals("function", command.getName());
    }

    @Test
    void testGetDescription() {
        String desc = command.getDescription();
        assertTrue(desc != null && !desc.isEmpty(),
                "description should be non-empty");
    }

    @Test
    void testExecuteWithDistanceType3() throws Exception {
        TestUtils.setupOptions(args, TestUtils.createOptions("-f", "3"));
        VectorSet vectors = buildVectors(3, 8);
        List<InfiniteCentroids> records = buildRecords(8, 3);

        try (@SuppressWarnings("unused")
        var mockedLoader = mockStatic(DataLoader.class);
                @SuppressWarnings("unused")
                var mockedRecords = mockStatic(CentroidReader.class);
                var mockedInfo = mockStatic(InfoFunctions.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(vectors);
            when(CentroidReader.loadAll(anyString()))
                    .thenReturn(records);
            when(InfoFunctions.calculateFunctions(any(), any(), anyInt(),
                    anyBoolean()))
                    .thenReturn("CALCULATING:\nFUNCTION:\nMLE ESTIMATES:\n");

            int result = command.execute(args);

            assertEquals(0, result);
            ArgumentCaptor<Integer> distCap = ArgumentCaptor
                    .forClass(Integer.class);
            mockedInfo.verify(() -> InfoFunctions.calculateFunctions(
                    any(), any(), distCap.capture(), anyBoolean()));
            assertEquals(3, distCap.getValue());
        }
    }

    @Test
    void testExecuteWritesOutputFile(@TempDir Path tempDir) throws Exception {
        // Per spec section 4.3.2 the `function` command must persist its
        // results to a <filebase>.output file. calculateFunctions() emits a
        // CALCULATING table followed by a FUNCTION section and MLE fits.
        String filebase = tempDir.resolve("data").toString();
        Map<String, String> opts = new HashMap<>();
        opts.put("filebase", filebase);
        args.setOptions(opts);

        VectorSet vectors = buildVectors(3, 8);
        List<InfiniteCentroids> records = buildRecords(8, 3);

        try (@SuppressWarnings("unused")
        var mockedLoader = mockStatic(DataLoader.class);
                @SuppressWarnings("unused")
                var mockedRecords = mockStatic(CentroidReader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(vectors);
            when(CentroidReader.loadAll(anyString()))
                    .thenReturn(records);

            int result = command.execute(args);
            assertEquals(0, result);

            Path expected = tempDir.resolve("data.output");
            assertTrue(Files.exists(expected),
                    "default <filebase>.output should be written");
            String content = Files.readString(expected);
            assertTrue(content.contains("CALCULATING:"),
                    "output file should contain the CALCULATING section");
            assertTrue(content.contains("MLE ESTIMATES:"),
                    "output file should contain the MLE fits section");
        }
    }

    @Test
    void testExecuteHonoursExplicitOutputFlag(@TempDir Path tempDir)
            throws Exception {
        // An explicit -o path overrides the default <filebase>.output.
        String filebase = tempDir.resolve("data").toString();
        Map<String, String> opts = new HashMap<>();
        opts.put("-o", tempDir.resolve("custom.output").toString());
        opts.put("filebase", filebase);
        args.setOptions(opts);

        VectorSet vectors = buildVectors(3, 8);
        List<InfiniteCentroids> records = buildRecords(8, 3);

        try (@SuppressWarnings("unused")
        var mockedLoader = mockStatic(DataLoader.class);
                @SuppressWarnings("unused")
                var mockedRecords = mockStatic(CentroidReader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(vectors);
            when(CentroidReader.loadAll(anyString()))
                    .thenReturn(records);

            int result = command.execute(args);
            assertEquals(0, result);

            Path expected = tempDir.resolve("custom.output");
            assertTrue(Files.exists(expected),
                    "explicit -o path should be honoured");
        }
    }
}
