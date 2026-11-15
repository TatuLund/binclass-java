/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.events;

import org.jspecify.annotations.NullMarked;

/**
 * A single stochastic-complexity sample for one cluster count, posted after
 * each {@code k} of a GLA-style search so the SC-curve tab can update live. It
 * carries scalars only — the best-partition snapshot rides on its own
 * {@link SearchBestPartition} event so "SC curve" and "best partition" stay
 * decoupled.
 */
@NullMarked
public record SearchProgress(String display, int k, double sc, boolean finished)
        implements
            AbstractEvent {
}
