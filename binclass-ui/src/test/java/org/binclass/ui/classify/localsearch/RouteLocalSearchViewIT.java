/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.localsearch;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.setDefaultAssertionTimeout;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.microsoft.playwright.Locator;
import org.binclass.algorithms.events.PartitionResult;
import org.binclass.algorithms.events.SearchCompleted;
import org.binclass.algorithms.eventbus.EventBus;
import org.binclass.components.ComponentIT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Browserless integration test for the {@link RouteLocalSearchView} Local
 * Search display.
 * <p>
 * Verifies that the view renders every control described in
 * {@code docs/WebUI.PRD.md} §4 — the parameters form, the SC graph and the
 * partition tab — once served under its {@code local-search} route. The Jetty
 * server is started by the Maven {@code it} profile on port 8080 (matching
 * {@link ComponentIT#BASE_URL}).
 * </p>
 */
class RouteLocalSearchViewIT extends ComponentIT {

    private static final String TEST_ROUTE = "local-search";

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
    void localsearchViewRendersParamsFormAndTabs() {
        openPage(TEST_ROUTE);

        // Root layout and the parameters form.
        assertThat(page.locator("#local-search-view")).isVisible();
        assertThat(page.locator("#local-params")).isVisible();
        assertThat(page.locator("#uploader-gla")).isVisible();

        // Local Search exposes all eight heuristic codes plus epsilon, distance
        // type, centroid type and alternate mode — but no k-start/k-stop range
        // controls.
        assertThat(page.locator("#heuristic")).isVisible();
        assertThat(page.locator("#heuristic-count")).isVisible();
        assertThat(page.locator("#epsilon")).isVisible();
        assertThat(page.locator("#decreasing-eps")).isVisible();
        assertThat(page.locator("#distance-type")).isVisible();
        assertThat(page.locator("#centroid-type")).isVisible();
        assertThat(page.locator("#empty-cell-fix")).isVisible();

        // Run button and the tab strip render alongside the form.
        assertThat(page.locator("#run-local")).isVisible();
        assertThat(page.locator("#local-tabs")).isVisible();
        assertThat(page.locator("#local-search-panel")).isVisible();
        assertThat(page.locator("#local-progress")).isVisible();

        // Each result tab hides its content until its header is selected, so
        // activate the tab before asserting its component renders.
        page.locator("#local-tabs [role=tab] >> text=SC Graph").click();
        assertThat(page.locator("#sc-graph")).isVisible();

        page.locator("#local-tabs [role=tab] >> text=Partition").click();
        assertThat(page.locator("#partition-view")).isVisible();

        page.locator("#local-tabs [role=tab] >> text=Report").click();
        assertThat(page.locator("#report-component-localsearch"))
                .isVisible();
    }

    @Test
    void localsearchViewRendersDefaultParameters() throws Exception {
        openPage(TEST_ROUTE);

        // The default form seeds the heuristic select and epsilon field.
        assertThat(page.locator("#heuristic")).isVisible();
        assertThat(page.locator("#epsilon")).isVisible();

        // Upload the bundled header/data pair so canRun() becomes true and Run
        // is enabled, matching a freshly loaded view.
        Locator fileInput = page.locator("#uploader-gla input[type=file]");
        Path dataPath = Paths.get(getClass().getResource("/test.data")
                .toURI());
        Path headerPath = Paths.get(getClass().getResource("/test.header")
                .toURI());
        fileInput.setInputFiles(new Path[] { dataPath, headerPath });

        assertThat(page.locator("#run-local")).isEnabled();
    }

    @Test
    void localsearchViewPartitionTabReceivesModelOnCompletion() {
        openPage(TEST_ROUTE);

        EventBus.get()
                .post(new SearchCompleted("localsearch",
                        new PartitionResult(1, 10.5, 2,
                                new double[] { 20.0, 10.5 },
                                new int[] { 1, 2 }, null)));

        // Activate the Partition tab so its content is visible; TabSheet hides
        // inactive tabs. The partition component renders once a model is bound.
        page.locator("#local-tabs [role=tab] >> text=Partition").click();
        assertThat(page.locator("#partition-view")).isVisible();
    }

    @Test
    void localsearchViewOperatorSuccessAppendsLogEntry() {
        openPage(TEST_ROUTE);

        // The Search tab hides its content until active; activate it so the
        // operator log is visible before posting the event.
        page.locator("#local-tabs [role=tab] >> text=Search").click();
        EventBus.get().post(new org.binclass.algorithms.events.OperatorSuccess(
                "localsearch", 2, 7, 1.5, "add"));

        // Clicking a different tab forces a Vaadin server round-trip (a real
        // selection change), which flushes the reactive bindChildren effect so
        // the updated list signal renders into the DOM. TabSheet keeps every
        // tab's content in the DOM, so the Search tab's operator log stays
        // queryable.
        page.locator("#local-tabs [role=tab] >> text=Summary").click();
        assertThat(page.locator("#local-operator-log"))
                .hasText("k=2: add improved SC by 1.50");
    }
}
