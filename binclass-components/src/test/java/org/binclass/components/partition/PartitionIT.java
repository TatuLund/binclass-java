/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.partition;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.binclass.components.ComponentIT;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Playwright end-to-end tests for the {@link Partition} component.
 * <p>
 * These drive the real web component rendered by Vaadin in a browser and verify
 * that changing the bound model reactively propagates to the rendered clusters.
 * The demo view exposes a button that moves one vector between two clusters;
 * clicking it pushes a new {@link PartitionModel} through the bound signal, so
 * the header and cluster list rebuild without any manual update. These tests
 * open a cluster, trigger the move, and assert the moved vector left its old
 * cluster and arrived in the other one.
 * </p>
 */
class PartitionIT extends ComponentIT {

    private static final String TEST_ROUTE = "partition";

    /** Returns every {@code binclass-cluster} element rendered on the page. */
    private Locator clusters(Page page) {
        return page.locator("//binclass-cluster");
    }

    /** Returns the caret toggle button of a cluster, identified by its role. */
    private Locator caret(Locator cluster) {
        return cluster.locator("[role='button']");
    }

    /**
     * Returns the vectors slotted into a cluster's vectors block when opened.
     */
    private Locator vectorsInContent(Locator cluster) {
        return cluster.locator("binclass-vector");
    }

    /**
     * Opens a cluster by clicking its caret so buffered vectors are pushed to
     * the client. Callers assert the resulting count with {@link #assertThat},
     * which retries until the reactive population settles.
     */
    private void openAndPopulate(Locator cluster) {
        caret(cluster).click();
    }

    /** Returns the rendered name of every vector inside a cluster. */
    private List<String> vectorNames(Locator cluster) {
        return cluster.locator("binclass-vector .label").allTextContents();
    }

    @Test
    void movingVectorReactivatesPartitionModel() {

        openDemo(TEST_ROUTE);
        // The partition body hosts two clusters at indices 1 and 2; index 0
        // is
        // the standalone cluster from createClusterSection(). Open the
        // first
        // partition cluster to populate its buffered vectors. It starts
        // with
        // three: vec-a, vec-b and vec-c.
        Locator firstCluster = clusters(page).nth(1);
        openAndPopulate(firstCluster);
        assertThat(vectorsInContent(firstCluster)).hasCount(3);

        // Trigger the move by clicking the demo button; it pushes a new
        // model
        // through the bound signal so both clusters rebuild reactively. The
        // fresh Cluster instances start closed, so the opened cluster snaps
        // shut on its own.
        page.locator("#move-vector-btn").click();

        // Re-query and reopen the first partition cluster to populate its
        // vectors again; it now holds two: vec-a and vec-b (vec-c was moved
        // out).
        Locator movedFirst = clusters(page).nth(1);
        openAndPopulate(movedFirst);
        assertThat(vectorsInContent(movedFirst)).hasCount(2);

        // Open the second partition cluster and confirm it now holds four
        // vectors: vec-d, vec-e, vec-f and the moved vec-c.
        Locator movedSecond = clusters(page).nth(2);
        openAndPopulate(movedSecond);
        assertThat(vectorsInContent(movedSecond)).hasCount(4);

        // Assert vec-c left the first cluster and arrived in the second
        // one.
        List<String> firstClusterNames = vectorNames(movedFirst);
        assertFalse(
                firstClusterNames.stream()
                        .anyMatch(n -> n.contains("vec-c")),
                "vec-c should no longer be rendered in the first cluster");

        List<String> secondClusterNames = vectorNames(movedSecond);
        assertTrue(
                secondClusterNames.stream()
                        .anyMatch(n -> n.contains("vec-c")),
                "vec-c should now be rendered in the second cluster");
    }

}
