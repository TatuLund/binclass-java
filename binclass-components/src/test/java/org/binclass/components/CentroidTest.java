/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Centroid}.
 */
class CentroidTest {

    @Test
    void getProbabilitiesReturnsEmptyArrayWhenNothingSet() {
        Centroid centroid = new Centroid();
        assertArrayEquals(new double[0], centroid.getProbabilities());
    }

    @Test
    void setProbabilitiesRoundTripsDoubleArray() {
        Centroid centroid = new Centroid();
        centroid.setProbabilities(new double[] { 0.1, 0.5, 0.9 });
        assertArrayEquals(new double[] { 0.1, 0.5, 0.9 },
                centroid.getProbabilities());
    }

    @Test
    void setProbabilitiesHandlesNull() {
        Centroid centroid = new Centroid();
        centroid.setProbabilities(null);
        assertArrayEquals(new double[0], centroid.getProbabilities());
    }

    @Test
    void setDataClampsProbabilities() {
        CentroidData data = new CentroidData(new double[] { -1.0, 2.0, 0.5 });
        Centroid centroid = new Centroid();
        centroid.setData(data);
        assertArrayEquals(new double[] { 0.0, 1.0, 0.5 },
                centroid.getProbabilities());
    }

    @Test
    void setDataPushesMetadata() {
        CentroidData data = new CentroidData(
                new double[] { 0.2, 0.8 },
                "cluster-a",
                1.5,
                0.3,
                42);
        Centroid centroid = new Centroid();
        centroid.setData(data);

        assertEquals("cluster-a", centroid.getElement().getProperty("name"));
        assertEquals(1.5, centroid.getElement().getProperty("classDistortion",
                Double.NaN));
        assertEquals(0.3,
                centroid.getElement().getProperty("weight", Double.NaN));
        assertEquals(42, centroid.getElement().getProperty("vectorCount", -1));
    }

    @Test
    void setDataRejectsNull() {
        Centroid centroid = new Centroid();
        assertThrows(IllegalArgumentException.class,
                () -> centroid.setData(null));
    }

    @Test
    void getProbabilitiesReturnsDefensiveCopy() {
        Centroid centroid = new Centroid();
        centroid.setProbabilities(new double[] { 0.5 });
        double[] probs = centroid.getProbabilities();
        probs[0] = 0.9;
        assertArrayEquals(new double[] { 0.5 }, centroid.getProbabilities());
    }

    @Test
    void setProbabilitiesClampsOutboundValues() {
        Centroid centroid = new Centroid();
        centroid.setProbabilities(new double[] { -5.0, 10.0 });
        assertArrayEquals(new double[] { 0.0, 1.0 },
                centroid.getProbabilities());
    }

    @Test
    void probabilitiesPropertyIsListType() {
        Centroid centroid = new Centroid();
        centroid.setProbabilities(new double[] { 0.25, 0.75 });
        List<?> values = centroid.getElement().getPropertyBean("probabilities",
                List.class);
        assertEquals(List.of(0.25, 0.75), values);
    }
}
