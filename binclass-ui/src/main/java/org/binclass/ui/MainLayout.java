/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui;

import java.util.Locale;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.AfterNavigationEvent;
import com.vaadin.flow.router.AfterNavigationObserver;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.server.menu.MenuConfiguration;
import com.vaadin.flow.server.menu.MenuEntry;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.Layout;

/**
 * Root layout rendered around every display via the Vaadin {@code @Layout}
 * annotation. It hosts a title bar with a responsive drawer toggle and a side
 * navigation built from every route annotated with {@link Menu}.
 *
 * <p>
 * Each display view carries an {@link Menu} annotation describing its label,
 * order and icon; {@link MenuConfiguration#getMenuEntries()} collects them into
 * {@link MenuEntry} records that this layout turns into {@link SideNavItem}s. A
 * new display is therefore added as one annotated {@code @Route} class with no
 * change to the wiring here, keeping this layout stable across iterations (see
 * §2 of the Web UI PRD). The current-route highlight in the drawer follows the
 * route automatically once each view is registered.
 * </p>
 */
@Layout
public class MainLayout extends AppLayout implements AfterNavigationObserver {

    private final H1 title = new H1("BinClass");

    public MainLayout() {
        title.setId("app-title");
        addToNavbar(new DrawerToggle(), title);

        SideNav sideNav = buildSideNav();
        sideNav.setId("sidenav");

        addToDrawer(new Scroller(sideNav));
    }

    /**
     * Updates the navbar title to match the view currently in the content area.
     * The title is read from the active view's own {@link Menu} annotation, so
     * each display contributes its own label without extending
     * {@link AppLayout} itself and without a lookup loop.
     */
    @Override
    public void afterNavigation(AfterNavigationEvent event) {
        title.setText(getCurrentPageTitle());
    }

    /**
     * Reads the {@link Menu} label from the active view class via reflection,
     * so each display contributes its own title without a lookup loop. Returns
     * the application name when the current view carries no {@link Menu}
     * annotation.
     *
     * @return the menu title, or {@code "BinClass"} when none applies
     */
    private String getCurrentPageTitle() {
        Component content = getContent();
        if (content == null) {
            return title.getText();
        }
        Menu menu = content.getClass().getAnnotation(Menu.class);
        return menu == null ? title.getText() : menu.title();
    }

    /**
     * Builds the side navigation from every route annotated with {@link Menu},
     * ordered by its {@code order} value. Entries without an icon are added
     * without a prefix component.
     *
     * @return a populated {@link SideNav}
     */
    private static SideNav buildSideNav() {
        SideNav nav = new SideNav();
        for (MenuEntry entry : MenuConfiguration.getMenuEntries()) {
            Component prefix = entry.icon() != null
                    ? iconComponent(entry.icon())
                    : null;
            nav.addItem(new SideNavItem(entry.title(), entry.path(), prefix));
        }
        return nav;
    }

    /**
     * Parses a {@link Menu}-icon string into a Vaadin icon component. The value
     * may be a bare icon name or the {@code vaadin:} prefixed form used by the
     * convention; unknown names fall back to no icon rather than failing.
     *
     * @param iconString
     *            the icon declared on the view's {@link Menu} annotation
     * @return an icon component, or {@code null} when none can be resolved
     */
    private static Component iconComponent(String iconString) {
        if (iconString == null || iconString.isBlank()) {
            return null;
        }
        String name = iconString.trim();
        if (name.startsWith("vaadin:")) {
            name = name.substring("vaadin:".length()).trim();
        }
        try {
            return VaadinIcon.valueOf(name.toUpperCase(Locale.ROOT)).create();
        } catch (IllegalArgumentException expected) {
            return null;
        }
    }
}
