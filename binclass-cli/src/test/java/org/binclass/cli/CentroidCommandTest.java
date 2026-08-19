package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearAllCaches;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.VectorSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

/**
 * Unit tests for CentroidCommand to verify algorithm execution.
 */
class CentroidCommandTest {

    private CentroidCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new CentroidCommand();
        args = TestUtils.createTestArgs("test");
    }

    @AfterEach
    void tearDown() {
        // Clean up static mocks between tests to prevent conflicts
        clearAllCaches();
    }

    private VectorSet createMockVectorSet(int nVectors, int length) {
        return TestUtils.createMockVectorSet(nVectors, length);
    }

    @Test
    void testExecuteWithDefaultParameters() throws Exception {
        // Setup - default parameters for centroid creation
        Map<String, String> opts = new HashMap<>();

        VectorSet mockVectorSet = createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString())).thenReturn(mockVectorSet);

            // Capture InfiniteCentroids parameter passed to centroid creation
            ArgumentCaptor<InfiniteCentroids> centroidsCaptor = ArgumentCaptor
                    .forClass(InfiniteCentroids.class);

            // Execute - should create initial centroids from loaded vectors
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify data loader was called with the correct input file
            mockedLoader.verify(() -> DataLoader.loadVectors("test"));
        }
    }

    @Test
    void testExecuteWithVerbose() throws Exception {
        // Setup - verbose mode enabled
        Map<String, String> opts = new HashMap<>();
        TestUtils.setupOptions(args, TestUtils.createOptions("-v", ""));

        VectorSet mockVectorSet = createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString())).thenReturn(mockVectorSet);

            // Execute - should execute successfully with verbose output
            int result = command.execute(args);

            assertEquals(0, result);
            mockedLoader.verify(() -> DataLoader.loadVectors("test"));
        }
    }

    @Test
    void testExecuteWithFilebase() throws Exception {
        // Setup - custom filebase parameter
        Map<String, String> opts = new HashMap<>();
        opts.put("-f", "custom_filebase");
        args.setOptions(opts);

        VectorSet mockVectorSet = createMockVectorSet(5, 12);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString())).thenReturn(mockVectorSet);

            // Execute - should use custom filebase
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithSingleVector() throws Exception {
        // Setup - single vector (edge case)
        VectorSet mockVectorSet = createMockVectorSet(1, 8);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString())).thenReturn(mockVectorSet);

            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithMultipleCentroids() throws Exception {
        // Setup - multiple vectors to create more centroids
        VectorSet mockVectorSet = createMockVectorSet(10, 16);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString())).thenReturn(mockVectorSet);

            int result = command.execute(args);

            assertEquals(0, result);
            mockedLoader.verify(() -> DataLoader.loadVectors("test"));
        }
    }

    @Test
    void testGetName() {
        assertEquals("centroids", command.getName());
    }

    @Test
    void testGetDescription() {
        String desc = command.getDescription();
        org.junit.jupiter.api.Assertions.assertNotNull(desc);
        org.junit.jupiter.api.Assertions.assertFalse(desc.isEmpty());
    }
}
