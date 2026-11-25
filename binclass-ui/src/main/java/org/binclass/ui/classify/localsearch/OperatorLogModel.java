/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.localsearch;

import java.util.List;

import org.jspecify.annotations.NullMarked;

/**
 * Immutable presentation model for the visual operator log shown on the Local
 * Search tab. It carries everything {@link OperatorLog} needs to render three
 * sections:
 * <ul>
 * <li>a <b>legend</b> mapping each of the six local-search operators to a
 * colored square, its friendly label and a tooltip describing what it
 * does;</li>
 * <li>one <b>per-{@code k} row</b> listing the operator names that lowered the
 * stochastic complexity at that cluster count, so successes appear grouped on a
 * single line labeled by {@code k};</li>
 * <li>a set of <b>statistic bars</b>, one per operator that succeeded, whose
 * length is proportional to how many times it improved SC during the run.</li>
 * </ul>
 * <p>
 * Built by {@link LocalSearchViewModel} from the raw {@code OperatorSuccess}
 * events and consumed reactively by the {@link OperatorLog} component. All
 * fields are immutable lists so a signal can hold it without copying.
 * </p>
 */
@NullMarked
public final class OperatorLogModel {

    /**
     * A single legend entry: the operator's canonical name, its friendly label,
     * a tooltip with a longer description and the hex color of its square.
     *
     * @param operator
     *            the canonical operator name (e.g. {@code SPLITJOIN1})
     * @param label
     *            the short friendly label shown beside the square
     * @param tooltip
     *            the longer description shown on hover
     * @param color
     *            the hex color of the operator's square, e.g. {@code #2563eb}
     */
    public record LegendItem(String operator, String label, String tooltip,
            String color) {
    }

    /**
     * One row of the success grid for a single cluster count. The list holds
     * the canonical operator names that improved SC at {@link #k()}, in the
     * order they succeeded; empty when no operator improved at that {@code k}.
     *
     * @param k
     *            the cluster count labeling this row (1-based)
     * @param operators
     *            the operator names that succeeded at this {@code k}, in order
     */
    public record KRow(int k, List<String> operators) {
    }

    /**
     * One horizontal bar of the statistics block. Its rendered length is
     * proportional to {@link #count()} relative to the largest count in the
     * run.
     *
     * @param operator
     *            the canonical operator name
     * @param count
     *            how many times this operator improved SC during the run
     */
    public record StatBar(String operator, int count) {
    }

    private final List<LegendItem> legend;
    private final List<KRow> rows;
    private final List<StatBar> stats;

    /**
     * Returns an empty model with no rows or statistic bars, used to clear the
     * {@link OperatorLog} component before a search starts. The legend is left
     * empty too so the component renders nothing until populated.
     *
     * @return an empty operator-log model
     */
    public static OperatorLogModel empty() {
        return new OperatorLogModel(List.of(), List.of(), List.of());
    }

    /**
     * Builds a new model.
     *
     * @param legend
     *            the fixed legend entries, one per operator (must not be null)
     * @param rows
     *            the per-{@code k} success rows ordered by {@code k} ascending
     *            (may be empty when no operator succeeded)
     * @param stats
     *            the per-operator statistic bars ordered by count descending
     *            (may be empty when no operator succeeded)
     */
    public OperatorLogModel(List<LegendItem> legend, List<KRow> rows,
            List<StatBar> stats) {
        this.legend = List.copyOf(legend);
        this.rows = List.copyOf(rows);
        this.stats = List.copyOf(stats);
    }

    /**
     * Returns the immutable legend entries.
     *
     * @return an ordered list of legend items, never null
     */
    public List<LegendItem> legend() {
        return legend;
    }

    /**
     * Returns the immutable per-{@code k} success rows.
     *
     * @return an ordered list of rows (one per {@code k} with a success), never
     *         null
     */
    public List<KRow> rows() {
        return rows;
    }

    /**
     * Returns the immutable statistic bars.
     *
     * @return one bar per operator that succeeded, ordered by count descending,
     *         never null
     */
    public List<StatBar> stats() {
        return stats;
    }
}
