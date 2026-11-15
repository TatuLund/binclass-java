/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify;

import org.jspecify.annotations.NullMarked;

/**
 * Empty-cell (orphaned-centroid) fix method exposed by the GLA Search form,
 * mirroring the {@code -eX} flag understood by {@code ClassifyCommand}. Each
 * constant carries the numeric mode code used by
 * {@link org.binclass.algorithms.gla.GLAConfig#alternateMode()} and a readable
 * label shown in the UI select.
 * <p>
 * The codes follow C {@code parse_classify()}: 1 = worst-match only, 2 =
 * random-vector fill, 3 = worst-match plus an extra iteration (weights), 4 =
 * random-vector fill plus an extra iteration. Default is mode 1 per
 * {@code vars.c} ({@code alternate_worst_match = TRUE},
 * {@code alternate_empty_cell_fix = FALSE}).
 * </p>
 */
@NullMarked
public enum EmptyCellFix {

    /** Fill the empty cell with the absolute worst-match vector. */
    WORST_MATCH(1, "Worst match"),
    /** Fill the empty cell with a random vector. */
    RANDOM_VECTOR(2, "Random vector"),
    /** Worst-match fill plus an extra iteration (for class weights). */
    WORST_MATCH_EXTRA_ITER(3, "Worst match + extra iteration"),
    /** Random-vector fill plus an extra iteration (for class weights). */
    RANDOM_VECTOR_EXTRA_ITER(4, "Random vector + extra iteration");

    private final int code;
    private final String label;

    EmptyCellFix(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * Returns the numeric mode code used by
     * {@link org.binclass.algorithms.gla.GLAConfig#alternateMode()}.
     *
     * @return the flag code (1..4)
     */
    public int code() {
        return code;
    }

    /**
     * Returns the friendly label shown in the GLA Search select.
     *
     * @return a human-readable description of the fix method
     */
    public String label() {
        return label;
    }

    /**
     * Maps a numeric mode code to its {@link EmptyCellFix}, falling back to
     * {@link #WORST_MATCH} for unknown values so the default is preserved.
     *
     * @param value
     *            the flag code (1..4)
     * @return the matching constant, or {@link #WORST_MATCH} when unmatched
     */
    public static EmptyCellFix fromCode(int value) {
        for (EmptyCellFix type : values()) {
            if (type.code == value) {
                return type;
            }
        }
        return WORST_MATCH;
    }
}
