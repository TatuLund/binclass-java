/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.signals.local.ValueSignal;
import com.vaadin.flow.shared.Registration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Vaadin wrapper for the {@code binclass-graph} web component.
 * <p>
 * Renders a line chart of Stochastic Complexity as a function of the number of
 * clusters: an adaptive x/y axis, a connected polyline through the points and a
 * highlighted global-minimum point with crosshairs to both axes. The whole flat
 * point list is pushed to the client under the {@code model} property; when
 * bound through a reactive {@link ValueSignal} it travels reactively instead of
 * being set individually.
 * </p>
 */
@NullMarked
@Tag("binclass-graph")
@JsModule("./src/graph.ts")
public class Graph extends Component implements HasSize {

    /**
     * Property name under which the point list is pushed to the client as a
     * single object ({@code { points: [...] }}).
     */
    public static final String PROPERTY_MODEL = "model";

    @Nullable
    private ValueSignal<GraphModel> modelSignal;

    @Nullable
    private Registration effectRegistration;

    /**
     * Sets all presentation data from a {@link GraphModel}, pushing the flat
     * point list to the client. When a signal is bound through
     * {@link #bindModel(ValueSignal)} the whole object travels reactively
     * through that signal instead of being set individually, so later updates
     * stay in sync with any external changes to the bound value. Passing
     * {@code null} clears the chart.
     *
     * @param model
     *            the immutable graph presentation data; must not be null
     */
    @SuppressWarnings("unused")
    public void setData(GraphModel model) {
        if (model == null) {
            getElement().removeProperty(PROPERTY_MODEL);
            return;
        }
        if (modelSignal != null) {
            modelSignal.set(model);
        } else {
            pushModel(model);
        }
    }

    /**
     * Pushes the point list of a model to the client as a single object. Called
     * when no graph-model signal is bound; ignored when one is, since the whole
     * object travels through {@link #PROPERTY_MODEL}.
     *
     * @param model
     *            the immutable graph presentation data; must not be null
     */
    private void pushModel(GraphModel model) {
        List<Map<String, Object>> points = new ArrayList<>();
        for (GraphPoint point : model.points()) {
            points.add(toMap(point));
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("points", points);
        getElement().setPropertyMap(PROPERTY_MODEL, payload);
    }

    /**
     * Converts a point record into the flat map pushed to the client. Keys
     * match the TypeScript field names so Jackson serializes them verbatim.
     *
     * @param point
     *            the point to serialize; must not be null
     * @return an ordered map of the point's fields
     */
    private static Map<String, Object> toMap(GraphPoint point) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", point.x());
        map.put("y", point.y());
        return map;
    }

    /**
     * Binds the given graph-model signal to this component. The whole
     * {@link GraphModel} is pushed to the client under {@link #PROPERTY_MODEL}
     * whenever the signal value changes, so no manual update is required after
     * a signal change. Passing {@code null} unbinds any existing binding and
     * restores manual property updates.
     *
     * @param model
     *            the signal to bind, or {@code null} to clear a previous
     *            binding; must not be null otherwise
     */
    @SuppressWarnings("unused")
    public void bindModel(ValueSignal<GraphModel> model) {
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
    }

    /**
     * Returns the point list currently pushed to the client. Each entry is a
     * map keyed by the TypeScript field names with primitive values.
     *
     * @return an ordered list of point maps, or an empty list when none set
     */
    @SuppressWarnings({ "unchecked", "null" })
    public List<Map<String, Object>> getPoints() {
        Object model = getElement().getPropertyBean(PROPERTY_MODEL,
                Object.class);
        if (!(model instanceof Map)) {
            return List.of();
        }
        Object points = ((Map<?, ?>) model).get("points");
        if (!(points instanceof List)) {
            return List.of();
        }
        return ((List<?>) points).stream()
                .map(point -> (Map<String, Object>) point)
                .toList();
    }

    /**
     * Formats a stochastic-complexity value using the root locale so serialized
     * values are stable. SC axis labels use whole numbers while the global
     * minimum is shown with two decimals.
     *
     * @param value
     *            the value to format; must not be null
     * @return the formatted string, never null
     */
    public static String formatSc(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

}
