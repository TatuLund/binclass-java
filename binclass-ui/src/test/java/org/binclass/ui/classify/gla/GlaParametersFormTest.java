/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.gla;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.SearchType;

/**
 * Unit tests for {@link GlaParametersForm}, covering the flag-to-config
 * translation and search-type resolution described in {@code docs/WebUI.PRD.md}
 * §3.2 — done here in the UI module so the mapping stays close to the form that
 * feeds it.
 */
class GlaParametersFormTest {

    /** Builds a form with sensible defaults for the given overrides. */
    private static GlaParametersForm form(int kStart, int kStop, int maxIter,
            String centroidFile) {
        return new GlaParametersForm(
                "test",
                kStart,
                kStop,
                0,
                0,
                0.1,
                false,
                GLAConfig.DEFAULT.heuristic(),
                GLAConfig.DEFAULT.alternateMode(),
                false,
                GLAConfig.DEFAULT.centroidType(),
                false,
                false,
                false,
                false,
                false,
                0.0,
                false,
                false,
                GLAConfig.DEFAULT.distanceType(),
                maxIter,
                GLAConfig.DEFAULT.heuristicCount(),
                GLAConfig.DEFAULT.safetyLimit(),
                GLAConfig.DEFAULT.iterBase(),
                centroidFile);
    }

    @Test
    void defaultsMatchGlaConfigDefaults() {
        GlaParametersForm form = GlaParametersForm.defaults();

        assertEquals(1, form.kStart());
        assertEquals(0, form.kStop());
        assertEquals(GLAConfig.DEFAULT.epsilon(), form.epsilon());
        assertEquals(GLAConfig.DEFAULT.distanceType(), form.distanceType());
    }

    @Test
    void automaticSearchWhenNoRangeOrIterations() {
        GlaParametersForm form = form(1, 0, 0, "");

        assertEquals(SearchType.AUTO, form.determineSearchType());
    }

    @Test
    void rangeSearchWhenKStartExceedsOne() {
        GlaParametersForm form = form(3, 0, 0, "");

        assertEquals(SearchType.NAUTO, form.determineSearchType());
    }

    @Test
    void rangeSearchWhenKStopSet() {
        GlaParametersForm form = form(1, 5, 0, "");

        assertEquals(SearchType.NAUTO, form.determineSearchType());
    }

    @Test
    void rangeSearchWhenMaxIterPositive() {
        GlaParametersForm form = form(1, 0, 100, "");

        assertEquals(SearchType.NAUTO, form.determineSearchType());
    }

    @Test
    void lcentSearchWhenCentroidFilePresent() {
        GlaParametersForm form = form(1, 0, 0, "centroids.txt");

        assertEquals(SearchType.LCENT, form.determineSearchType());
    }

    @Test
    void toConfigCarriesScalarValuesThrough() {
        GlaParametersForm form = new GlaParametersForm(
                "test",
                2,
                4,
                7,
                9,
                0.15,
                true,
                3,
                2,
                true,
                4,
                true,
                true,
                true,
                true,
                true,
                1.25,
                true,
                false,
                6,
                50,
                8,
                1000,
                3,
                "centroids.txt");

        GLAConfig config = form.toConfig(42);

        assertEquals(0.15, config.epsilon());
        assertEquals(3, config.heuristic());
        assertEquals(2, config.alternateMode());
        assertEquals(4, config.centroidType());
        assertEquals(6, config.distanceType());
        assertEquals(50, config.maxIter());
        assertTrue(config.decreasingEpsilon());
    }

    @Test
    void toConfigSetsVectorCount() {
        GLAConfig config = form(1, 0, 0, "").toConfig(128);

        // n is stored as the vector count; verify it round-trips via a getter.
        assertEquals(128, config.n());
    }

    @Test
    void toConfigValidatesEpsilon() {
        GlaParametersForm form = new GlaParametersForm(
                "test", 1, 0, 0, 0, 0.6, false,
                GLAConfig.DEFAULT.heuristic(),
                GLAConfig.DEFAULT.alternateMode(),
                false, GLAConfig.DEFAULT.centroidType(), false, false, false,
                false,
                false, 0.0, false, false, GLAConfig.DEFAULT.distanceType(), 0,
                GLAConfig.DEFAULT.heuristicCount(),
                GLAConfig.DEFAULT.safetyLimit(),
                GLAConfig.DEFAULT.iterBase(), "");

        assertThrows(IllegalArgumentException.class, () -> form.toConfig(10));
    }

    @Test
    void decreasingEpsilonSkipsValidation() {
        GlaParametersForm form = new GlaParametersForm(
                "test", 1, 0, 0, 0, 0.6, true,
                GLAConfig.DEFAULT.heuristic(),
                GLAConfig.DEFAULT.alternateMode(),
                false, GLAConfig.DEFAULT.centroidType(), false, false, false,
                false,
                false, 0.0, false, false, GLAConfig.DEFAULT.distanceType(), 0,
                GLAConfig.DEFAULT.heuristicCount(),
                GLAConfig.DEFAULT.safetyLimit(),
                GLAConfig.DEFAULT.iterBase(), "");

        // A bare decreasing-epsilon value passes validation unchanged.
        assertEquals(0.6, form.toConfig().epsilon());
    }
}
