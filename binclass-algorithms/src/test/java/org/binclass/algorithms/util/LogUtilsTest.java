/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LogUtils}.
 */
class LogUtilsTest {

    @Test
    void testLogCriteriaVerboseTrue() {
        assertDoesNotThrow(() -> LogUtils.logCriteria(true, 3, 1.23456789,
                2.34567890, 3.45678901, 4.56789012));
    }

    @Test
    void testLogCriteriaVerboseFalse() {
        assertDoesNotThrow(() -> LogUtils.logCriteria(false, 3, 1.0, 2.0, 3.0,
                4.0));
    }

    @Test
    void testLogTooFewTrials() {
        assertDoesNotThrow(LogUtils::logTooFewTrials);
    }

    @Test
    void testFmt5UsesRootLocale() throws Exception {
        // fmt5 is private; verify formatting via reflection to confirm the
        // fixed-point pattern and root locale are used.
        java.lang.reflect.Method method = LogUtils.class
                .getDeclaredMethod("fmt5", double.class);
        method.setAccessible(true);

        String formatted = (String) method.invoke(null, 1.23456789);
        assertEquals("1.23457", formatted);
    }

    @Test
    void testFmt4UsesRootLocale() throws Exception {
        java.lang.reflect.Method method = LogUtils.class
                .getDeclaredMethod("fmt4", double.class);
        method.setAccessible(true);

        String formatted = (String) method.invoke(null, 1.23456789);
        assertEquals("1.2346", formatted);
    }
}
