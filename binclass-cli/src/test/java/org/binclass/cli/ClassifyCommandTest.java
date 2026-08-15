package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.GLAEngine;
import org.binclass.algorithms.util.MathUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

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
                    any(), configCaptor.capture()));

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
                    any(), configCaptor.capture()));

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
                    any(), configCaptor.capture()));

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
                    any(), configCaptor.capture()));

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
                    any(), any(), configCaptor.capture()));

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
                    any(), any(), configCaptor.capture()));

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
                    any(), configCaptor.capture()));

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
            // k=3 finds no improvement and converges).
            mockedGlaEngine.verify(() -> GLAEngine.gla(any(), any(), any(),
                    any(), configCaptor.capture()), times(1));

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
                    any(), configCaptor.capture()));

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
                    any(), configCaptor.capture()));

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
                    any(), configCaptor.capture()));

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
                    any(), configCaptor.capture()));

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
                    any(), configCaptor.capture()));

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
    void testLogFactorialsInitialized() throws Exception {
        // Setup - reset LOG2_FACTORIALS to simulate uninitialized state
        java.lang.reflect.Field field = MathUtils.class
                .getDeclaredField("LOG2_FACTORIALS");
        field.setAccessible(true);
        field.set(null, null);

        VectorSet mockVectorSet = TestUtils.createMockVectorSet(N_VECTORS,
                VECTOR_LENGTH);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedGlaEngine = mockStatic(GLAEngine.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Mock GLAEngine.gla to return immediately without executing actual
            // algorithm logic
            Partition resultPartition = new Partition(1);
            when(GLAEngine.gla(any(), any(), any(), any(), any()))
                    .thenReturn(resultPartition);

            // Execute command - should initialize log factorials before calling
            // GLA
            int result = command.execute(args);
            assertEquals(0, result);

            // Verify the static field is populated after initialization
            double[] storedArray = (double[]) field.get(null);
            assertNotNull(storedArray, "LOG2_FACTORIALS should be populated");
            assertTrue(storedArray.length >= 6,
                    "LOG2_FACTORIALS array should have at least 7 elements (n+n=6)");

            // Verify known values are correctly computed: log2(n!)
            assertEquals(0.0, storedArray[0], 1e-10); // log2(0!) = 0
            assertEquals(0.0, storedArray[1], 1e-10); // log2(1!) = 0
            assertEquals(Math.log(2) / Math.log(2), storedArray[2], 1e-10); // log2(2!)
                                                                            // =
                                                                            // 1
        }
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
}
