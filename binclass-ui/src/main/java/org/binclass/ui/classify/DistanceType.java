/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify;

import org.jspecify.annotations.NullMarked;

/**
 * Distance metric exposed by the GLA Search form, mirroring the {@code -f} flag
 * understood by {@code ClassifyCommand}. Each constant carries the numeric code
 * used by {@link org.binclass.algorithms.gla.GLAConfig} and a human-readable
 * label shown in the UI select.
 * <p>
 * The codes follow the C parser ({@code original/parser.c}): 1=Hamming, 2=L1,
 * 3=L2, 4=Shannon codelength, with hybrid variants combining a metric and a
 * codelength term.
 * </p>
 */
@NullMarked
public enum DistanceType {

    /** Hamming distance (also used as Gower for mixed data). */
    HAMMING(1, "Hamming"),
    /** L1 / Manhattan mean absolute error. */
    L1_MANHATTAN(2, "L1 (Manhattan)"),
    /** L2 / Euclidean mean square error. */
    L2_EUCLIDEAN(3, "L2 (Euclidean)"),
    /** Shannon codelength only. */
    CODELENGTH(4, "Shannon Codelength"),
    /** L1 metric combined with Shannon codelength. */
    L1_CODELENGTH(5, "L1 + Codelength"),
    /** L2 metric combined with Shannon codelength. */
    L2_CODELENGTH(6, "L2 + Codelength"),
    /** Codelength combined with a symmetric relative measure. */
    CODELENGTH_SR(7, "Codelength + SR"),
    /** Codelength combined with a symmetric accuracy measure. */
    CODELENGTH_SA(8, "Codelength + SA");

    private final int code;
    private final String label;

    DistanceType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    /**
     * Returns the numeric distance-type code used by
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
     * @return a human-readable description of the metric
     */
    public String label() {
        return label;
    }

    /**
     * Maps a numeric distance-type code to its {@link DistanceType}, falling
     * back to {@link #HAMMING} for unknown values so the default is preserved.
     *
     * @param value
     *            the flag code (1..8)
     * @return the matching constant, or {@link #HAMMING} when unmatched
     */
    public static DistanceType fromCode(int value) {
        for (DistanceType type : values()) {
            if (type.code == value) {
                return type;
            }
        }
        return HAMMING;
    }
}
