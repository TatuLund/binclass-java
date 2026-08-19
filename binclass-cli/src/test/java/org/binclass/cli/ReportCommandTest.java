package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.report.ReportGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

/**
 * Unit tests for ReportCommand to verify report generation functionality.
 */
class ReportCommandTest {

    private ReportCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new ReportCommand();
        args = TestUtils.createTestArgs("test");
    }

    @Test
    void testExecuteWithDefaultParameters() throws Exception {
        // Setup - default parameters
        args.setOptions(new HashMap<>());

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedReportGen = mockStatic(ReportGenerator.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));

            // Capture ReportGenerator parameters
            ArgumentCaptor<Partition> partitionCaptor = ArgumentCaptor
                    .forClass(Partition.class);
            ArgumentCaptor<InfiniteCentroids> centroidsCaptor = ArgumentCaptor
                    .forClass(InfiniteCentroids.class);

            when(ReportGenerator.generateReport(any(), any()))
                    .thenReturn("Test Report");

            // Execute
            int result = command.execute(args);

            // Verify - should execute successfully
            assertEquals(0, result);

            // Verify ReportGenerator was called with correct parameters
            mockedReportGen.verify(() -> ReportGenerator.generateReport(
                    partitionCaptor.capture(), centroidsCaptor.capture()));
        }
    }

    @Test
    void testExecuteWithEpsilonParameter() throws Exception {
        // Setup - with epsilon parameter
        Map<String, String> opts = new HashMap<>();
        opts.put("-E", "0.1");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedReportGen = mockStatic(ReportGenerator.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));
            when(ReportGenerator.generateReport(any(), any()))
                    .thenReturn("Test Report");

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithClassWeights() throws Exception {
        // Setup - with class weights flag
        Map<String, String> opts = new HashMap<>();
        opts.put("-w", "");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedReportGen = mockStatic(ReportGenerator.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));
            when(ReportGenerator.generateReport(any(), any()))
                    .thenReturn("Test Report");

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithPrintDigits() throws Exception {
        // Setup - with print digits flag
        Map<String, String> opts = new HashMap<>();
        opts.put("-d", "");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedReportGen = mockStatic(ReportGenerator.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));
            when(ReportGenerator.generateReport(any(), any()))
                    .thenReturn("Test Report");

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithAffinityMatrix() throws Exception {
        // Setup - with affinity matrix flag
        Map<String, String> opts = new HashMap<>();
        opts.put("-a", "");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedReportGen = mockStatic(ReportGenerator.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));
            when(ReportGenerator.generateReport(any(), any()))
                    .thenReturn("Test Report");

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithHellingerDistance() throws Exception {
        // Setup - with Hellinger distance flag
        Map<String, String> opts = new HashMap<>();
        opts.put("-h", "");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedReportGen = mockStatic(ReportGenerator.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));
            when(ReportGenerator.generateReport(any(), any()))
                    .thenReturn("Test Report");

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithLogCentroids() throws Exception {
        // Setup - with log centroids flag
        Map<String, String> opts = new HashMap<>();
        opts.put("-l", "");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedReportGen = mockStatic(ReportGenerator.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));
            when(ReportGenerator.generateReport(any(), any()))
                    .thenReturn("Test Report");

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithAllParameters() throws Exception {
        // Setup - with all parameters
        Map<String, String> opts = new HashMap<>();
        opts.put("-E", "0.1");
        opts.put("-w", "");
        opts.put("-d", "");
        opts.put("-a", "");
        opts.put("-h", "");
        opts.put("-l", "");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedReportGen = mockStatic(ReportGenerator.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));
            when(ReportGenerator.generateReport(any(), any()))
                    .thenReturn("Test Report");

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithInvalidEpsilon() throws Exception {
        // Setup - with invalid epsilon (>= 0.5)
        Map<String, String> opts = new HashMap<>();
        opts.put("-E", "0.6");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));

            // Execute - should throw IllegalArgumentException for invalid
            // epsilon
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                    () -> command.execute(args),
                    "Invalid epsilon (>= 0.5) should throw exception");
        }
    }

    @Test
    void testExecuteWithNonNumericEpsilon() throws Exception {
        // Setup - with non-numeric epsilon
        Map<String, String> opts = new HashMap<>();
        opts.put("-E", "abc");
        args.setOptions(opts);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(4, 8));

            org.junit.jupiter.api.Assertions.assertThrows(Exception.class,
                    () -> command.execute(args),
                    "Non-numeric epsilon should throw exception");
        }
    }
}
