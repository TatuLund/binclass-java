/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;

/**
 * Centroid Manager display — computes centroids from an existing partition
 * file.
 */
@Route("centroids")
@Menu(title = "Centroids", order = 9, icon = "vaadin:grid")
public class RouteCentroidView extends VerticalLayout {

    public RouteCentroidView() {
        add(new H1("Centroid Manager"));
        Span body = new Span("Centroid Manager display goes here.");
        body.setId("centroids-body");
        add(body);
    }
}
