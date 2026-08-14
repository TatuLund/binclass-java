package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearAllCaches;
import static org.mockito.Mockito.eq;

import org.binclass.algorithms.classify.Classifier;
import org.binclass.algorithms.core.VectorSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for IdentifyCommand to verify classifier execution.
 */
public class IdentifyCommandTest {

    private IdentifyCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new IdentifyCommand();
        args = TestUtils.createTestArgs("test");
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up static mocks between tests to prevent conflicts
        clearAllCaches();
    }

    @Test
    void testExecuteWithDefaultParameters() throws Exception {
        // Setup - default distance type is 1 (Shannon codelength)
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            var mockedClassifier = mockStatic(Classifier.class);

            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute
            int result = command.execute(args);

            // Verify - should call identifyVectors (Shannon codelength) by
            // default with epsilon=0.001
            assertEquals(0, result);
            mockedClassifier.verify(() -> Classifier.identifyVectors(any(),
                    any(), any(), eq(0.001)));
        }
    }

    @Test
    void testExecuteWithEpsilon() throws Exception {
        // Setup - epsilon=0.01 with default distance type 1 (Shannon)
        TestUtils.setupOptions(args, TestUtils.createOptions("-E", "0.01"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            var mockedClassifier = mockStatic(Classifier.class);

            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute
            int result = command.execute(args);

            // Verify - should call identifyVectors with epsilon=0.01
            assertEquals(0, result);
            mockedClassifier.verify(() -> Classifier.identifyVectors(any(),
                    any(), any(), eq(0.01)));
        }
    }

    @Test
    void testExecuteWithHammingDistance() throws Exception {
        // Setup - distance type 2 (Hamming) should call identifyVectorsFast
        TestUtils.setupOptions(args, TestUtils.createOptions("-f", "2"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            var mockedClassifier = mockStatic(Classifier.class);

            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute
            int result = command.execute(args);

            // Verify - should call identifyVectorsFast (Hamming distance)
            assertEquals(0, result);
            mockedClassifier.verify(
                    () -> Classifier.identifyVectorsFast(any(), any(), any()));
        }
    }

    @Test
    void testExecuteWithJeffreysPrior() throws Exception {
        // Setup - Jeffreys prior flag present
        TestUtils.setupOptions(args, TestUtils.createOptions("-J", ""));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            var mockedClassifier = mockStatic(Classifier.class);

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
            var mockedClassifier = mockStatic(Classifier.class);

            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute
            int result = command.execute(args);

            // Verify - should execute successfully with class weights
            assertEquals(0, result);
        }
    }
}
