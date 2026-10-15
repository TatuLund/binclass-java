/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.shared.SlotUtils;
import org.jspecify.annotations.NullMarked;

/**
 * Vaadin wrapper for the {@code binclass-cluster} web component.
 * <p>
 * Groups a {@link Centroid} block with a lazily populated, collapsible block of
 * assigned vectors. The caret toggle (provided by {@link Details}) shows or
 * hides the vectors; they are hidden by default and pushed to the client when
 * the cluster is opened. The centroid lives in the {@code centroid} slot and is
 * always visible; vectors live in the {@code vectors} slot.
 * </p>
 */
@NullMarked
@Tag("binclass-cluster")
@JsModule("./cluster.ts")
public class Cluster extends Details {

    /** Slot name for the centroid child. */
    public static final String SLOT_CENTROID = "centroid";

    /** Slot name for the vector children. */
    public static final String SLOT_VECTORS = "vectors";

    /** Server-side buffer of vectors rendered when the cluster is expanded. */
    private List<VectorData> bufferedVectors = List.of();

    private Centroid centroid;

    /**
     * Creates a cluster from its presentation data, wiring the centroid into
     * the {@code centroid} slot and buffering any provided vectors. The
     * buffered vectors are pushed to the client automatically whenever the
     * caret is opened; they can also be rendered immediately with
     * {@link #setVectors}.
     *
     * @param data
     *            the immutable cluster presentation data; must not be null
     */
    public Cluster(ClusterData data) {
        if (data == null) {
            throw new IllegalArgumentException("ClusterData must not be null");
        }
        setSummaryText(buildSummary(data));
        this.centroid = new Centroid();
        centroid.setData(data.centroid());
        SlotUtils.setSlot(this, SLOT_CENTROID, centroid);
        this.bufferedVectors = List.copyOf(data.vectors());
        addOpenedChangeListener(event -> {
            if (event.isOpened()) {
                populateSlot();
            }
        });
    }

    /**
     * Builds the summary text combining the cluster name with its distortion,
     * weight, and vector count.
     *
     * @param data
     *            the cluster presentation data
     * @return a human-readable summary string for the caret label
     */
    private static String buildSummary(ClusterData data) {
        StringBuilder sb = new StringBuilder();
        sb.append(data.name());
        sb.append("  (n=").append(data.vectorCount());
        if (!Double.isNaN(data.classDistortion())) {
            sb.append(", dist=")
                    .append(String.format("%.3f", data.classDistortion()));
        }
        if (!Double.isNaN(data.weight())) {
            sb.append(", w=").append(String.format("%.3f", data.weight()));
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * Replaces the vectors rendered in this cluster. Existing vector children
     * are removed and the new ones are added to the {@code vectors} slot.
     *
     * @param vectors
     *            the vectors to render; may be empty for lazy loading, but must
     *            not be null
     */
    public void setVectors(List<VectorData> vectors) {
        if (vectors == null) {
            throw new IllegalArgumentException("Vector list must not be null");
        }
        this.bufferedVectors = List.copyOf(vectors);
        populateSlot();
    }

    /**
     * Returns a copy of the vectors currently buffered for this cluster.
     *
     * @return an unmodifiable copy of the buffered vector data
     */
    public List<VectorData> getBufferedVectors() {
        return List.copyOf(bufferedVectors);
    }

    /**
     * Rebuilds the {@code vectors} slot from the server-side buffer. Called
     * when new data is set and whenever the cluster is opened, so lazy-loaded
     * vectors are rendered on demand.
     */
    private void populateSlot() {
        SlotUtils.clearSlot(this, SLOT_VECTORS);
        List<Component> added = new ArrayList<>();
        for (VectorData vector : bufferedVectors) {
            Vector component = new Vector();
            component.setData(vector);
            added.add(component);
        }
        if (!added.isEmpty()) {
            SlotUtils.setSlot(this, SLOT_VECTORS,
                    added.toArray(new Component[0]));
        }
    }

    /**
     * Returns the centroid rendered by this cluster.
     *
     * @return the {@link Centroid} child; never null for a cluster created with
     *         data
     */
    public Centroid getCentroid() {
        return centroid;
    }

    /**
     * Sets the centroid rendered by this cluster, replacing any previous one in
     * the {@code centroid} slot.
     *
     * @param data
     *            the centroid presentation data; must not be null
     */
    public void setCentroid(CentroidData data) {
        if (data == null) {
            throw new IllegalArgumentException("CentroidData must not be null");
        }
        this.centroid = new Centroid();
        centroid.setData(data);
        SlotUtils.setSlot(this, SLOT_CENTROID, centroid);
    }

    /**
     * Returns whether the cluster is currently expanded. Delegates to
     * {@link Details#isOpened()}.
     *
     * @return {@code true} when the vectors block is visible
     */
    public boolean isOpenClustered() {
        return isOpened();
    }
}
