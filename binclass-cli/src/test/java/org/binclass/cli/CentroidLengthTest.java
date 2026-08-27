package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.gla.GLAEngine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests to verify centroid length is correctly derived from VectorSet.
 */
class CentroidLengthTest {

    private ClassifyCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new ClassifyCommand();
        args = TestUtils.createTestArgs("test");
    }

    @Test
    void testCentroidLengthMatchesVectorSet47Bits() throws Exception {
        // Setup - create vectors with length 47 (like entero dataset)
        int vectorLength = 47;
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3,
                vectorLength);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);

            // Capture the InfiniteCentroids passed to GLA
            org.mockito.ArgumentCaptor<InfiniteCentroids> centroidsCaptor = org.mockito.ArgumentCaptor
                    .forClass(InfiniteCentroids.class);

            when(GLAEngine.gla(any(), any(), centroidsCaptor.capture(), any(),
                    any()))
                    .thenReturn(resultPartition);

            // Execute - should complete without IndexOutOfBoundsException
            int result = command.execute(args);

            // Verify successful execution (no centroid length mismatch error)
            assertEquals(0, result);

            // Verify GLA was called with correct parameters. The forward scan
            // invokes GLA once per cluster count, so at least one call is
            // expected.
            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(),
                    centroidsCaptor.capture(), any(), any()), atLeastOnce());

            // CRITICAL: Verify the captured centroids have the same length as
            // the vector set
            InfiniteCentroids capturedCentroids = centroidsCaptor.getValue();
            assertEquals(vectorLength, capturedCentroids.get(0).getLength(),
                    "Centroid length should match vector set length");
        }
    }

    @Test
    void testCentroidLengthMatchesVectorSet16Bits() throws Exception {
        // Setup - create vectors with length 16 (default)
        int vectorLength = 16;
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3,
                vectorLength);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);

            ArgumentCaptor<InfiniteCentroids> centroidsCaptor = ArgumentCaptor
                    .forClass(InfiniteCentroids.class);

            when(GLAEngine.gla(any(), any(), centroidsCaptor.capture(), any(),
                    any()))
                    .thenReturn(resultPartition);

            // Execute - should complete successfully
            int result = command.execute(args);

            // Verify successful execution
            assertEquals(0, result);

            // CRITICAL: Verify the captured centroids have the same length as
            // the vector set
            InfiniteCentroids capturedCentroids = centroidsCaptor.getValue();
            assertEquals(vectorLength, capturedCentroids.get(0).getLength(),
                    "Centroid length should match vector set length");
        }
    }

    @Test
    void testCentroidLengthMatchesVectorSet100Bits() throws Exception {
        // Setup - create vectors with length 100 (large dataset)
        int vectorLength = 100;
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3,
                vectorLength);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);

            ArgumentCaptor<InfiniteCentroids> centroidsCaptor = ArgumentCaptor
                    .forClass(InfiniteCentroids.class);

            when(GLAEngine.gla(any(), any(), centroidsCaptor.capture(), any(),
                    any()))
                    .thenReturn(resultPartition);

            // Execute - should complete successfully
            int result = command.execute(args);

            // Verify successful execution
            assertEquals(0, result);

            // CRITICAL: Verify the captured centroids have the same length as
            // the vector set
            InfiniteCentroids capturedCentroids = centroidsCaptor.getValue();
            assertEquals(vectorLength, capturedCentroids.get(0).getLength(),
                    "Centroid length should match vector set length");
        }
    }

    @Test
    void testCentroidLengthMatchesVectorSet1Bit() throws Exception {
        // Setup - create vectors with length 1 (minimum)
        int vectorLength = 1;
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3,
                vectorLength);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);

            ArgumentCaptor<InfiniteCentroids> centroidsCaptor = ArgumentCaptor
                    .forClass(InfiniteCentroids.class);

            when(GLAEngine.gla(any(), any(), centroidsCaptor.capture(), any(),
                    any()))
                    .thenReturn(resultPartition);

            // Execute - should complete successfully
            int result = command.execute(args);

            // Verify successful execution
            assertEquals(0, result);

            // CRITICAL: Verify the captured centroids have the same length as
            // the vector set
            InfiniteCentroids capturedCentroids = centroidsCaptor.getValue();
            assertEquals(vectorLength, capturedCentroids.get(0).getLength(),
                    "Centroid length should match vector set length");
        }
    }

    @Test
    void testCentroidLengthMatchesVectorSet5313Bits() throws Exception {
        // Setup - create vectors with length 5313 (matching entero dataset
        // size)
        int vectorLength = 5313;
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3,
                vectorLength);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);

            ArgumentCaptor<InfiniteCentroids> centroidsCaptor = ArgumentCaptor
                    .forClass(InfiniteCentroids.class);

            when(GLAEngine.gla(any(), any(), centroidsCaptor.capture(), any(),
                    any()))
                    .thenReturn(resultPartition);

            // Execute - should complete successfully without
            // IndexOutOfBoundsException
            int result = command.execute(args);

            // Verify successful execution
            assertEquals(0, result);

            // CRITICAL: Verify the captured centroids have the same length as
            // the vector set
            InfiniteCentroids capturedCentroids = centroidsCaptor.getValue();
            assertEquals(vectorLength, capturedCentroids.get(0).getLength(),
                    "Centroid length should match vector set length");
        }
    }
}
