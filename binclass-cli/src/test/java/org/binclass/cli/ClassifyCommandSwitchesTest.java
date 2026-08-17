package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for CLI switches to verify they are properly handled.
 */
class ClassifyCommandSwitchesTest {

    private ClassifyCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new ClassifyCommand();
        args = TestUtils.createTestArgs("test");
    }

    @Test
    void testDumpFileParameter() throws Exception {
        // Setup - with dump file parameter
        Map<String, String> opts = new HashMap<>();
        opts.put("-d", "dumpfile.txt");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testCentroidFileParameter() throws Exception {
        // Setup - with centroid file parameter
        Map<String, String> opts = new HashMap<>();
        opts.put("-L", "centroids.txt");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testBothDumpAndCentroidFiles() throws Exception {
        // Setup - with both dump and centroid file parameters
        Map<String, String> opts = new HashMap<>();
        opts.put("-d", "dumpfile.txt");
        opts.put("-L", "centroids.txt");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testDumpFileWithInvalidPath() throws Exception {
        // Setup - with invalid dump file path (no dump handling implemented
        // yet)
        Map<String, String> opts = new HashMap<>();
        opts.put("-d", "/invalid/path/dump.txt");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));

            int result = command.execute(args);
            assertEquals(0, result); // No dump file handling implemented yet
        }
    }

    @Test
    void testCentroidFileWithInvalidPath() throws Exception {
        // Setup - with invalid centroid file path
        Map<String, String> opts = new HashMap<>();
        opts.put("-L", "/invalid/path/centroids.txt");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));

            int result = command.execute(args);
            assertEquals(1, result); // Returns error code for invalid path
        }
    }
}
