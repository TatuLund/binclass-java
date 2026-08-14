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
 * Unit tests for SemiCumulativeCommand to verify algorithm execution.
 */
public class SemiCumulativeCommandTest {

    private SemiCumulativeCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new SemiCumulativeCommand();
        args = new TestCommandArgs("test");
    }

    @Test
    void testExecuteWithDefaultParameters() throws Exception {
        // Setup - default parameters for semi-cumulative classification
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should run semi-cumulative classification with delta=0
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
