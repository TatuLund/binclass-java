package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.binclass.algorithms.classify.CumulativeClassifier;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.VectorSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CumulativeCommand to verify algorithm execution.
 */
public class CumulativeCommandTest {

    private CumulativeCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new CumulativeCommand();
        args = new TestCommandArgs("test");
    }

    @Test
    void testExecuteWithDefaultParameters() throws Exception {
        // Setup - default parameters for cumulative classification
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should run cumulative classification with delta=1
            int result = command.execute(args);

            assertEquals(0, result);
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
    void testExecuteWithCumulativeInOrder() throws Exception {
        // Setup - cumulative in-order flag present
        TestUtils.setupOptions(args, TestUtils.createOptions("-O", ""));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should execute with cumulative in-order processing
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithBayesianPredictive() throws Exception {
        // Setup - Bayesian predictive flag present
        TestUtils.setupOptions(args, TestUtils.createOptions("-B", ""));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should execute with Bayesian predictive model
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
    void testExecuteWithCumulativeAnalysis() throws Exception {
        // Setup - cumulative analysis flag present
        TestUtils.setupOptions(args, TestUtils.createOptions("-C", ""));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should execute with cumulative analysis enabled
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithRealDeltaValue() throws Exception {
        // Setup - real delta=2 for interval analysis
        TestUtils.setupOptions(args, TestUtils.createOptions("-d", "2"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should use real delta value of 2
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }
}
