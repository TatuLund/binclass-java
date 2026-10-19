/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * Base class for Playwright end-to-end tests of the custom Binclass components.
 * <p>
 * Follows Vaadin's guidance for driving a running dev build with a real
 * browser: a single shared {@link Playwright} instance is created in
 * {@link BeforeAll} and closed in {@link AfterAll}, while each test gets its
 * own headless Chromium page opened in {@link #openBrowser()} and closed in
 * {@link #closeBrowser()}. The server itself is started by the
 * jetty-ee11-maven-plugin bound to the {@code pre-integration-test} phase of
 * the {@code it} profile, so tests only need to navigate to routes and assert
 * on the rendered custom elements.
 * </p>
 */
public abstract class ComponentIT {

    /**
     * Base URL of the dev build started by the jetty plugin in the {@code it}
     * profile.
     */
    protected static final String BASE_URL = "http://localhost:8080";

    private static Playwright playwright;

    /**
     * Creates the shared Playwright instance used by all tests in the class.
     */
    @BeforeAll
    static void createPlaywright() {
        playwright = Playwright.create();
    }

    /**
     * Closes the shared Playwright instance and releases its browser binaries.
     */
    @AfterAll
    static void closePlaywright() {
        if (playwright != null) {
            playwright.close();
        }
    }

    /**
     * Fresh browser page for each test, created in {@link #openBrowser()} and
     * closed in {@link #closeBrowser()}. Tests assert against this instance.
     */
    protected Page page;

    /**
     * Launches a fresh headless Chromium page before each test so every test
     * starts from a clean slate. The page is closed in {@link #closeBrowser()}.
     */
    @BeforeEach
    void openBrowser() {
        Browser browser = playwright.chromium()
                .launch(new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage();
    }

    /**
     * Closes the per-test page so it does not leak between tests.
     */
    @AfterEach
    void closeBrowser() {
        if (page != null) {
            page.close();
        }
    }

    /**
     * Waits until every custom component on the demo page has rendered its
     * cells into its shadow DOM.
     * <p>
     * Lit elements populate their shadow DOM asynchronously, so a locator's
     * {@code count()} taken immediately after navigation returns zero even
     * though the element exists in the light DOM. This polls until each of the
     * vector, centroid, and cluster-centroid components has at least one cell,
     * giving assertions a stable starting point.
     *
     * @param page
     *            the page to wait on; must be pointed at the demo view
     */
    protected void waitForComponentsRendered(Page page) {
        page.waitForFunction(
                """
                        () => {
                          const hasCells = (tag, sel) => {
                            const el = document.querySelector(tag);
                            return el && el.shadowRoot
                              && el.shadowRoot.querySelectorAll(sel).length > 0;
                          };
                          const clusterReady = () => {
                            const clusters = document.querySelectorAll('binclass-cluster');
                            for (const c of clusters) {
                              const slots = c.querySelectorAll('[slot=centroid]');
                              for (const s of slots) {
                                if (s.shadowRoot
                                  && s.shadowRoot.querySelectorAll('.cells > .cell').length > 0) return true;
                              }
                            }
                            return false;
                          };
                          return hasCells('binclass-vector', '.cells > .cell') &&
                                 hasCells('binclass-centroid', '.cells > .cell') &&
                                 clusterReady();
                        }""");
    }

    /**
     * Navigates the per-test {@link #page} to the demo view that hosts all four
     * components and waits until every custom component has rendered its cells.
     * <p>
     * The page is owned by the base class, so callers assert against the
     * {@link #page} field directly instead of wrapping it in a
     * {@code try}-with-resources block or declaring {@code IOException}.
     * </p>
     *
     * @param route
     *            the route to navigate to after the page is created
     */
    protected void openDemo(String route) {
        page.navigate(BASE_URL + "/" + route);
        waitForComponentsRendered(page);
    }

    /**
     * Returns the rendered width, in pixels, of a single cell's bounding box. A
     * null box (element not visible) is reported as zero so callers can treat
     * it as "no size".
     */
    protected static double cellWidth(com.microsoft.playwright.Locator cell) {
        var box = cell.boundingBox();
        return box == null ? 0 : box.width;
    }

    /**
     * Returns the rendered height, in pixels, of a single cell's bounding box.
     */
    protected static double cellHeight(com.microsoft.playwright.Locator cell) {
        var box = cell.boundingBox();
        return box == null ? 0 : box.height;
    }

    /**
     * Returns whether the given cells container carries the {@code overflow}
     * class that drives the right-edge fade when its content scrolls.
     * <p>
     * The class is applied directly to the container element
     * ({@code .cells.overflow}), so it is checked on the element itself via a
     * client-side evaluation rather than as a descendant combinator, which
     * would look for a child element with that class.
     */
    protected static boolean hasOverflow(
            com.microsoft.playwright.Locator cells) {
        return cells.getAttribute("class").contains("overflow");
    }
}
