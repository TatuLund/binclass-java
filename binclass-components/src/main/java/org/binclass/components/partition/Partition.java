/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.partition;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.shared.SlotUtils;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import com.vaadin.flow.shared.Registration;
import org.jspecify.annotations.NullMarked;

/**
 * Vaadin wrapper for the {@code binclass-partition} web component.
 * <p>
 * Renders a partition header with global metrics (Stochastic Complexity,
 * cluster count, vector count) and a body composed of {@link Cluster}
 * components. The header is pushed either as individual properties on this
 * element or reactively as a whole object through a bound signal; the clusters
 * are added as children in the {@code body} slot.
 * </p>
 */
@NullMarked
@Tag("binclass-partition")
@JsModule("./src/partition.ts")
public class Partition extends Component {

    /** Slot name for the header child (optional custom header). */
    public static final String SLOT_HEADER = "header";

    /** Slot name for the cluster children. */
    public static final String SLOT_BODY = "body";

    /**
     * Property name under which the whole {@link PartitionModel} is pushed to
     * the client when bound as a reactive signal through
     * {@link #bindPartitionModel(ValueSignal)}. The header metrics and cluster
     * list travel together inside this object.
     */
    public static final String PROPERTY_MODEL = "partition";

    /**
     * Signal holding the currently bound model, or {@code null} when unbound.
     */
    private ValueSignal<PartitionModel> modelSignal;

    /**
     * Registration of the signal effect that rebuilds the body clusters
     * whenever the bound {@link #modelSignal} changes. Removed when the
     * partition is unbound through {@link #bindPartitionModel(ValueSignal)}
     * with a null value.
     */
    private Registration effectRegistration;

    /**
     * Sets all presentation data from a {@link PartitionModel} record, pushing
     * header metrics to the client and replacing the clusters in the body slot
     * with freshly built {@link Cluster} wrappers.
     * <p>
     * When a signal is bound through {@link #bindPartitionModel(ValueSignal)},
     * the model travels reactively through that signal instead of setting the
     * header properties individually, so later updates stay in sync with any
     * external changes to the bound value.
     *
     * @param model
     *            the immutable partition presentation data; must not be null
     */
    public void setModel(PartitionModel model) {
        if (model == null) {
            throw new IllegalArgumentException(
                    "PartitionModel must not be null");
        }
        if (modelSignal != null) {
            modelSignal.set(model);
        } else {
            pushHeaderMetrics(model);
        }
        rebuildClusters(model.clusters());
    }

    /**
     * Pushes the header metrics of a model to the client as individual
     * properties. Called when no partition-model signal is bound; ignored when
     * one is, since the whole object travels through {@link #PROPERTY_MODEL}.
     *
     * @param model
     *            the immutable partition presentation data; must not be null
     */
    private void pushHeaderMetrics(PartitionModel model) {
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
    }

    /**
     * Replaces the clusters rendered in the body slot with freshly built
     * {@link Cluster} wrappers, clearing the slot when none are provided.
     *
     * @param clusters
     *            the cluster presentation data to render; may be empty but must
     *            not be null
     */
    private void rebuildClusters(List<ClusterData> clusters) {
        List<Component> clusterComponents = new ArrayList<>();
        for (ClusterData cluster : clusters) {
            clusterComponents.add(new Cluster(cluster));
        }
        if (!clusterComponents.isEmpty()) {
            SlotUtils.setSlot(this, SLOT_BODY,
                    clusterComponents.toArray(new Component[0]));
        } else {
            SlotUtils.clearSlot(this, SLOT_BODY);
        }
    }

    /**
     * Binds the given partition-model signal to this component. The whole
     * {@link PartitionModel} is pushed to the client under
     * {@link #PROPERTY_MODEL} whenever the signal value changes, and the body
     * clusters are rebuilt reactively through a {@link Signal} effect so no
     * manual update is required after a signal change. Passing {@code null}
     * unbinds any existing binding and restores manual property updates.
     *
     * @param model
     *            the signal to bind, or {@code null} to clear a previous
     *            binding; must not be null otherwise
     */
    public void bindPartitionModel(ValueSignal<PartitionModel> model) {
        if (model == null) {
            if (effectRegistration != null) {
                effectRegistration.remove();
                effectRegistration = null;
            }
            this.modelSignal = null;
            return;
        }
        this.modelSignal = model;
        getElement().bindProperty(PROPERTY_MODEL, model, null);
        effectRegistration = Signal.effect(this,
                () -> rebuildClusters(model.get().clusters()));
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
