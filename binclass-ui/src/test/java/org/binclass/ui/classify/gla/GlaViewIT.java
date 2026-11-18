/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.gla;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.setDefaultAssertionTimeout;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.microsoft.playwright.Locator;
import org.binclass.algorithms.eventbus.EventBus;
import org.binclass.algorithms.events.PartitionResult;
import org.binclass.algorithms.events.SearchCompleted;
import org.binclass.algorithms.events.SearchProgress;
import org.binclass.components.ComponentIT;
import org.junit.Ignore;
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

        // Run button and the tab strip render alongside the form.
        assertThat(page.locator("#run-gla")).isVisible();
        assertThat(page.locator("#gla-tabs")).isVisible();
        assertThat(page.locator("#search-panel")).isVisible();
        assertThat(page.locator("#gla-progress")).isVisible();

        // Each result tab hides its content until its header is selected, so
        // activate the tab before asserting its component renders.
        page.locator("#gla-tabs [role=tab] >> text=SC Graph").click();
        assertThat(page.locator("#sc-graph")).isVisible();

        page.locator("#gla-tabs [role=tab] >> text=Partition").click();
        assertThat(page.locator("#partition-view")).isVisible();

        page.locator("#gla-tabs [role=tab] >> text=Report").click();
        assertThat(page.locator("#report-component-gla")).isVisible();
    }

    @Test
    void glaViewRendersDefaultParameters() throws Exception {
        openPage(TEST_ROUTE);

        // The default form seeds the range fields with sensible values.
        assertThat(page.locator("#k-start")).isVisible();
        assertThat(page.locator("#epsilon")).isVisible();

        // Upload the bundled header/data pair so canRun() becomes true and Run
        // is enabled, matching a freshly loaded view.
        Locator fileInput = page.locator("#uploader-gla input[type=file]");
        Path dataPath = Paths.get(getClass().getResource("/test.data")
                .toURI());
        Path headerPath = Paths.get(getClass().getResource("/test.header")
                .toURI());
        fileInput.setInputFiles(new Path[] { dataPath, headerPath });

        assertThat(page.locator("#run-gla")).isEnabled();
    }

    @Test
    @Ignore("Flaky")
    void glaViewGraphAccumulatesPointsOnCompletion() {
        openPage(TEST_ROUTE);

        // Activate the SC Graph tab so its chart is visible; TabSheet hides
        // inactive tabs.
        page.locator("#gla-tabs [role=tab] >> text=SC Graph").click();
        waitForGraphLine();

        // A completed search accumulates one point per evaluated k through
        // progress events, then reports completion. Two points must draw a
        // polyline.
        EventBus.get().post(new SearchProgress("gla", 1, 20.0, false));
        EventBus.get().post(new SearchProgress("gla", 2, 10.5, false));

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

        // Activate the Partition tab so its content is visible; TabSheet hides
        // inactive tabs. The partition component renders once a model is bound.
        page.locator("#gla-tabs [role=tab] >> text=Partition").click();
        assertThat(page.locator("#partition-view")).isVisible();
    }
}
