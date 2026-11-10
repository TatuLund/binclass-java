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
 * Bootstrap Analysis display — runs many GLA trials from resampled data and
 * statistically analyses SC values.
 */
@Route("bootstrap")
@Menu(title = "Bootstrap", order = 8, icon = "vaadin:calc")
public class RouteBootstrapView extends VerticalLayout {

    public RouteBootstrapView() {
        add(new H1("Bootstrap Analysis"));
        Span body = new Span("Bootstrap Analysis display goes here.");
        body.setId("bootstrap-body");
        add(body);
    }
}
