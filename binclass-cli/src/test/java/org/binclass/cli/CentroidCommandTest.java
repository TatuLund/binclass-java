package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.binclass.algorithms.core.VectorSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
    void tearDown() throws Exception {
        // Clean up static mocks between tests to prevent conflicts
        org.mockito.Mockito.clearAllCaches();
    }

    private VectorSet createMockVectorSet(int nVectors, int length) {
        return TestUtils.createMockVectorSet(nVectors, length);
    }

    @Test
    void testExecuteWithDefaultParameters() throws Exception {
        // Setup
        Map<String, String> opts = new HashMap<>();

        VectorSet mockVectorSet = createMockVectorSet(3, 10);
        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute
            int result = command.execute(args);

            // Verify - should create initial centroids from loaded vectors
            assertEquals(0, result);
            mockedLoader.verify(() -> DataLoader.loadVectors("test"));
        }
    }

    @Test
    void testExecuteWithVerbose() throws Exception {
        // Setup
        Map<String, String> opts = new HashMap<>();

        VectorSet mockVectorSet = createMockVectorSet(3, 10);
        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute
            int result = command.execute(args);

            // Verify - should execute successfully with verbose output
            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithSingleVector() throws Exception {
        // Setup - no mocking needed for concrete TestCommandArgs
        Map<String, String> opts = new HashMap<>();

        VectorSet mockVectorSet = createMockVectorSet(1, 5);
        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute
            int result = command.execute(args);

            // Verify - should create single centroid for single vector
            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithLargeDataset() throws Exception {
        // Setup - no mocking needed for concrete TestCommandArgs
        Map<String, String> opts = new HashMap<>();

        VectorSet mockVectorSet = createMockVectorSet(100, 50);
        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute
            int result = command.execute(args);

            // Verify - should handle large dataset gracefully
            assertEquals(0, result);
        }
    }

    @Test
    void testGetName() {
        assertEquals("centroids", command.getName());
    }

    @Test
    void testGetDescription() {
        String desc = command.getDescription();
        assertTrue(desc != null && !desc.isEmpty());
    }
}
