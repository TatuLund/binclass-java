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
 * Function Plot display — renders SC (and Shannon entropy) as a function of k.
 */
@Route("function")
@Menu(title = "Function Plot", order = 11, icon = "vaadin:chart-line")
public class RouteFunctionView extends VerticalLayout {

    public RouteFunctionView() {
        add(new H1("Function Plot"));
        Span body = new Span("Function Plot display goes here.");
        body.setId("function-body");
        add(body);
    }
}
