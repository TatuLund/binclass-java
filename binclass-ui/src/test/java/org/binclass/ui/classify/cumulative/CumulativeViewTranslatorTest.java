/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.cumulative;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.DynamicPartition;
import org.binclass.components.partition.ClusterData;
import org.binclass.components.partition.PartitionModel;

/**
 * Unit tests for {@link CumulativeViewTranslator}, covering the algorithm-to-
 * presentation mapping described in {@code docs/WebUI.PRD.md} §5.2.1 — done
 * here in the UI module so the components stay free of an algorithms
 * dependency.
 */
class CumulativeViewTranslatorTest {

    /** Builds a two-cluster dynamic partition with labelled vectors. */
    private static DynamicPartition sampleDynamicPartition() {
        DynamicPartition dynPart = new DynamicPartition(2, 4);
        BinaryVector a = new BinaryVector(new int[] { 1, 0, 1, 0 }, 4);
        a.setStrain("alpha");
        BinaryVector b = new BinaryVector(new int[] { 1, 0, 1, 0 }, 4);
        b.setStrain("alpha");
        BinaryVector c = new BinaryVector(new int[] { 0, 1, 0, 1 }, 4);
        c.setStrain("beta");
        dynPart.putVector(1, a);
        dynPart.putVector(1, b);
        dynPart.putVector(2, c);
        return dynPart;
    }

    @Test
    void toPartitionModelBuildsOneClusterPerCluster() {
        PartitionModel model = CumulativeViewTranslator.toPartitionModel(
                sampleDynamicPartition());

        assertEquals(2, model.clusterCount());
        assertEquals(3, model.vectorCount());
        assertNotNull(model.stochasticComplexity());
        assertTrue(Double.isFinite(model.stochasticComplexity()));
    }

    @Test
    void toPartitionModelUsesMostFrequentStrainAsName() {
        PartitionModel model = CumulativeViewTranslator.toPartitionModel(
                sampleDynamicPartition());
        ClusterData cluster1 = model.clusters().get(0);

        assertEquals("alpha", cluster1.name());
    }

    @Test
    void toPartitionModelFallsBackToIndexWhenNoLabels() {
        DynamicPartition dynPart = new DynamicPartition(1, 3);
        BinaryVector a = new BinaryVector(new int[] { 1, 0, 0 }, 3);
        BinaryVector b = new BinaryVector(new int[] { 0, 1, 0 }, 3);
        dynPart.putVector(1, a);
        dynPart.putVector(1, b);

        PartitionModel model = CumulativeViewTranslator.toPartitionModel(
                dynPart);
        ClusterData cluster = model.clusters().get(0);

        assertEquals("1", cluster.name());
    }

    @Test
    void toPartitionModelDerivesCentroidProbabilitiesFromFrequencies() {
        PartitionModel model = CumulativeViewTranslator.toPartitionModel(
                sampleDynamicPartition());
        ClusterData cluster1 = model.clusters().get(0);

        // Two vectors [1,0,1,0] -> bit 0 and bit 2 each have probability 1.
        double[] probs = cluster1.centroid().probabilities();
        assertEquals(1.0, probs[0]);
        assertEquals(0.0, probs[1]);
        assertEquals(1.0, probs[2]);
        assertEquals(0.0, probs[3]);
    }

    @Test
    void toPartitionModelCarriesLazyVectorData() {
        PartitionModel model = CumulativeViewTranslator.toPartitionModel(
                sampleDynamicPartition());
        ClusterData cluster1 = model.clusters().get(0);

        assertEquals(2, cluster1.vectors().size());
        assertEquals("alpha", cluster1.vectors().get(0).identifier());
    }

    @Test
    void toPartitionModelToleratesEmptyPartition() {
        PartitionModel model = CumulativeViewTranslator.toPartitionModel(
                new DynamicPartition(2, 4));

        assertEquals(0, model.clusterCount());
        assertEquals(0, model.vectorCount());
    }

    @Test
    void toPartitionModelToleratesNullPartition() {
        PartitionModel model = CumulativeViewTranslator.toPartitionModel(null);

        assertEquals(0, model.clusterCount());
        assertEquals(0, model.vectorCount());
    }
}
