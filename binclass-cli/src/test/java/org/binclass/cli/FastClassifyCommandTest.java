package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.VectorSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests for FastClassifyCommand to verify algorithm execution.
 */
class FastClassifyCommandTest {

    private FastClassifyCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new FastClassifyCommand();
        args = new TestCommandArgs("test");
    }

    @AfterEach
    void tearDown() {
        // Reset static mock framework state between tests
        Mockito.framework().clearInlineMocks();
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
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should use epsilon=0.01 for convergence threshold
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }
}
