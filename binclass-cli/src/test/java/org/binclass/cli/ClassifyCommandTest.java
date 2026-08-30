package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.binclass.algorithms.centroid.CentroidInitializer;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.GLAEngine;
import org.binclass.algorithms.gla.LocalSearch;
import org.binclass.algorithms.gla.SearchType;
import org.binclass.algorithms.util.MathUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Unit tests for ClassifyCommand to verify algorithm execution.
 */
class ClassifyCommandTest {

    private ClassifyCommand command;
    private TestCommandArgs args;

    private static final int N_VECTORS = 3;
    private static final int VECTOR_LENGTH = 10;

    @BeforeEach
    void setUp() {
        command = new ClassifyCommand();
        args = TestUtils.createTestArgs("test");
    }

    @Test
    void testExecuteWithDefaultParameters() throws Exception {
        // Setup - default heuristic is 1 (standard GLA)
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Capture GLAConfig parameter
            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            // Execute
            int result = command.execute(args);

            // Verify - should execute successfully with default parameters
            assertEquals(0, result);

            // Verify data loader was called with the correct input file
            mockedLoader.verify(() -> DataLoader.loadVectors("test"));
            // Verify GLA was called and capture the config parameter
            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()), atLeastOnce());

            // Verify GLAConfig has correct default values
            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(0.001, capturedConfig.epsilon()); // Default epsilon
            assertEquals(1.8, capturedConfig.pnnThreshold()); // Default
                                                              // pnnThreshold
            assertEquals(1, capturedConfig.heuristic()); // Default heuristic
                                                         // (standard)
            assertEquals(N_VECTORS, capturedConfig.n()); // n = number of
                                                         // vectors in
                                                         // mockVectorSet
        }
    }

    @Test
    void testExecuteWithHeuristic1() throws Exception {
        // Setup - heuristic 1 = standard Shannon codelength
        TestUtils.setupOptions(args, TestUtils.createOptions("-r", "1"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            // Execute
            int result = command.execute(args);

            // Verify - should execute successfully with heuristic 1
            assertEquals(0, result);

            // Verify GLA was called and capture the config parameter
            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()), atLeastOnce());

            // Verify GLAConfig has correct heuristic value
            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(1, capturedConfig.heuristic());
        }
    }

    @Test
    void testExecuteWithHeuristic2() throws Exception {
        // Setup - heuristic 2 = stochastic relaxation
        TestUtils.setupOptions(args, TestUtils.createOptions("-r", "2"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.glaSr(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            // Execute
            int result = command.execute(args);

            // Verify - should execute successfully with heuristic 2
            assertEquals(0, result);

            // Verify GLASr was called and capture the config parameter
            mockedGlaEngine.verify(() -> GLAEngine.glaSr(any(), any(), any(),
                    any(), configCaptor.capture()), atLeastOnce());

            // Verify GLAConfig has correct heuristic value
            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(2, capturedConfig.heuristic());
        }
    }

    @Test
    void testExecuteWithHeuristic3() throws Exception {
        // Setup - heuristic 3 = simulated annealing
        TestUtils.setupOptions(args, TestUtils.createOptions("-r", "3"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.glaSa(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            // Execute
            int result = command.execute(args);

            // Verify - should execute successfully with heuristic 3
            assertEquals(0, result);

            // Verify GLASa was called and capture the config parameter
            mockedGlaEngine.verify(() -> GLAEngine.glaSa(any(), any(), any(),
                    any(), configCaptor.capture()), atLeastOnce());

            // Verify GLAConfig has correct heuristic value
            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(3, capturedConfig.heuristic());
        }
    }

    @Test
    void testExecuteWithHeuristic4() throws Exception {
        // Setup - heuristic 4 = hybrid L1
        TestUtils.setupOptions(args, TestUtils.createOptions("-r", "4"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.hybridGlaL1(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            // Execute
            int result = command.execute(args);

            // Verify - should execute successfully with heuristic 4
            assertEquals(0, result);

            // Verify hybridGlaL1 was called and capture the config parameter
            mockedGlaEngine.verify(() -> GLAEngine.hybridGlaL1(any(), any(),
                    any(), any(), configCaptor.capture()), atLeastOnce());

            // Verify GLAConfig has correct heuristic value
            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(4, capturedConfig.heuristic());
        }
    }

    @Test
    void testExecuteWithHeuristic5() throws Exception {
        // Setup - heuristic 5 = hybrid L2
        TestUtils.setupOptions(args, TestUtils.createOptions("-r", "5"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.hybridGlaL2(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            // Execute
            int result = command.execute(args);

            // Verify - should execute successfully with heuristic 5
            assertEquals(0, result);

            // Verify hybridGlaL2 was called and capture the config parameter
            mockedGlaEngine.verify(() -> GLAEngine.hybridGlaL2(any(), any(),
                    any(), any(), configCaptor.capture()), atLeastOnce());

            // Verify GLAConfig has correct heuristic value
            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(5, capturedConfig.heuristic());
        }
    }

    @Test
    void testExecuteWithHeuristic6() throws Exception {
        // Setup - heuristic 6 = MAE
        TestUtils.setupOptions(args, TestUtils.createOptions("-r", "6"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.maeGla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            // Execute
            int result = command.execute(args);

            // Verify - should execute successfully with heuristic 6
            assertEquals(0, result);

            // Verify maeGla was called and capture the config parameter
            mockedGlaEngine.verify(() -> GLAEngine.maeGla(any(), any(), any(),
                    any(), configCaptor.capture()), atLeastOnce());

            // Verify GLAConfig has correct heuristic value
            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(6, capturedConfig.heuristic());
        }
    }

    @Test
    void testExecuteWithKstartAndKstop() throws Exception {
        TestUtils.setupOptions(args,
                TestUtils.createOptions("-b", "2", "-s", "5"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);
        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            int result = command.execute(args);
            assertEquals(0, result);

            // Verify GLA was called at least once and capture the config
            // parameter. With mocks returning same partition, convergence
            // triggers early after 2 iterations (k=2 updates bestPartition,
            // k=3 finds no improvement and converges). The forward scan
            // invokes GLA once per cluster count, so at least one call is
            // expected.
            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()), atLeastOnce());

            // Verify kcStopWhen is set correctly in GLAConfig (hardcoded to 5)
            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(5, capturedConfig.kcStopWhen());
        }
    }

    @Test
    void testExecuteWithEpsilon() throws Exception {
        TestUtils.setupOptions(args, TestUtils.createOptions("-E", "0.1"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            int result = command.execute(args);

            // Verify GLA was called and capture the config parameter
            assertEquals(0, result);
            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()), atLeastOnce());

            // Verify epsilon is set correctly in GLAConfig
            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(0.1, capturedConfig.epsilon());
        }
    }

    @Test
    void testExecuteWithVerboseMode() throws Exception {
        TestUtils.setupOptions(args, new HashMap<>());
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);
        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            int result = command.execute(args);
            assertEquals(0, result);

            // Verify GLA was called and capture the config parameter
            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()), atLeastOnce());

            // Verify default values are preserved in verbose mode
            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(0.001, capturedConfig.epsilon());
            assertEquals(N_VECTORS, capturedConfig.n());
        }
    }

    @Test
    void testExecuteWithQuietMode() throws Exception {
        TestUtils.setupOptions(args, TestUtils.createOptions("-q", ""));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);
        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            int result = command.execute(args);
            assertEquals(0, result);

            // Verify GLA was called and capture the config parameter
            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()), atLeastOnce());

            // Verify default values are preserved in quiet mode
            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(0.001, capturedConfig.epsilon());
            assertEquals(N_VECTORS, capturedConfig.n());
        }
    }

    @Test
    void testExecuteWithDistanceType() throws Exception {
        TestUtils.setupOptions(args, TestUtils.createOptions("-f", "2"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);
        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            int result = command.execute(args);
            assertEquals(0, result);

            // Verify GLA was called and capture the config parameter
            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()), atLeastOnce());

            // Verify default values are preserved with distance type 2
            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(0.001, capturedConfig.epsilon());
            assertEquals(N_VECTORS, capturedConfig.n());
        }
    }

    @Test
    void testExecuteWithMaxIter() throws Exception {
        TestUtils.setupOptions(args, TestUtils.createOptions("-n", "50"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);
        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            int result = command.execute(args);
            assertEquals(0, result);

            // Verify GLA was called and capture the config parameter
            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()));

            // Verify maxIter is set correctly in GLAConfig
            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(50, capturedConfig.maxIter());
        }
    }

    @Test
    void testExecuteWithCentroidType() throws Exception {
        TestUtils.setupOptions(args, TestUtils.createOptions("-c", "2"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);
        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            int result = command.execute(args);
            assertEquals(0, result);

            // Verify GLA was called and capture the config parameter
            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()), atLeastOnce());

            // Verify centroidType is set correctly in GLAConfig
            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(2, capturedConfig.centroidType());
        }
    }

    @Test
    void testExecuteWithAllOptions() throws Exception {
        TestUtils.setupOptions(args, TestUtils.createOptions(
                "-b", "3", "-s", "10", "-S", "5", "-E", "0.05",
                "-r", "4", "-t", "", "-e", "2", "-m", "",
                "-c", "3", "-l", "", "-J", "", "-w", "",
                "-B", "0.5", "-C", "", "-R", "", "-f", "2",
                "-n", "100", "-j", "3", "-F", "200", "-a", "5",
                "-d", "dump.txt", "-L", "centroids.dat"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);
        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            Partition resultPartition = new Partition(1);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);
            when(GLAEngine.hybridGlaL1(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);

            int result = command.execute(args);
            assertEquals(0, result);

            // Verify hybridGlaL1 was called (heuristic 4) and capture the
            // config parameter
            mockedGlaEngine.verify(() -> GLAEngine.hybridGlaL1(any(), any(),
                    any(), any(), configCaptor.capture()), atLeastOnce());

            // Verify all options are correctly set in GLAConfig
            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(0.05, capturedConfig.epsilon());
            assertEquals(4, capturedConfig.heuristic());
            assertEquals(3, capturedConfig.centroidType());
            assertEquals(100, capturedConfig.maxIter());
        }
    }

    @Test
    void testExecuteWithInvalidKstart() {
        // Setup - no mocking needed for concrete TestCommandArgs
        Map<String, String> opts = new HashMap<>();
        opts.put("-b", "abc");
        TestUtils.setupOptions(args, opts);

        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);
        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should throw IllegalArgumentException
            assertThrows(IllegalArgumentException.class,
                    () -> command.execute(args));
        }
    }

    @Test
    void testExecuteWithInvalidEpsilon() {
        Map<String, String> opts = new HashMap<>();
        opts.put("-E", "0.6");
        TestUtils.setupOptions(args, opts);

        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);
        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should throw IllegalArgumentException
            assertThrows(IllegalArgumentException.class,
                    () -> command.execute(args));
        }
    }

    @Test
    void testGetName() {
        assertEquals("classify", command.getName());
    }

    @Test
    void testGetDescription() {
        String desc = command.getDescription();
        assertTrue(desc != null && !desc.isEmpty());
    }

    @Test
    void testLogFactorialsDirectInitialization() throws Exception {
        // Directly test prepareLog2Factorials without going through command
        // execution
        java.lang.reflect.Field field = MathUtils.class
                .getDeclaredField("LOG2_FACTORIALS");
        field.setAccessible(true);
        field.set(null, null);

        // Call prepareLog2Factorials directly (mirrors C code pattern)
        int n = N_VECTORS;
        double[] result = MathUtils.prepareLog2Factorials((n + n));

        assertNotNull(result,
                "prepareLog2Factorials should return non-null array");
        assertEquals(6, result.length, "Array length should be 2*n=6");

        // Verify known values: log2(n!) for small n
        assertEquals(0.0, result[0], 1e-10); // log2(0!) = log2(1) = 0
        assertEquals(0.0, result[1], 1e-10); // log2(1!) = log2(1) = 0
        assertEquals(Math.log(2) / Math.log(2), result[2], 1e-10); // log2(2!) =
                                                                   // log2(2) =
                                                                   // 1
        assertEquals(Math.log(6) / Math.log(2), result[3], 1e-10); // log2(3!) =
                                                                   // log2(6) ≈
                                                                   // 2.585

        // Verify the static field is also populated
        double[] storedArray = (double[]) field.get(null);
        assertNotNull(storedArray, "LOG2_FACTORIALS should be populated");
    }

    // ------------------------------------------------------------------
    // Phase 4 gaps: G4 (-j heuristicCount), G9 (methods summary),
    // G13 (requireBetter/filterExactK) and the -r7/-r8 local-search fix.
    // ------------------------------------------------------------------

    @Test
    void testExecuteWithHeuristicCount() throws Exception {
        // -j X sets heuristicCount = X + 1 (mirrors C ls_heuristic_count).
        TestUtils.setupOptions(args, TestUtils.createOptions("-n", "2",
                "-j", "5"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(new Partition(1));

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);
            command.execute(args);

            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()));
            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(6, capturedConfig.heuristicCount(),
                    "-j 5 must map to heuristicCount == 6");
        }
    }

    @Test
    void testExecuteRequireBetterFlag() throws Exception {
        TestUtils.setupOptions(args,
                TestUtils.createOptions("-n", "2", "-B", "0"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(new Partition(1));

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);
            command.execute(args);

            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()));
            GLAConfig capturedConfig = configCaptor.getValue();
            assertTrue(capturedConfig.requireBetter(),
                    "-B must set requireBetter == true");
        }
    }

    @Test
    void testExecuteRequireBetterDefaultFalse() throws Exception {
        TestUtils.setupOptions(args, TestUtils.createOptions("-n", "2"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(new Partition(1));

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);
            command.execute(args);

            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()));
            GLAConfig capturedConfig = configCaptor.getValue();
            assertFalse(capturedConfig.requireBetter(),
                    "requireBetter defaults to false without -B");
        }
    }

    @Test
    void testExecuteFilterExactKDefaultFalse() throws Exception {
        TestUtils.setupOptions(args, TestUtils.createOptions("-n", "2"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(new Partition(1));

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);
            command.execute(args);

            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()));
            GLAConfig capturedConfig = configCaptor.getValue();
            assertFalse(capturedConfig.filterExactK(),
                    "filterExactK defaults to false");
        }
    }

    @Test
    void testExecuteWithBestCodeLength() throws Exception {
        TestUtils.setupOptions(args,
                TestUtils.createOptions("-n", "2", "-C", "0"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(new Partition(1));

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);
            command.execute(args);

            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()));
            GLAConfig capturedConfig = configCaptor.getValue();
            assertTrue(capturedConfig.bestCodeLength(),
                    "-C must set bestCodeLength == true");
        }
    }

    @Test
    void testDetermineSearchTypeNAuto() throws Exception {
        SearchType st = callDetermineSearchType(Map.of("-n", "10"));
        assertEquals(SearchType.NAUTO, st);
    }

    @Test
    void testDetermineSearchTypeLCent() throws Exception {
        // -L selects loaded centroids only when -n is absent.
        SearchType st = callDetermineSearchType(Map.of("-L", "centroids.txt"));
        assertEquals(SearchType.LCENT, st);
    }

    @Test
    void testDetermineSearchTypeNAutoTakesPrecedenceOverLCent()
            throws Exception {
        // -n is checked first in determineSearchType(), so both present ->
        // NAUTO.
        SearchType st = callDetermineSearchType(Map.of("-n", "10", "-L",
                "centroids.txt"));
        assertEquals(SearchType.NAUTO, st);
    }

    @Test
    void testDetermineSearchTypeAutoDefault() throws Exception {
        SearchType st = callDetermineSearchType(Map.of());
        assertEquals(SearchType.AUTO, st);
    }

    private SearchType callDetermineSearchType(Map<String, String> opts)
            throws Exception {
        Method m = ClassifyCommand.class.getDeclaredMethod(
                "determineSearchType",
                Map.class);
        m.setAccessible(true);
        return (SearchType) m.invoke(command, opts);
    }

    @Test
    void testExecuteWithHeuristic7RunsLocalSearch() throws Exception {
        // -r7 routes through LocalSearch.localSearch after populating the
        // partition. Force NAUTO so runRangeSearch drives a single GLA step.
        TestUtils.setupOptions(args,
                TestUtils.createOptions("-n", "2", "-r", "7"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class);
                var mockedLocalSearch = mockStatic(LocalSearch.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(new Partition(1));
            when(GLAEngine.partitionToSet(any())).thenReturn(mockVectorSet);
            LocalSearch.localSearch(any(), any(), anyInt(), anyInt(), anyInt(),
                    anyBoolean(), any());

            int result = command.execute(args);
            assertEquals(0, result);
            mockedLocalSearch.verify(() -> LocalSearch.localSearch(any(), any(),
                    anyInt(), anyInt(), anyInt(), anyBoolean(), any()),
                    atLeastOnce());
        }
    }

    @Test
    void testExecuteWithHeuristic8RunsLocalSearch() throws Exception {
        TestUtils.setupOptions(args,
                TestUtils.createOptions("-n", "2", "-r", "8"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class);
                var mockedLocalSearch = mockStatic(LocalSearch.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(new Partition(1));
            when(GLAEngine.partitionToSet(any())).thenReturn(mockVectorSet);
            LocalSearch.localSearch(any(), any(), anyInt(), anyInt(), anyInt(),
                    anyBoolean(), any());

            int result = command.execute(args);
            assertEquals(0, result);
            mockedLocalSearch.verify(() -> LocalSearch.localSearch(any(), any(),
                    anyInt(), anyInt(), anyInt(), anyBoolean(), any()),
                    atLeastOnce());
        }
    }

    @Test
    void testMethodsLogsSummaryBlock() throws Exception {
        TestUtils.setupOptions(args, TestUtils.createOptions("-n", "2", "-r",
                "7", "-c", "3", "-B", "0", "-R", ""));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory
                .getLogger(ClassifyCommand.class);
        ch.qos.logback.classic.Level previousLevel = logger.getEffectiveLevel();
        logger.setLevel(ch.qos.logback.classic.Level.INFO);
        ListAppender<LoggingEvent> appender = new ListAppender<>();
        appender.setContext(logger.getLoggerContext());
        appender.start();
        @SuppressWarnings("unchecked")
        ch.qos.logback.core.Appender<ch.qos.logback.classic.spi.ILoggingEvent> raw = (ch.qos.logback.core.Appender) appender;
        logger.addAppender(raw);

        try {
            try (var mockedLoader = mockStatic(DataLoader.class);
                    var mockedGlaEngine = mockStatic(GLAEngine.class)) {
                mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                        .thenReturn(mockVectorSet);
                when(GLAEngine.gla(any(), any(), any(), any(), any()))
                        .thenReturn(new Partition(1));
                when(GLAEngine.partitionToSet(any())).thenReturn(mockVectorSet);

                command.execute(args);
            }

            List<String> lines = appender.list.stream()
                    .map(e -> e.getFormattedMessage()).toList();
            String joined = String.join("\n", lines);
            assertTrue(joined.contains("Methods:"), "methods summary header");
            assertTrue(joined.contains("Cycling all strategies"),
                    "-r7 cycler line present");
            assertTrue(joined.contains("Semirandom initial centroids"),
                    "-c3 centroid-type line present");
            assertTrue(joined.contains("Better codelength for k+1 required"),
                    "require_better line present");
            assertTrue(joined.contains("Rounded centroids are used"),
                    "rounded-centroids line present");
        } finally {
            @SuppressWarnings("unchecked")
            ch.qos.logback.core.Appender<ch.qos.logback.classic.spi.ILoggingEvent> rawDetach = (ch.qos.logback.core.Appender) appender;
            logger.detachAppender(rawDetach);
            logger.setLevel(previousLevel);
        }
    }

    private Object callInitializePartition(VectorSet vs, int k,
            InfiniteCentroids seed, int type) throws Exception {
        Method m = ClassifyCommand.class.getDeclaredMethod(
                "initializePartition", VectorSet.class, int.class,
                InfiniteCentroids.class, int.class);
        m.setAccessible(true);
        return m.invoke(command, vs, k, seed, type);
    }

    @Test
    void testInitializePartitionCentroidTypeRouting() throws Exception {
        VectorSet vs = TestUtils.createMockVectorSet(N_VECTORS, VECTOR_LENGTH);
        InfiniteCentroids seed = new InfiniteCentroids(N_VECTORS + 1,
                VECTOR_LENGTH);

        try (var mockInit = mockStatic(CentroidInitializer.class)) {
            InfiniteCentroids rRandom = new InfiniteCentroids(2, VECTOR_LENGTH);
            InfiniteCentroids rSemi = new InfiniteCentroids(3, VECTOR_LENGTH);
            InfiniteCentroids rPick = new InfiniteCentroids(4, VECTOR_LENGTH);
            InfiniteCentroids rPnn = new InfiniteCentroids(5, VECTOR_LENGTH);

            mockInit.when(() -> CentroidInitializer.randomInit(anyInt(),
                    anyInt())).thenReturn(rRandom);
            mockInit.when(() -> CentroidInitializer.semiRandomInit(anyInt(),
                    anyInt())).thenReturn(rSemi);
            mockInit.when(() -> CentroidInitializer.pickInit(anyInt(), anyInt(),
                    any(VectorSet.class))).thenReturn(rPick);
            mockInit.when(() -> CentroidInitializer.pnnInit(anyInt(), anyInt(),
                    any(VectorSet.class))).thenReturn(rPnn);

            // type 1 (classic) -> randomInit
            Object p1 = callInitializePartition(vs, N_VECTORS - 1, seed, 1);
            InfiniteCentroids c1 = asCentroids(p1);
            assertSame(rRandom, c1);
            assertEquals(N_VECTORS, ((Partition) p1.getClass().getMethod(
                    "partition").invoke(p1)).size());

            // type 2/3 (semi-random) -> semiRandomInit
            Object p2 = callInitializePartition(vs, N_VECTORS - 1, seed, 2);
            assertSame(rSemi, asCentroids(p2));
            Object p3 = callInitializePartition(vs, N_VECTORS - 1, seed, 3);
            assertSame(rSemi, asCentroids(p3));

            // type 4 (pick input vectors) -> pickInit
            Object p4 = callInitializePartition(vs, N_VECTORS - 1, seed, 4);
            assertSame(rPick, asCentroids(p4));

            // type 5 (PNN) -> pnnInit
            Object p5 = callInitializePartition(vs, N_VECTORS - 1, seed, 5);
            assertSame(rPnn, asCentroids(p5));
        }
    }

    private InfiniteCentroids asCentroids(Object partitionInit)
            throws Exception {
        return (InfiniteCentroids) partitionInit.getClass().getMethod(
                "centroids").invoke(partitionInit);
    }

    @Test
    void testPopulatePartitionForLocalSearchFillsEmptyPartition()
            throws Exception {
        VectorSet vs = TestUtils.createMockVectorSet(N_VECTORS, VECTOR_LENGTH);
        Partition empty = new Partition(N_VECTORS + 1);
        InfiniteCentroids seed = new InfiniteCentroids(N_VECTORS + 1,
                VECTOR_LENGTH);
        GLAConfig config = new GLAConfig(0.001, 1.8, 7, 1, 4, 0, 500, 0,
                N_VECTORS, 20, 5, false, false, false, false, false, false,
                0.0, false, 1, 6, false, false, false, false,
                false, true, false);

        try (var mockEngine = mockStatic(GLAEngine.class)) {
            Method m = ClassifyCommand.class.getDeclaredMethod(
                    "populatePartitionForLocalSearch", VectorSet.class,
                    Partition.class, InfiniteCentroids.class, GLAConfig.class);
            m.setAccessible(true);
            m.invoke(null, vs, empty, seed, config);

            // NearestNeighbor assignment + removeEmpty must have run.
            mockEngine.verify(() -> GLAEngine.removeEmpty(any(), any()),
                    atLeastOnce());
            mockEngine.verify(() -> GLAEngine.recomputeCentroids(any(), any(),
                    anyBoolean(), anyInt()), atLeastOnce());

            // Every vector is now assigned to some cluster (no loss).
            int total = 0;
            for (int i = 1; i <= empty.size(); i++) {
                total += empty.getSize(i);
            }
            assertEquals(N_VECTORS, total,
                    "all vectors must be assigned after population");
        }
    }

    @Test
    void testExecuteWithHeuristic7NoEmptyClusterError() throws Exception {
        // End-to-end regression: -r7 on a populated set runs local search and
        // returns 0 without the "Empty cluster" IllegalStateException.
        TestUtils.setupOptions(args,
                TestUtils.createOptions("-n", "2", "-r", "7"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(new Partition(1));
            when(GLAEngine.partitionToSet(any())).thenReturn(mockVectorSet);

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    // --- G12: -eX empty-cell / orphaned-centroid fix mapping ---------------

    @Test
    void testExecuteWithE2SetsWorstMatchOnly() throws Exception {
        TestUtils.setupOptions(args, TestUtils.createOptions("-e", "2"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(new Partition(1));

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);
            int result = command.execute(args);
            assertEquals(0, result);

            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()), atLeastOnce());

            GLAConfig capturedConfig = configCaptor.getValue();
            assertTrue(capturedConfig.alternateWorstMatch());
            assertFalse(capturedConfig.alternateEmptyCellFix());
        }
    }

    @Test
    void testExecuteWithE3SetsEmptyCellFixOnly() throws Exception {
        TestUtils.setupOptions(args, TestUtils.createOptions("-e", "3"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(new Partition(1));

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);
            int result = command.execute(args);
            assertEquals(0, result);

            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()), atLeastOnce());

            GLAConfig capturedConfig = configCaptor.getValue();
            assertFalse(capturedConfig.alternateWorstMatch());
            assertTrue(capturedConfig.alternateEmptyCellFix());
        }
    }

    @Test
    void testExecuteWithE4SetsBothFixes() throws Exception {
        TestUtils.setupOptions(args, TestUtils.createOptions("-e", "4"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(new Partition(1));

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);
            int result = command.execute(args);
            assertEquals(0, result);

            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()), atLeastOnce());

            GLAConfig capturedConfig = configCaptor.getValue();
            assertTrue(capturedConfig.alternateWorstMatch());
            assertTrue(capturedConfig.alternateEmptyCellFix());
        }
    }

    @Test
    void testExecuteWithDefaultESetsWorstMatchOnly() throws Exception {
        // No -e flag: default is worst_match=TRUE, empty_cell_fix=FALSE per C
        // vars.c.
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(new Partition(1));

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);
            int result = command.execute(args);
            assertEquals(0, result);

            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()), atLeastOnce());

            GLAConfig capturedConfig = configCaptor.getValue();
            assertTrue(capturedConfig.alternateWorstMatch());
            assertFalse(capturedConfig.alternateEmptyCellFix());
        }
    }

    // --- G14: decreasing_epsilon (bare -E) ----------------------------------

    @Test
    void testExecuteWithBareEpsilonDecreasingMode() throws Exception {
        // Bare -E (non-numeric value, as produced by CliParser) selects the
        // decreasing_epsilon mode and keeps the default epsilon.
        TestUtils.setupOptions(args, TestUtils.createOptions("-E", "true"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(new Partition(1));

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);
            int result = command.execute(args);
            assertEquals(0, result);

            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()), atLeastOnce());

            GLAConfig capturedConfig = configCaptor.getValue();
            assertTrue(capturedConfig.decreasingEpsilon());
            assertEquals(0.001, capturedConfig.epsilon(), 0.0);
        }
    }

    @Test
    void testExecuteWithNumericEpsilonNotDecreasing() throws Exception {
        // Numeric -E keeps decreasing_epsilon disabled and uses the value.
        TestUtils.setupOptions(args, TestUtils.createOptions("-E", "0.1"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(new Partition(1));

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);
            int result = command.execute(args);
            assertEquals(0, result);

            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()), atLeastOnce());

            GLAConfig capturedConfig = configCaptor.getValue();
            assertFalse(capturedConfig.decreasingEpsilon());
            assertEquals(0.1, capturedConfig.epsilon(), 0.0);
        }
    }

    // --- G16: -W kcStopWhen wiring ------------------------------------------

    @Test
    void testExecuteWithKstopwhenFlag() throws Exception {
        TestUtils.setupOptions(args,
                TestUtils.createOptions("-W", "3"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(new Partition(1));

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);
            int result = command.execute(args);
            assertEquals(0, result);

            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()), atLeastOnce());

            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(3, capturedConfig.kcStopWhen());
        }
    }

    @Test
    void testExecuteWithDefaultKstopwhen() throws Exception {
        // No -W flag: kcStopWhen defaults to 5.
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(new Partition(1));

            ArgumentCaptor<GLAConfig> configCaptor = ArgumentCaptor
                    .forClass(GLAConfig.class);
            int result = command.execute(args);
            assertEquals(0, result);

            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()), atLeastOnce());

            GLAConfig capturedConfig = configCaptor.getValue();
            assertEquals(5, capturedConfig.kcStopWhen());
        }
    }
}
