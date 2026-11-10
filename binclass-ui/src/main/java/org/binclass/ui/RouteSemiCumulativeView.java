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
 * Semi-Cumulative Classification display — join-GLA search growing from a
 * 2-cluster seed.
 */
@Route("sclassify")
@Menu(title = "Semi-Cumulative", order = 4, icon = "vaadin:scale")
public class RouteSemiCumulativeView extends VerticalLayout {

    public RouteSemiCumulativeView() {
        add(new H1("Semi-Cumulative Classification"));
        Span body = new Span(
                "Semi-Cumulative Classification display goes here.");
        body.setId("sclassify-body");
        add(body);
    }
}
