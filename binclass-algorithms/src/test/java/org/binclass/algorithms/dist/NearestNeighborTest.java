/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link NearestNeighbor}.
 */
public class NearestNeighborTest {

    @Test
    public void testFastNearestNeighborIdenticalVectors() {
        // Create a VectorSet with identical vectors [1, 1, 1]
        VectorSet vectors = new VectorSet();
        for (int i = 0; i < 3; i++) {
            BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1 }, 3);
            vectors.addElement(vector);
        }

        // Create centroids: one with all 1s, one with all 0s
        InfiniteCentroids centroids = new InfiniteCentroids(2, 3);
        Centroid centroid0 = centroids.get(0);
        for (int i = 0; i < 3; i++) {
            centroid0.set(i, 1.0);
        }

        Centroid centroid1 = centroids.get(1);
        for (int i = 0; i < 3; i++) {
            centroid1.set(i, 0.0);
        }

        Partition partition = new Partition(2);
        NearestNeighbor.fastNearestNeighbor(vectors, partition, centroids);

        // All vectors should be assigned to class 1 (centroid with all 1s)
        assertEquals(3, partition.getElements(1).size());
    }

    @Test
    public void testFastNearestNeighborDifferentVectors() {
        // Create a VectorSet with different vectors
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0, 1 });
        vectors.addElement(v1);

        BinaryVector v2 = new BinaryVector(new int[] { 0, 1, 0 });
        vectors.addElement(v2);

        // Create centroids: one with [1, 0, 1], one with [0, 1, 0]
        InfiniteCentroids centroids = new InfiniteCentroids(2);

        Centroid centroid0 = centroids.get(0);
        double[] probs0 = { 0.9, 0.1, 0.9 };
        for (int i = 0; i < 3; i++) {
            centroid0.set(i, probs0[i]);
        }

        Centroid centroid1 = centroids.get(1);
        double[] probs1 = { 0.1, 0.9, 0.1 };
        for (int i = 0; i < 3; i++) {
            centroid1.set(i, probs1[i]);
        }

        Partition partition = new Partition(2);
        NearestNeighbor.fastNearestNeighbor(vectors, partition, centroids);

        // v1 should be in cluster 0 (centroid with [0.9, 0.1, 0.9])
        assertEquals(1, partition.getElements(1).size());
        assertTrue(partition.contains(1, v1));
    }

    @Test
    public void testFastNearestNeighborSingleCentroid() {
        // Create a VectorSet with vectors
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0 });
        vectors.addElement(v1);

        BinaryVector v2 = new BinaryVector(new int[] { 0, 1 });
        vectors.addElement(v2);

        // Create a single centroid with all 0.5 probabilities
        InfiniteCentroids centroids = new InfiniteCentroids(1);
        Centroid centroid0 = centroids.get(0);
        for (int i = 0; i < 2; i++) {
            centroid0.set(i, 0.5);
        }

        Partition partition = new Partition(1);
        NearestNeighbor.fastNearestNeighbor(vectors, partition, centroids);

        // Both vectors should be in the single cluster (centroid with all 0.5)
        assertEquals(2, partition.getElements(1).size());

        // Both vectors should be in the single cluster (centroid with all 0.5)
        assertEquals(2, partition.getElements(1).size());
    }

    @Test
    public void testFastNearestNeighborNullVectorSet() {
        InfiniteCentroids centroids = new InfiniteCentroids(2);
        Partition partition = new Partition(2);

        assertThrows(NullPointerException.class, () -> NearestNeighbor
                .fastNearestNeighbor(null, partition, centroids));
    }

    @Test
    public void testFastNearestNeighborNullPartition() {
        VectorSet vectors = new VectorSet();
        BinaryVector vector = new BinaryVector(new int[] { 1, 0 });
        vectors.addElement(vector);

        InfiniteCentroids centroids = new InfiniteCentroids(2);

        assertThrows(NullPointerException.class, () -> NearestNeighbor
                .fastNearestNeighbor(vectors, null, centroids));
    }

    @Test
    public void testFastNearestNeighborNullCentroids() {
        VectorSet vectors = new VectorSet();
        BinaryVector vector = new BinaryVector(new int[] { 1, 0 });
        vectors.addElement(vector);

        Partition partition = new Partition(2);

        assertThrows(NullPointerException.class, () -> NearestNeighbor
                .fastNearestNeighbor(vectors, partition, null));
    }

    @Test
    public void testMaeNearestNeighbor() {
        // Create a VectorSet with vectors
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0, 1 });
        vectors.addElement(v1);

        BinaryVector v2 = new BinaryVector(new int[] { 0, 1, 0 });
        vectors.addElement(v2);

        // Create centroids: one with [1, 0, 1], one with [0, 1, 0]
        InfiniteCentroids centroids = new InfiniteCentroids(2);

        Centroid centroid0 = centroids.get(0);
        double[] probs0 = { 0.9, 0.1, 0.9 };
        for (int i = 0; i < 3; i++) {
            centroid0.set(i, probs0[i]);
        }

        Centroid centroid1 = centroids.get(1);
        double[] probs1 = { 0.1, 0.9, 0.1 };
        for (int i = 0; i < 3; i++) {
            centroid1.set(i, probs1[i]);
        }

        Partition partition = new Partition(2);
        NearestNeighbor.maeNearestNeighbor(vectors, partition, centroids);

        // v1 should be in cluster 0 (centroid with [0.9, 0.1, 0.9])
        assertEquals(1, partition.getElements(1).size());
        assertTrue(partition.contains(1, v1));
    }

    @Test
    public void testMseNearestNeighbor() {
        // Create a VectorSet with vectors
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0, 1 });
        vectors.addElement(v1);

        BinaryVector v2 = new BinaryVector(new int[] { 0, 1, 0 });
        vectors.addElement(v2);

        // Create centroids: one with [1, 0, 1], one with [0, 1, 0]
        InfiniteCentroids centroids = new InfiniteCentroids(2);

        Centroid centroid0 = centroids.get(0);
        double[] probs0 = { 0.9, 0.1, 0.9 };
        for (int i = 0; i < 3; i++) {
            centroid0.set(i, probs0[i]);
        }

        Centroid centroid1 = centroids.get(1);
        double[] probs1 = { 0.1, 0.9, 0.1 };
        for (int i = 0; i < 3; i++) {
            centroid1.set(i, probs1[i]);
        }

        Partition partition = new Partition(2);
        NearestNeighbor.mseNearestNeighbor(vectors, partition, centroids);

        // v1 should be in cluster 0 (centroid with [0.9, 0.1, 0.9])
        assertEquals(1, partition.getElements(1).size());
        assertTrue(partition.contains(1, v1));
    }

    @Test
    public void testInfNearestNeighbor() {
        // Create a VectorSet with vectors
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0, 1 });
        vectors.addElement(v1);

        BinaryVector v2 = new BinaryVector(new int[] { 0, 1, 0 });
        vectors.addElement(v2);

        // Create centroids: one with [1, 0, 1], one with [0, 1, 0]
        InfiniteCentroids centroids = new InfiniteCentroids(2);

        Centroid centroid0 = centroids.get(0);
        double[] probs0 = { 0.9, 0.1, 0.9 };
        for (int i = 0; i < 3; i++) {
            centroid0.set(i, probs0[i]);
        }

        Centroid centroid1 = centroids.get(1);
        double[] probs1 = { 0.1, 0.9, 0.1 };
        for (int i = 0; i < 3; i++) {
            centroid1.set(i, probs1[i]);
        }

        Partition partition = new Partition(2);

        NearestNeighbor.infNearestNeighbor(vectors, partition, centroids,
                false);

        // v1 should be in cluster 0 (centroid with [0.9, 0.1, 0.9])
        assertEquals(1, partition.getElements(1).size());
        assertTrue(partition.contains(1, v1));
    }

    @Test
    public void testInfNearestNeighborWithWeights() {
        // Create a VectorSet with vectors
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0, 1 });
        vectors.addElement(v1);

        BinaryVector v2 = new BinaryVector(new int[] { 0, 1, 0 });
        vectors.addElement(v2);

        // Create centroids: one with [1, 0, 1], one with [0, 1, 0]
        InfiniteCentroids centroids = new InfiniteCentroids(2, 3);

        Centroid centroid0 = centroids.get(0);
        double[] probs0 = { 0.9, 0.1, 0.9 };
        for (int i = 0; i < 3; i++) {
            centroid0.set(i, probs0[i]);
        }

        Centroid centroid1 = centroids.get(1);
        double[] probs1 = { 0.1, 0.9, 0.1 };
        for (int i = 0; i < 3; i++) {
            centroid1.set(i, probs1[i]);
        }

        centroid0.setWeight(1);
        centroid1.setWeight(1);

        Partition partition = new Partition(2);
        NearestNeighbor.infNearestNeighbor(vectors, partition, centroids, true);

        // v1 should be in cluster 0 (centroid with [0.9, 0.1, 0.9])
        assertEquals(1, partition.getElements(1).size());
        assertTrue(partition.contains(1, v1));
        assertTrue(partition.contains(2, v2));
    }

    @Test
    public void testInfNearestNeighborNullVectorSet() {
        InfiniteCentroids centroids = new InfiniteCentroids(2);
        Partition partition = new Partition(2);

        assertThrows(NullPointerException.class, () -> NearestNeighbor
                .infNearestNeighbor(null, partition, centroids, false));
    }

    @Test
    public void testInfNearestNeighborNullPartition() {
        VectorSet vectors = new VectorSet();
        BinaryVector vector = new BinaryVector(new int[] { 1, 0 });
        vectors.addElement(vector);

        InfiniteCentroids centroids = new InfiniteCentroids(2);

        assertThrows(NullPointerException.class, () -> NearestNeighbor
                .infNearestNeighbor(vectors, null, centroids, false));
    }

    @Test
    public void testInfNearestNeighborNullCentroids() {
        VectorSet vectors = new VectorSet();
        BinaryVector vector = new BinaryVector(new int[] { 1, 0 });
        vectors.addElement(vector);

        Partition partition = new Partition(2);

        assertThrows(NullPointerException.class, () -> NearestNeighbor
                .infNearestNeighbor(vectors, partition, null, false));
    }

    @Test
    public void testAllMethodsAssignCorrectly() {
        // Create a VectorSet with vectors that should be clearly separated
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0, 0 });
        vectors.addElement(v1);

        BinaryVector v2 = new BinaryVector(new int[] { 0, 1, 1 });
        vectors.addElement(v2);

        // Create centroids: one with [1, 0, 0], one with [0, 1, 1]
        InfiniteCentroids centroids = new InfiniteCentroids(2);

        Centroid centroid0 = centroids.get(0);
        double[] probs0 = { 0.95, 0.05, 0.05 };
        for (int i = 0; i < 3; i++) {
            centroid0.set(i, probs0[i]);
        }

        Centroid centroid1 = centroids.get(1);
        double[] probs1 = { 0.05, 0.95, 0.95 };
        for (int i = 0; i < 3; i++) {
            centroid1.set(i, probs1[i]);
        }

        // Test fastNearestNeighbor
        Partition partitionFast = new Partition(2);
        NearestNeighbor.fastNearestNeighbor(vectors, partitionFast, centroids);
        assertEquals(2, partitionFast.size());

        // Test maeNearestNeighbor
        Partition partitionMae = new Partition(2);
        NearestNeighbor.maeNearestNeighbor(vectors, partitionMae, centroids);
        assertEquals(2, partitionMae.size());

        // Test mseNearestNeighbor
        Partition partitionMse = new Partition(2);
        NearestNeighbor.mseNearestNeighbor(vectors, partitionMse, centroids);
        assertEquals(2, partitionMse.size());

        // Test infNearestNeighbor (without weights)
        Partition partitionInf = new Partition(2);
        NearestNeighbor.infNearestNeighbor(vectors, partitionInf, centroids,
                false);
        assertEquals(2, partitionInf.size());

        // Test infNearestNeighbor (with weights)
        Partition partitionInfW = new Partition(2);
        NearestNeighbor.infNearestNeighbor(vectors, partitionInfW, centroids,
                true);
        assertEquals(2, partitionInfW.size());
    }
}
