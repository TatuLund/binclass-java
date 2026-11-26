/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.events;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A cumulative-classification new-cluster decision, posted each time a vector
 * extends the dynamic partition by creating a class. Joins are not logged, so
 * one event maps to one log line in the view.
 * <p>
 * When the stochastic-complexity variant is used ({@code bayesianPredictive}
 * false) the record carries the resulting SC value printed in bold; the
 * Bayesian-predictive variant leaves it {@code null} and logs without a score.
 * </p>
 */
@NullMarked
public record NewCluster(String display, int clusterIndex,
        @Nullable Double stochasticComplexity)
        implements
            AbstractEvent {
}
