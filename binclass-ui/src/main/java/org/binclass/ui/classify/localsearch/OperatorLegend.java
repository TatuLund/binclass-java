/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.localsearch;

import org.jspecify.annotations.NullMarked;

/**
 * Fixed metadata for the six local-search operators shown in the visual
 * operator log legend. Each entry carries the friendly label, a longer tooltip
 * describing what the operator does (so users can tell e.g. Split-Join 1 from
 * Split-Join 2) and the hex color of its swatch square. The order matches
 * {@code Heuristic} codes 1..6 and is the single source of truth for both the
 * legend rows and the operator-square colors used in the per-{@code k} grid and
 * statistic bars.
 */
@NullMarked
public enum OperatorLegend {

    /** Split-join local search (operator 1). */
    SPLITJOIN1(
            "Split-Join 1",
            "Split and join v1 — pick a random class, join it with the closest centroid, then split the most incoherent class.",
            "#2563eb"),

    /** Replace-worst operator. */
    REPLACEWORST(
            "Replace-Worst",
            "Replace worst class — set the centroid of the most incoherent class to a random vector from any class, then repartition.",
            "#dc2626"),

    /** Replace-smallest operator. */
    REPLACESMALLEST(
            "Replace-Smallest",
            "Replace smallest class — set the centroid of the largest class to a random vector from any class, then repartition.",
            "#16a34a"),

    /** Random-swap operator (variant 1). */
    RANDOMSWAP(
            "Random-Swap v1",
            "Random swap v1 — move a random vector between two classes and repartition.",
            "#9333ea"),

    /** Split-join local search (operator 5, variant 2). */
    SPLITJOIN2(
            "Split-Join 2",
            "Split and join v2 — join the smallest class with its closest neighbour, then split the most incoherent class.",
            "#0891b2"),

    /** Random-swap operator (variant 2). */
    RANDOMSWAP2(
            "Random-Swap v2",
            "Random swap v2 — set a random class's centroid to a random vector from that same class, then repartition.",
            "#e1781d");

    private final String label;
    private final String tooltip;
    private final String color;

    OperatorLegend(String label, String tooltip, String color) {
        this.label = label;
        this.tooltip = tooltip;
        this.color = color;
    }

    /**
     * Returns the friendly label shown beside the operator's legend swatch.
     *
     * @return the short label, never null
     */
    public String label() {
        return label;
    }

    /**
     * Returns the longer description shown on hover over the legend swatch.
     *
     * @return the tooltip text, never null
     */
    public String tooltip() {
        return tooltip;
    }

    /**
     * Returns the hex color of the operator's square.
     *
     * @return a CSS hex color string, never null
     */
    public String color() {
        return color;
    }

    /**
     * Returns the legend entry for an operator name, or {@code null} when the
     * name is not one of the six known operators.
     *
     * @param name
     *            the canonical operator name (e.g. {@code SPLITJOIN1})
     * @return the matching legend entry, or {@code null} if unknown
     */
    public static OperatorLegend fromName(String name) {
        for (OperatorLegend entry : values()) {
            if (entry.name().equals(name)) {
                return entry;
            }
        }
        return null;
    }
}
