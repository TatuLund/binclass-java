/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.partition;

import java.util.List;
import java.util.stream.IntStream;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.shared.HasTooltip;

/**
 * Vaadin wrapper for the {@code binclass-centroid} web component.
 * <p>
 * Renders a probability centroid as grey-shaded cells: white at 0, black at 1,
 * and graded grey in between. A tooltip shows cluster distortion, weight, and
 * vector count.
 * </p>
 */
@Tag("binclass-centroid")
@JsModule("./src/centroid.ts")
public class Centroid extends Component implements HasTooltip {

    /**
     * DOM event fired when a cell is selected, carrying the clicked index and
     * centroid name.
     */
    public static final String EVENT_SELECTED = "centroids-selected";

    /**
     * Sets all presentation data for this centroid from a {@link CentroidData}
     * record.
     *
     * @param data
     *            the immutable presentation data; must not be null
     */
    public void setData(CentroidData data) {
        if (data == null) {
            throw new IllegalArgumentException("CentroidData must not be null");
        }
        setProbabilities(data.clampedProbabilities());
        setName(data.name());
        setClassDistortion(data.classDistortion());
        setWeight(data.weight());
        setVectorCount(data.vectorCount());
        setTooltipText(tooltipText(data));
    }

    /**
     * Sets the per-bit probabilities rendered as grey cells. Values are clamped
     * into {@code [0, 1]}.
     *
     * @param probabilities
     *            per-bit probabilities in {@code [0, 1]}
     */
    public void setProbabilities(double[] probabilities) {
        double[] clamped = probabilities == null
                ? new double[0]
                : new CentroidData(probabilities).clampedProbabilities();
        List<Double> values = IntStream.range(0, clamped.length)
                .mapToDouble(i -> clamped[i])
                .boxed()
                .toList();
        getElement().setPropertyList("probabilities", values);
    }

    /**
     * Returns the per-bit probabilities currently pushed to the client.
     *
     * @return the probability array, or an empty array when none have been set
     */
    public double[] getProbabilities() {
        List<?> values = getElement().getPropertyBean("probabilities",
                List.class);
        double[] result = new double[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = ((Number) values.get(i)).doubleValue();
        }
        return result;
    }

    /**
     * Sets the cluster name shown in the tooltip.
     *
     * @param name
     *            the cluster label; must not be null
     */
    public void setName(String name) {
        getElement().setProperty("name", name == null ? "" : name);
    }

    /**
     * Sets the class distortion (average Hamming distance to centroid). Shown
     * in tooltips.
     *
     * @param classDistortion
     *            average Hamming distance to centroid
     */
    public void setClassDistortion(double classDistortion) {
        getElement().setProperty("classDistortion", classDistortion);
    }

    /**
     * Sets the cluster weight (frequency ratio or raw count). Shown in
     * tooltips.
     *
     * @param weight
     *            the cluster weight
     */
    public void setWeight(double weight) {
        getElement().setProperty("weight", weight);
    }

    /**
     * Sets the number of vectors assigned to the cluster. Shown in tooltips.
     *
     * @param vectorCount
     *            the number of assigned vectors
     */
    public void setVectorCount(int vectorCount) {
        getElement().setProperty("vectorCount", vectorCount);
    }

    /**
     * Builds the tooltip text for a centroid from its name, class distortion,
     * weight, and vector count, matching the client-side rendering.
     *
     * @param data
     *            the immutable presentation data; must not be null
     * @return the multi-line tooltip text shown on hover
     */
    private static String tooltipText(CentroidData data) {
        String name = data.name();
        StringBuilder text = new StringBuilder();
        if (!name.isEmpty()) {
            text.append("Name: ").append(name);
        }
        if (!Double.isNaN(data.classDistortion())) {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append("Class distortion: ")
                    .append(String.format(java.util.Locale.ROOT, "%.3f",
                            data.classDistortion()));
        }
        if (!Double.isNaN(data.weight())) {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append("Weight: ")
                    .append(String.format(java.util.Locale.ROOT, "%.3f",
                            data.weight()));
        }
        if (!text.isEmpty()) {
            text.append('\n');
        }
        text.append("Vectors: ").append(data.vectorCount());
        return text.toString();
    }

}
