/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.partition;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.binclass.components.ComponentIT;
import com.microsoft.playwright.options.AriaRole;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Playwright end-to-end tests for the {@link Cluster} component.
 * <p>
 * These drive the real web component rendered by Vaadin in a browser, so they
 * exercise the client-side caret toggle and lazy vector population that unit
 * tests cannot cover. They navigate to the demo view and assert on what the
 * user sees using web-first assertions: locators people can see, checks that
 * retry until the condition holds or time out, and an accessibility assertion
 * on {@code aria-expanded} so a broken toggle fails fast with a readable
 * message.
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
     * </p>
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
    void clickingCaretOpensCluster() {
        openDemo(TEST_ROUTE);

        // The standalone cluster starts closed; the caret reports
        // aria-expanded=false.
        Locator caret = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("cluster-1"));
        assertThat(caret).hasAttribute("aria-expanded", "false");

        // Clicking the caret toggle opens the details and flips aria-expanded.
        caret.click();

        assertThat(caret).hasAttribute("aria-expanded", "true");

        // The vectors block is slotted into [part='vectors']; it becomes
        // visible
        // once opened, so assert visibility directly instead of waiting first.
        Locator vectors = page.locator("binclass-cluster")
                .filter(new Locator.FilterOptions().setHas(caret))
                .locator("[part='vectors']");
        assertThat(vectors).isVisible();
    }

    @Test
    void openingClusterPopulatesVectors() {
        openDemo(TEST_ROUTE);

        // The standalone cluster buffers three vectors that render on open.
        Locator caret = page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("cluster-1"));
        Locator vectors = page.locator("binclass-cluster")
                .filter(new Locator.FilterOptions().setHas(caret))
                .locator("binclass-vector");

        caret.click();

        // hasCount retries until all buffered vectors render.
        assertThat(vectors).hasCount(3);
    }

    @Test
    void centroidAlwaysVisibleWhenClosed() {
        openDemo(TEST_ROUTE);

        // The standalone cluster starts closed; its centroid block is still
        // rendered and visible while the vectors part stays hidden.
        Locator first = clusters(page).first();
        assertThat(centroidCells(first)).hasCount(16);
        assertThat(firstCentroidCell(first)).isVisible();
    }

    @Test
    void centroidCellsClampToMaximumSize() {
        openDemo(TEST_ROUTE);

        // The standalone cluster's sixteen-probability centroid in a wide
        // container would yield ~150px cells; each must be clamped to at
        // most the 12px maximum.
        for (Locator cell : centroidCells(clusters(page).first()).all()) {
            assertTrue(cellWidth(cell) <= 12,
                    "centroid cell width should not exceed 12px, was: "
                            + cellWidth(cell));
        }
    }

    @Test
    void openingPartitionClusterPopulatesVectors() {
        openDemo(TEST_ROUTE);

        // The partition body hosts two clusters; each buffers three vectors.
        Locator second = clusters(page).nth(1);
        caret(second).click();

        assertThat(vectorsInContent(second)).hasCount(3);
    }

}
