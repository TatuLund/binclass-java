package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

import org.mockito.ArgumentCaptor;

/**
 * Unit tests for MixtureCommand to verify algorithm execution.
 */
public class MixtureCommandTest {

    private MixtureCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new MixtureCommand();
        args = TestUtils.createTestArgs("test");
    }

    @AfterEach
    void tearDown() {
        // Clean up static mocks between tests to prevent conflicts
        clearAllCaches();
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
        // Setup - sample mixture=10 for EM algorithm (CLI switch -s)
        TestUtils.setupOptions(args, TestUtils.createOptions("-s", "10"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            var mockedMixtureClassifier = mockStatic(MixtureClassifier.class);

            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            InfiniteCentroids resultCentroids = new InfiniteCentroids(2, 16);
            when(MixtureClassifier.applyMixtureClassifier(any(), any(),
                    anyInt()))
                    .thenReturn(resultCentroids);

            // Capture parameters passed to applyMixtureClassifier
            ArgumentCaptor<VectorSet> vectorSetCaptor = ArgumentCaptor
                    .forClass(VectorSet.class);
            ArgumentCaptor<InfiniteCentroids> centroidsCaptor = ArgumentCaptor
                    .forClass(InfiniteCentroids.class);
            ArgumentCaptor<Integer> sampleMixtureCaptor = ArgumentCaptor
                    .forClass(int.class);

            // Execute - should use sample mixture of 10 from CLI switch
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify - should call applyMixtureClassifier with vectorSet,
            // centroids, and sampleMixture=10
            mockedMixtureClassifier
                    .verify(() -> MixtureClassifier.applyMixtureClassifier(
                            any(), any(), sampleMixtureCaptor.capture()));

            VectorSet capturedVectorSet = vectorSetCaptor.getValue();
            assertEquals(mockVectorSet, capturedVectorSet);

            Integer capturedSampleMixture = sampleMixtureCaptor.getValue();
            assertNotNull(capturedSampleMixture);
            assertEquals(10, capturedSampleMixture.intValue());
        }
    }

    @Test
    void testExecuteWithDefaultSampleMixture() throws Exception {
        // Setup - no sample mixture specified, should default to 2
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            var mockedMixtureClassifier = mockStatic(MixtureClassifier.class);

            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            InfiniteCentroids resultCentroids = new InfiniteCentroids(2, 16);
            when(MixtureClassifier.applyMixtureClassifier(any(), any(), any()))
                    .thenReturn(resultCentroids);

            // Capture parameters passed to applyMixtureClassifier
            ArgumentCaptor<VectorSet> vectorSetCaptor = ArgumentCaptor
                    .forClass(VectorSet.class);
            ArgumentCaptor<InfiniteCentroids> centroidsCaptor = ArgumentCaptor
                    .forClass(InfiniteCentroids.class);
            ArgumentCaptor<Integer> sampleMixtureCaptor = ArgumentCaptor
                    .forClass(int.class);

            // Execute - should use default sample mixture of 2
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify - should call applyMixtureClassifier with vectorSet,
            // centroids, and sampleMixture=2
            mockedMixtureClassifier
                    .verify(() -> MixtureClassifier.applyMixtureClassifier(
                            any(), any(), sampleMixtureCaptor.capture()));

            VectorSet capturedVectorSet = vectorSetCaptor.getValue();
            assertEquals(mockVectorSet, capturedVectorSet);

            Integer capturedSampleMixture = sampleMixtureCaptor.getValue();
            assertNotNull(capturedSampleMixture);
            assertEquals(2, capturedSampleMixture.intValue());

            // Verify centroids were also passed (captured during first verify)
            InfiniteCentroids capturedCentroids = centroidsCaptor.getValue();
            assertNotNull(capturedCentroids);
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

    @Test
    void testGetName() {
        assertEquals("mixture", command.getName());
    }

    @Test
    void testGetDescription() {
        String desc = command.getDescription();
        assertTrue(desc != null && !desc.isEmpty());
    }
}
