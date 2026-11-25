/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.localsearch;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import com.vaadin.flow.shared.Registration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Visual operator log for the Local Search tab. Renders three sections from an
 * {@link OperatorLogModel}:
 * <ul>
 * <li>a <b>legend</b> — one line per operator showing its friendly label and a
 * colored square whose hover {@code title} carries a longer description;</li>
 * <li><b>per-{@code k} rows</b> — one labeled row (the cluster count) with a
 * colored success square for every operator that lowered the stochastic
 * complexity at that {@code k};</li>
 * <li>a set of <b>statistic bars</b> — one horizontally stacked bar per
 * operator, whose length is proportional to how many times it improved SC
 * during the run, each prefixed by an equal-width label so the bars align.</li>
 * </ul>
 * <p>
 * The component owns no state beyond its binding: feed it a model with
 * {@link #bindModel(ValueSignal)} and it re-renders whenever that signal
 * changes. It is a plain server-side component, so browserless tests can query
 * the rendered DOM directly (legend labels, per-{@code k} rows by id, stat
 * bars).
 * </p>
 */
@NullMarked
@StyleSheet("/operatorlog.css")
public final class OperatorLog extends Div {

    /** CSS class applied to every operator success square. */
    public static final String SQUARE_CLASS = "op-log-square";

    /** CSS class applied to each per-{@code k} row container. */
    public static final String ROW_CLASS = "op-log-row";

    private final Span legendContainer;
    private final Span rowsContainer;
    private final Span statsContainer;

    @Nullable
    private ValueSignal<OperatorLogModel> modelSignal;

    @Nullable
    private Registration effectRegistration;

    @Nullable
    private Registration attachRegistration;

    /**
     * Builds the component with its three empty sections and a scrollable,
     * column-flex root so long runs stay compact.
     */
    public OperatorLog() {
        legendContainer = new Span();
        rowsContainer = new Span();
        statsContainer = new Span();
        legendContainer.setId("operator-log-legend");
        rowsContainer.setId("operator-log-rows");
        statsContainer.setId("operator-log-stats");

        addClassName("op-log-root");
        add(legendContainer, rowsContainer, statsContainer);
    }

    /**
     * Binds the given operator-log model signal to this component. The whole
     * {@link OperatorLogModel} is rendered whenever the signal value changes,
     * so later updates stay in sync without manual work. Passing {@code null}
     * unbinds any existing binding and clears the rendered sections.
     *
     * @param model
     *            the signal to bind, or {@code null} to clear a previous
     *            binding; must not be null otherwise
     */
    public void bindModel(@Nullable ValueSignal<OperatorLogModel> model) {
        if (model == null) {
            if (effectRegistration != null) {
                effectRegistration.remove();
                effectRegistration = null;
            }
            this.modelSignal = null;
            render(OperatorLogModel.empty());
            return;
        }
        this.modelSignal = model;
        if (effectRegistration != null) {
            effectRegistration.remove();
        }
        if (attachRegistration != null) {
            attachRegistration.remove();
        }
        effectRegistration = Signal.effect(this, () -> render(model.get()));
        // Render synchronously on attach so the initial model is present in
        // the DOM before any round-trip, and again reactively whenever the
        // bound signal changes from another thread. peek() reads outside a
        // reactive context without setting up dependency tracking.
        attachRegistration = addAttachListener(event -> render(model.peek()));
        render(model.peek());
    }

    /**
     * Renders the given model into the three sections. Called on first bind and
     * again whenever the bound signal changes.
     *
     * @param model
     *            the presentation model to render; must not be null
     */
    public void render(OperatorLogModel model) {
        legendContainer.removeAll();
        rowsContainer.removeAll();
        statsContainer.removeAll();

        if (model == null || model.legend().isEmpty()) {
            return;
        }

        renderLegend(model);
        renderRows(model);
        renderStats(model);
    }

    /** Renders the fixed legend: one stacked row per operator. */
    private void renderLegend(OperatorLogModel model) {
        Span header = new Span("Legend");
        header.addClassName("op-log-header");
        legendContainer.add(header);
        for (OperatorLogModel.LegendItem item : model.legend()) {
            Div line = new Div();
            Span label = new Span(item.label());
            line.addClassName("op-log-line");
            line.add(label);
            Span swatch = square(item.color());
            swatch.getElement().setAttribute("title", item.tooltip());
            line.add(swatch);
            legendContainer.add(line);
        }
    }

    /** Renders one labeled row per {@code k} holding its success squares. */
    private void renderRows(OperatorLogModel model) {
        for (OperatorLogModel.KRow row : model.rows()) {
            Span label = new Span("k=" + row.k());
            label.addClassName("op-log-row-label");
            Span cells = new Span();
            cells.setId(ROW_CLASS + "-" + row.k());
            for (String operator : row.operators()) {
                cells.add(square(colorFor(operator)));
            }
            Span line = new Span(label, cells);
            line.addClassName(ROW_CLASS);
            rowsContainer.add(line);
        }
    }

    /** Renders one proportional bar per operator that succeeded. */
    private void renderStats(OperatorLogModel model) {
        if (model.stats().isEmpty()) {
            return;
        }
        int max = 0;
        for (OperatorLogModel.StatBar bar : model.stats()) {
            max = Math.max(max, bar.count());
        }
        for (OperatorLogModel.StatBar bar : model.stats()) {
            Span label = new Span(bar.operator());
            label.addClassName("op-log-stat-label");
            Div track = new Div();
            track.addClassName("op-log-track");
            int width = max > 0 ? Math.max(2, bar.count() * 400 / max) : 0;
            Div fill = new Div();
            fill.addClassName("op-log-fill");
            fill.getElement().getStyle()
                    .set("width", width + "px")
                    .set("background-color", colorFor(bar.operator()));
            track.add(fill);
            Div line = new Div(label, track);
            line.addClassName("op-log-stat-row");
            statsContainer.add(line);
        }
    }

    /**
     * Builds a small colored square with the given background color. Used for
     * legend swatches and per-{@code k} success markers.
     *
     * @param color
     *            the hex color of the square; must not be null
     * @return the styled square span
     */
    private static Span square(String color) {
        Span s = new Span();
        s.addClassName(SQUARE_CLASS);
        s.getElement().getStyle()
                .set("background-color", color);
        return s;
    }

    /**
     * Returns the palette color for an operator name, falling back to a neutral
     * gray when the operator is not one of the six legend entries.
     *
     * @param operator
     *            the canonical operator name (e.g. {@code SPLITJOIN1})
     * @return the hex color string, never null
     */
    private static String colorFor(String operator) {
        OperatorLegend entry = OperatorLegend.fromName(operator);
        return entry != null ? entry.color() : "#6b7280";
    }

    /**
     * Returns the currently bound model signal, or {@code null} when unbound.
     * Exposed for tests that want to inspect the binding.
     *
     * @return the bound signal, or {@code null} if none is bound
     */
    @Nullable
    public ValueSignal<OperatorLogModel> boundSignal() {
        return modelSignal;
    }
}
