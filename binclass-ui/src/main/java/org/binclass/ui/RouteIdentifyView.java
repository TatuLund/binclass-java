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
 * Identify nearest centroid display — classifies a fresh set of vectors against
 * an existing centroid model.
 */
@Route("identify")
@Menu(title = "Identify", order = 6, icon = "vaadin:eye")
public class RouteIdentifyView extends VerticalLayout {

    public RouteIdentifyView() {
        add(new H1("Identify"));
        Span body = new Span("Identify display goes here.");
        body.setId("identify-body");
        add(body);
    }
}
