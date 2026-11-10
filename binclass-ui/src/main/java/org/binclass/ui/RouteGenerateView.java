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
 * Data Generator display — produces synthetic binary vectors from reference
 * data.
 */
@Route("generate")
@Menu(title = "Data Generator", order = 12, icon = "vaadin:code")
public class RouteGenerateView extends VerticalLayout {

    public RouteGenerateView() {
        add(new H1("Data Generator"));
        Span body = new Span("Data Generator display goes here.");
        body.setId("generate-body");
        add(body);
    }
}
