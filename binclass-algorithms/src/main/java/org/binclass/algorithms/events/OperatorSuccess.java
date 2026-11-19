/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.events;

import org.jspecify.annotations.NullMarked;

/**
 * A single local-search operator application lowered the stochastic complexity,
 * posted by {@code LocalSearch.localSearch()} so the Search tab can surface
 * which strategy improved SC mid-run. It carries only scalars — the cluster
 * count, the heuristic code that selected the driver ({@code -r7} cycler or
 * {@code -r8} adaptive), and the reduction in SC achieved by this one operator.
 * <p>
 * Kept separate from {@link SearchProgress} so the SC curve stays driven by
 * per-{@code k} samples while per-operator progress updates only the status
 * text; it does not feed the graph.
 */
@NullMarked
public record OperatorSuccess(String display, int k, int heuristicCode,
        double scDelta, String operator) implements AbstractEvent {
}
