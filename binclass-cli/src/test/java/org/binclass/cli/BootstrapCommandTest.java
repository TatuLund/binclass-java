package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import java.util.HashMap;
import java.util.Map;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.GLAEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

/**
 * Unit tests for BootstrapCommand to verify algorithm execution.
 */
public class BootstrapCommandTest {

    private BootstrapCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new BootstrapCommand();
        args = TestUtils.createTestArgs("test");
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up static mocks between tests to prevent conflicts
        org.mockito.Mockito.clearAllCaches();
    }

    @Test
    void testExecuteWithDefaultParameters() throws Exception {
        // Setup - default heuristic is 1 (standard GLA)
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Execute - should run default 5 bootstrap trials
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithCustomBootstrapI() throws Exception {
        // Setup - custom bootstrap iterations=3
        TestUtils.setupOptions(args, TestUtils.createOptions("-I", "3"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Execute - should run 3 bootstrap trials
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithHeuristic1() throws Exception {
        // Setup - heuristic 1 = standard Shannon codelength
        TestUtils.setupOptions(args, TestUtils.createOptions("-r", "1"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Execute
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithHeuristic2() throws Exception {
        // Setup - heuristic 2 = stochastic relaxation
        TestUtils.setupOptions(args, TestUtils.createOptions("-r", "2"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.glaSr(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Execute
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithHeuristic3() throws Exception {
        // Setup - heuristic 3 = simulated annealing
        TestUtils.setupOptions(args, TestUtils.createOptions("-r", "3"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.glaSa(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Execute
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithInvalidBootstrapI() {
        Map<String, String> opts = new HashMap<>();
        opts.put("-I", "abc");
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
        assertEquals("bootstrap", command.getName());
    }

    @Test
    void testGetDescription() {
        String desc = command.getDescription();
        assertTrue(desc != null && !desc.isEmpty());
    }

    @Test
    void testExecuteWithBootstrapK() throws Exception {
        // Setup - bootstrap K=2 (will use k=3 in GLA)
        TestUtils.setupOptions(args, TestUtils.createOptions("-K", "2"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Execute
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithBootstrapSize() throws Exception {
        // Setup - bootstrap size=50
        TestUtils.setupOptions(args, TestUtils.createOptions("-N", "50"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Execute
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithEpsilon() throws Exception {
        // Setup - epsilon=0.01 for convergence threshold
        TestUtils.setupOptions(args, TestUtils.createOptions("-E", "0.01"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Capture GLAConfig parameter
            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            // Execute
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify - should call GLA with epsilon=0.01 from CLI switch
            // (called once per trial)
            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()), times(5));

            GLAConfig capturedConfig = configCaptor.getAllValues().get(0);
            assertEquals(0.01, capturedConfig.epsilon());
        }
    }

    @Test
    void testExecuteWithJeffreysPrior() throws Exception {
        // Setup - Jeffreys prior flag present
        TestUtils.setupOptions(args, TestUtils.createOptions("-J", ""));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            var mockedGlaEngine = mockStatic(GLAEngine.class);

            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute
            int result = command.execute(args);

            // Verify - should execute successfully with Jeffreys prior
            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithClassWeights() throws Exception {
        // Setup - class weights flag present
        TestUtils.setupOptions(args, TestUtils.createOptions("-w", ""));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            var mockedGlaEngine = mockStatic(GLAEngine.class);

            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute
            int result = command.execute(args);

            // Verify - should execute successfully with class weights
            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithHeuristicCapturesConfig() throws Exception {
        // Setup - heuristic=2 (stochastic relaxation) from CLI switch -r
        TestUtils.setupOptions(args, TestUtils.createOptions("-r", "2"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.glaSr(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Capture GLAConfig parameter
            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            // Execute - default 5 bootstrap trials will run
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify - should call glaSr with heuristic=2 from CLI switch
            // (called once per trial)
            mockedGlaEngine.verify(() -> GLAEngine.glaSr(any(), any(), any(),
                    any(), configCaptor.capture()), times(5));

            GLAConfig capturedConfig = configCaptor.getAllValues().get(0);
            assertEquals(2, capturedConfig.heuristic());
        }
    }

    @Test
    void testExecuteWithCentroidTypeCapturesConfig() throws Exception {
        // Setup - centroid type=3 from CLI switch -c
        TestUtils.setupOptions(args, TestUtils.createOptions("-c", "3"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Capture GLAConfig parameter
            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            // Execute
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify - should call GLA with centroidType=3 from CLI switch
            // (called once per trial)
            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()), times(5));

            GLAConfig capturedConfig = configCaptor.getAllValues().get(0);
            assertEquals(3, capturedConfig.centroidType());
        }
    }

    private VectorSet createMockVectorSet(int nVectors, int length) {
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
}
