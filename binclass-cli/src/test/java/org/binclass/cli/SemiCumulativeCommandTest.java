package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.binclass.algorithms.classify.CumulativeConfig;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.JoinGLA;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.mockito.ArgumentCaptor;

/**
 * Unit tests for SemiCumulativeCommand to verify algorithm execution.
 */
class SemiCumulativeCommandTest {

    private SemiCumulativeCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new SemiCumulativeCommand();
        args = TestUtils.createTestArgs("test");
    }

    @AfterEach
    void tearDown() {
        // Clean up static mocks between tests to prevent conflicts
        clearAllCaches();
    }

    @Test
    void testExecuteWithDefaultParameters() throws Exception {
        // Setup - default parameters for semi-cumulative classification
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedJoinGLA = mockStatic(JoinGLA.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);
            // Mock JoinGLA.joinGLa to return a valid partition
            Partition mockPartition = new org.binclass.algorithms.core.Partition(
                    2);
            when(JoinGLA.joinGLA(any(), any(), any(), any()))
                    .thenReturn(mockPartition);
            // Capture parameters passed to joinGLA
            ArgumentCaptor<VectorSet> vectorSetCaptor = ArgumentCaptor
                    .forClass(VectorSet.class);
            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            // Execute - should run semi-cumulative classification with Join-GLA
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify - should call joinGLA with vectorSet and GLAConfig
            mockedJoinGLA.verify(
                    () -> JoinGLA.joinGLA(vectorSetCaptor.capture(), any(),
                            any(), configCaptor.capture()));

            VectorSet capturedVectorSet = vectorSetCaptor.getValue();
            assertEquals(mockVectorSet, capturedVectorSet);

            GLAConfig capturedConfig = configCaptor.getValue();
            assertNotNull(capturedConfig);
        }
    }

    @Test
    void testExecuteWithVerbose() throws Exception {
        // Setup - verbose mode enabled
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
        // Setup - absolute match flag present
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
    void testExecuteWithEpsilon() throws Exception {
        // Setup - epsilon=0.01 for convergence threshold
        TestUtils.setupOptions(args, TestUtils.createOptions("-E", "0.01"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should use epsilon=0.01 for convergence threshold
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithJeffreysPrior() throws Exception {
        // Setup - Jeffreys prior flag present
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
    void testExecuteWithJoinTarget() throws Exception {
        // Setup - join target=3 for semi-cumulative analysis
        TestUtils.setupOptions(args, TestUtils.createOptions("-j", "3"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should use join target of 3
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithGLAThreshold() throws Exception {
        // Setup - GLA threshold=1.5 for semi-cumulative analysis
        TestUtils.setupOptions(args, TestUtils.createOptions("-T", "1.5"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should use GLA threshold of 1.5
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWritesPartitionToFile(@TempDir Path tempDir)
            throws Exception {
        // With no -P flag, the resulting partition must be written to
        // <filebase>.partition. This is what was missing before: a plain
        // `sclassify data/test` produced no file.
        String filebase = tempDir.resolve("data").toString();
        Map<String, String> opts = new HashMap<>();
        opts.put("filebase", filebase);
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedJoinGLA = mockStatic(JoinGLA.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(3, 10));

            Partition partition = new org.binclass.algorithms.core.Partition(2);
            int length = 10;
            BinaryVector v1 = new BinaryVector(new int[length], 0, length, 1,
                    "strainA");
            BinaryVector v2 = new BinaryVector(new int[length], 0, length, 2,
                    "strainB");
            partition.addElement(1, v1);
            partition.addElement(2, v2);
            when(JoinGLA.joinGLA(any(), any(), any(), any()))
                    .thenReturn(partition);

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
        TestUtils.setupOptions(args, TestUtils.createOptions("-P",
                tempDir.resolve("custom.partition").toString(), "filebase",
                filebase));

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedJoinGLA = mockStatic(JoinGLA.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(3, 10));

            Partition partition = new org.binclass.algorithms.core.Partition(2);
            int length = 10;
            BinaryVector v1 = new BinaryVector(new int[length], 0, length, 1,
                    "strainA");
            partition.addElement(1, v1);
            when(JoinGLA.joinGLA(any(), any(), any(), any()))
                    .thenReturn(partition);

            int result = command.execute(args);
            assertEquals(0, result);

            Path expected = tempDir.resolve("custom.partition");
            assertTrue(Files.exists(expected),
                    "explicit -P path should be used");
        }
    }

    @Test
    void testGetName() {
        assertEquals("sclassify", command.getName());
    }

    @Test
    void testGetDescription() {
        String desc = command.getDescription();
        assertTrue(desc != null && !desc.isEmpty());
    }
}
