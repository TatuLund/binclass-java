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
 * Cumulative Classification display — processes vectors one at a time, growing
 * a dynamic partition.
 */
@Route("cumulative")
@Menu(title = "Cumulative Class.", order = 3, icon = "vaadin:cluster")
public class RouteCumulativeView extends VerticalLayout {

    public RouteCumulativeView() {
        add(new H1("Cumulative Classification"));
        Span body = new Span("Cumulative Classification display goes here.");
        body.setId("cumulative-body");
        add(body);
    }
}
