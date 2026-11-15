/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.events;

import org.jspecify.annotations.NullMarked;

/**
 * Posted when a GLA-style search fails before producing a best partition, so
 * the view can surface the failure message and re-enable its Run button.
 */
@NullMarked
public record SearchError(String display,
        String message) implements AbstractEvent {
}
