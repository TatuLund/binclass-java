/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.partition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.shared.SlotUtils;
import com.vaadin.flow.signals.local.ValueSignal;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Partition}.
 */
@SuppressWarnings("null")
class PartitionTest {

    private ClusterData cluster(String name, int vectorCount) {
        return new ClusterData(name, 1.0, 0.5, vectorCount,
                new CentroidData(new double[] { 1.0, 0.0 }));
    }

    @Test
    void setModelPushesHeaderMetrics() {
        PartitionModel model = new PartitionModel(
                1234.5,
                3,
                90,
                2.75,
                List.of(cluster("a", 30), cluster("b", 30), cluster("c", 30)));
        Partition partition = new Partition();
        partition.setModel(model);

        assertEquals(1234.5, partition.getElement()
                .getProperty("stochasticComplexity", Double.NaN));
        assertEquals(3, partition.getElement().getProperty("clusterCount", -1));
        assertEquals(90, partition.getElement().getProperty("vectorCount", -1));
        assertEquals(2.75, partition.getElement()
                .getProperty("overallDistortion", Double.NaN));
    }

    @Test
    void setModelRejectsNull() {
        Partition partition = new Partition();
        assertThrows(IllegalArgumentException.class,
                () -> partition.setModel(null));
    }

    @Test
    void setModelBuildsClustersInBodySlot() {
        PartitionModel model = new PartitionModel(
                10.0,
                2,
                4,
                Double.NaN,
                List.of(cluster("a", 2), cluster("b", 2)));
        Partition partition = new Partition();
        partition.setModel(model);

        long count = SlotUtils.getElementsInSlot(partition, Partition.SLOT_BODY)
                .filter(e -> e.getComponent().isPresent())
                .count();
        assertEquals(2, count);
    }

    @Test
    void setModelClearsBodyWhenNoClusters() {
        PartitionModel model = new PartitionModel(5.0);
        Partition partition = new Partition();
        partition.setModel(model);

        long count = SlotUtils.getElementsInSlot(partition, Partition.SLOT_BODY)
                .filter(e -> e.getComponent().isPresent())
                .count();
        assertEquals(0, count);
    }

    @Test
    void addClusterAppendsToBody() {
        Partition partition = new Partition();
        Cluster first = new Cluster(cluster("a", 1));
        Cluster second = new Cluster(cluster("b", 1));
        partition.addCluster(first);
        partition.addCluster(second);

        assertEquals(2, partition.getClusterCount());
    }

    @Test
    void addClusterRejectsNull() {
        Partition partition = new Partition();
        assertThrows(IllegalArgumentException.class,
                () -> partition.addCluster(null));
    }

    @Test
    void getClusterCountCountsOnlyComponentBackedElements() {
        Partition partition = new Partition();
        Cluster cluster = new Cluster(cluster("a", 1));
        com.vaadin.flow.dom.Element plain = new com.vaadin.flow.dom.Element(
                "div");
        partition.addCluster(cluster);
        SlotUtils.addToSlot(partition, Partition.SLOT_BODY, plain);

        assertEquals(1, partition.getClusterCount());
    }

    @Test
    void settersExposeHeaderMetrics() {
        Partition partition = new Partition();
        partition.setStochasticComplexity(99.0);
        partition.setClusterCount(5);
        partition.setVectorCount(200);
        partition.setOverallDistortion(1.25);

        assertEquals(99.0, partition.getElement()
                .getProperty("stochasticComplexity", Double.NaN));
        assertEquals(5, partition.getElement().getProperty("clusterCount", -1));
        assertEquals(200,
                partition.getElement().getProperty("vectorCount", -1));
        assertEquals(1.25, partition.getElement()
                .getProperty("overallDistortion", Double.NaN));
    }

    @Test
    void setModelOverwritesExistingClusters() {
        Partition partition = new Partition();
        partition.addCluster(new Cluster(cluster("old", 1)));
        partition.setModel(new PartitionModel(
                1.0,
                1,
                2,
                Double.NaN,
                List.of(cluster("new", 2))));

        assertEquals(1, partition.getClusterCount());
    }

    @Test
    void bindPartitionModelSetsPropertyOnElement() {
        Partition partition = new Partition();
        ValueSignal<PartitionModel> signal = new ValueSignal<>(
                new PartitionModel(5.0));

        partition.bindPartitionModel(signal);

        org.junit.jupiter.api.Assertions.assertTrue(partition.getElement()
                .hasProperty(Partition.PROPERTY_MODEL));
    }

    @Test
    void setModelUpdatesBoundSignal() {
        Partition partition = new Partition();
        ValueSignal<PartitionModel> signal = new ValueSignal<>(
                new PartitionModel(1.0));
        partition.bindPartitionModel(signal);

        PartitionModel updated = new PartitionModel(
                42.0,
                2,
                8,
                Double.NaN,
                List.of(cluster("a", 4), cluster("b", 4)));
        partition.setModel(updated);

        assertEquals(updated, signal.peek());
    }

    @Test
    void setModelUsesSignalPathWhenBound() {
        Partition partition = new Partition();
        ValueSignal<PartitionModel> signal = new ValueSignal<>(
                new PartitionModel(1.0));
        partition.bindPartitionModel(signal);
        partition.setModel(new PartitionModel(
                42.0,
                2,
                8,
                Double.NaN,
                List.of(cluster("a", 4), cluster("b", 4))));

        // Header metrics travel inside the bound object, not as individual
        // scalar properties.
        assertEquals(Double.NaN,
                partition.getElement().getProperty("stochasticComplexity",
                        Double.NaN));
    }

    @Test
    void bindPartitionModelNullClearsBoundSignal() {
        Partition partition = new Partition();
        ValueSignal<PartitionModel> signal = new ValueSignal<>(
                new PartitionModel(1.0));
        partition.bindPartitionModel(signal);

        partition.bindPartitionModel((ValueSignal<PartitionModel>) null);

        // After unbinding, setModel falls back to pushing scalar properties
        // because no signal is bound anymore.
        partition.setModel(new PartitionModel(42.0));
        assertEquals(42.0, partition.getElement()
                .getProperty("stochasticComplexity", Double.NaN));
    }

    @Test
    void signalSetPropagatesToComponentAutomatically() {
        PartitionModel model = new PartitionModel(
                10.0,
                2,
                4,
                Double.NaN,
                List.of(cluster("a", 2), cluster("b", 2)));
        ValueSignal<PartitionModel> signal = new ValueSignal<>(model);
        Partition partition = new Partition();
        partition.bindPartitionModel(signal);

        UI ui = new UI();
        ui.setId("reactive");
        ui.getElement().appendChild(partition.getElement());
        UI.setCurrent(ui);

        // Directly change the bound value; no setModel() call is made.
        PartitionModel updated = new PartitionModel(
                20.0,
                3,
                6,
                Double.NaN,
                List.of(cluster("a", 2), cluster("b", 2), cluster("c", 2)));
        signal.set(updated);

        // Clusters are rebuilt reactively through the bound signal effect.
        assertEquals(3, partition.getClusterCount());
    }
}
