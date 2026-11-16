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
     * Returns an empty-but-valid graph model so a bound chart can start with no
     * data and grow as points are appended, instead of showing a misleading
     * placeholder point.
     *
     * @return an empty {@link GraphModel}
     */
    public static GraphModel empty() {
        return new GraphModel(List.of());
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
