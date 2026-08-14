package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.generate.DataGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for GenerateCommand to verify algorithm execution.
 */
public class GenerateCommandTest {

    private GenerateCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new GenerateCommand();
        args = new TestCommandArgs("test");
    }

    @Test
    void testExecuteWithDefaultParameters() throws Exception {
        // Setup - default parameters for vector generation (random type)
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should generate random vectors by default
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithCustomVecsToGen() throws Exception {
        // Setup - generate 50 vectors with custom count
        TestUtils.setupOptions(args, TestUtils.createOptions("-v", "50"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should generate 50 vectors
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithRandomGenerator() throws Exception {
        // Setup - random generator type (type=1)
        TestUtils.setupOptions(args, TestUtils.createOptions("-G", "1"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should use random vector generation
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithMarkovGenerator() throws Exception {
        // Setup - Markov chain generator type (type=2)
        TestUtils.setupOptions(args, TestUtils.createOptions("-G", "2"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should use Markov chain generation
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithBernoulliGenerator() throws Exception {
        // Setup - Bernoulli model generator type (type=3)
        TestUtils.setupOptions(args, TestUtils.createOptions("-G", "3"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should use Bernoulli model generation
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithUniqueVectors() throws Exception {
        // Setup - unique vectors flag present
        TestUtils.setupOptions(args, TestUtils.createOptions("-u", ""));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should generate unique vectors only
            int result = command.execute(args);

            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithVerbose() throws Exception {
        // Setup - verbose mode enabled for vector generation
        TestUtils.setupOptions(args, new HashMap<>());
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute - should execute successfully with verbose output
            int result = command.execute(args);

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
