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
 * GLA Search display — the flagship view. Mirrors {@code ClassifyCommand} and
 * offers both range and automatic search controlled by the same switches.
 */
@Route("gla")
@Menu(title = "GLA Search", order = 1, icon = "vaadin:search")
public class RouteGlaView extends VerticalLayout {

    public RouteGlaView() {
        add(new H1("GLA Search"));
        Span body = new Span("GLA Search display goes here.");
        body.setId("gla-body");
        add(body);
    }
}
