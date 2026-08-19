package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

/**
 * Unit tests for SortPartCommand to verify sorting/partitioning functionality.
 */
class SortPartCommandTest {

    private SortPartCommand command;
    private TestCommandArgs args;

    @BeforeEach
    void setUp() {
        command = new SortPartCommand();
        args = TestUtils.createTestArgs("test");
    }

    @Test
    void testExecuteWithDefaultParameters() throws Exception {
        // Setup - default parameters, capture partition creation
        VectorSet mockVectorSet = TestUtils.createMockVectorSet(4, 8);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString())).thenReturn(mockVectorSet);

            ArgumentCaptor<Partition> partitionCaptor = ArgumentCaptor
                    .forClass(Partition.class);

            // Execute
            int result = command.execute(args);

            // Verify - should execute successfully and create a partition
            assertEquals(0, result);

            // Verify data loader was called with the correct input file
            mockedLoader.verify(() -> DataLoader.loadVectors("test"));
        }
    }

    @Test
    void testExecuteWithOutputFile() throws Exception {
        // Setup - with output file parameter
        Map<String, String> opts = new HashMap<>();
        opts.put("-o", "output.partition");
        args.setOptions(opts);

        VectorSet mockVectorSet = TestUtils.createMockVectorSet(4, 8);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString())).thenReturn(mockVectorSet);

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithSortBySize() throws Exception {
        // Setup - with sort by size flag
        Map<String, String> opts = new HashMap<>();
        opts.put("-s", "");
        args.setOptions(opts);

        VectorSet mockVectorSet = TestUtils.createMockVectorSet(4, 8);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString())).thenReturn(mockVectorSet);

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithSortByIndex() throws Exception {
        // Setup - with sort by index flag
        Map<String, String> opts = new HashMap<>();
        opts.put("-i", "");
        args.setOptions(opts);

        VectorSet mockVectorSet = TestUtils.createMockVectorSet(4, 8);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString())).thenReturn(mockVectorSet);

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithReverseOrder() throws Exception {
        // Setup - with reverse order flag
        Map<String, String> opts = new HashMap<>();
        opts.put("-r", "");
        args.setOptions(opts);

        VectorSet mockVectorSet = TestUtils.createMockVectorSet(4, 8);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString())).thenReturn(mockVectorSet);

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testExecuteWithAllParameters() throws Exception {
        // Setup - with all parameters
        Map<String, String> opts = new HashMap<>();
        opts.put("-o", "output.partition");
        opts.put("-s", "");
        args.setOptions(opts);

        VectorSet mockVectorSet = TestUtils.createMockVectorSet(4, 8);

        try (var mockedLoader = mockStatic(DataLoader.class)) {
            when(DataLoader.loadVectors(anyString())).thenReturn(mockVectorSet);

            int result = command.execute(args);
            assertEquals(0, result);
        }
    }

    @Test
    void testSortClassesBySizeWithUnbalancedPartition() throws Exception {
        // Setup - create a partition with unbalanced classes (class 1: 3
        // vectors, class 2: 5 vectors)
        Partition inputPartition = new Partition(2);

        // Add 3 vectors to class 1
        for (int i = 0; i < 3; i++) {
            int[] el = new int[8];
            BinaryVector bv = new BinaryVector(el, 0, 8, 0, "strain" + i);
            inputPartition.addElement(1, bv);
        }

        // Add 5 vectors to class 2
        for (int i = 0; i < 5; i++) {
            int[] el = new int[8];
            BinaryVector bv = new BinaryVector(el, 0, 8, 0, "strain" + i);
            inputPartition.addElement(2, bv);
        }

        // Execute - call sortClassesBySize directly
        Partition sortedPartition = SortPartCommand
                .sortClassesBySize(inputPartition);

        // Verify - class 2 (larger) should now be in position 1, class 1
        // (smaller) in position 2
        assertEquals(5, sortedPartition.getElements(1).size(),
                "Larger class should be first after sorting");
        assertEquals(3, sortedPartition.getElements(2).size(),
                "Smaller class should be second after sorting");
    }

    @Test
    void testSortClassesBySizeWithBalancedPartition() throws Exception {
        // Setup - create a partition with balanced classes (both have 4
        // vectors)
        Partition inputPartition = new Partition(2);

        for (int i = 0; i < 4; i++) {
            int[] el1 = new int[8];
            BinaryVector bv1 = new BinaryVector(el1, 0, 8, 0, "strain" + i);
            inputPartition.addElement(1, bv1);

            int[] el2 = new int[8];
            BinaryVector bv2 = new BinaryVector(el2, 0, 8, 0,
                    "strain" + (i + 4));
            inputPartition.addElement(2, bv2);
        }

        // Execute - call sortClassesBySize directly
        Partition sortedPartition = SortPartCommand
                .sortClassesBySize(inputPartition);

        // Verify - both classes should still have 4 vectors (no change needed)
        assertEquals(4, sortedPartition.getElements(1).size());
        assertEquals(4, sortedPartition.getElements(2).size());
    }

    @Test
    void testGetName() {
        assertEquals("sortpart", command.getName());
    }

    @Test
    void testGetDescription() {
        String desc = command.getDescription();
        org.junit.jupiter.api.Assertions.assertNotNull(desc);
        org.junit.jupiter.api.Assertions.assertFalse(desc.isEmpty());
    }
}
