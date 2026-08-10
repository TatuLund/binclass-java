/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.generate;

import static org.junit.jupiter.api.Assertions.*;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DataGenerator} covering data generation algorithms.
 */
final class DataGeneratorTest {

    @Test
    void bernoulliGenWithSingleCluster() {
        Partition partition = createPartition(new int[][] { { 1, 0, 1 } },
                new int[] { 5 });

        VectorSet result = DataGenerator.bernoulliGen(partition, 3);

        assertNotNull(result);
        assertEquals(3, result.size()); // 3 vectors generated
    }

    @Test
    void bernoulliGenWithMultipleClusters() {
        Partition partition = createPartition(
                new int[][] { { 1, 0 }, { 0, 1 } }, new int[] { 4, 4 });

        VectorSet result = DataGenerator.bernoulliGen(partition, 2);

        assertNotNull(result);
        assertEquals(4, result.size()); // 2 vectors per cluster * 2 clusters
    }

    @Test
    void markovGenWithEmptyVectorSet() {
        VectorSet empty = new VectorSet();

        VectorSet result = DataGenerator.markovGen(empty, 5);

        assertNotNull(result);
        assertEquals(0, result.size()); // Empty input produces empty output
    }

    @Test
    void markovGenWithSingleVector() {
        int[] el = { 1, 0, 1, 0 };
        BinaryVector bv = new BinaryVector(el, 4);
        VectorSet vectors = new VectorSet();
        vectors.addElement(bv);

        VectorSet result = DataGenerator.markovGen(vectors, 3);

        assertNotNull(result);
        assertEquals(3, result.size()); // Generated 3 vectors
    }

    @Test
    void vectorGenWithEmptyVectorSet() {
        VectorSet empty = new VectorSet();

        VectorSet result = DataGenerator.vectorGen(empty, 5);

        assertNotNull(result);
        assertEquals(0, result.size()); // Empty input produces empty output
    }

    @Test
    void vectorGenWithSingleVector() {
        int[] el = { 1, 0, 1 };
        BinaryVector bv = new BinaryVector(el, 3);
        VectorSet vectors = new VectorSet();
        vectors.addElement(bv);

        VectorSet result = DataGenerator.vectorGen(vectors, 4);

        assertNotNull(result);
        assertEquals(4, result.size()); // Generated 4 random vectors
    }

    @Test
    void vectorGenProducesCorrectLength() {
        int[] el = { 1, 0, 1, 0, 1 };
        BinaryVector bv = new BinaryVector(el, 5);
        VectorSet vectors = new VectorSet();
        vectors.addElement(bv);

        VectorSet result = DataGenerator.vectorGen(vectors, 2);

        for (BinaryVector generated : result) {
            assertEquals(5, generated.getLength()); // Same length as input
        }
    }

    @Test
    void randomGenWithPositiveLength() {
        BinaryVector result = DataGenerator.randomGen(10);

        assertNotNull(result);
        assertEquals(10, result.getLength());
    }

    @Test
    void randomGenWithNegativeLength() {
        assertThrows(IllegalArgumentException.class,
                () -> DataGenerator.randomGen(-5));
    }

    @Test
    void randomGenProducesBinaryValues() {
        BinaryVector result = DataGenerator.randomGen(100);
        int[] el = result.getEl();

        for (int value : el) {
            assertTrue(value == 0 || value == 1); // All values should be 0 or 1
        }
    }

    @Test
    void bernoulliGenWithNullPartition() {
        assertThrows(NullPointerException.class,
                () -> DataGenerator.bernoulliGen(null, 5));
    }

    @Test
    void markovGenWithNullVectors() {
        assertThrows(NullPointerException.class,
                () -> DataGenerator.markovGen(null, 5));
    }

    @Test
    void vectorGenWithNullVectors() {
        assertThrows(NullPointerException.class,
                () -> DataGenerator.vectorGen(null, 5));
    }

    // Helper methods

    private Partition createPartition(int[][] vectors, int[] sizes) {
        Partition partition = new Partition(vectors.length, vectors[0].length);
        for (int c = 0; c < vectors.length; c++) {
            for (int v = 0; v < sizes[c]; v++) {
                BinaryVector bv = new BinaryVector(vectors[c],
                        vectors[c].length);
                partition.addElement(c + 1, bv); // 1-based index
            }
        }
        return partition;
    }
}
