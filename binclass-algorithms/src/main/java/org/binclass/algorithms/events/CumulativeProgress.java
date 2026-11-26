/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.events;

import org.jspecify.annotations.NullMarked;

/**
 * A cumulative-classification progress sample, posted after roughly every ten
 * percent of the input vectors are processed so the determinate ProgressBar can
 * advance in matching ten-percent steps. It carries only the number of vectors
 * processed so far — the finished dynamic partition rides on its own
 * {@link SearchCompleted} event.
 * <p>
 * Mirrors the lightweight, algorithms-module event posting that GLA and Local
 * Search already use; the view model only listens.
 * </p>
 */
@NullMarked
public record CumulativeProgress(String display, int vectorsProcessed)
        implements
            AbstractEvent {
}
