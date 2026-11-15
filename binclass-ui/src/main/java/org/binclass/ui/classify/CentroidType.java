/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify;

import org.jspecify.annotations.NullMarked;

/**
 * Centroid strategy exposed by the GLA Search form, mirroring the {@code -c}
 * flag understood by {@code ClassifyCommand}. Each constant carries the numeric
 * code used by {@link org.binclass.algorithms.gla.GLAConfig} and a readable
 * label shown in the UI select.
 * <p>
 * The codes follow the C parser ({@code original/parser.c}): 1=CLASSIC, 2=SRAND
 * (random seed), 3=SEMI (semi-random), 4=RAND (random), 5=PNN.
 * </p>
 */
@NullMarked
public enum CentroidType {

    /** Classic majority-rule centroid. */
    CLASSIC(1, "Classic"),
    /** Random-seed initialization. */
    SRAND(2, "Random Seed"),
    /** Semi-random initialization. */
    SEMI(3, "Semi-Random"),
    /** Fully random initialization. */
    RAND(4, "Random"),
    /** PNN (progressive nearest neighbor) centroid. */
    PNN(5, "PNN");

    private final int code;
    private final String label;

    CentroidType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * Returns the numeric centroid-type code used by
     * {@link org.binclass.algorithms.gla.GLAConfig}.
     *
     * @return the flag code (1..5)
     */
    public int code() {
        return code;
    }

    /**
     * Returns the friendly label shown in the GLA Search select.
     *
     * @return a human-readable description of the strategy
     */
    public String label() {
        return label;
    }

    /**
     * Maps a numeric centroid-type code to its {@link CentroidType}, falling
     * back to {@link #CLASSIC} for unknown values so the default is preserved.
     *
     * @param value
     *            the flag code (1..5)
     * @return the matching constant, or {@link #CLASSIC} when unmatched
     */
    public static CentroidType fromCode(int value) {
        for (CentroidType type : values()) {
            if (type.code == value) {
                return type;
            }
        }
        return CLASSIC;
    }
}
