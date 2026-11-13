/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.report;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.binclass.components.ComponentIT;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.setDefaultAssertionTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Playwright end-to-end tests for the {@link ReportComponent}. They navigate to
 * the demo view and assert on what the user sees using web-first assertions.
 */
class ReportIT extends ComponentIT {

    private static final String TEST_ROUTE = "report";

    /**
     * Vaadin dev mode renders client-side, so give assertions extra time for
     * the bootstrap to upgrade custom elements before asserting.
     */
    @BeforeAll
    static void setAssertionTimeout() {
        setDefaultAssertionTimeout(20000);
    }

    @Test
    void reportComponentRendersWithControls() {
        openPage(TEST_ROUTE);

        assertThat(page.locator("#report-component")).isVisible();
        assertThat(page.locator("#print-digits")).isVisible();
        assertThat(page.locator("#affinity-matrix")).isVisible();
        assertThat(page.locator("#use-hellinger")).isVisible();
        assertThat(page.locator("#report-params")).isVisible();
    }

    @Test
    void reportTextRendersSanitizedContent() {
        openPage(TEST_ROUTE);

        // The new DOM renders the title as an H3 (#report-header) instead of a
        // flat monospace text node, so assert on that element.
        Locator header = page.locator("#report-header");
        assertThat(header).isVisible();
        // The default report contains the header line.
        assertTrue(header.textContent().contains("STATISTICAL REPORT"));
    }

    @Test
    void enablingNearnessPopulatesMatrixTable() {
        openPage(TEST_ROUTE);

        // Enable nearness via the -p bitmask (RP_NEARNESS = 2). Fill the
        // native number input inside the IntegerField and commit with Enter so
        // Vaadin's value-change listener triggers regeneration.
        page.locator("#report-params input[type=number]").fill("2");
        page.locator("#report-params input[type=number]").press("Enter");

        // Vaadin's Table renders as a <table> element directly (no wrapper), so
        // the locator targets #report-affinity itself.
        Locator matrix = page.locator("#report-affinity");
        assertThat(matrix).isVisible();
        assertTrue(matrix.locator("tr").count() >= 3);
    }
}
