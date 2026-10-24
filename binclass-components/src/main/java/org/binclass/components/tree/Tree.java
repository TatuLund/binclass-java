/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.tree;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.signals.local.ValueSignal;
import com.vaadin.flow.shared.Registration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

/**
 * Vaadin wrapper for the {@code binclass-tree} web component.
 * <p>
 * Renders a binary dendrogram as SVG: orthogonal branches, knob dots at every
 * branching point, inline labels on leaves and native {@code <title>} tooltips
 * on knobs. The whole flat node list is pushed to the client under the
 * {@code model} property; when bound through a reactive {@link ValueSignal} it
 * travels reactively instead of being set individually.
 * </p>
 */
@NullMarked
@Tag("binclass-tree")
@JsModule("./src/tree.ts")
public class Tree extends Component {

    /**
     * Property name under which the flat node list is pushed to the client as a
     * single object ({@code { nodes: [...] }}).
     */
    public static final String PROPERTY_MODEL = "model";

    @Nullable
    private ValueSignal<TreeModel> modelSignal;

    @Nullable
    private Registration effectRegistration;

    /**
     * Sets all presentation data from a {@link TreeModel}, pushing the flat
     * node list to the client. When a signal is bound through
     * {@link #bindModel(ValueSignal)} the whole object travels reactively
     * through that signal instead of being set individually, so later updates
     * stay in sync with any external changes to the bound value.
     *
     * @param model
     *            the immutable tree presentation data; must not be null
     */
    @SuppressWarnings("unused")
    public void setData(TreeModel model) {
        if (model == null) {
            throw new IllegalArgumentException("TreeModel must not be null");
        }
        if (modelSignal != null) {
            modelSignal.set(model);
        } else {
            pushModel(model);
        }
    }

    /**
     * Pushes the flat node list of a model to the client as a single object.
     * Called when no tree-model signal is bound; ignored when one is, since the
     * whole object travels through {@link #PROPERTY_MODEL}.
     *
     * @param model
     *            the immutable tree presentation data; must not be null
     */
    private void pushModel(TreeModel model) {
        List<Map<String, Object>> nodes = model.nodes().stream()
                .map(this::toMap)
                .toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("nodes", nodes);
        getElement().setPropertyMap(PROPERTY_MODEL, payload);
    }

    /**
     * Converts a node record into the flat map pushed to the client. Keys match
     * the TypeScript field names so Jackson serializes them verbatim.
     *
     * @param node
     *            the node to serialize; must not be null
     * @return an ordered map of the node's fields
     */
    private Map<String, Object> toMap(TreeNodeData node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", node.id());
        map.put("name", node.name());
        map.put("sc", node.sc());
        map.put("size", node.size());
        map.put("label", node.label() == null ? "" : node.label());
        map.put("frequency", node.frequency());
        map.put("isLeaf", node.isLeaf());
        map.put("leftChild", node.leftChild());
        map.put("rightChild", node.rightChild());
        return map;
    }

    /**
     * Binds the given tree-model signal to this component. The whole
     * {@link TreeModel} is pushed to the client under {@link #PROPERTY_MODEL}
     * whenever the signal value changes, so no manual update is required after
     * a signal change. Passing {@code null} unbinds any existing binding and
     * restores manual property updates.
     *
     * @param model
     *            the signal to bind, or {@code null} to clear a previous
     *            binding; must not be null otherwise
     */
    @SuppressWarnings("unused")
    public void bindModel(ValueSignal<TreeModel> model) {
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
     * Returns the flat node list currently pushed to the client. Each entry is
     * a map keyed by the TypeScript field names with primitive values.
     *
     * @return an ordered list of node maps, or an empty list when none set
     */
    @SuppressWarnings({ "unchecked", "null" })
    public List<Map<String, Object>> getNodes() {
        Object model = getElement().getPropertyBean(PROPERTY_MODEL,
                Object.class);
        if (!(model instanceof Map)) {
            return List.of();
        }
        Object nodes = ((Map<?, ?>) model).get("nodes");
        if (!(nodes instanceof List)) {
            return List.of();
        }
        return ((List<?>) nodes).stream()
                .map(node -> (Map<String, Object>) node)
                .toList();
    }

    /**
     * Builds the multi-line tooltip text for a node from its most common strain
     * name, joint cluster size and frequency of that name. Matches the
     * client-side rendering so Java-side assertions can verify the same
     * content.
     *
     * @param data
     *            the node to describe; must not be null
     * @return the multi-line tooltip text shown on hover
     */
    public static String tooltipText(TreeNodeData data) {
        StringBuilder text = new StringBuilder();
        if (!data.label().isEmpty()) {
            text.append("Name: ").append(data.label());
        }
        text.append("\nSize: ").append(data.size());
        if (data.frequency() > 0) {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append("Frequency: ").append(data.frequency());
        }
        return text.toString();
    }

    /**
     * Returns the frequency of the most common strain name within a subtree, or
     * zero when no name is known. Convenience for translation layers that only
     * have the label available.
     *
     * @param label
     *            the most common strain name, may be null or empty
     * @return the frequency of that name, or zero
     */
    public static int frequencyOf(@Nullable String label) {
        return (label == null || label.isEmpty()) ? 0 : 1;
    }

    /**
     * Formats a double using the root locale so serialized values are stable.
     *
     * @param value
     *            the value to format
     * @return the formatted string
     */
    public static String formatSc(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    /**
     * Sanitizes a label for safe insertion into an SVG {@code <text>} element.
     * Uses JSoup's content mode to strip any markup while preserving plain
     * text, matching the PRD requirement that all displayed user HTML be
     * sanitized.
     *
     * @param raw
     *            the raw label; may be null or empty
     * @return the sanitized text, never null
     */
    public static String sanitize(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        Document doc = Jsoup.parseBodyFragment(raw);
        return doc.body().text();
    }

}
