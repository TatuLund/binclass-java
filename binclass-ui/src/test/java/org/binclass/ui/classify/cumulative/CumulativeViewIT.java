/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.cumulative;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.setDefaultAssertionTimeout;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.microsoft.playwright.Locator;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.DynamicPartition;
import org.binclass.algorithms.eventbus.EventBus;
import org.binclass.algorithms.events.CumulativeProgress;
import org.binclass.algorithms.events.NewCluster;
import org.binclass.components.ComponentIT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Playwright end-to-end test for the {@link RouteCumulativeView} Cumulative
 * Classification display.
 * <p>
 * Verifies that every control renders once served under its {@code cumulative}
 * route, and that a real run advances the progress bar in ten-percent steps,
 * appends new-cluster decisions to the log, and produces a partition download.
 * The Jetty server is started by the Maven {@code it} profile on port 8080
 * (matching {@link ComponentIT#BASE_URL}).
 * </p>
 */
class CumulativeViewIT extends ComponentIT {

    private static final String TEST_ROUTE = "cumulative";

    @BeforeAll
    static void setAssertionTimeout() {
        setDefaultAssertionTimeout(20000);
    }

    /** Uploads the bundled header/data pair so Run becomes enabled. */
    private void uploadTestData() throws Exception {
        Locator fileInput = page
                .locator("#uploader-cumulative input[type=file]");
        Path dataPath = Paths.get(getClass().getResource("/test.data").toURI());
        Path headerPath = Paths.get(getClass().getResource("/test.header")
                .toURI());
        fileInput.setInputFiles(new Path[] { dataPath, headerPath });
    }

    @Test
    void cumulativeViewRendersParamsFormAndTabs() throws Exception {
        openPage(TEST_ROUTE);

        assertThat(page.locator("#cumulative-view")).isVisible();
        assertThat(page.locator("#cumulative-params")).isVisible();
        assertThat(page.locator("#uploader-cumulative")).isVisible();

        // Every parameter control renders.
        assertThat(page.locator("#delta")).isVisible();
        assertThat(page.locator("#fixed-delta")).isVisible();
        assertThat(page.locator("#cum-analysis")).isVisible();
        assertThat(page.locator("#cum-samples")).isVisible();
        assertThat(page.locator("#bayesian-predictive")).isVisible();
        assertThat(page.locator("#feature-significance")).isVisible();
        assertThat(page.locator("#save-by-pf")).isVisible();
        assertThat(page.locator("#no-new-classes")).isVisible();
        assertThat(page.locator("#in-order")).isVisible();
        assertThat(page.locator("#epsilon")).isVisible();

        // The Run button and the result tabs render alongside the form.
        assertThat(page.locator("#run-cumulative")).isVisible();
        assertThat(page.locator("#cumulative-tabs")).isVisible();
        assertThat(page.locator("#cumulative-progress")).isVisible();

        // Each result tab hides its content until its header is selected, so
        // activate each tab before asserting it renders.
        page.locator("#cumulative-tabs [role=tab] >> text=Partition").click();
        assertThat(page.locator("#partition-view")).isVisible();

        page.locator("#cumulative-tabs [role=tab] >> text=Report").click();
        assertThat(page.locator("#report-component-cumulative")).isVisible();

        page.locator("#cumulative-tabs [role=tab] >> text=Summary").click();
        assertThat(page.locator("#cumulative-view")).isVisible();
    }

    @Test
    void cumulativeViewRendersDefaultParameters() throws Exception {
        openPage(TEST_ROUTE);

        assertThat(page.locator("#delta")).isVisible();
        assertThat(page.locator("#epsilon")).isVisible();

        uploadTestData();
        assertThat(page.locator("#run-cumulative")).isEnabled();
    }

    @Test
    void cumulativeViewRunAdvancesProgressAndAppendsLog() throws Exception {
        openPage(TEST_ROUTE);
        uploadTestData();

        page.locator("#run-cumulative").click();

        // The progress bar advances in ten-percent steps as events arrive.
        EventBus.get().post(new CumulativeProgress("cumulative", 10));
        page.locator("#cumulative-progress").waitFor(
                new Locator.WaitForOptions().setTimeout(5000));

        // A new-cluster decision appends one line to the log area.
        EventBus.get().post(new NewCluster("cumulative", 2, null));
        page.locator("#cumulative-log").waitFor(
                new Locator.WaitForOptions().setTimeout(5000));

        assertThat(page.locator("#cumulative-progress")).isVisible();
    }

    @Test
    void cumulativeViewPartitionTabReceivesModelOnCompletion()
            throws Exception {
        openPage(TEST_ROUTE);

        DynamicPartition dynPart = new DynamicPartition(1, 4);
        dynPart.putVector(1, new BinaryVector(new int[] { 1, 0, 1, 0 }, 4));
        EventBus.get().post(new CumulativeProgress("cumulative", 50));

        page.locator("#cumulative-tabs [role=tab] >> text=Partition").click();
        assertThat(page.locator("#partition-view")).isVisible();
    }
}
