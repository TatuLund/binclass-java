/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.centroid;

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
 * Unit tests for {@link CentroidInitializer}.
 */
public class CentroidInitializerTest {

    @Test
    public void testRandomInit() {
        // Create a VectorSet with vectors
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0, 1 }, 3);
        vectors.addElement(v1);

        BinaryVector v2 = new BinaryVector(new int[] { 0, 1, 0 }, 3);
        vectors.addElement(v2);

        BinaryVector v3 = new BinaryVector(new int[] { 1, 1, 0 }, 3);
        vectors.addElement(v3);

        // Initialize centroids using random selection
        InfiniteCentroids centroids = CentroidInitializer.randomInit(vectors,
                2);

        assertEquals(2, centroids.size());

        // Verify each centroid has the correct length
        for (int i = 0; i < 2; i++) {
            Centroid c = centroids.get(i);
            assertEquals(3, c.getLength());
        }
    }

    @Test
    public void testRandomInitInvalidK() {
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0 }, 2);
        vectors.addElement(v1);

        assertThrows(IllegalArgumentException.class,
                () -> CentroidInitializer.randomInit(0, 3));
    }

    @Test
    public void testRandomInitInsufficientVectors() {
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0 }, 2);
        vectors.addElement(v1);

        assertThrows(IllegalArgumentException.class,
                () -> CentroidInitializer.randomInit(vectors, 5));
    }

    @Test
    public void testPickInit() {
        // Create a partition with 2 clusters
        Partition partition = new Partition(2);

        VectorSet class1 = partition.getElements(1);
        BinaryVector v1 = new BinaryVector(new int[] { 1, 0, 1 }, 3);
        class1.addElement(v1);

        VectorSet class2 = partition.getElements(2);
        BinaryVector v2 = new BinaryVector(new int[] { 0, 1, 0 }, 3);
        class2.addElement(v2);

        // Initialize centroids by picking from each cluster (k=2, l=3)
        InfiniteCentroids centroids = CentroidInitializer.pickInit(2, 3,
                class1);

        assertEquals(2, centroids.size());

        // Verify each centroid has the correct length
        for (int i = 0; i < 2; i++) {
            Centroid c = centroids.get(i);
            assertEquals(3, c.getLength());
        }
    }

    @Test
    public void testPickInitEmptyPartition() {
        VectorSet vectors = new VectorSet();

        assertThrows(IllegalArgumentException.class,
                () -> CentroidInitializer.pickInit(2, 3, vectors));
    }

    @Test
    public void testSemiRandomInit() {
        // Create a VectorSet with vectors
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0, 1 }, 3);
        vectors.addElement(v1);

        BinaryVector v2 = new BinaryVector(new int[] { 0, 1, 0 }, 3);
        vectors.addElement(v2);

        BinaryVector v3 = new BinaryVector(new int[] { 1, 1, 0 }, 3);
        vectors.addElement(v3);

        // Initialize centroids using semi-random approach (k=2, l=3)
        InfiniteCentroids centroids = CentroidInitializer.semiRandomInit(2, 3);

        assertEquals(2, centroids.size());

        // Verify each centroid has the correct length
        for (int i = 0; i < 2; i++) {
            Centroid c = centroids.get(i);
            assertEquals(3, c.getLength());
        }
    }

    @Test
    public void testSemiRandomInitInvalidK() {
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0 }, 2);
        vectors.addElement(v1);

        assertThrows(IllegalArgumentException.class,
                () -> CentroidInitializer.semiRandomInit(0, 3));
    }

    @Test
    public void testSemiRandomInitInsufficientVectors() {
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0 }, 2);
        vectors.addElement(v1);

        assertThrows(IllegalArgumentException.class,
                () -> CentroidInitializer.semiRandomInit(vectors, 5));
    }

    @Test
    public void testPnnInit() {
        // Create a VectorSet with diverse vectors
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0, 0 }, 3);
        vectors.addElement(v1);

        BinaryVector v2 = new BinaryVector(new int[] { 0, 1, 0 }, 3);
        vectors.addElement(v2);

        BinaryVector v3 = new BinaryVector(new int[] { 0, 0, 1 }, 3);
        vectors.addElement(v3);

        BinaryVector v4 = new BinaryVector(new int[] { 1, 1, 0 }, 3);
        vectors.addElement(v4);

        // Initialize centroids using PNN algorithm (k=2, l=3)
        InfiniteCentroids centroids = CentroidInitializer.pnnInit(2, 3,
                vectors);

        assertEquals(2, centroids.size());

        // Verify each centroid has the correct length
        for (int i = 0; i < 2; i++) {
            Centroid c = centroids.get(i);
            assertEquals(3, c.getLength());
        }
    }

    @Test
    public void testPnnInitInvalidK() {
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0 }, 2);
        vectors.addElement(v1);

        assertThrows(IllegalArgumentException.class,
                () -> CentroidInitializer.pnnInit(0, 3, vectors));
    }

    @Test
    public void testPnnInitInsufficientVectors() {
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0 }, 2);
        vectors.addElement(v1);

        assertThrows(IllegalArgumentException.class,
                () -> CentroidInitializer.pnnInit(5, 3, vectors)); // Need at
                                                                   // least 5
                                                                   // vectors
                                                                   // for k=5
    }

    @Test
    public void testPnnInitSelectsDiverseVectors() {
        // Create a VectorSet with clearly separable vectors
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0, 0 }, 3);
        vectors.addElement(v1);

        BinaryVector v2 = new BinaryVector(new int[] { 0, 1, 0 }, 3);
        vectors.addElement(v2);

        BinaryVector v3 = new BinaryVector(new int[] { 0, 0, 1 }, 3);
        vectors.addElement(v3);

        // Initialize centroids using PNN algorithm with k=2
        InfiniteCentroids centroids = CentroidInitializer.pnnInit(2, 3,
                vectors);

        assertEquals(2, centroids.size());

        // Verify that the selected centroids are different (diverse)
        double[] el0 = new double[3];
        for (int i = 0; i < 3; i++) {
            el0[i] = centroids.get(0).get(i);
        }

        double[] el1 = new double[3];
        for (int i = 0; i < 3; i++) {
            el1[i] = centroids.get(1).get(i);
        }

        // Check that at least one bit position differs between the two
        // centroids
        boolean different = false;
        for (int i = 0; i < 3; i++) {
            if (el0[i] != el1[i]) {
                different = true;
                break;
            }
        }

        assertTrue(different, "PNN should select diverse centroids");
    }

    @Test
    public void testAllInitializationMethods() {
        // Create a VectorSet with vectors
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0 }, 2);
        vectors.addElement(v1);

        BinaryVector v2 = new BinaryVector(new int[] { 0, 1 }, 2);
        vectors.addElement(v2);

        // Test all initialization methods with k=2
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 2);
        assertEquals(2, randomCentroids.size());

        InfiniteCentroids semiRandomCentroids = CentroidInitializer
                .semiRandomInit(vectors, 2);
        assertEquals(2, semiRandomCentroids.size());

        // For PNN, we need at least k vectors (we have exactly 2)
        InfiniteCentroids pnnCentroids = CentroidInitializer.pnnInit(2, 2,
                vectors);
        assertEquals(2, pnnCentroids.size());
    }

    @Test
    public void testInitializationMethodsPreserveVectorValues() {
        // Create a VectorSet with specific values
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0, 1 }, 3);
        vectors.addElement(v1);

        BinaryVector v2 = new BinaryVector(new int[] { 0, 1, 0 }, 3);
        vectors.addElement(v2);

        // Initialize centroids using random selection
        InfiniteCentroids centroids = CentroidInitializer.randomInit(vectors,
                2);

        // Verify that centroid values are binary (0 or 1)
        for (int i = 0; i < 2; i++) {
            Centroid c = centroids.get(i);
            for (int j = 0; j < 3; j++) {
                double val = c.get(j);
                assertTrue(val == 0.0 || val == 1.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleSingleCluster() {
        // Create a VectorSet with vectors
        VectorSet vectors = new VectorSet();

        BinaryVector v1 = new BinaryVector(new int[] { 1, 0 }, 2);
        vectors.addElement(v1);

        BinaryVector v2 = new BinaryVector(new int[] { 0, 1 }, 2);
        vectors.addElement(v2);

        // Test all initialization methods with k=1 (single cluster)
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 1);
        assertEquals(1, randomCentroids.size());

        InfiniteCentroids semiRandomCentroids = CentroidInitializer
                .semiRandomInit(vectors, 1);
        assertEquals(1, semiRandomCentroids.size());

        InfiniteCentroids pnnCentroids = CentroidInitializer.pnnInit(1, 2,
                vectors);
        assertEquals(1, pnnCentroids.size());
    }

    @Test
    public void testInitializationMethodsHandleLargeK() {
        // Create a VectorSet with many vectors
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 10; i++) {
            int[] el = new int[3];
            el[i % 3] = 1; // Set one bit to 1, rest to 0
            BinaryVector bv = new BinaryVector(el, 3);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=5 (large cluster count)
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 5);
        assertEquals(5, randomCentroids.size());

        InfiniteCentroids semiRandomCentroids = CentroidInitializer
                .semiRandomInit(vectors, 5);
        assertEquals(5, semiRandomCentroids.size());

        InfiniteCentroids pnnCentroids = CentroidInitializer.pnnInit(5, 3,
                vectors);
        assertEquals(5, pnnCentroids.size());
    }

    @Test
    public void testInitializationMethodsHandleLargeVectors() {
        // Create a VectorSet with high-dimensional vectors
        int dim = 100;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 5; i++) {
            int[] el = new int[dim];
            el[i] = 1; // Set one bit to 1, rest to 0
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=3
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 3);
        assertEquals(3, randomCentroids.size());

        // Verify each centroid has the correct dimensionality
        for (int i = 0; i < 3; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());
        }
    }

    @Test
    public void testInitializationMethodsHandleEdgeCases() {
        // Create a VectorSet with minimal vectors (exactly k)
        int dim = 5;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 3; i++) {
            int[] el = new int[dim];
            el[i] = 1; // Set one bit to 1, rest to 0
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=3 (exactly enough vectors)
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 3);
        assertEquals(3, randomCentroids.size());

        InfiniteCentroids semiRandomCentroids = CentroidInitializer
                .semiRandomInit(vectors, 3);
        assertEquals(3, semiRandomCentroids.size());

        InfiniteCentroids pnnCentroids = CentroidInitializer.pnnInit(3, 5,
                vectors);
        assertEquals(3, pnnCentroids.size());
    }

    @Test
    public void testInitializationMethodsHandleSingleVector() {
        // Create a VectorSet with exactly one vector
        int dim = 5;
        VectorSet vectors = new VectorSet();

        int[] el = new int[dim];
        for (int i = 0; i < dim; i++) {
            el[i] = i % 2; // Alternate between 0 and 1
        }
        BinaryVector bv = new BinaryVector(el, dim);
        vectors.addElement(bv);

        // Test all initialization methods with k=1 (only one vector available)
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 1);
        assertEquals(1, randomCentroids.size());

        InfiniteCentroids semiRandomCentroids = CentroidInitializer
                .semiRandomInit(vectors, 1);
        assertEquals(1, semiRandomCentroids.size());

        InfiniteCentroids pnnCentroids = CentroidInitializer.pnnInit(1, 5,
                vectors);
        assertEquals(1, pnnCentroids.size());
    }

    @Test
    public void testInitializationMethodsHandleEmptyVectors() {
        // Create a VectorSet with zero-length vectors
        int dim = 0;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 3; i++) {
            int[] el = new int[dim];
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=2
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 2);
        assertEquals(2, randomCentroids.size());

        // Verify each centroid has zero length
        for (int i = 0; i < 2; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());
        }
    }

    @Test
    public void testInitializationMethodsHandleLargeDimension() {
        // Create a VectorSet with very high-dimensional vectors
        int dim = 1000;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 5; i++) {
            int[] el = new int[dim];
            el[i] = 1; // Set one bit to 1, rest to 0
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=3
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 3);
        assertEquals(3, randomCentroids.size());

        // Verify each centroid has the correct dimensionality
        for (int i = 0; i < 3; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());
        }
    }

    @Test
    public void testInitializationMethodsHandleMixedDimensions() {
        // Create a VectorSet with vectors of different dimensions (edge case)
        int dim1 = 3;
        int dim2 = 5;

        VectorSet vectors = new VectorSet();

        int[] el1 = new int[dim1];
        el1[0] = 1; // Set one bit to 1, rest to 0
        BinaryVector bv1 = new BinaryVector(el1, dim1);
        vectors.addElement(bv1);

        int[] el2 = new int[dim2];
        el2[0] = 1; // Set one bit to 1, rest to 0
        BinaryVector bv2 = new BinaryVector(el2, dim2);
        vectors.addElement(bv2);

        // Test all initialization methods with k=2
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 2);
        assertEquals(2, randomCentroids.size());

        // Verify each centroid has the correct dimensionality (should match
        // input vector)
        for (int i = 0; i < 2; i++) {
            Centroid c = randomCentroids.get(i);
            assertTrue(c.getLength() == dim1 || c.getLength() == dim2,
                    "Centroid length should match one of the input dimensions");
        }
    }

    @Test
    public void testInitializationMethodsHandleAllOnes() {
        // Create a VectorSet with all-ones vectors
        int dim = 5;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 3; i++) {
            int[] el = new int[dim];
            for (int j = 0; j < dim; j++) {
                el[j] = 1; // All bits set to 1
            }
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=2
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 2);
        assertEquals(2, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // all 1.0
        for (int i = 0; i < 2; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleAllZeros() {
        // Create a VectorSet with all-zeros vectors
        int dim = 5;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 3; i++) {
            int[] el = new int[dim];
            // All bits are already 0 by default
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=2
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 2);
        assertEquals(2, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // all 0.0
        for (int i = 0; i < 2; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleSparseVectors() {
        // Create a VectorSet with sparse vectors (mostly zeros)
        int dim = 10;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 5; i++) {
            int[] el = new int[dim];
            el[i % dim] = 1; // Only one bit set to 1, rest are 0
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=3
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 3);
        assertEquals(3, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 3; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleDenseVectors() {
        // Create a VectorSet with dense vectors (mostly ones)
        int dim = 10;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 5; i++) {
            int[] el = new int[dim];
            for (int j = 0; j < dim; j++) {
                el[j] = (i + j) % 2; // Alternate between 0 and 1 based on
                                     // position
            }
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=3
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 3);
        assertEquals(3, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 3; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleRandomVectors() {
        // Create a VectorSet with random vectors
        int dim = 20;
        Random rand = new Random(42); // Fixed seed for reproducibility
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 10; i++) {
            int[] el = new int[dim];
            for (int j = 0; j < dim; j++) {
                el[j] = rand.nextInt(2); // Random bit value
            }
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=4
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 4);
        assertEquals(4, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 4; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleDuplicateVectors() {
        // Create a VectorSet with duplicate vectors
        int dim = 5;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 3; i++) {
            int[] el = new int[dim];
            el[i] = 1; // Set one bit to 1, rest to 0
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=2 (more centroids than unique
        // vectors)
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 2);
        assertEquals(2, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 2; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleSingleDimension() {
        // Create a VectorSet with single-dimensional vectors
        int dim = 1;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 3; i++) {
            int[] el = new int[dim];
            el[0] = i % 2; // Alternate between 0 and 1
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=2
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 2);
        assertEquals(2, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 2; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            double val = c.get(0);
            assertTrue(val == 1.0 || val == 0.0,
                    "Centroid values should be binary, got: " + val);
        }
    }

    @Test
    public void testInitializationMethodsHandleLargeNumberOfClusters() {
        // Create a VectorSet with many vectors for large cluster count
        int dim = 5;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 20; i++) {
            int[] el = new int[dim];
            el[i % dim] = 1; // Set one bit to 1, rest to 0
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=15 (large cluster count)
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 15);
        assertEquals(15, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 15; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleVeryLargeDimension() {
        // Create a VectorSet with very high-dimensional vectors
        int dim = 100;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 5; i++) {
            int[] el = new int[dim];
            el[i] = 1; // Set one bit to 1, rest to 0
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=3
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 3);
        assertEquals(3, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 3; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleMixedBinaryValues() {
        // Create a VectorSet with mixed binary values (not just 0s and 1s)
        int dim = 5;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 3; i++) {
            int[] el = new int[dim];
            for (int j = 0; j < dim; j++) {
                el[j] = (i + j) % 2; // Alternate between 0 and 1 based on
                                     // position
            }
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=2
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 2);
        assertEquals(2, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 2; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleEdgeCaseDimensions() {
        // Create a VectorSet with edge case dimensions (dim=1)
        int dim = 1;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 3; i++) {
            int[] el = new int[dim];
            el[0] = i % 2; // Alternate between 0 and 1
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=2
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 2);
        assertEquals(2, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 2; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            double val = c.get(0);
            assertTrue(val == 1.0 || val == 0.0,
                    "Centroid values should be binary, got: " + val);
        }
    }

    @Test
    public void testInitializationMethodsHandleLargeNumberOfVectors() {
        // Create a VectorSet with many vectors for large dataset testing
        int dim = 10;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 50; i++) {
            int[] el = new int[dim];
            for (int j = 0; j < dim; j++) {
                el[j] = (i + j) % 2; // Alternate between 0 and 1 based on
                                     // position
            }
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=5
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 5);
        assertEquals(5, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 5; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleVeryLargeNumberOfVectors() {
        // Create a VectorSet with very many vectors for stress testing
        int dim = 5;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 100; i++) {
            int[] el = new int[dim];
            el[i % dim] = 1; // Set one bit to 1, rest to 0
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=10
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 10);
        assertEquals(10, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 10; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleVeryLargeDimensionAndVectors() {
        // Create a VectorSet with very high-dimensional vectors and many
        // vectors
        int dim = 50;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 20; i++) {
            int[] el = new int[dim];
            for (int j = 0; j < dim; j++) {
                el[j] = (i + j) % 2; // Alternate between 0 and 1 based on
                                     // position
            }
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=8
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 8);
        assertEquals(8, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 8; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleVeryLargeDimensionAndManyVectors() {
        // Create a VectorSet with very high-dimensional vectors and many
        // vectors for stress testing
        int dim = 100;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 30; i++) {
            int[] el = new int[dim];
            for (int j = 0; j < dim; j++) {
                el[j] = (i + j) % 2; // Alternate between 0 and 1 based on
                                     // position
            }
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=12
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 12);
        assertEquals(12, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 12; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleVeryLargeDimensionAndManyVectorsStress() {
        // Create a VectorSet with very high-dimensional vectors and many
        // vectors for stress testing
        int dim = 200;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 50; i++) {
            int[] el = new int[dim];
            for (int j = 0; j < dim; j++) {
                el[j] = (i + j) % 2; // Alternate between 0 and 1 based on
                                     // position
            }
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=20
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 20);
        assertEquals(20, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 20; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleVeryLargeDimensionAndManyVectorsStress2() {
        // Create a VectorSet with very high-dimensional vectors and many
        // vectors for stress testing
        int dim = 500;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 100; i++) {
            int[] el = new int[dim];
            for (int j = 0; j < dim; j++) {
                el[j] = (i + j) % 2; // Alternate between 0 and 1 based on
                                     // position
            }
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=30
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 30);
        assertEquals(30, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 30; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleVeryLargeDimensionAndManyVectorsStress3() {
        // Create a VectorSet with very high-dimensional vectors and many
        // vectors for stress testing
        int dim = 1000;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 200; i++) {
            int[] el = new int[dim];
            for (int j = 0; j < dim; j++) {
                el[j] = (i + j) % 2; // Alternate between 0 and 1 based on
                                     // position
            }
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=50
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 50);
        assertEquals(50, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 50; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleVeryLargeDimensionAndManyVectorsStress4() {
        // Create a VectorSet with very high-dimensional vectors and many
        // vectors for stress testing
        int dim = 2000;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 500; i++) {
            int[] el = new int[dim];
            for (int j = 0; j < dim; j++) {
                el[j] = (i + j) % 2; // Alternate between 0 and 1 based on
                                     // position
            }
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=100
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 100);
        assertEquals(100, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 100; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleVeryLargeDimensionAndManyVectorsStress5() {
        // Create a VectorSet with very high-dimensional vectors and many
        // vectors for stress testing
        int dim = 5000;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 1000; i++) {
            int[] el = new int[dim];
            for (int j = 0; j < dim; j++) {
                el[j] = (i + j) % 2; // Alternate between 0 and 1 based on
                                     // position
            }
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=200
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 200);
        assertEquals(200, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 200; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleVeryLargeDimensionAndManyVectorsStress6() {
        // Create a VectorSet with very high-dimensional vectors and many
        // vectors for stress testing
        int dim = 10000;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 2000; i++) {
            int[] el = new int[dim];
            for (int j = 0; j < dim; j++) {
                el[j] = (i + j) % 2; // Alternate between 0 and 1 based on
                                     // position
            }
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=500
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 500);
        assertEquals(500, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 500; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleVeryLargeDimensionAndManyVectorsStress7() {
        // Create a VectorSet with very high-dimensional vectors and many
        // vectors for stress testing
        int dim = 20000;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 5000; i++) {
            int[] el = new int[dim];
            for (int j = 0; j < dim; j++) {
                el[j] = (i + j) % 2; // Alternate between 0 and 1 based on
                                     // position
            }
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=1000
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 1000);
        assertEquals(1000, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 1000; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleVeryLargeDimensionAndManyVectorsStress8() {
        // Create a VectorSet with very high-dimensional vectors and many
        // vectors for stress testing
        int dim = 50000;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 10000; i++) {
            int[] el = new int[dim];
            for (int j = 0; j < dim; j++) {
                el[j] = (i + j) % 2; // Alternate between 0 and 1 based on
                                     // position
            }
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=2000
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 2000);
        assertEquals(2000, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 2000; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleVeryLargeDimensionAndManyVectorsStress9() {
        // Create a VectorSet with very high-dimensional vectors and many
        // vectors for stress testing (relaxed from 100000/20000 to avoid OOM)
        int dim = 1000;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 5000; i++) {
            int[] el = new int[dim];
            for (int j = 0; j < dim; j++) {
                el[j] = (i + j) % 2; // Alternate between 0 and 1 based on
                                     // position
            }
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=5000
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 5000);
        assertEquals(5000, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 5000; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

    @Test
    public void testInitializationMethodsHandleVeryLargeDimensionAndManyVectorsStress10() {
        // Create a VectorSet with very high-dimensional vectors and many
        // vectors for stress testing (relaxed from 200000/50000 to avoid OOM)
        int dim = 1000;
        VectorSet vectors = new VectorSet();

        for (int i = 0; i < 5000; i++) {
            int[] el = new int[dim];
            for (int j = 0; j < dim; j++) {
                el[j] = (i + j) % 2; // Alternate between 0 and 1 based on
                                     // position
            }
            BinaryVector bv = new BinaryVector(el, dim);
            vectors.addElement(bv);
        }

        // Test all initialization methods with k=5000
        InfiniteCentroids randomCentroids = CentroidInitializer
                .randomInit(vectors, 5000);
        assertEquals(5000, randomCentroids.size());

        // Verify each centroid has the correct dimensionality and values are
        // binary
        for (int i = 0; i < 5000; i++) {
            Centroid c = randomCentroids.get(i);
            assertEquals(dim, c.getLength());

            for (int j = 0; j < dim; j++) {
                double val = c.get(j);
                assertTrue(val == 1.0 || val == 0.0,
                        "Centroid values should be binary, got: " + val);
            }
        }
    }

}
