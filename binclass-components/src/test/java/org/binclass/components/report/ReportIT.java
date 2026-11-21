/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.report;

import com.microsoft.playwright.Locator;
import org.binclass.components.ComponentIT;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.setDefaultAssertionTimeout;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

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

    /**
     * Opens the report section whose {@code Details} summary carries
     * {@code summary}. Sections start collapsed, so callers expand one before
     * asserting on content that lives inside it.
     *
     * @param summary
     *            the exact summary text of the section to open
     */
    private void openSection(String summary) {
        Locator details = page.locator("vaadin-details")
                .filter(new Locator.FilterOptions().setHasText(summary));
        assertThat(details).isVisible();
        // Vaadin Details renders its label inside a <summary> that lives in the
        // shadow DOM of its own direct child, vaadin-details-summary. Clicking
        // that summary container toggles the section open so its slotted
        // content becomes visible. This mirrors the DramaFinder
        // DetailsElement.getSummaryLocator() pattern (":scope >
        // vaadin-details-summary").
        details.locator(":scope > vaadin-details-summary").click();
    }

    @Test
    void enablingNearnessPopulatesMatrixTable() {
        openPage(TEST_ROUTE);

        // Enable nearness via the -p bitmask (RP_NEARNESS = 2). Fill the
        // native number input inside the IntegerField and commit with Enter so
        // Vaadin's value-change listener triggers regeneration. With only
        // RP_NEARNESS set, the matrix is the sole rendered section.
        page.locator("#report-params input[type=number]").fill("2");
        page.locator("#report-params input[type=number]").press("Enter");

        // The matrix now lives inside a collapsed Details, so open it before
        // asserting on its rows.
        openSection("CLASS NEARNESS MATRIX:");

        // Vaadin's Table renders as a <table> element directly (no wrapper), so
        // the locator targets #report-affinity itself. Header row plus one data
        // row per cluster.
        Locator matrix = page.locator("#report-affinity");
        assertThat(matrix).isVisible();
        assertTrue(matrix.locator("tr").count() >= 3);
    }

    @Test
    void totalFrequenciesSectionIsCollapsibleAndVisible() {
        openPage(TEST_ROUTE);

        // The default report enables every section, so the total-frequencies
        // section renders as a collapsed Details.
        Locator details = page.locator("vaadin-details")
                .filter(new Locator.FilterOptions().setHasText(
                        "TOTAL FREQUENCIES:"));
        assertThat(details).isVisible();

        openSection("TOTAL FREQUENCIES:");

        // Once opened, the transposed frequency table is visible inside it.
        assertThat(details.locator(".frequency-table")).isVisible();
    }

    @Test
    void listOfClassesSectionRendersPerCluster() {
        openPage(TEST_ROUTE);

        Locator details = page.locator("vaadin-details")
                .filter(new Locator.FilterOptions().setHasText(
                        "LIST OF CLASSES:"));
        assertThat(details).isVisible();

        openSection("LIST OF CLASSES:");

        // The demo host partition has six clusters (see ReportView), so the
        // section renders one class-info-block per cluster.
        assertThat(details.locator(".class-info-block")).hasCount(6);
    }

    @Test
    void frequencyTableHeaderCarriesCornerAndBitHeaders() {
        openPage(TEST_ROUTE);

        Locator details = page.locator("vaadin-details")
                .filter(new Locator.FilterOptions().setHasText(
                        "TOTAL FREQUENCIES:"));
        openSection("TOTAL FREQUENCIES:");

        Locator table = details.locator(".frequency-table");
        assertThat(table).isVisible();

        // Each bit-position header carries the frequency-header-cell hook so
        // CSS can give the header row its own look.
        List<String> headerTexts = table
                .locator("th.frequency-header-cell")
                .allTextContents();
        assertTrue(headerTexts.size() >= 2,
                "expected one header per bit position, got " + headerTexts);

        // The leading corner cell is empty and shares the header class so the
        // bit-position headers stay aligned with their data columns below.
        assertEquals("", headerTexts.get(0).trim());
    }
}
