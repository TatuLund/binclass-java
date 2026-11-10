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
 * Local Search display — focused on heuristic local search (cycler / adaptive).
 */
@Route("local-search")
@Menu(title = "Local Search", order = 2, icon = "vaadin:crosshair")
public class RouteLocalSearchView extends VerticalLayout {

    public RouteLocalSearchView() {
        add(new H1("Local Search"));
        Span body = new Span("Local Search display goes here.");
        body.setId("local-search-body");
        add(body);
    }
}
