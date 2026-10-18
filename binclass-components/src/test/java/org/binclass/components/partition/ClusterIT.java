/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.partition;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.binclass.components.ComponentIT;
import org.junit.jupiter.api.Test;

/**
 * Playwright end-to-end tests for the {@link Cluster} component.
 * <p>
 * These drive the real web component rendered by Vaadin in a browser, so they
 * exercise the client-side caret toggle and lazy vector population that unit
 * tests cannot cover. They navigate to the demo view and assert on the custom
 * element's structure using CSS/XPath locators with implicit waits.
 * </p>
 */
class ClusterIT extends ComponentIT {

    private static final String TEST_ROUTE = "partition";

    /**
     * Returns every {@code binclass-cluster} element rendered in the current
     * page.
     */
    private Locator clusters(Page page) {
        return page.locator("//binclass-cluster");
    }

    /** Returns the caret toggle button of a cluster, identified by its role. */
    private Locator caret(Locator cluster) {
        return cluster.locator("[role='button']");
    }

    /**
     * Returns the vectors slotted into a cluster's vectors block when opened.
     * <p>
     * Vaadin slots place children in the host's light DOM, so the pierce-based
     * descendant combinator {@code [part='vectors'] binclass-vector} resolves
     * to zero; the vectors are found directly as slotted children of the
     * cluster.
     */
    private Locator vectorsInContent(Locator cluster) {
        return cluster.locator("binclass-vector");
    }

    /**
     * Returns the centroid cells of a cluster, identified by their
     * {@code .cell} class inside the slotted centroid block.
     */
    private Locator centroidCells(Locator cluster) {
        return cluster.locator("[slot='centroid'] .cells > .cell");
    }

    /**
     * Returns the first cell of a cluster's centroid for size assertions.
     */
    private Locator firstCentroidCell(Locator cluster) {
        return centroidCells(cluster).first();
    }

    @Test
    void clickingCaretOpensCluster() throws IOException {
        try (Page page = openDemo(TEST_ROUTE)) {
            // The standalone cluster starts closed; the vectors part is hidden.
            Locator first = clusters(page).first();
            caret(first)
                    .waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE));
            assertEquals(false, first.locator("[part='vectors']").isVisible());

            // Clicking the caret toggle opens the details.
            caret(first).click();

            // After opening, the vectors part becomes visible. Wait for the
            // client/server round-trip that flips the opened attribute.
            first.locator("[part='vectors']")
                    .waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE));
            assertEquals(true, first.locator("[part='vectors']").isVisible());
        }
    }

    @Test
    void openingClusterPopulatesVectors() throws IOException {
        try (Page page = openDemo(TEST_ROUTE)) {
            // The standalone cluster buffers three vectors that render on open.
            Locator first = clusters(page).first();
            caret(first).click();

            // Wait for the client/server round-trip that populates the slot,
            // then assert all buffered vectors rendered.
            vectorsInContent(first)
                    .first()
                    .waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE));
            assertEquals(3, vectorsInContent(first).count());
        }
    }

    @Test
    void centroidAlwaysVisibleWhenClosed() throws IOException {
        try (Page page = openDemo(TEST_ROUTE)) {
            // The standalone cluster starts closed; its centroid block is still
            // rendered and visible while the vectors part stays hidden.
            Locator first = clusters(page).first();
            assertEquals(16, centroidCells(first).count());
            assertTrue(centroidCells(first).first().isVisible(),
                    "the centroid should be visible before opening the cluster");
        }
    }

    @Test
    void centroidCellsClampToMaximumSize() throws IOException {
        try (Page page = openDemo(TEST_ROUTE)) {
            // The standalone cluster's sixteen-probability centroid in a wide
            // container would yield ~150px cells; each must be clamped to at
            // most the 12px maximum.
            Locator first = clusters(page).first();
            for (Locator cell : centroidCells(first).all()) {
                assertTrue(cellWidth(cell) <= 12,
                        "centroid cell width should not exceed 12px, was: "
                                + cellWidth(cell));
            }
        }
    }

    @Test
    void openingPartitionClusterPopulatesVectors() throws IOException {
        try (Page page = openDemo(TEST_ROUTE)) {
            // The partition body hosts two clusters; each buffers three
            // vectors.
            Locator second = clusters(page).nth(1);
            caret(second).click();

            // Wait for the client/server round-trip that populates the slot,
            // then assert all buffered vectors rendered.
            vectorsInContent(second)
                    .first()
                    .waitFor(new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE));
            assertEquals(3, vectorsInContent(second).count());
        }
    }

}
