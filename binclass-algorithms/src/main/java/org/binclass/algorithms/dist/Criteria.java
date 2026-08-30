/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.dist;

import org.jspecify.annotations.NullMarked;

/**
 * Immutable result of {@link DistanceCalculator#calculateCriteria}, mirroring
 * the values computed by C's {@code calculate_criteria()} in
 * {@code classify.c}.
 * <p>
 * The fields correspond to the columns printed by the original program:
 * <ul>
 * <li>{@code sc} — stochastic complexity of the current clustering;</li>
 * <li>{@code d} — average distortion (MAE / MSE / overall distortion for L1 /
 * L2 / Hamming, or codelength-based distance otherwise);</li>
 * <li>{@code i1} — average codelength (or {@code C->I} when the distance type
 * is a pure codelength metric);</li>
 * <li>{@code i2} — Shannon entropy estimate.</li>
 * </ul>
 * The record carries no behaviour so that callers can compare and log scoring
 * results without depending on internal state of {@link InfiniteCentroids}.
 * </p>
 */
@NullMarked
public record Criteria(double sc, double d, double i1, double i2) {
}
