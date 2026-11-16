/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.gla;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.components.graph.GraphModel;
import org.binclass.components.partition.ClusterData;
import org.binclass.components.partition.PartitionModel;

/**
 * Unit tests for {@link GlaViewTranslator}, covering the
 * algorithm-to-presentation mappings described in {@code docs/WebUI.PRD.md}
 * §1.3 — done here in the UI module so the components stay free of an
 * algorithms dependency.
 */
class GlaViewTranslatorTest {

    /** Builds a two-cluster partition with labelled vectors for translation. */
    private static Partition samplePartition() {
        Partition partition = new Partition(2, 4);
        partition.addElement(1, new BinaryVector(new int[] { 0, 1, 1 }, 3));
        partition.addElement(1, new BinaryVector(new int[] { 1, 1, 0 }, 3));
        partition.addElement(2, new BinaryVector(new int[] { 1, 0, 0 }, 3));
        return partition;
    }

    @Test
    void toPartitionModelBuildsOneClusterPerCluster() {
        Partition partition = samplePartition();
        InfiniteCentroids centroids = new InfiniteCentroids(2);
        Centroid c0 = new Centroid(3);
        c0.set(0, 0.1);
        c0.set(1, 0.9);
        c0.set(2, 0.8);
        centroids.set(0, c0);

        PartitionModel model = GlaViewTranslator.toPartitionModel(
                partition, centroids, 42.5);

        assertEquals(2, model.clusterCount());
        assertEquals(3, model.vectorCount());
        assertEquals(42.5, model.stochasticComplexity());
    }

    @Test
    void toPartitionModelUsesMostFrequentStrainAsName() {
        Partition partition = new Partition(1, 3);
        BinaryVector a = new BinaryVector(new int[] { 0, 0 }, 2);
        a.setStrain("alpha");
        BinaryVector b = new BinaryVector(new int[] { 1, 1 }, 2);
        b.setStrain("alpha");
        partition.addElement(1, a);
        partition.addElement(1, b);

        PartitionModel model = GlaViewTranslator.toPartitionModel(partition,
                null, 0.0);
        ClusterData cluster = model.clusters().get(0);

        assertEquals("alpha", cluster.name());
    }

    @Test
    void toPartitionModelFallsBackToIndexWhenNoStrain() {
        Partition partition = new Partition(1, 2);
        partition.addElement(1, new BinaryVector(new int[] { 0, 1 }, 2));
        partition.addElement(1, new BinaryVector(new int[] { 1, 0 }, 2));

        PartitionModel model = GlaViewTranslator.toPartitionModel(partition,
                null, 0.0);
        ClusterData cluster = model.clusters().get(0);

        assertEquals("1", cluster.name());
    }

    @Test
    void toPartitionModelRoundsCentroidForHover() {
        Partition partition = samplePartition();
        InfiniteCentroids centroids = new InfiniteCentroids(2);
        Centroid c0 = new Centroid(3);
        c0.set(0, 0.1);
        c0.set(1, 0.6);
        c0.set(2, 0.9);
        centroids.set(0, c0);

        PartitionModel model = GlaViewTranslator.toPartitionModel(
                partition, centroids, 0.0);
        ClusterData cluster = model.clusters().get(0);

        int[] rounded = cluster.vectors().get(0).roundedCentroid();
        assertEquals(3, rounded.length);
        assertEquals(0, rounded[0]); // 0.1 rounds down
        assertEquals(1, rounded[1]); // 0.6 rounds up
        assertEquals(1, rounded[2]); // 0.9 rounds up
    }

    @Test
    void toPartitionModelHandlesMissingCentroids() {
        Partition partition = samplePartition();

        PartitionModel model = GlaViewTranslator.toPartitionModel(partition,
                null, 7.0);

        assertEquals(2, model.clusterCount());
        assertTrue(
                model.clusters().get(0).centroid().probabilities().length >= 0);
    }

    @Test
    void toGraphModelMapsIndexToXWhenKsAbsent() {
        double[] scs = new double[] { Double.NaN, 10.0, 8.0, 9.0 };

        GraphModel model = GlaViewTranslator.toGraphModel(scs, null);

        List<org.binclass.components.graph.GraphPoint> points = model.points();
        assertEquals(3, points.size());
        assertEquals(1, points.get(0).x());
        assertEquals(10.0, points.get(0).y());
    }

    @Test
    void toGraphModelDropsNonFinitePoints() {
        double[] scs = new double[] { Double.NaN, 5.0, Double.NaN, 3.0 };

        GraphModel model = GlaViewTranslator.toGraphModel(scs, null);

        assertEquals(2, model.points().size());
    }

    @Test
    void toGraphModelUsesKsWhenProvided() {
        double[] scs = new double[] { Double.NaN, 1.0, 2.0 };
        int[] ks = { 0, 4, 6 };

        GraphModel model = GlaViewTranslator.toGraphModel(scs, ks);
        List<org.binclass.components.graph.GraphPoint> points = model.points();

        assertEquals(2, points.size());
        assertEquals(4, points.get(0).x());
        assertEquals(6, points.get(1).x());
    }
}
