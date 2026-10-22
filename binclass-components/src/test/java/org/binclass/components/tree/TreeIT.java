/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.tree;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.binclass.components.ComponentIT;
import org.junit.jupiter.api.Test;

/**
 * Playwright end-to-end tests for the {@link Tree} component.
 * <p>
 * These drive the real web component rendered by Vaadin in a browser and verify
 * that the flat node list pushed from Java produces the expected SVG: edges for
 * each branch, knobs at every branching point with their tooltip text, and one
 * inline label per leaf. The demo view hosts three trees — a single cluster, a
 * two-cluster merge and a deeper ladder — so each shape can be asserted on its
 * own.
 * </p>
 */
class TreeIT extends ComponentIT {

    private static final String TEST_ROUTE = "tree";

    /** Returns the tree element with the given demo id. */
    private Locator tree(Page page, String id) {
        return page.locator("#" + id);
    }

    /** Returns every branch edge drawn inside a tree's SVG canvas. */
    private Locator edges(Locator tree) {
        return tree.locator(".canvas .edge");
    }

    /** Returns every branching-point knob drawn inside a tree's SVG canvas. */
    private Locator knobs(Locator tree) {
        return tree.locator(".canvas .knob");
    }

    /** Returns every inline leaf label drawn inside a tree's SVG canvas. */
    private Locator labels(Locator tree) {
        return tree.locator(".canvas .label");
    }

    /**
     * Waits until each demo tree has rendered its SVG canvas into shadow DOM.
     */
    private void waitForTreesRendered(Page page) {
        page.waitForSelector("#tree-single .canvas");
        page.waitForSelector("#tree-two .canvas");
        page.waitForSelector("#tree-ladder .canvas");
    }

    @Test
    void singleClusterTreeRendersOneLabelAndNoBranches() {
        page.navigate(BASE_URL + "/" + TEST_ROUTE);
        waitForTreesRendered(page);

        assertThat(edges(tree(page, "tree-single"))).hasCount(0);
        assertThat(knobs(tree(page, "tree-single"))).hasCount(0);
        assertThat(labels(tree(page, "tree-single"))).hasCount(1);
    }

    @Test
    void twoClusterTreeRendersOneKnobAndTwoLabels() {
        page.navigate(BASE_URL + "/" + TEST_ROUTE);
        waitForTreesRendered(page);

        Locator tree = tree(page, "tree-two");
        assertThat(edges(tree)).hasCount(2);
        assertThat(knobs(tree)).hasCount(1);
        assertThat(labels(tree)).hasCount(2);
    }

    @Test
    void ladderTreeRendersTwoKnobsAndThreeLabels() {
        page.navigate(BASE_URL + "/" + TEST_ROUTE);
        waitForTreesRendered(page);

        Locator tree = tree(page, "tree-ladder");
        assertThat(edges(tree)).hasCount(4);
        assertThat(knobs(tree)).hasCount(2);
        assertThat(labels(tree)).hasCount(3);
    }

    @Test
    void mergeKnobCarriesTooltipText() {
        page.navigate(BASE_URL + "/" + TEST_ROUTE);
        waitForTreesRendered(page);

        // The two-cluster merge carries strain A, joint size 12 and frequency
        // 1.
        Locator knob = knobs(tree(page, "tree-two")).first();
        assertThat(knob.locator("title")).hasCount(1);
        assertEquals("Name: A\nSize: 12\nFrequency: 1",
                knob.locator("title").textContent());
    }

    @Test
    void leafLabelsRenderStrainNames() {
        page.navigate(BASE_URL + "/" + TEST_ROUTE);
        waitForTreesRendered(page);

        // The two leaves carry strains A and B respectively.
        List<String> texts = labels(tree(page, "tree-two")).allTextContents();
        assertTrue(texts.contains("A"), "expected strain A among leaf labels: "
                + texts);
        assertTrue(texts.contains("B"), "expected strain B among leaf labels: "
                + texts);
    }
}
