package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for CompareCommand validation of -V constraint.
 */
class CompareCommandValidationTest {

    private CompareCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new CompareCommand();
        args = TestUtils.createTestArgs("test");
    }

    @Test
    void testValidNearnessMetric1() throws Exception {
        // Setup - valid nearness metric 1 (Hamming)
        Map<String, String> opts = new HashMap<>();
        opts.put("-V", "1");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testValidNearnessMetric2() throws Exception {
        // Setup - valid nearness metric 2 (Total Frequency)
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
    void testValidNearnessMetric3() throws Exception {
        // Setup - valid nearness metric 3 (Partition)
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
    void testInvalidNearnessMetric0() throws Exception {
        // Setup - invalid nearness metric 0 (below range)
        Map<String, String> opts = new HashMap<>();
        opts.put("-V", "0");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));

            // Execute - should throw IllegalArgumentException for invalid
            // metric
            assertThrows(Exception.class, () -> command.execute(args),
                    "Invalid nearness metric 0 should throw exception");
        }
    }

    @Test
    void testInvalidNearnessMetric4() throws Exception {
        // Setup - invalid nearness metric 4 (above range)
        Map<String, String> opts = new HashMap<>();
        opts.put("-V", "4");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));

            // Execute - should throw IllegalArgumentException for invalid
            // metric
            assertThrows(Exception.class, () -> command.execute(args),
                    "Invalid nearness metric 4 should throw exception");
        }
    }

    @Test
    void testInvalidNearnessMetric5() throws Exception {
        // Setup - invalid nearness metric 5 (above range)
        Map<String, String> opts = new HashMap<>();
        opts.put("-V", "5");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));

            // Execute - should throw IllegalArgumentException for invalid
            // metric
            assertThrows(Exception.class, () -> command.execute(args),
                    "Invalid nearness metric 5 should throw exception");
        }
    }

    @Test
    void testDefaultNearnessMetric() throws Exception {
        // Setup - no -V flag specified (should use default)
        args.setOptions(new HashMap<>());

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }
}
