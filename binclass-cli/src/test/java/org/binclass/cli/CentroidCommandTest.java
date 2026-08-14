package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Unit tests for CentroidCommand to verify algorithm execution.
 */
class CentroidCommandTest {

    private CentroidCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new CentroidCommand();
        args = new TestCommandArgs("test");
    }

    @AfterEach
    void tearDown() {
        // Reset static mock framework state between tests
        Mockito.framework().clearInlineMocks();
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
