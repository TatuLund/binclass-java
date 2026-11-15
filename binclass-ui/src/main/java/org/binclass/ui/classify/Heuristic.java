/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify;

import org.jspecify.annotations.NullMarked;

/**
 * Local-search heuristic exposed by the GLA Search form, mirroring the
 * {@code -r} flag understood by {@code ClassifyCommand}. Each constant carries
 * the numeric code used by {@link org.binclass.algorithms.gla.GLAConfig} and a
 * readable label shown in the UI select.
 * <p>
 * The codes follow the C parser ({@code original/parser.c}): 1=SPLITJOIN1,
 * 2=REPLACEWORST, 3=REPLACESMALLEST, 4=RANDOMSWAP, 5=SPLITJOIN2, 6=RANDOMSWAP2.
 * Values 7 and 8 are meta-heuristics: {@code -r7} cycles through every operator
 * ({@link #CYCLER}) and {@code -r8} adapts operator selection to success rates
 * ({@link #ADAPTIVE}).
 * </p>
 */
@NullMarked
public enum Heuristic {

    /** Split-join local search (operator 1). */
    SPLITJOIN1(1, "Split-Join 1"),
    /** Replace-worst operator. */
    REPLACEWORST(2, "Replace-Worst"),
    /** Replace-smallest operator. */
    REPLACESMALLEST(3, "Replace-Smallest"),
    /** Random-swap operator. */
    RANDOMSWAP(4, "Random-Swap"),
    /** Split-join local search (operator 5). */
    SPLITJOIN2(5, "Split-Join 2"),
    /** Random-swap operator (variant 2). */
    RANDOMSWAP2(6, "Random-Swap 2"),
    /** Cycle through every operator in turn. */
    CYCLER(7, "Cycler (all operators)"),
    /** Adaptively select operators by success probability. */
    ADAPTIVE(8, "Adaptive");

    private final int code;
    private final String label;

    Heuristic(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * Returns the numeric heuristic code used by
     * {@link org.binclass.algorithms.gla.GLAConfig}.
     *
     * @return the flag code (1..8)
     */
    public int code() {
        return code;
    }

    /**
     * Returns the friendly label shown in the GLA Search select.
     *
     * @return a human-readable description of the operator
     */
    public String label() {
        return label;
    }

    /**
     * Maps a numeric heuristic code to its {@link Heuristic}, falling back to
     * {@link #SPLITJOIN1} for unknown values so the default is preserved.
     *
     * @param value
     *            the flag code (1..8)
     * @return the matching constant, or {@link #SPLITJOIN1} when unmatched
     */
    public static Heuristic fromCode(int value) {
        for (Heuristic type : values()) {
            if (type.code == value) {
                return type;
            }
        }
        return SPLITJOIN1;
    }
}
