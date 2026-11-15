/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.events;

import org.jspecify.annotations.NullMarked;

/**
 * A new best partition was detected mid-search so the Partition tab can refresh
 * live, before the search finishes. Carries the full snapshot as a
 * {@link PartitionResult}; kept separate from {@link SearchProgress} so the SC
 * curve and the best-partition view update independently.
 */
@NullMarked
public record SearchBestPartition(String display, PartitionResult result)
        implements
            AbstractEvent {
}
