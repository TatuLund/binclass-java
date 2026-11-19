/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.gla;

import java.util.Random;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.dist.DistanceCalculator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Reproduces the "Division by zero in stochasticComplexityUniform" bug that
 * occurs when running Adaptive (heuristic 8) or Cycler (heuristic 7).
 */
class LocalSearchDivZeroReproTest {

    private static final int L = 4;

    private Partition buildThreeClusterPartition() {
        Partition p = new Partition(3);
        p.addElement(1, new BinaryVector(new int[] { 0, 0, 0, 0 }, L));
        p.addElement(1, new BinaryVector(new int[] { 0, 0, 0, 1 }, L));
        p.addElement(1, new BinaryVector(new int[] { 0, 0, 1, 0 }, L));

        p.addElement(2, new BinaryVector(new int[] { 1, 1, 1, 1 }, L));
        p.addElement(2, new BinaryVector(new int[] { 1, 1, 1, 0 }, L));
        p.addElement(2, new BinaryVector(new int[] { 1, 1, 0, 1 }, L));

        p.addElement(3, new BinaryVector(new int[] { 1, 0, 1, 0 }, L));
        p.addElement(3, new BinaryVector(new int[] { 0, 1, 0, 1 }, L));
        p.addElement(3, new BinaryVector(new int[] { 1, 1, 0, 0 }, L));
        return p;
    }

    private InfiniteCentroids buildThreeCentroids() {
        return new InfiniteCentroids(new double[][] {
                { 0.1, 0.1, 0.1, 0.1 },
                { 0.9, 0.9, 0.9, 0.9 },
                { 0.5, 0.5, 0.5, 0.5 },
        }, 3);
    }

    @Test
    void reproduceDivZeroAcrossSeeds() {
        int failures = 0;
        for (int seed = 0; seed < 200; seed++) {
            Partition p = buildThreeClusterPartition();
            InfiniteCentroids c = buildThreeCentroids();
            int n = 9;
            try {
                LocalSearch.localSearch(p, c, 50, L, n, false,
                        new Random(seed));
            } catch (ArithmeticException ex) {
                failures++;
                System.out.println("Seed " + seed + " triggered: "
                        + ex.getMessage());
            }
        }
        System.out.println("Total failures across 200 seeds: " + failures);
    }

    @Test
    void directK1DividesByZero() {
        Partition p = new Partition(3); // all clusters empty
        assertThrows(ArithmeticException.class,
                () -> DistanceCalculator.stochasticComplexityUniform(p, 1, L));
    }
}
