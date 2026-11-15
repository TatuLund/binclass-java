package org.binclass.ui;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;

@Route("")
@Menu(title = "Info", order = 0, icon = "vaadin:info")
public class InfoView extends VerticalLayout {
    public InfoView() {
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        add(new H3("Binclass UI"));
        add(new Span("Welcome to the Binclass."));
    }
}
