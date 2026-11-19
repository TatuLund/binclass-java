/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.localsearch;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.binclass.algorithms.gla.GLAConfig;

/**
 * Unit tests for {@link LocalSearchParametersForm}, covering the flag-to-config
 * translation and epsilon validation described in {@code docs/WebUI.PRD.md}
 * §4.2 — done here in the UI module so the mapping stays close to the form that
 * feeds it. Local Search drops the search-type switches, so validity is just
 * the epsilon range check.
 */
class LocalSearchParametersFormTest {

    /** Builds a form with sensible defaults for the given overrides. */
    private static LocalSearchParametersForm form(int heuristic, int maxIter) {
        return new LocalSearchParametersForm(
                "test",
                heuristic,
                GLAConfig.DEFAULT.heuristicCount(),
                0.1,
                false,
                GLAConfig.DEFAULT.alternateMode(),
                GLAConfig.DEFAULT.centroidType(),
                GLAConfig.DEFAULT.distanceType(),
                false);
    }

    @Test
    void defaultsMatchGlaConfigDefaults() {
        LocalSearchParametersForm form = LocalSearchParametersForm.defaults();

        assertEquals(GLAConfig.DEFAULT.heuristic(), form.heuristic());
        assertEquals(GLAConfig.DEFAULT.epsilon(), form.epsilon());
        assertEquals(GLAConfig.DEFAULT.distanceType(), form.distanceType());
    }

    @Test
    void toConfigSetsHeuristicMetaFlags() {
        // Code 7 enables the cycler, code 8 the adaptive heuristic.
        LocalSearchParametersForm cycler = new LocalSearchParametersForm(
                "test", 7, GLAConfig.DEFAULT.heuristicCount(), 0.1, false,
                GLAConfig.DEFAULT.alternateMode(),
                GLAConfig.DEFAULT.centroidType(),
                GLAConfig.DEFAULT.distanceType(), false);
        LocalSearchParametersForm adaptive = new LocalSearchParametersForm(
                "test", 8, GLAConfig.DEFAULT.heuristicCount(), 0.1, false,
                GLAConfig.DEFAULT.alternateMode(),
                GLAConfig.DEFAULT.centroidType(),
                GLAConfig.DEFAULT.distanceType(), false);

        assertTrue(cycler.toConfig().lsCycler());
        assertTrue(!cycler.toConfig().lsAdaptive());
        assertTrue(adaptive.toConfig().lsAdaptive());
        assertTrue(!adaptive.toConfig().lsCycler());
    }

    @Test
    void toConfigCarriesScalarValuesThrough() {
        LocalSearchParametersForm form = new LocalSearchParametersForm(
                "test", 3, 8, 0.15, true, 2, 4, 6, true);

        GLAConfig config = form.toConfig(42);

        assertEquals(0.15, config.epsilon());
        assertEquals(3, config.heuristic());
        assertEquals(2, config.alternateMode());
        assertEquals(4, config.centroidType());
        assertEquals(6, config.distanceType());
        assertTrue(config.decreasingEpsilon());
    }

    @Test
    void toConfigSetsVectorCount() {
        GLAConfig config = form(1, 0).toConfig(128);

        // n is stored as the vector count; verify it round-trips via a getter.
        assertEquals(128, config.n());
    }

    @Test
    void toConfigValidatesEpsilon() {
        LocalSearchParametersForm form = new LocalSearchParametersForm(
                "test", 1, GLAConfig.DEFAULT.heuristicCount(), 0.6, false,
                GLAConfig.DEFAULT.alternateMode(),
                GLAConfig.DEFAULT.centroidType(),
                GLAConfig.DEFAULT.distanceType(), false);

        assertThrows(IllegalArgumentException.class, () -> form.toConfig(10));
    }

    @Test
    void decreasingEpsilonSkipsValidation() {
        LocalSearchParametersForm form = new LocalSearchParametersForm(
                "test", 1, GLAConfig.DEFAULT.heuristicCount(), 0.6, true,
                GLAConfig.DEFAULT.alternateMode(),
                GLAConfig.DEFAULT.centroidType(),
                GLAConfig.DEFAULT.distanceType(), false);

        // A bare decreasing-epsilon value passes validation unchanged.
        assertEquals(0.6, form.toConfig().epsilon());
    }

    @Test
    void isValidReflectsEpsilonRange() {
        // In-range epsilon is valid.
        assertTrue(form(1, 0).isValid());
        // Out-of-range epsilon without decreasing mode is invalid.
        LocalSearchParametersForm bad = new LocalSearchParametersForm(
                "test", 1, GLAConfig.DEFAULT.heuristicCount(), 0.6, false,
                GLAConfig.DEFAULT.alternateMode(),
                GLAConfig.DEFAULT.centroidType(),
                GLAConfig.DEFAULT.distanceType(), false);
        assertFalse(bad.isValid());
        // Out-of-range epsilon with decreasing mode is valid (bare value).
        LocalSearchParametersForm bare = new LocalSearchParametersForm(
                "test", 1, GLAConfig.DEFAULT.heuristicCount(), 0.6, true,
                GLAConfig.DEFAULT.alternateMode(),
                GLAConfig.DEFAULT.centroidType(),
                GLAConfig.DEFAULT.distanceType(), false);
        assertTrue(bare.isValid());
    }
}
