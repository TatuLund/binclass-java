/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.events;

import org.jspecify.annotations.NullMarked;

/**
 * Signals that a display has started a long-running search. Optional — the UI
 * starts work by calling the ViewModel's {@code run()} command rather than
 * waiting for this event, but it is posted so views can show an initial status.
 */
@NullMarked
public record SearchStarted(String display, String filebase, int expectedK)
        implements
            AbstractEvent {
}
