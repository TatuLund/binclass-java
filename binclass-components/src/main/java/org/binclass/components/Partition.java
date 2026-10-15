/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.shared.SlotUtils;
import org.jspecify.annotations.NullMarked;

/**
 * Vaadin wrapper for the {@code binclass-partition} web component.
 * <p>
 * Renders a partition header with global metrics (Stochastic Complexity,
 * cluster count, vector count) and a body composed of {@link Cluster}
 * components. The header is pushed as properties on this element; the clusters
 * are added as children in the {@code body} slot.
 * </p>
 */
@NullMarked
@Tag("binclass-partition")
@JsModule("./partition.ts")
public class Partition extends Component {

    /** Slot name for the header child (optional custom header). */
    public static final String SLOT_HEADER = "header";

    /** Slot name for the cluster children. */
    public static final String SLOT_BODY = "body";

    /**
     * Sets all presentation data from a {@link PartitionModel} record, pushing
     * header metrics to the client and replacing the clusters in the body slot
     * with freshly built {@link Cluster} wrappers.
     *
     * @param model
     *            the immutable partition presentation data; must not be null
     */
    public void setModel(PartitionModel model) {
        if (model == null) {
            throw new IllegalArgumentException(
                    "PartitionModel must not be null");
        }
        getElement().setProperty("stochasticComplexity",
                model.stochasticComplexity());
        getElement().setProperty("clusterCount", model.clusterCount());
        getElement().setProperty("vectorCount", model.vectorCount());
        if (Double.isNaN(model.overallDistortion())) {
            getElement().setProperty("overallDistortion", Double.NaN);
        } else {
            getElement().setProperty("overallDistortion",
                    model.overallDistortion());
        }

        List<Component> clusters = new ArrayList<>();
        for (ClusterData cluster : model.clusters()) {
            clusters.add(new Cluster(cluster));
        }
        if (!clusters.isEmpty()) {
            SlotUtils.setSlot(this, SLOT_BODY,
                    clusters.toArray(new Component[0]));
        } else {
            SlotUtils.clearSlot(this, SLOT_BODY);
        }
    }

    /**
     * Sets the Stochastic Complexity shown in the header.
     *
     * @param stochasticComplexity
     *            the partition's Stochastic Complexity (lower is better)
     */
    public void setStochasticComplexity(double stochasticComplexity) {
        getElement().setProperty("stochasticComplexity", stochasticComplexity);
    }

    /**
     * Sets the number of clusters shown in the header.
     *
     * @param clusterCount
     *            the number of clusters
     */
    public void setClusterCount(int clusterCount) {
        getElement().setProperty("clusterCount", clusterCount);
    }

    /**
     * Sets the total vector count shown in the header.
     *
     * @param vectorCount
     *            the total number of vectors across all clusters
     */
    public void setVectorCount(int vectorCount) {
        getElement().setProperty("vectorCount", vectorCount);
    }

    /**
     * Sets the overall distortion shown in the header when cheaply available.
     *
     * @param overallDistortion
     *            average Hamming distance to centroid, or {@code NaN} when
     *            unavailable
     */
    public void setOverallDistortion(double overallDistortion) {
        getElement().setProperty("overallDistortion", overallDistortion);
    }

    /**
     * Adds a cluster to the body of this partition. The cluster is placed in
     * the {@code body} slot.
     *
     * @param cluster
     *            the cluster to add; must not be null
     */
    public void addCluster(Cluster cluster) {
        if (cluster == null) {
            throw new IllegalArgumentException("Cluster must not be null");
        }
        SlotUtils.addToSlot(this, SLOT_BODY, cluster);
    }

    /**
     * Returns the number of clusters currently rendered in the body.
     *
     * @return the count of cluster children in the {@code body} slot
     */
    public int getClusterCount() {
        return (int) SlotUtils.getElementsInSlot(this, SLOT_BODY).filter(
                element -> element.getComponent().isPresent()).count();
    }

    /**
     * Returns the clusters currently rendered in the body of this partition.
     *
     * @return an ordered list of the {@link Cluster} components placed in the
     *         {@code body} slot
     */
    public List<Cluster> getClusters() {
        return SlotUtils.getElementsInSlot(this, SLOT_BODY)
                .filter(element -> element.getComponent().isPresent())
                .map(element -> (Cluster) element.getComponent().get())
                .collect(Collectors.toList());
    }
}
