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
 * Cut / Interval Analysis display — trims/partitions via interval intersection
 * strategies.
 */
@Route("cut")
@Menu(title = "Cut / Interval", order = 14, icon = "vaadin:scissors")
public class RouteCutView extends VerticalLayout {

    public RouteCutView() {
        add(new H1("Cut / Interval Analysis"));
        Span body = new Span("Cut / Interval Analysis display goes here.");
        body.setId("cut-body");
        add(body);
    }
}
