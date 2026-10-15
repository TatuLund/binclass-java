/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Playwright end-to-end tests for the {@link Centroid} component.
 * <p>
 * These drive the real web component rendered by Vaadin in a browser, so they
 * exercise the client-side grid sizing and responsive re-measure that unit
 * tests cannot cover. They navigate to the demo view and assert on the custom
 * element's structure using CSS/XPath locators with implicit waits.
 * <p>
 * The demo hosts several centroids in a fixed DOM order, so each target is
 * selected by its position among all {@code binclass-centroid} elements rather
 * than by an id (Vaadin does not sync the Java-set {@code id} attribute to the
 * client custom element). Order is: short centroid, long centroid, cluster
 * centroid, then the two partition centroids.
 * </p>
 */
class CentroidIT extends ComponentIT {

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
    void shortCentroidRendersOneCellPerProbability() throws IOException {
        try (Page page = openDemo()) {
            // The short centroid carries five probabilities, so it must render
            // exactly five cells. It is the first centroid in DOM order.
            assertEquals(5, cells(centroids(page).nth(0)).count());
        }
    }

    @Test
    void longCentroidRendersOneCellPerProbability() throws IOException {
        try (Page page = openDemo()) {
            // The long centroid carries six hundred probabilities. It is the
            // second centroid in DOM order.
            assertEquals(600, cells(centroids(page).nth(1)).count());
        }
    }

    @Test
    void shortCentroidCellsClampToMaximumSize() throws IOException {
        try (Page page = openDemo()) {
            // Five probabilities in a wide container would yield ~150px cells;
            // each must be clamped to at most the 12px maximum.
            for (Locator cell : cells(centroids(page).nth(0)).all()) {
                assertTrue(cellWidth(cell) <= 12,
                        "cell width should not exceed 12px, was: "
                                + cellWidth(cell));
            }
        }
    }

    @Test
    void longCentroidCellsClampToMinimumSize() throws IOException {
        try (Page page = openDemo()) {
            // Six hundred probabilities in a narrow container would yield
            // sub-2px
            // cells; each must be clamped to at least the 2px minimum.
            for (Locator cell : cells(centroids(page).nth(1)).all()) {
                assertTrue(cellWidth(cell) >= 2,
                        "cell width should not fall below 2px, was: "
                                + cellWidth(cell));
            }
        }
    }

    @Test
    void longCentroidShowsOverflowFade() throws IOException {
        try (Page page = openDemo()) {
            // Six hundred cells at the 2px minimum still exceed the container
            // width, so the strip must scroll and carry the overflow class that
            // drives the right-edge fade.
            Locator container = cellsContainer(centroids(page).nth(1));
            boolean hasOverflow = container.getAttribute("class")
                    .contains("overflow");
            assertTrue(hasOverflow,
                    "cells container should carry the overflow class when it scrolls");
        }
    }

    @Test
    void allCellsStayWithinBoundsAndAreSquare() throws IOException {
        try (Page page = openDemo()) {
            // Every rendered cell across every centroid must be square and stay
            // within the 2px..12px bounds.
            for (Locator centroid : centroids(page).all()) {
                for (Locator cell : cells(centroid).all()) {
                    double width = cellWidth(cell);
                    double height = cellHeight(cell);
                    assertTrue(width >= 2 && width <= 12,
                            "cell width out of bounds: " + width);
                    assertEquals(width, height, 0.5,
                            "cell should be square");
                }
            }
        }
    }

    @Test
    void responsiveCellsResizeWhenContainerGrows() throws IOException {
        try (Page page = openDemo()) {
            // The short centroid's cells grow as the container widens. Resize
            // the
            // element and assert the cell width increases with it.
            Locator first = centroids(page).nth(0);
            double before = cellWidth(first.locator(".cells > .cell").first());

            page.setViewportSize(1600, 900);
            double after = cellWidth(first.locator(".cells > .cell").first());

            assertTrue(after >= before,
                    "cell width should not shrink when the container grows: "
                            + before + " -> " + after);
        }
    }

}
