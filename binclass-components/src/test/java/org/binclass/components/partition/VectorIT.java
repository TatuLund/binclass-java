/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.partition;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.binclass.components.ComponentIT;
import org.junit.jupiter.api.Test;

/**
 * Playwright end-to-end tests for the {@link Vector} component.
 * <p>
 * These drive the real web component rendered by Vaadin in a browser, so they
 * exercise the client-side grid sizing and name-inline switch that unit tests
 * cannot cover. They navigate to the demo view and assert on the custom
 * element's structure using CSS/XPath locators with implicit waits.
 * <p>
 * The demo hosts several vectors in a fixed DOM order, so each target is
 * selected by its position among all {@code binclass-vector} elements rather
 * than by an id (Vaadin does not sync the Java-set {@code id} attribute to the
 * client custom element). Order is: 4-bit vector, 9-bit vector, 100-bit vector,
 * then the 145-bit vector.
 * </p>
 */
class VectorIT extends ComponentIT {

    private static final String TEST_ROUTE = "partition";

    /**
     * Returns every {@code binclass-vector} element rendered in the current
     * page.
     */
    private Locator vectors(Page page) {
        return page.locator("//binclass-vector");
    }

    /**
     * Returns the grid cells of a vector, identified by their {@code .cell}
     * class.
     */
    private Locator cells(Locator vector) {
        return vector.locator(".cells > .cell");
    }

    /**
     * Returns the cells container of a vector for overflow-class assertions.
     */
    private Locator cellsContainer(Locator vector) {
        return vector.locator(".cells");
    }

    @Test
    void smallVectorRendersFullSquareGrid() throws IOException {
        try (Page page = openDemo(TEST_ROUTE)) {
            // The 2x2 vector has four bits, so the grid must contain exactly
            // four cells. It is the first vector in DOM order.
            assertEquals(4, cells(vectors(page).nth(0)).count());
        }
    }

    @Test
    void largeVectorShowsNameInlineAndAllCells() throws IOException {
        try (Page page = openDemo(TEST_ROUTE)) {
            // The 145-bit vector renders a 12x12 grid with one remainder cell,
            // and its name is shown inline because the side length reaches the
            // nine-cell threshold. It is the fourth vector in DOM order.
            Locator big = vectors(page).nth(3);
            assertEquals(145, cells(big).count());
            assertTrue(big.locator(".label.name-inline").isVisible());
        }
    }

    @Test
    void mediumVectorHidesNameInline() throws IOException {
        try (Page page = openDemo(TEST_ROUTE)) {
            // The 9-bit vector renders a 3x3 grid; the side length is below the
            // nine-cell threshold, so its name lives in a tooltip instead of an
            // inline label. It is the second vector in DOM order.
            Locator medium = vectors(page).nth(1);
            assertEquals(9, cells(medium).count());
            assertTrue(medium.locator(".label.name-inline").count() == 0,
                    "the small vector should not show its name inline");
        }
    }

    @Test
    void maxClampKeepsSmallGridCellsUnderMaxSize() throws IOException {
        try (Page page = openDemo(TEST_ROUTE)) {
            // The 4-bit vector in a wide container would yield ~150px cells;
            // each
            // must be clamped to at most the 12px maximum.
            for (Locator cell : cells(vectors(page).nth(0)).all()) {
                assertTrue(cellWidth(cell) <= 12,
                        "cell width should not exceed 12px, was: "
                                + cellWidth(cell));
            }
        }
    }

    @Test
    void minClampAndOverflowFadeForWideGrid() throws IOException {
        try (Page page = openDemo(TEST_ROUTE)) {
            // The 145-bit vector in a narrow container would yield sub-2px
            // cells;
            // each must be clamped to at least the 2px minimum, and because six
            // hundredths of a pixel times 145 still exceeds the container
            // width,
            // the strip scrolls and carries the overflow class that drives the
            // right-edge fade.
            Locator big = vectors(page).nth(3);
            for (Locator cell : cells(big).all()) {
                assertTrue(cellWidth(cell) >= 2,
                        "cell width should not fall below 2px, was: "
                                + cellWidth(cell));
            }
            boolean hasOverflow = cellsContainer(big).getAttribute("class")
                    .contains("overflow");
            assertTrue(hasOverflow,
                    "cells container should carry the overflow class when it scrolls");
        }
    }

    @Test
    void hoverHighlightsMismatchedCells() throws IOException {
        try (Page page = openDemo(TEST_ROUTE)) {
            // The medium vector carries a rounded centroid that differs from
            // its bits on hover.
            Locator medium = vectors(page).nth(1);
            medium.hover();
            assertEquals(true, medium.locator(".cells > .cell.mismatch").first()
                    .isVisible());
        }
    }

    @Test
    void responsiveCellsResizeWhenContainerGrows() throws IOException {
        try (Page page = openDemo(TEST_ROUTE)) {
            // The 4-bit vector's cells grow as the container widens. Resize and
            // assert the cell width increases with it. It is the first vector
            // in DOM order.
            Locator small = vectors(page).nth(0);
            double before = cellWidth(cells(small).first());

            page.setViewportSize(1600, 900);
            double after = cellWidth(cells(small).first());

            assertTrue(after >= before,
                    "cell width should not shrink when the container grows: "
                            + before + " -> " + after);
        }
    }

}
