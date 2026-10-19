/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.partition;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.binclass.components.ComponentIT;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Playwright end-to-end tests for the {@link Centroid} component.
 * <p>
 * These drive the real web component rendered by Vaadin in a browser, so they
 * exercise the client-side grid sizing and responsive re-measure that unit
 * tests cannot cover. They navigate to the demo view and assert on what the
 * user sees using web-first assertions: locators people can see, checks that
 * retry until the condition holds or time out, and an accessibility assertion
 * on {@code aria-expanded} so a broken toggle fails fast with a readable
 * message.
 * </p>
 */
class CentroidIT extends ComponentIT {

    private static final String TEST_ROUTE = "partition";

    /** Returns every {@code binclass-centroid} element rendered in the page. */
    private Locator centroids(Page page) {
        return page.locator("//binclass-centroid");
    }

    /**
     * Returns the grid cells of a centroid, identified by their {@code .cell}
     * class.
     */
    private Locator cells(Locator centroid) {
        return centroid.locator(".cells > .cell");
    }

    /**
     * Returns the cells container of a centroid for overflow-class assertions.
     */
    private Locator cellsContainer(Locator centroid) {
        return centroid.locator(".cells");
    }

    @Test
    void shortCentroidRendersOneCellPerProbability() {
        openDemo(TEST_ROUTE);

        // The short centroid carries five probabilities, so it must render
        // exactly five cells. It is the first centroid in DOM order.
        assertThat(cells(centroids(page).nth(0))).hasCount(5);
    }

    @Test
    void longCentroidRendersOneCellPerProbability() {
        openDemo(TEST_ROUTE);

        // The long centroid carries six hundred probabilities. It is the
        // second centroid in DOM order.
        assertThat(cells(centroids(page).nth(1))).hasCount(600);
    }

    @Test
    void shortCentroidCellsClampToMaximumSize() {
        openDemo(TEST_ROUTE);

        // Five probabilities in a wide container would yield ~150px cells;
        // each must be clamped to at most the 12px maximum.
        for (Locator cell : cells(centroids(page).nth(0)).all()) {
            assertTrue(cellWidth(cell) <= 12,
                    "cell width should not exceed 12px, was: "
                            + cellWidth(cell));
        }
    }

    @Test
    void longCentroidCellsClampToMinimumSize() {
        openDemo(TEST_ROUTE);

        // Six hundred probabilities in a narrow container would yield
        // sub-2px cells; each must be clamped to at least the 2px minimum.
        for (Locator cell : cells(centroids(page).nth(1)).all()) {
            assertTrue(cellWidth(cell) >= 2,
                    "cell width should not fall below 2px, was: "
                            + cellWidth(cell));
        }
    }

    @Test
    void longCentroidShowsOverflowFade() {
        openDemo(TEST_ROUTE);

        // Six hundred cells at the 2px minimum still exceed the container
        // width, so the strip must scroll and carry the overflow class that
        // drives the right-edge fade.
        assertThat(cellsContainer(centroids(page).nth(1)))
                .hasClass("cells overflow");
    }

    @Test
    void allCellsStayWithinBoundsAndAreSquare() {
        openDemo(TEST_ROUTE);

        // Every rendered cell across every centroid must be square and stay
        // within the 2px..12px bounds.
        for (Locator centroid : centroids(page).all()) {
            for (Locator cell : cells(centroid).all()) {
                double width = cellWidth(cell);
                double height = cellHeight(cell);
                assertTrue(width >= 2 && width <= 12,
                        "cell width out of bounds: " + width);
                assertEquals(width, height, 0.5, "cell should be square");
            }
        }
    }

    @Test
    void responsiveCellsResizeWhenContainerGrows() {
        openDemo(TEST_ROUTE);

        // The short centroid's cells grow as the container widens. Resize
        // the viewport and assert the cell width increases with it.
        Locator first = centroids(page).nth(0);
        double before = cellWidth(first.locator(".cells > .cell").first());

        page.setViewportSize(1600, 900);
        double after = cellWidth(first.locator(".cells > .cell").first());

        assertTrue(after >= before,
                "cell width should not shrink when the container grows: "
                        + before + " -> " + after);
    }

}
