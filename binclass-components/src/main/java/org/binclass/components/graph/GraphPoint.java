/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.graph;

import org.jspecify.annotations.NullMarked;

/**
 * A single point of a line chart rendered by the {@link Graph} component: a
 * cluster count {@code x} and its stochastic complexity {@code y}.
 * <p>
 * Cluster counts are always whole integers while SC values are decimals, and a
 * given {@code k} may have no finite SC (a failed run), so points need not form
 * a contiguous integer range. The explicit pair model captures that directly —
 * the client derives the x-axis range from the data itself and needs no
 * separate "start index". Independent of {@code binclass-algorithms}; the
 * translation layer in {@code binclass-ui} maps an automatic/range/local search
 * result into a list of these records.
 * </p>
 */
@NullMarked
public record GraphPoint(int x, double y) {

    /**
     * Creates a graph point, requiring the SC value to be finite so rendering
     * never has to special-case {@code NaN} / {@code Infinity}.
     *
     * @param x
     *            the cluster count; an integer
     * @param y
     *            the stochastic complexity; must be a finite number
     */
    public GraphPoint {
        if (!Double.isFinite(y)) {
            throw new IllegalArgumentException(
                    "GraphPoint SC value must be finite");
        }
    }
}
