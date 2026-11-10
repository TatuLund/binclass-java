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
 * Compare Partitions display — builds a contingency matrix between two
 * partitions and computes the nearness distance.
 */
@Route("compare")
@Menu(title = "Compare", order = 10, icon = "vaadin:table")
public class RouteCompareView extends VerticalLayout {

    public RouteCompareView() {
        add(new H1("Compare Partitions"));
        Span body = new Span("Compare Partitions display goes here.");
        body.setId("compare-body");
        add(body);
    }
}
