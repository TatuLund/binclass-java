package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.binclass.algorithms.compare.PartitionComparator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for CompareCommand to verify partition comparison functionality.
 */
class CompareCommandTest {

    private CompareCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new CompareCommand();
        args = TestUtils.createTestArgs("test");
    }

    @Test
    void testExecuteWithValidNearnessMetric1() throws Exception {
        // Setup - use nearness metric 1 (Hamming)
        Map<String, String> opts = new HashMap<>();
        opts.put("-V", "1");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));

            // Execute - should execute successfully with nearness metric 1
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithValidNearnessMetric2() throws Exception {
        // Setup - use nearness metric 2 (total frequency)
        Map<String, String> opts = new HashMap<>();
        opts.put("-V", "2");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithValidNearnessMetric3() throws Exception {
        // Setup - use nearness metric 3 (partition)
        Map<String, String> opts = new HashMap<>();
        opts.put("-V", "3");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithInvalidNearnessMetric() throws Exception {
        // Setup - use invalid nearness metric 4 (should throw)
        Map<String, String> opts = new HashMap<>();
        opts.put("-V", "4");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));

            // Execute - should throw IllegalArgumentException for invalid
            // metric
            assertThrows(Exception.class, () -> command.execute(args),
                    "Invalid nearness metric (4) should throw exception");
        }
    }

    @Test
    void testExecuteWithDefaultNearnessMetric() throws Exception {
        // Setup - no -V flag specified (should use default 1)
        args.setOptions(new HashMap<>());

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));

            // Execute - should succeed with default metric 1
            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithMultipleVectors() throws Exception {
        // Setup - compare with more vectors
        Map<String, String> opts = new HashMap<>();
        opts.put("-V", "1");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(6, 8));

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testGetName() {
        assertEquals("compare", command.getName());
    }

    @Test
    void testGetDescription() {
        String desc = command.getDescription();
        org.junit.jupiter.api.Assertions.assertNotNull(desc);
        org.junit.jupiter.api.Assertions.assertFalse(desc.isEmpty());
    }
}
