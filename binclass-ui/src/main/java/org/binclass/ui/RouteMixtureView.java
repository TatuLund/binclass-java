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
 * Mixture Classifier / EM display — fits a Gaussian-mixture-like (Bernoulli)
 * model via EM.
 */
@Route("mixture")
@Menu(title = "Mixture (EM)", order = 7, icon = "vaadin:flask")
public class RouteMixtureView extends VerticalLayout {

    public RouteMixtureView() {
        add(new H1("Mixture Classifier"));
        Span body = new Span("Mixture (EM) display goes here.");
        body.setId("mixture-body");
        add(body);
    }
}
