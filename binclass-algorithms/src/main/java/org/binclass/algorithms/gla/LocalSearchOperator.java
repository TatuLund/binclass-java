/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.gla;

/**
 * Enumerates the local search operators used by the GLA heuristic search.
 * <p>
 * Mirrors the {@code eHeuristic} enum from {@code glainf.h} in the original C
 * codebase. Each value maps to the numeric identifier used by the adaptive
 * probability bookkeeping in {@link LocalSearch#localSearch}. The first five
 * operators (indices 0..4) participate in the weighted selection scheme;
 * {@link #NONE} is a placeholder that performs no operator.
 * </p>
 */
public enum LocalSearchOperator {

    /** Replace smallest class — {@code split_and_join2} style replacement. */
    REPLACESMALLEST(1),

    /** Split and join (SJ1). */
    SPLITJOIN1(2),

    /** Split and join variant 2 (SJ2). */
    SPLITJOIN2(3),

    /** Replace worst class. */
    REPLACEWORST(4),

    /** Random swap of a vector between two classes. */
    RANDOMSWAP(5),

    /** Random swap within a single class. */
    RANDOMSWAP2(6),

    /** No operator applied. */
    NONE(0);

    private final int id;

    LocalSearchOperator(int id) {
        this.id = id;
    }

    /**
     * Returns the numeric identifier associated with this operator, matching
     * the C {@code eHeuristic} values.
     *
     * @return the operator's numeric id (0 for {@link #NONE})
     */
    public int getId() {
        return this.id;
    }

    /**
     * Maps a numeric heuristic identifier to its {@link LocalSearchOperator}.
     * <p>
     * Mirrors the C switch dispatch used inside {@code local_search()} where
     * indices 1..6 select an operator. Unknown values map to {@link #NONE}.
     * </p>
     *
     * @param id
     *            the numeric heuristic identifier (0..6)
     * @return the matching operator, or {@link #NONE} if unmatched
     */
    public static LocalSearchOperator fromId(int id) {
        return switch (id) {
        case 1 -> REPLACESMALLEST;
        case 2 -> SPLITJOIN1;
        case 3 -> SPLITJOIN2;
        case 4 -> REPLACEWORST;
        case 5 -> RANDOMSWAP;
        case 6 -> RANDOMSWAP2;
        default -> NONE;
        };
    }
}
