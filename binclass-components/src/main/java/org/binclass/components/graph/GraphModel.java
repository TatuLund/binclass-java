/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.graph;

import java.util.List;

import org.jspecify.annotations.NullMarked;

/**
 * Immutable presentation data for a whole line chart rendered by the
 * {@link Graph} component: an ordered list of {@link GraphPoint}s describing
 * how Stochastic Complexity changes as the number of clusters varies.
 * <p>
 * Points arrive in search order; the client sorts them by cluster count before
 * drawing so the line connects left-to-right regardless of push order. The list
 * is immutable and copies are returned from {@link #points()} so callers cannot
 * mutate internal state. Independent of {@code binclass-algorithms}; the
 * translation layer in {@code binclass-ui} builds this from whatever search
 * produced it (range, automatic, or local).
 * </p>
 */
@NullMarked
public record GraphModel(List<GraphPoint> points) {

    /**
     * Creates a graph model from a list of points. The list is copied and kept
     * non-empty so the chart always has data to render.
     *
     * @param points
     *            the ordered point list; must not be null or empty
     */
    public GraphModel(List<GraphPoint> points) {
        this.points = List.copyOf(points);
        if (this.points.isEmpty()) {
            throw new IllegalArgumentException("points must not be empty");
        }
    }

    /**
     * Returns an unmodifiable copy of the point list so callers cannot mutate
     * internal state.
     *
     * @return an unmodifiable copy of {@link #points()}
     */
    public List<GraphPoint> points() {
        return List.copyOf(points);
    }
}
