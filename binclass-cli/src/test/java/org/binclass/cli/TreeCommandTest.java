package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;

import org.binclass.algorithms.classify.Classifier;
import org.binclass.algorithms.core.TreeNode;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.tree.TreeBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for TreeCommand to verify tree building execution.
 */
class TreeCommandTest {

    private TreeCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new TreeCommand();
        args = TestUtils.createTestArgs("test");
    }

    @AfterEach
    void tearDown() throws Exception {
        clearAllCaches();
    }

    @Test
    void testExecuteWithDefaultParameters() throws Exception {
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            var mockedClassifier = mockStatic(Classifier.class);
            var mockedTreeBuilder = mockStatic(TreeBuilder.class);

            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute
            int result = command.execute(args);

            assertEquals(0, result);
            mockedClassifier.verify(() -> Classifier.identifyVectors(any(),
                    any(), any(), eq(0.001)));
        }
    }

    @Test
    void testExecuteWithHellingerDistance() throws Exception {
        TestUtils.setupOptions(args, TestUtils.createOptions("-H", "2"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            var mockedClassifier = mockStatic(Classifier.class);
            var mockedTreeBuilder = mockStatic(TreeBuilder.class);

            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute
            int result = command.execute(args);

            assertEquals(0, result);
            mockedTreeBuilder
                    .verify(() -> TreeBuilder.makeTreePnn(any(), any()));
        }
    }

    @Test
    void testExecuteWithClassNearness() throws Exception {
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            var mockedClassifier = mockStatic(Classifier.class);
            var mockedTreeBuilder = mockStatic(TreeBuilder.class);

            mockedLoader.when(() -> DataLoader.loadVectors(anyString()))
                    .thenReturn(mockVectorSet);

            // Execute
            int result = command.execute(args);

            assertEquals(0, result);
            mockedTreeBuilder
                    .verify(() -> TreeBuilder.makeTreePnn2(any(), any()));
        }
    }

    @Test
    void testExecuteWithInvalidHellingerValue() throws Exception {
        TestUtils.setupOptions(args, TestUtils.createOptions("-H", "5"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            assertThrows(IllegalArgumentException.class,
                    () -> command.execute(args));
        }
    }

    @Test
    void testExecuteWithInvalidHellingerType() throws Exception {
        TestUtils.setupOptions(args, TestUtils.createOptions("-H", "abc"));
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(3, 10);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            assertThrows(IllegalArgumentException.class,
                    () -> command.execute(args));
        }
    }

    @Test
    void testExecuteWritesTreeToFile(@TempDir Path tempDir) throws Exception {
        // With no -o flag, the dendrogram must be written to <filebase>.tree.
        String filebase = tempDir.resolve("data").toString();
        args.setOptions(TestUtils.createOptions("filebase", filebase));

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedTreeBuilder = mockStatic(TreeBuilder.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(3, 10));

            // A single-cluster tree is a leaf node named "C1".
            TreeNode root = new TreeNode(0.0, "C1");
            when(TreeBuilder.makeTreePnn2(any(), any()))
                    .thenReturn(root);

            int result = command.execute(args);
            assertEquals(0, result);

            Path expected = tempDir.resolve("data.tree");
            assertTrue(Files.exists(expected),
                    "default <filebase>.tree should be written");
            String content = Files.readString(expected);
            assertTrue(content.contains("C1"),
                    "tree output should contain the leaf name");
        }
    }

    @Test
    void testExecuteHonoursExplicitTreeFlag(@TempDir Path tempDir)
            throws Exception {
        // An explicit -o path overrides the default <filebase>.tree.
        String filebase = tempDir.resolve("data").toString();
        TestUtils.setupOptions(args, TestUtils.createOptions("-o",
                tempDir.resolve("custom.tree").toString(), "filebase",
                filebase));

        try (var mockedLoader = mockStatic(DataLoader.class);
                var mockedTreeBuilder = mockStatic(TreeBuilder.class)) {
            when(DataLoader.loadVectors(anyString()))
                    .thenReturn(TestUtils.createMockVectorSet(3, 10));

            TreeNode root = new TreeNode(0.0, "C1");
            when(TreeBuilder.makeTreePnn2(any(), any()))
                    .thenReturn(root);

            int result = command.execute(args);
            assertEquals(0, result);

            Path expected = tempDir.resolve("custom.tree");
            assertTrue(Files.exists(expected),
                    "explicit -o path should be used");
        }
    }
}
