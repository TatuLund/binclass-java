package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.gla.GLAEngine;
import org.binclass.algorithms.core.VectorSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for FunctionCommand to verify algorithm execution.
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

    @Test
    void testExecuteWithDefaultParameters() throws Exception {
        // Setup - default parameters for information-theoretic function
        // computation
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(3);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Execute - should compute information-theoretic functions for k=1
            // to 3
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithVerbose() throws Exception {
        // Setup - verbose mode enabled for function computation
        TestUtils.setupOptions(args, new HashMap<>());
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(3);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Execute - should execute successfully with verbose output
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithClassWeights() throws Exception {
        // Setup - class weights flag present for function computation
        TestUtils.setupOptions(args, TestUtils.createOptions("-w", ""));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(3);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Execute - should execute with class weights enabled
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithDistanceType1() throws Exception {
        // Setup - Shannon codelength distance type (type=1)
        TestUtils.setupOptions(args, TestUtils.createOptions("-f", "1"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(3);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Execute - should use Shannon codelength distance type 1
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithDistanceType2() throws Exception {
        // Setup - Hamming distance type (type=2)
        TestUtils.setupOptions(args, TestUtils.createOptions("-f", "2"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(3);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Execute - should use Hamming distance type 2
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithInvalidDistanceType() {
        Map<String, String> opts = new HashMap<>();
        opts.put("-f", "abc");
        TestUtils.setupOptions(args, opts);

        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);
        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(3);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            assertThrows(IllegalArgumentException.class,
                    () -> command.execute(args));
        }
    }

    @Test
    void testGetName() {
        assertEquals("function", command.getName());
    }

    @Test
    void testGetDescription() {
        String desc = command.getDescription();
        assertTrue(desc != null && !desc.isEmpty());
    }

    @Test
    void testExecuteWithDistanceType3() throws Exception {
        // Setup - L2 distance type (type=3)
        TestUtils.setupOptions(args, TestUtils.createOptions("-f", "3"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(3);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Execute - should use L2 distance type 3
            int result = command.execute(args);

            assertEquals(0, result);
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

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(3, 10));
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(new Partition(3));

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

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(3, 10));
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(new Partition(3));

            int result = command.execute(args);
            assertEquals(0, result);

            Path expected = tempDir.resolve("custom.output");
            assertTrue(Files.exists(expected),
                    "explicit -o path should be honoured");
        }
    }
}
