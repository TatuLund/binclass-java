/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.dist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Criteria}.
 */
class CriteriaTest {

    @Test
    void recordAccessorsReturnValues() {
        Criteria criteria = new Criteria(1.5, 2.5, 3.5, 4.5);

        assertEquals(1.5, criteria.sc());
        assertEquals(2.5, criteria.d());
        assertEquals(3.5, criteria.i1());
        assertEquals(4.5, criteria.i2());
    }

    @Test
    void recordComponentsReturnValues() {
        Criteria criteria = new Criteria(1.5, 2.5, 3.5, 4.5);

        assertEquals(1.5, criteria.sc());
        assertEquals(2.5, criteria.d());
        assertEquals(3.5, criteria.i1());
        assertEquals(4.5, criteria.i2());
    }

    @Test
    void recordEqualityBasedOnComponents() {
        Criteria first = new Criteria(1.0, 2.0, 3.0, 4.0);
        Criteria second = new Criteria(1.0, 2.0, 3.0, 4.0);

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void recordInequalityWhenComponentsDiffer() {
        Criteria first = new Criteria(1.0, 2.0, 3.0, 4.0);
        Criteria second = new Criteria(1.0, 2.0, 3.0, 5.0);

        assertNotEquals(first, second);
    }

    @Test
    void recordToStringContainsValues() {
        Criteria criteria = new Criteria(1.0, 2.0, 3.0, 4.0);

        String string = criteria.toString();
        assertTrue(string.contains("sc=1.0"));
        assertTrue(string.contains("d=2.0"));
        assertTrue(string.contains("i1=3.0"));
        assertTrue(string.contains("i2=4.0"));
    }
}
