/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.gla;

/**
 * Search strategy used by the SC minimizer, mirroring the C {@code eSearch}
 * enum from {@code const.h}. The default is {@link #AUTO}, which runs the two
 * phase automatic SC-minimizer (forward scan followed by a ping-pong
 * enhancement).
 */
public enum SearchType {

    /** Automatic SC-minimizer: forward scan then ping-pong enhancement. */
    AUTO,

    /**
     * Non-automatic range search over a fixed {@code kstart..kstop} interval.
     */
    NAUTO,

    /** Load predefined centroids and evaluate them directly. */
    LCENT,

    /** Adaptive search driven by a threshold. */
    ADAP;

    /**
     * Maps the numeric identifier used by C's parser to its {@link SearchType}.
     * <p>
     * The C enum is {@code ST_AUTO=1, ST_NAUTO=2, ST_LCENT=3, ST_ADAP=4}; the
     * Java switch dispatches on those values. Unknown values map to
     * {@link #AUTO} so that the default search method is preserved when a flag
     * is absent.
     * </p>
     *
     * @param id
     *            the numeric search-type identifier (1..4)
     * @return the matching {@link SearchType}, or {@link #AUTO} if unmatched
     */
    public static SearchType fromId(int id) {
        return switch (id) {
        case 2 -> NAUTO;
        case 3 -> LCENT;
        case 4 -> ADAP;
        default -> AUTO;
        };
    }

    /**
     * Returns the numeric identifier associated with this search type, matching
     * the C {@code eSearch} values.
     *
     * @return the search-type id (1 for {@link #AUTO})
     */
    public int getId() {
        return switch (this) {
        case AUTO -> 1;
        case NAUTO -> 2;
        case LCENT -> 3;
        case ADAP -> 4;
        };
    }
}
