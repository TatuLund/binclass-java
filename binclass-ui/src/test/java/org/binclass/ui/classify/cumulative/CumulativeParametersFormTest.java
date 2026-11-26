/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.cumulative;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.binclass.algorithms.classify.CumulativeConfig;
import org.binclass.algorithms.util.MathUtils;

/**
 * Unit tests for {@link CumulativeParametersForm}, covering the flag-to-config
 * translation described in {@code docs/WebUI.PRD.md} §5.2 — done here in the UI
 * module so the mapping stays close to the form that feeds it.
 */
class CumulativeParametersFormTest {

    @Test
    void defaultsMatchCumulativeConfigDefaults() {
        CumulativeParametersForm form = CumulativeParametersForm.defaults();

        assertEquals(1, form.delta());
        assertFalse(form.fixedDelta());
        assertTrue(form.bayesianPredictive());
        assertTrue(form.cumSaveByPf());
        assertFalse(form.inOrder());
        assertEquals(MathUtils.EPSILON, form.epsilon());
    }

    @Test
    void fixedDeltaTrueWhenDFlagProvided() {
        CumulativeParametersForm form = new CumulativeParametersForm(
                "test", 5, true, false, 0, true, false, true, false, false,
                MathUtils.EPSILON);

        assertEquals(5, form.delta());
        assertTrue(form.fixedDelta());
    }

    @Test
    void fixedDeltaFalseWhenDFlagAbsent() {
        CumulativeParametersForm form = new CumulativeParametersForm(
                "test", 3, false, false, 0, true, false, true, false, false,
                MathUtils.EPSILON);

        assertEquals(3, form.delta());
        assertFalse(form.fixedDelta());
    }

    @Test
    void toConfigCarriesScalarValuesThrough() {
        CumulativeParametersForm form = new CumulativeParametersForm(
                "test", 4, true, true, 7, false, true, false, true, true,
                MathUtils.EPSILON);

        CumulativeConfig config = form.toConfig(100);

        assertEquals(4, config.delta());
        assertTrue(config.fixedDelta());
        assertTrue(config.cumulativeAnalysis());
        assertEquals(7, config.cumulativeSamples());
        assertFalse(config.bayesianPredictive());
        assertTrue(config.testFeatureSignificance());
        assertFalse(config.cumSaveByPf());
        assertTrue(config.cumNoNewClasses());
        assertTrue(config.inOrder());
        assertEquals(MathUtils.EPSILON, config.epsilon());
    }

    @Test
    void toConfigStoresVectorCount() {
        CumulativeParametersForm form = new CumulativeParametersForm(
                "test", 1, false, false, 0, true, false, true, false, false,
                MathUtils.EPSILON);

        CumulativeConfig config = form.toConfig(42);

        assertEquals(42, config.n());
    }

    @Test
    void isValidTrueWhenDeltaNonNegativeAndEpsilonInRange() {
        CumulativeParametersForm form = new CumulativeParametersForm(
                "test", 0, false, false, 0, true, false, true, false, false,
                MathUtils.EPSILON);

        assertTrue(form.isValid());
    }

    @Test
    void isValidFalseWhenEpsilonOutOfRange() {
        CumulativeParametersForm form = new CumulativeParametersForm(
                "test", 1, false, false, 0, true, false, true, false, false,
                0.9);

        assertFalse(form.isValid());
        assertTrue(form.isValidEpsilon());

        form = new CumulativeParametersForm(
                "test", 1, false, false, 0, true, false, true, false, false,
                -0.5);
        assertFalse(form.isValid());
    }

    @Test
    void toConfigValidatesEpsilon() {
        CumulativeParametersForm form = new CumulativeParametersForm(
                "test", 1, false, false, 0, true, false, true, false, false,
                0.9);

        assertThrows(IllegalArgumentException.class, () -> form.toConfig());
    }
}
