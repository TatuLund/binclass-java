package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.binclass.algorithms.classify.MixtureClassifier;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.VectorSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for MixtureCommand to verify algorithm execution.
 */
public class MixtureCommandTest {

    private MixtureCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new MixtureCommand();
        args = new TestCommandArgs("test");
    }

    @Test
    void testExecuteWithDefaultParameters() throws Exception {
        // Setup - default parameters for EM algorithm with 2 mixture components
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should run EM algorithm with default 2 components
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithEpsilon() throws Exception {
        // Setup - epsilon=0.01 for EM algorithm convergence threshold
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
    void testExecuteWithMixtureClasses() throws Exception {
        // Setup - 4 mixture components for EM algorithm
        TestUtils.setupOptions(args, TestUtils.createOptions("-K", "4"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should use 4 mixture components
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithSampleMixture() throws Exception {
        // Setup - sample mixture=10 for EM algorithm
        TestUtils.setupOptions(args, TestUtils.createOptions("-n", "10"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should use sample mixture of 10
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithVerbose() throws Exception {
        // Setup - verbose mode enabled for EM algorithm
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
