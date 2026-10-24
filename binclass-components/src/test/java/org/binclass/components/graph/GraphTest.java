/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.vaadin.flow.signals.local.ValueSignal;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link GraphPoint} / {@link GraphModel} records and the
 * {@link Graph} wrapper. The point list is pushed to the client as a single
 * object ({@code { points: [...] }}) so assertions verify both the record
 * invariants and the serialized shape.
 */
class GraphTest {

    // --- GraphPoint ------------------------------------------------------

    @Test
    void graphPointStoresCoordinates() {
        GraphPoint point = new GraphPoint(3, 24.5);
        assertEquals(3, point.x());
        assertEquals(24.5, point.y(), 0.0);
    }

    @Test
    void graphPointRejectsNonFiniteY() {
        assertThrows(IllegalArgumentException.class,
                () -> new GraphPoint(1, Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new GraphPoint(1, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> new GraphPoint(1, Double.NEGATIVE_INFINITY));
    }

    // --- GraphModel ------------------------------------------------------

    @Test
    void graphModelRejectsEmptyList() {
        assertThrows(IllegalArgumentException.class,
                () -> new GraphModel(List.of()));
    }

    @Test
    void graphModelStoresPointsUnchanged() {
        List<GraphPoint> points = List.of(new GraphPoint(1, 30.0),
                new GraphPoint(2, 25.0));
        GraphModel model = new GraphModel(points);
        assertEquals(points, model.points());
    }

    @Test
    void graphModelPointsReturnUnmodifiableCopy() {
        GraphModel model = new GraphModel(List.of(new GraphPoint(1, 30.0)));
        assertThrows(UnsupportedOperationException.class,
                () -> model.points().add(new GraphPoint(2, 25.0)));
    }

    @Test
    void graphModelCopiesInputList() {
        List<GraphPoint> input = new java.util.ArrayList<>(
                List.of(new GraphPoint(1, 30.0)));
        GraphModel model = new GraphModel(input);
        input.add(new GraphPoint(2, 25.0));
        assertEquals(1, model.points().size());
    }

    // --- Graph wrapper ---------------------------------------------------

    @Test
    void setDataPushesModelProperty() {
        Graph graph = new Graph();
        graph.setData(new GraphModel(
                List.of(new GraphPoint(1, 30.0), new GraphPoint(2, 25.0))));

        assertTrue(graph.getElement().hasProperty(Graph.PROPERTY_MODEL));
    }

    @Test
    void setDataWithNullClearsModel() {
        Graph graph = new Graph();
        graph.setData(new GraphModel(List.of(new GraphPoint(1, 30.0))));
        assertTrue(graph.getElement().hasProperty(Graph.PROPERTY_MODEL));

        graph.setData(null);
        // With no signal bound, a null model removes the pushed property so
        // the client clears the chart.
        assertTrue(!graph.getElement().hasProperty(Graph.PROPERTY_MODEL));
    }

    @Test
    void getPointsReturnsFlatListOfPointMaps() {
        Graph graph = new Graph();
        graph.setData(new GraphModel(List.of(
                new GraphPoint(1, 30.0),
                new GraphPoint(2, 25.0))));

        List<Map<String, Object>> points = graph.getPoints();
        assertEquals(2, points.size());

        Map<String, Object> first = points.get(0);
        assertEquals(1, (Integer) first.get("x"));
        assertEquals(30.0, (Double) first.get("y"), 0.0);
        assertEquals(2, (Integer) points.get(1).get("x"));
    }

    @Test
    void getPointsReturnsEmptyListWhenNothingSet() {
        Graph graph = new Graph();
        assertEquals(0, graph.getPoints().size());
    }

    @Test
    void bindModelThenSetUpdatesBoundSignal() {
        Graph graph = new Graph();
        ValueSignal<GraphModel> signal = new ValueSignal<>(
                new GraphModel(List.of(new GraphPoint(1, 30.0))));
        graph.bindModel(signal);

        GraphModel updated = new GraphModel(List.of(new GraphPoint(2, 25.0)));
        graph.setData(updated);

        assertEquals(updated, signal.peek());
    }

    // --- Static helpers --------------------------------------------------

    @Test
    void formatScUsesRootLocaleWithTwoDecimals() {
        assertEquals("24.73", Graph.formatSc(24.725));
        assertEquals("0.00", Graph.formatSc(0.0));
    }
}
