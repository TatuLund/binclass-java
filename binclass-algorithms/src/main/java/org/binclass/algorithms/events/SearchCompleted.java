/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.events;

import org.jspecify.annotations.NullMarked;

/**
 * The final best partition and its SC curve, posted when a GLA-style search
 * completes successfully. Drives the same signals as
 * {@link SearchBestPartition} so the graph and partition tabs rebuild on
 * completion just as they did live during the run.
 */
@NullMarked
public record SearchCompleted(String display, PartitionResult result)
        implements
            AbstractEvent {
}
