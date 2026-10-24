/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.demo;

import java.util.List;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.binclass.components.graph.Graph;
import org.binclass.components.graph.GraphModel;
import org.binclass.components.graph.GraphPoint;

/**
 * Minimal Vaadin view that hosts the {@link Graph} component with
 * representative mock data so Playwright tests can drive it. The data does not
 * need to be mathematically correct, only visually representative: a narrow
 * range (1–10), a wide range (1–200), an interior minimum, an endpoint minimum
 * and a flat / single-value series that exercises the zero-range guard.
 */
@Route("graph")
public class GraphView extends VerticalLayout {

    public GraphView() {
        setSpacing(true);
        setPadding(true);

        add(createNarrowSection());
        add(createWideSection());
        add(createInteriorMinimumSection());
        add(createEndpointMinimumSection());
        add(createFlatSection());
        add(createSingleValueSection());
    }

    private VerticalLayout createNarrowSection() {
        VerticalLayout section = new VerticalLayout();
        section.add("Graph — narrow range 1–10");

        Graph graph = new Graph();
        graph.setId("graph-narrow");
        graph.setData(new GraphModel(List.of(
                new GraphPoint(1, 32.0),
                new GraphPoint(2, 26.0),
                new GraphPoint(3, 24.5),
                new GraphPoint(4, 23.8),
                new GraphPoint(5, 23.2),
                new GraphPoint(6, 22.9),
                new GraphPoint(7, 22.7),
                new GraphPoint(8, 22.6),
                new GraphPoint(9, 22.5),
                new GraphPoint(10, 22.4))));
        section.add(graph);

        return section;
    }

    private VerticalLayout createWideSection() {
        VerticalLayout section = new VerticalLayout();
        section.add("Graph — wide range 1–200");

        Graph graph = new Graph();
        graph.setId("graph-wide");
        List<GraphPoint> points = new java.util.ArrayList<>();
        double sc = 35.0;
        for (int k = 1; k <= 200; k++) {
            sc -= 8.0 / Math.sqrt(k);
            if (sc < 21.0) {
                sc = 21.0 + (k % 7) * 0.05;
            }
            points.add(new GraphPoint(k, sc));
        }
        graph.setData(new GraphModel(points));
        section.add(graph);

        return section;
    }

    private VerticalLayout createInteriorMinimumSection() {
        VerticalLayout section = new VerticalLayout();
        section.add("Graph — interior minimum (valley)");

        // Minimum sits at k = 6, not on an endpoint.
        Graph graph = new Graph();
        graph.setId("graph-interior");
        graph.setData(new GraphModel(List.of(
                new GraphPoint(1, 30.0),
                new GraphPoint(2, 27.0),
                new GraphPoint(3, 25.0),
                new GraphPoint(4, 24.0),
                new GraphPoint(5, 23.6),
                new GraphPoint(6, 23.1),
                new GraphPoint(7, 23.9),
                new GraphPoint(8, 25.5),
                new GraphPoint(9, 27.0),
                new GraphPoint(10, 28.5))));
        section.add(graph);

        return section;
    }

    private VerticalLayout createEndpointMinimumSection() {
        VerticalLayout section = new VerticalLayout();
        section.add("Graph — minimum at the right endpoint");

        // Monotonically decreasing: minimum is the last point.
        Graph graph = new Graph();
        graph.setId("graph-endpoint");
        graph.setData(new GraphModel(List.of(
                new GraphPoint(1, 30.0),
                new GraphPoint(2, 28.5),
                new GraphPoint(3, 27.0),
                new GraphPoint(4, 25.5),
                new GraphPoint(5, 24.0))));
        section.add(graph);

        return section;
    }

    private VerticalLayout createFlatSection() {
        VerticalLayout section = new VerticalLayout();
        section.add("Graph — flat series (zero-range guard)");

        // All y equal: the client expands symmetrically so ticks exist.
        Graph graph = new Graph();
        graph.setId("graph-flat");
        graph.setData(new GraphModel(List.of(
                new GraphPoint(1, 25.0),
                new GraphPoint(2, 25.0),
                new GraphPoint(3, 25.0),
                new GraphPoint(4, 25.0))));
        section.add(graph);

        return section;
    }

    private VerticalLayout createSingleValueSection() {
        VerticalLayout section = new VerticalLayout();
        section.add("Graph — single value");

        // One point: derive x/y bands with symmetric padding around it.
        Graph graph = new Graph();
        graph.setId("graph-single");
        graph.setData(new GraphModel(List.of(new GraphPoint(4, 25.0))));
        section.add(graph);

        return section;
    }
}
