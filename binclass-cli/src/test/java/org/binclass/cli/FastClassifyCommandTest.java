package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.SplitGLA;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.mockito.ArgumentCaptor;

/**
 * Unit tests for FastClassifyCommand to verify algorithm execution.
 */
class FastClassifyCommandTest {

    private FastClassifyCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new FastClassifyCommand();
        args = TestUtils.createTestArgs("test");
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up static mocks between tests to prevent conflicts
        org.mockito.Mockito.clearAllCaches();
    }

    @Test
    void testExecuteWithDefaultParameters() throws Exception {
        // Setup - default parameters for Split-GLA algorithm execution
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should run Split-GLA algorithm with default parameters
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithVerbose() throws Exception {
        // Setup - verbose mode enabled for Split-GLA execution
        TestUtils.setupOptions(args, new HashMap<>());
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should execute successfully with verbose output
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithUseAbsMatch() throws Exception {
        // Setup - absolute match flag present for Split-GLA execution
        TestUtils.setupOptions(args, TestUtils.createOptions("-A", ""));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should execute with absolute match mode
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithKstopWhen() throws Exception {
        // Setup - kstop when=5 for Split-GLA algorithm termination
        TestUtils.setupOptions(args, TestUtils.createOptions("-k", "5"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should use kstop when parameter of 5
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithJeffreysPrior() throws Exception {
        // Setup - Jeffreys prior flag present for Split-GLA execution
        TestUtils.setupOptions(args, TestUtils.createOptions("-J", ""));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should execute successfully with Jeffreys prior
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithEpsilon() throws Exception {
        // Setup - epsilon=0.01 for Split-GLA algorithm convergence threshold
        TestUtils.setupOptions(args, TestUtils.createOptions("-E", "0.01"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            var mockedSplitGla = mockStatic(SplitGLA.class);

            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(2);
            when(SplitGLA.splitGLA(any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Capture GLAConfig parameter
            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            // Execute - should use epsilon=0.01 for convergence threshold
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify - should call splitGLA with epsilon=0.01 from CLI switch
            mockedSplitGla.verify(() -> SplitGLA.splitGLA(any(), any(), any(),
                    configCaptor.capture()));

            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(0.01, capturedConfig.epsilon());
        }
    }

    @Test
    void testExecuteWithKstopWhenCapturesConfig() throws Exception {
        // Setup - kstopwhen=5 from CLI switch -S
        TestUtils.setupOptions(args, TestUtils.createOptions("-S", "5"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            var mockedSplitGla = mockStatic(SplitGLA.class);

            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(2);
            when(SplitGLA.splitGLA(any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Capture GLAConfig parameter
            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            // Execute - should use kstopwhen=5 from CLI switch
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify - should call splitGLA with kstopwhen=5 from CLI switch
            mockedSplitGla.verify(() -> SplitGLA.splitGLA(any(), any(), any(),
                    configCaptor.capture()));

            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(5, capturedConfig.kstopwhen());
        }
    }

    @Test
    void testExecuteWithJeffreysPriorCapturesConfig() throws Exception {
        // Setup - Jeffreys prior flag from CLI switch -J
        TestUtils.setupOptions(args, TestUtils.createOptions("-J", ""));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            var mockedSplitGla = mockStatic(SplitGLA.class);

            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(2);
            when(SplitGLA.splitGLA(any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Capture GLAConfig parameter
            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            // Execute - should execute successfully with Jeffreys prior
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify - should call splitGLA with jeffreysPrior=true from CLI
            // switch
            mockedSplitGla.verify(() -> SplitGLA.splitGLA(any(), any(), any(),
                    configCaptor.capture()));

            GLAConfig capturedConfig = configCaptor.getValue();
            assertTrue(capturedConfig.jeffreysPrior());
        }
    }

    @Test
    void testExecuteWithInvalidEpsilon() {
        Map<String, String> opts = new HashMap<>();
        opts.put("-E", "0.6");
        TestUtils.setupOptions(args, opts);

        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);
        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            assertThrows(IllegalArgumentException.class,
                    () -> command.execute(args));
        }
    }

    @Test
    void testGetName() {
        assertEquals("fclassify", command.getName());
    }

    @Test
    void testExecuteWritesPartitionToFile(@TempDir Path tempDir)
            throws Exception {
        // With no -P flag, Split-GLA's resulting partition must be written to
        // <filebase>.partition, mirroring C's inf_write_partition(). This is
        // what was missing before: a plain `fclassify data/test` produced no
        // file.
        String filebase = tempDir.resolve("data").toString();
        Map<String, String> opts = new HashMap<>();
        opts.put("filebase", filebase);
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedSplitGla = mockStatic(SplitGLA.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(3, 10));

            Partition resultPartition = new Partition(2);
            int length = 10;
            BinaryVector v1 = new BinaryVector(new int[length], 0, length, 1,
                    "strainA");
            BinaryVector v2 = new BinaryVector(new int[length], 0, length, 2,
                    "strainB");
            resultPartition.addElement(1, v1);
            resultPartition.addElement(2, v2);
            when(SplitGLA.splitGLA(any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            int result = command.execute(args);
            assertEquals(0, result);

            Path expected = tempDir.resolve("data.partition");
            assertTrue(Files.exists(expected),
                    "default <filebase>.partition should be written");
            String content = Files.readString(expected);
            assertTrue(content.contains("Class 1"),
                    "partition should contain class headers");
            assertTrue(content.contains("strainA"),
                    "partition should contain the strain identifier");
        }
    }

    @Test
    void testExecuteHonoursExplicitPartitionFlag(@TempDir Path tempDir)
            throws Exception {
        // An explicit -P path overrides the default <filebase>.partition.
        String filebase = tempDir.resolve("data").toString();
        TestUtils.setupOptions(args,
                TestUtils.createOptions("-P", tempDir.resolve(
                        "custom.partition").toString(), "filebase",
                        filebase));

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedSplitGla = mockStatic(SplitGLA.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(3, 10));

            Partition resultPartition = new Partition(2);
            int length = 10;
            BinaryVector v1 = new BinaryVector(new int[length], 0, length, 1,
                    "strainA");
            resultPartition.addElement(1, v1);
            when(SplitGLA.splitGLA(any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            int result = command.execute(args);
            assertEquals(0, result);

            Path expected = tempDir.resolve("custom.partition");
            assertTrue(Files.exists(expected),
                    "explicit -P path should be used");
        }
    }

    @Test
    void testGetDescription() {
        String desc = command.getDescription();
        assertTrue(desc != null && !desc.isEmpty());
    }
}
