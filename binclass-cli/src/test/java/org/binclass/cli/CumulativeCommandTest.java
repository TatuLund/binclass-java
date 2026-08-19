package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.binclass.algorithms.classify.CumulativeClassifier;
import org.binclass.algorithms.classify.CumulativeConfig;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.VectorSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

/**
 * Unit tests for CumulativeCommand to verify algorithm execution.
 */
public class CumulativeCommandTest {

    private CumulativeCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new CumulativeCommand();
        args = TestUtils.createTestArgs("test");
    }

    @Test
    void testExecuteWithDefaultParameters() throws Exception {
        // Setup - default parameters for cumulative classification
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedCumulativeClassifier = mockStatic(
                        CumulativeClassifier.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Capture CumulativeConfig passed to doCumulativeClassification
            ArgumentCaptor<CumulativeConfig> configCaptor = ArgumentCaptor
                    .forClass(CumulativeConfig.class);

            // Execute - should run cumulative classification with default
            // delta=1
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify all config fields match defaults
            mockedCumulativeClassifier.verify(
                    () -> CumulativeClassifier.doCumulativeClassification(
                            any(VectorSet.class), configCaptor.capture()));

            CumulativeConfig capturedConfig = configCaptor.getValue();
            assertEquals(1, capturedConfig.delta());
            assertFalse(capturedConfig.cumulativeAnalysis()); // default false
            assertEquals(0, capturedConfig.cumulativeSamples());
            assertFalse(capturedConfig.fixedDelta());
            assertTrue(capturedConfig.bayesianPredictive()); // default true
            assertFalse(capturedConfig.testFeatureSignificance());
            assertTrue(capturedConfig.cumSaveByPf()); // default true
            assertFalse(capturedConfig.cumNoNewClasses());
            assertFalse(capturedConfig.inOrder()); // default false
            assertEquals(0.001, capturedConfig.epsilon(), 0.0); // default
                                                                // epsilon
        }
    }

    @Test
    void testExecuteWithVerbose() throws Exception {
        // Setup - verbose mode enabled (no -q flag)
        TestUtils.setupOptions(args, new HashMap<>());
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedCumulativeClassifier = mockStatic(
                        CumulativeClassifier.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Capture CumulativeConfig passed to doCumulativeClassification
            ArgumentCaptor<CumulativeConfig> configCaptor = ArgumentCaptor
                    .forClass(CumulativeConfig.class);

            // Execute - should execute successfully with verbose output
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify all config fields match defaults (verbose doesn't affect
            // config)
            mockedCumulativeClassifier.verify(
                    () -> CumulativeClassifier.doCumulativeClassification(
                            any(VectorSet.class), configCaptor.capture()));

            CumulativeConfig capturedConfig = configCaptor.getValue();
            assertEquals(1, capturedConfig.delta());
            assertFalse(capturedConfig.cumulativeAnalysis());
            assertEquals(0, capturedConfig.cumulativeSamples());
            assertFalse(capturedConfig.fixedDelta());
            assertTrue(capturedConfig.bayesianPredictive());
            assertFalse(capturedConfig.testFeatureSignificance());
            assertTrue(capturedConfig.cumSaveByPf());
            assertFalse(capturedConfig.cumNoNewClasses());
            assertFalse(capturedConfig.inOrder()); // -O flag not present in
                                                   // this test
        }
    }

    @Test
    void testExecuteWithCumulativeInOrder() throws Exception {
        // Setup - cumulative in-order flag present (-O)
        TestUtils.setupOptions(args, TestUtils.createOptions("-O", ""));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedCumulativeClassifier = mockStatic(
                        CumulativeClassifier.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Capture CumulativeConfig passed to doCumulativeClassification
            ArgumentCaptor<CumulativeConfig> configCaptor = ArgumentCaptor
                    .forClass(CumulativeConfig.class);

            // Execute - should execute with cumulative in-order processing
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify all config fields match defaults (-O doesn't affect
            // config)
            mockedCumulativeClassifier.verify(
                    () -> CumulativeClassifier.doCumulativeClassification(
                            any(VectorSet.class), configCaptor.capture()));

            CumulativeConfig capturedConfig = configCaptor.getValue();
            assertEquals(1, capturedConfig.delta());
            assertFalse(capturedConfig.cumulativeAnalysis());
            assertEquals(0, capturedConfig.cumulativeSamples());
            assertFalse(capturedConfig.fixedDelta());
            assertTrue(capturedConfig.bayesianPredictive());
            assertFalse(capturedConfig.testFeatureSignificance());
            assertTrue(capturedConfig.cumSaveByPf());
            assertFalse(capturedConfig.cumNoNewClasses());
            assertTrue(capturedConfig.inOrder()); // -O flag enables in-order
                                                  // processing
        }
    }

    @Test
    void testExecuteWithBayesianPredictive() throws Exception {
        // Setup - Bayesian predictive flag present (-B)
        TestUtils.setupOptions(args, TestUtils.createOptions("-B", ""));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedCumulativeClassifier = mockStatic(
                        CumulativeClassifier.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Capture CumulativeConfig passed to doCumulativeClassification
            ArgumentCaptor<CumulativeConfig> configCaptor = ArgumentCaptor
                    .forClass(CumulativeConfig.class);

            // Execute - should execute with Bayesian predictive model enabled
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify all config fields match defaults (-B doesn't change
            // default)
            mockedCumulativeClassifier.verify(
                    () -> CumulativeClassifier.doCumulativeClassification(
                            any(VectorSet.class), configCaptor.capture()));

            CumulativeConfig capturedConfig = configCaptor.getValue();
            assertEquals(1, capturedConfig.delta());
            assertFalse(capturedConfig.cumulativeAnalysis());
            assertEquals(0, capturedConfig.cumulativeSamples());
            assertFalse(capturedConfig.fixedDelta());
            assertTrue(capturedConfig.bayesianPredictive()); // default true
            assertFalse(capturedConfig.testFeatureSignificance());
            assertTrue(capturedConfig.cumSaveByPf());
            assertFalse(capturedConfig.cumNoNewClasses());
            assertFalse(capturedConfig.inOrder()); // default false
            assertEquals(0.001, capturedConfig.epsilon(), 0.0); // default
                                                                // epsilon
        }
    }

    @Test
    void testExecuteWithEpsilon() throws Exception {
        // Setup - epsilon=0.01 for convergence threshold
        TestUtils.setupOptions(args, TestUtils.createOptions("-E", "0.01"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedCumulativeClassifier = mockStatic(
                        CumulativeClassifier.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Capture CumulativeConfig passed to doCumulativeClassification
            ArgumentCaptor<CumulativeConfig> configCaptor = ArgumentCaptor
                    .forClass(CumulativeConfig.class);

            // Execute - should use epsilon=0.01 for convergence threshold
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify all config fields match defaults (epsilon doesn't affect
            // config)
            mockedCumulativeClassifier.verify(
                    () -> CumulativeClassifier.doCumulativeClassification(
                            any(VectorSet.class), configCaptor.capture()));

            CumulativeConfig capturedConfig = configCaptor.getValue();
            assertEquals(1, capturedConfig.delta());
            assertFalse(capturedConfig.cumulativeAnalysis());
            assertEquals(0, capturedConfig.cumulativeSamples());
            assertFalse(capturedConfig.fixedDelta());
            assertTrue(capturedConfig.bayesianPredictive());
            assertFalse(capturedConfig.testFeatureSignificance());
            assertTrue(capturedConfig.cumSaveByPf());
            assertFalse(capturedConfig.cumNoNewClasses());
            assertEquals(0.01, capturedConfig.epsilon(), 0.0);
        }
    }

    @Test
    void testExecuteWithCumulativeAnalysis() throws Exception {
        // Setup - cumulative analysis flag present (-C)
        TestUtils.setupOptions(args, TestUtils.createOptions("-C", ""));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedCumulativeClassifier = mockStatic(
                        CumulativeClassifier.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Capture CumulativeConfig passed to doCumulativeClassification
            ArgumentCaptor<CumulativeConfig> configCaptor = ArgumentCaptor
                    .forClass(CumulativeConfig.class);

            // Execute - should execute with cumulative analysis enabled
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify all config fields match defaults (-C doesn't change
            // config)
            mockedCumulativeClassifier.verify(
                    () -> CumulativeClassifier.doCumulativeClassification(
                            any(VectorSet.class), configCaptor.capture()));

            CumulativeConfig capturedConfig = configCaptor.getValue();
            assertEquals(1, capturedConfig.delta());
            assertFalse(capturedConfig.cumulativeAnalysis());
            assertEquals(0, capturedConfig.cumulativeSamples());
            assertFalse(capturedConfig.fixedDelta());
            assertTrue(capturedConfig.bayesianPredictive());
            assertFalse(capturedConfig.testFeatureSignificance());
            assertTrue(capturedConfig.cumSaveByPf());
            assertFalse(capturedConfig.cumNoNewClasses());
            assertFalse(capturedConfig.inOrder()); // default false
            assertEquals(0.001, capturedConfig.epsilon(), 0.0); // default
                                                                // epsilon
        }
    }

    @Test
    void testExecuteWithRealDeltaValue() throws Exception {
        // Setup - real delta=2 for interval analysis
        TestUtils.setupOptions(args, TestUtils.createOptions("-d", "2"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedCumulativeClassifier = mockStatic(
                        CumulativeClassifier.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Capture CumulativeConfig passed to doCumulativeClassification
            ArgumentCaptor<CumulativeConfig> configCaptor = ArgumentCaptor
                    .forClass(CumulativeConfig.class);

            // Execute - should use real delta value of 2
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify all config fields are correctly set
            mockedCumulativeClassifier.verify(
                    () -> CumulativeClassifier.doCumulativeClassification(
                            any(VectorSet.class), configCaptor.capture()));

            CumulativeConfig capturedConfig = configCaptor.getValue();
            assertEquals(2, capturedConfig.delta());
            assertFalse(capturedConfig.cumulativeAnalysis());
            assertEquals(0, capturedConfig.cumulativeSamples());
            assertFalse(capturedConfig.fixedDelta()); // -d flag doesn't set
                                                      // fixedDelta
            assertTrue(capturedConfig.bayesianPredictive()); // default true
            assertFalse(capturedConfig.testFeatureSignificance());
            assertTrue(capturedConfig.cumSaveByPf()); // default true
            assertFalse(capturedConfig.cumNoNewClasses());
            assertFalse(capturedConfig.inOrder()); // default false
            assertEquals(0.001, capturedConfig.epsilon(), 0.0); // default
                                                                // epsilon
        }
    }

    @Test
    void testExecuteWithDefaultDelta() throws Exception {
        // Setup - no delta specified, should default to 1
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedCumulativeClassifier = mockStatic(
                        CumulativeClassifier.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Capture CumulativeConfig passed to doCumulativeClassification
            ArgumentCaptor<CumulativeConfig> configCaptor = ArgumentCaptor
                    .forClass(CumulativeConfig.class);

            // Execute - should use default delta=1
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify all config fields match defaults
            mockedCumulativeClassifier.verify(
                    () -> CumulativeClassifier.doCumulativeClassification(
                            any(VectorSet.class), configCaptor.capture()));

            CumulativeConfig capturedConfig = configCaptor.getValue();
            assertEquals(1, capturedConfig.delta());
            assertFalse(capturedConfig.cumulativeAnalysis());
            assertEquals(0, capturedConfig.cumulativeSamples());
            assertFalse(capturedConfig.fixedDelta()); // no -D flag
            assertTrue(capturedConfig.bayesianPredictive());
            assertFalse(capturedConfig.testFeatureSignificance());
            assertTrue(capturedConfig.cumSaveByPf());
            assertFalse(capturedConfig.cumNoNewClasses());
            assertFalse(capturedConfig.inOrder()); // default false
            assertEquals(0.001, capturedConfig.epsilon(), 0.0); // default
                                                                // epsilon
        }
    }

    @Test
    void testExecuteWithFixedDelta() throws Exception {
        // Setup - fixed delta=3 via -D flag
        TestUtils.setupOptions(args, TestUtils.createOptions("-D", "3"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedCumulativeClassifier = mockStatic(
                        CumulativeClassifier.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Capture CumulativeConfig passed to doCumulativeClassification
            ArgumentCaptor<CumulativeConfig> configCaptor = ArgumentCaptor
                    .forClass(CumulativeConfig.class);

            // Execute - should use fixed delta=3
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify all config fields are correctly set
            mockedCumulativeClassifier.verify(
                    () -> CumulativeClassifier.doCumulativeClassification(
                            any(VectorSet.class), configCaptor.capture()));

            CumulativeConfig capturedConfig = configCaptor.getValue();
            assertEquals(3, capturedConfig.delta());
            assertFalse(capturedConfig.cumulativeAnalysis());
            assertEquals(0, capturedConfig.cumulativeSamples());
            assertTrue(capturedConfig.fixedDelta()); // -D flag sets
                                                     // fixedDelta=true
            assertTrue(capturedConfig.bayesianPredictive());
            assertFalse(capturedConfig.testFeatureSignificance());
            assertTrue(capturedConfig.cumSaveByPf());
            assertFalse(capturedConfig.cumNoNewClasses());
            assertFalse(capturedConfig.inOrder()); // default false
            assertEquals(0.001, capturedConfig.epsilon(), 0.0); // default
                                                                // epsilon
        }
    }

    @Test
    void testExecuteWithAllFlags() throws Exception {
        // Setup - all flags present
        Map<String, String> opts = new HashMap<>();
        opts.put("-d", "5");
        opts.put("-S", ""); // disable bayesian predictive
        opts.put("-F", ""); // enable test feature significance
        opts.put("-c", ""); // disable cumSaveByPf
        opts.put("-n", ""); // enable no new classes
        TestUtils.setupOptions(args, opts);
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedCumulativeClassifier = mockStatic(
                        CumulativeClassifier.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Capture CumulativeConfig passed to doCumulativeClassification
            ArgumentCaptor<CumulativeConfig> configCaptor = ArgumentCaptor
                    .forClass(CumulativeConfig.class);

            // Execute - should use all specified flags
            int result = command.execute(args);

            assertEquals(0, result);

            // Verify all config fields are correctly set
            mockedCumulativeClassifier.verify(
                    () -> CumulativeClassifier.doCumulativeClassification(
                            any(VectorSet.class), configCaptor.capture()));

            CumulativeConfig capturedConfig = configCaptor.getValue();
            assertEquals(5, capturedConfig.delta());
            assertFalse(capturedConfig.cumulativeAnalysis());
            assertEquals(0, capturedConfig.cumulativeSamples());
            assertFalse(capturedConfig.fixedDelta()); // -d flag doesn't set
                                                      // fixedDelta
            assertFalse(capturedConfig.bayesianPredictive()); // -S disables it
            assertTrue(capturedConfig.testFeatureSignificance()); // -F enables
                                                                  // it
            assertFalse(capturedConfig.cumSaveByPf()); // -c disables it
            assertTrue(capturedConfig.cumNoNewClasses()); // -n enables it
            assertFalse(capturedConfig.inOrder()); // default false
            assertEquals(0.001, capturedConfig.epsilon(), 0.0); // default
                                                                // epsilon
        }
    }

    @Test
    void testExecuteWithInvalidDelta() {
        Map<String, String> opts = new HashMap<>();
        opts.put("-d", "abc");
        TestUtils.setupOptions(args, opts);

        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);
        try (var mockedLoader = mockStatic(DataLoader.class)) {
            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            assertThrows(IllegalArgumentException.class,
                    () -> command.execute(args));
        }
    }

    @Test
    void testGetName() {
        assertEquals("cumulative", command.getName());
    }

    @Test
    void testGetDescription() {
        String desc = command.getDescription();
        assertTrue(desc != null && !desc.isEmpty());
    }
}
