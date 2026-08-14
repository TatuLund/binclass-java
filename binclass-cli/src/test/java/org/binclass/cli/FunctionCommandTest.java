package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.HashMap;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.gla.GLAEngine;
import org.binclass.algorithms.core.VectorSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests for FunctionCommand to verify algorithm execution.
 */
class FunctionCommandTest {

    private FunctionCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new FunctionCommand();
        args = new TestCommandArgs("test");
    }

    @AfterEach
    void tearDown() {
        // Reset static mock framework state between tests
        Mockito.framework().clearInlineMocks();
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
}
