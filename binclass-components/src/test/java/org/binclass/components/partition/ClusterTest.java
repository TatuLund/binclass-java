/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.partition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.vaadin.flow.component.shared.SlotUtils;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Cluster}.
 */
class ClusterTest {

    private CentroidData centroid() {
        return new CentroidData(new double[] { 1.0, 0.0, 1.0, 1.0 });
    }

    @Test
    void constructorWiresCentroidIntoSlot() {
        ClusterData data = new ClusterData("cluster-1", 2.5, 0.4, 3,
                centroid());
        Cluster cluster = new Cluster(data);

        Centroid stored = cluster.getCentroid();
        assertSame(stored,
                SlotUtils.getChildInSlot(cluster, Cluster.SLOT_CENTROID));
    }

    @Test
    void constructorBuildsSummaryText() {
        ClusterData data = new ClusterData("cluster-1", 2.5, 0.4, 3,
                centroid());
        Cluster cluster = new Cluster(data);
        assertEquals("cluster-1  (n=3, dist=2.500, w=0.400)",
                cluster.getSummaryText());
    }

    @Test
    void constructorRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> new Cluster(null));
    }

    @Test
    void setVectorsPopulatesSlot() {
        Cluster cluster = new Cluster(
                new ClusterData("c", 1.0, 0.5, 2, centroid()));
        List<VectorData> vectors = List.of(
                new VectorData(new int[] { 1, 0, 1, 0 }, "v1"),
                new VectorData(new int[] { 0, 1, 0, 1 }, "v2"));
        cluster.setVectors(vectors);

        long count = SlotUtils.getElementsInSlot(cluster, Cluster.SLOT_VECTORS)
                .filter(e -> e.getComponent().isPresent())
                .count();
        assertEquals(2, count);
    }

    @Test
    void setVectorsClearsPrevious() {
        Cluster cluster = new Cluster(
                new ClusterData("c", 1.0, 0.5, 2, centroid()));
        cluster.setVectors(List.of(new VectorData(new int[] { 1, 0 }, "v1")));
        cluster.setVectors(List.of(
                new VectorData(new int[] { 1, 0 }, "a"),
                new VectorData(new int[] { 0, 1 }, "b")));

        long count = SlotUtils.getElementsInSlot(cluster, Cluster.SLOT_VECTORS)
                .filter(e -> e.getComponent().isPresent())
                .count();
        assertEquals(2, count);
    }

    @Test
    void setVectorsRejectsNull() {
        Cluster cluster = new Cluster(
                new ClusterData("c", 1.0, 0.5, 2, centroid()));
        assertThrows(IllegalArgumentException.class,
                () -> cluster.setVectors(null));
    }

    @Test
    void opensPopulatesVectorsFromBuffer() {
        ClusterData data = new ClusterData("c", 1.0, 0.5, 2, centroid(),
                List.of(
                        new VectorData(new int[] { 1, 0 }, "v1"),
                        new VectorData(new int[] { 0, 1 }, "v2")));
        Cluster cluster = new Cluster(data);

        long before = SlotUtils.getElementsInSlot(cluster, Cluster.SLOT_VECTORS)
                .filter(e -> e.getComponent().isPresent()).count();
        assertEquals(0, before);

        cluster.setOpened(true);

        long after = SlotUtils.getElementsInSlot(cluster, Cluster.SLOT_VECTORS)
                .filter(e -> e.getComponent().isPresent()).count();
        assertEquals(2, after);
    }

    @Test
    void isOpenClusteredDelegatesToOpenedState() {
        Cluster cluster = new Cluster(
                new ClusterData("c", 1.0, 0.5, 2, centroid()));
        assertFalse(cluster.isOpenClustered());
        cluster.setOpened(true);
        assertTrue(cluster.isOpenClustered());
    }

    @Test
    void setCentroidReplacesChild() {
        Cluster cluster = new Cluster(
                new ClusterData("c", 1.0, 0.5, 2, centroid()));
        CentroidData newData = new CentroidData(new double[] { 0.0, 1.0 });
        cluster.setCentroid(newData);

        Centroid stored = cluster.getCentroid();
        assertSame(stored,
                SlotUtils.getChildInSlot(cluster, Cluster.SLOT_CENTROID));
    }

    @Test
    void setCentroidRejectsNull() {
        Cluster cluster = new Cluster(
                new ClusterData("c", 1.0, 0.5, 2, centroid()));
        assertThrows(IllegalArgumentException.class,
                () -> cluster.setCentroid(null));
    }
}
