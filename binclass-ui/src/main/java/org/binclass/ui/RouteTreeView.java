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
 * Tree / Dendrogram display — builds a hierarchical dendrogram from a
 * partition.
 */
@Route("tree")
@Menu(title = "Dendrogram", order = 13, icon = "vaadin:file-tree")
public class RouteTreeView extends VerticalLayout {

    public RouteTreeView() {
        add(new H1("Dendrogram"));
        Span body = new Span("Dendrogram display goes here.");
        body.setId("tree-body");
        add(body);
    }
}
