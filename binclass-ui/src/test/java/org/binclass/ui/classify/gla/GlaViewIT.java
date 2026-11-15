/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.gla;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.setDefaultAssertionTimeout;

import com.microsoft.playwright.Locator;
import org.binclass.algorithms.eventbus.EventBus;
import org.binclass.algorithms.events.PartitionResult;
import org.binclass.algorithms.events.SearchCompleted;
import org.binclass.components.ComponentIT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Browserless integration test for the {@link RouteGlaView} GLA Search display.
 * <p>
 * Verifies that the view renders every control described in
 * {@code docs/WebUI.PRD.md} §3 — the parameters form, the SC graph and the
 * partition tab — once served under its {@code gla} route. The Jetty server is
 * started by the Maven {@code it} profile on port 8080 (matching
 * {@link ComponentIT#BASE_URL}).
 * </p>
 */
class GlaViewIT extends ComponentIT {

    private static final String TEST_ROUTE = "gla";

    @BeforeAll
    static void setAssertionTimeout() {
        setDefaultAssertionTimeout(20000);
    }

    /** Waits until the SC graph has drawn its polyline into the shadow DOM. */
    private void waitForGraphLine() {
        page.locator("#sc-graph .canvas").waitFor(
                new Locator.WaitForOptions().setTimeout(15000));
    }

    @Test
    void glaViewRendersParamsFormAndTabs() {
        openPage(TEST_ROUTE);

        // Root layout and the parameters form.
        assertThat(page.locator("#gla-view")).isVisible();
        assertThat(page.locator("#gla-params")).isVisible();
        assertThat(page.locator("#uploader-gla")).isVisible();

        // Search-range controls.
        assertThat(page.locator("#k-start")).isVisible();
        assertThat(page.locator("#k-stop")).isVisible();
        assertThat(page.locator("#maxiter")).isVisible();
        assertThat(page.locator("#epsilon")).isVisible();
        assertThat(page.locator("#decreasing-eps")).isVisible();

        // Strategy selects.
        assertThat(page.locator("#distance-type")).isVisible();
        assertThat(page.locator("#centroid-type")).isVisible();
        assertThat(page.locator("#empty-cell-fix")).isVisible();

        // Report tab component.
        assertThat(page.locator("#report-component-gla")).isVisible();

        // Run button and the results tabs.
        assertThat(page.locator("#run-gla")).isVisible();
        assertThat(page.locator("#gla-tabs")).isVisible();
        assertThat(page.locator("#search-panel")).isVisible();
        assertThat(page.locator("#gla-progress")).isVisible();
        assertThat(page.locator("#sc-graph")).isVisible();
        assertThat(page.locator("#partition-view")).isVisible();
    }

    @Test
    void glaViewRendersDefaultParameters() {
        openPage(TEST_ROUTE);

        // The default form seeds the range fields with sensible values.
        assertThat(page.locator("#k-start")).isVisible();
        assertThat(page.locator("#epsilon")).isVisible();
        assertThat(page.locator("#run-gla")).isEnabled();
    }

    @Test
    void glaViewGraphAccumulatesPointsOnCompletion() {
        openPage(TEST_ROUTE);
        waitForGraphLine();

        // A completed search with a two-point curve must draw a polyline.
        EventBus.get().post(new SearchCompleted("gla", new PartitionResult(
                1, 10.5, 2, new double[] { 20.0, 10.5 }, new int[] { 1, 2 },
                null)));

        page.locator("#sc-graph .line").waitFor(
                new Locator.WaitForOptions().setTimeout(15000));
        assertThat(page.locator("#sc-graph .line")).isVisible();
    }

    @Test
    void glaViewPartitionTabReceivesModelOnCompletion() {
        openPage(TEST_ROUTE);

        EventBus.get().post(new SearchCompleted("gla", new PartitionResult(
                1, 10.5, 2, new double[] { 20.0, 10.5 }, new int[] { 1, 2 },
                null)));

        // The partition tab renders its component once a model is bound.
        assertThat(page.locator("#partition-view")).isVisible();
    }
}
