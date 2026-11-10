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
 * Fast Classify / Split-GLA display — a fast k search seeded from the first two
 * vectors.
 */
@Route("fclassify")
@Menu(title = "Fast Classify", order = 5, icon = "vaadin:bolt")
public class RouteFastClassifyView extends VerticalLayout {

    public RouteFastClassifyView() {
        add(new H1("Fast Classify"));
        Span body = new Span("Fast Classify display goes here.");
        body.setId("fclassify-body");
        add(body);
    }
}
