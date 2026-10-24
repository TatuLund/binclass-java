/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.graph;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.binclass.components.ComponentIT;
import org.junit.jupiter.api.Test;

/**
 * Playwright end-to-end tests for the {@link Graph} component.
 * <p>
 * These drive the real web component rendered by Vaadin in a browser and verify
 * that the point list pushed from Java produces the expected SVG: an adaptive
 * x/y axis, a connected line through the points, a highlighted minimum-point
 * knob with crosshairs to both axes, and a tracking crosshair readout on hover.
 * The demo view hosts several graphs — a narrow range (1–10), a wide range
 * (1–200), an interior minimum, an endpoint minimum and flat / single-value
 * series — so each shape can be asserted on its own.
 * </p>
 */
class GraphIT extends ComponentIT {

    private static final String TEST_ROUTE = "graph";

    /** Returns the graph element with the given demo id. */
    private Locator graph(Page page, String id) {
        return page.locator("#" + id);
    }

    /** Returns the SVG canvas inside a graph's shadow DOM. */
    private Locator svg(Locator graph) {
        return graph.locator(".canvas");
    }

    /** Returns every x-axis tick label drawn inside a graph's SVG canvas. */
    private Locator xTicks(Locator graph) {
        return graph.locator(".canvas .x-tick");
    }

    /** Returns the minimum-point knob drawn inside a graph's SVG canvas. */
    private Locator minKnob(Locator graph) {
        return graph.locator(".canvas .knob");
    }

    /** Returns every crosshair line (to both axes) of the minimum point. */
    private Locator minHairs(Locator graph) {
        return graph.locator(".canvas .min-hair");
    }

    /**
     * Waits until each demo graph has rendered its SVG canvas into shadow DOM.
     */
    private void waitForGraphsRendered(Page page) {
        for (String id : List.of("graph-narrow", "graph-wide", "graph-interior",
                "graph-endpoint", "graph-flat", "graph-single")) {
            page.waitForSelector("#" + id + " .canvas");
        }
    }

    @Test
    void narrowRangeShowsIntegerXLabels() {
        page.navigate(BASE_URL + "/" + TEST_ROUTE);
        waitForGraphsRendered(page);

        // Interval 2 for maxX <= 10: labels snap to even values.
        List<String> ticks = xTicks(graph(page, "graph-narrow"))
                .allTextContents();
        assertTrue(ticks.contains("2"), "expected tick 2: " + ticks);
        assertTrue(ticks.contains("4"), "expected tick 4: " + ticks);
        assertTrue(ticks.contains("10"), "expected tick 10: " + ticks);
    }

    @Test
    void wideRangeShowsRoundXLabels() {
        page.navigate(BASE_URL + "/" + TEST_ROUTE);
        waitForGraphsRendered(page);

        // Interval 20 for maxX <= 200: labels snap to multiples of 20.
        List<String> ticks = xTicks(graph(page, "graph-wide"))
                .allTextContents();
        assertTrue(ticks.contains("20"), "expected tick 20: " + ticks);
        assertTrue(ticks.contains("40"), "expected tick 40: " + ticks);
        assertTrue(ticks.contains("200"), "expected tick 200: " + ticks);
    }

    @Test
    void wideRangeUsesCoarserXIntervalThanNarrow() {
        page.navigate(BASE_URL + "/" + TEST_ROUTE);
        waitForGraphsRendered(page);

        // Adaptive x-axis: the narrow range (max k = 10) uses interval 2 while
        // the wide range (max k = 200) uses interval 20. The wider span renders
        // more absolute ticks, but skips the fine step-2 labels.
        List<String> narrow = xTicks(graph(page, "graph-narrow"))
                .allTextContents();
        List<String> wide = xTicks(graph(page, "graph-wide")).allTextContents();
        assertTrue(narrow.contains("2") && narrow.contains("4"),
                "narrow range should use step 2: " + narrow);
        assertTrue(wide.contains("20"),
                "wide range should use step 20: " + wide);
        assertFalse(wide.contains("2"),
                "wide range should skip step-2 ticks: " + wide);
    }

    @Test
    void interiorMinimumRendersKnobAndCrosshairs() {
        page.navigate(BASE_URL + "/" + TEST_ROUTE);
        waitForGraphsRendered(page);

        Locator graph = graph(page, "graph-interior");
        assertThat(svg(graph)).hasCount(1);
        // The global minimum is at k = 6 (SC 23.1), an interior point.
        assertThat(minKnob(graph)).hasCount(1);
        assertThat(minHairs(graph)).hasCount(2);
    }

    @Test
    void endpointMinimumStillRendersKnob() {
        page.navigate(BASE_URL + "/" + TEST_ROUTE);
        waitForGraphsRendered(page);

        // Monotonically decreasing: minimum is the last point (k = 5).
        Locator graph = graph(page, "graph-endpoint");
        assertThat(minKnob(graph)).hasCount(1);
        assertThat(minHairs(graph)).hasCount(2);
    }

    @Test
    void flatSeriesRendersKnob() {
        page.navigate(BASE_URL + "/" + TEST_ROUTE);
        waitForGraphsRendered(page);

        Locator graph = graph(page, "graph-flat");
        assertThat(minKnob(graph)).hasCount(1);
    }

    @Test
    void singleValueRendersKnobAndAxes() {
        page.navigate(BASE_URL + "/" + TEST_ROUTE);
        waitForGraphsRendered(page);

        Locator graph = graph(page, "graph-single");
        assertThat(minKnob(graph)).hasCount(1);
        // A single value still produces at least one y tick.
        assertTrue(graph.locator(".canvas .y-tick").count() >= 1);
    }

    @Test
    void trackingCrosshairReadoutAppearsOnHover() {
        page.navigate(BASE_URL + "/" + TEST_ROUTE);
        waitForGraphsRendered(page);

        Locator graph = graph(page, "graph-narrow");
        // The knob only exists after the first deferred _draw() runs, so wait
        // for
        // it to ensure the canvas (and its hover-area / tooltip nodes) are
        // drawn
        // before we hover; otherwise showGuide can run on a not-yet-created
        // node.
        assertThat(graph.locator(".canvas .knob")).hasCount(1);
        Locator hoverArea = graph.locator(".canvas .hover-area");
        assertThat(hoverArea).hasCount(1);

        // Hover over the middle of the plot and wait for a tooltip with k and
        // SC.
        // Each poll re-triggers a synthetic mousemove so the tooltip is shown
        // on
        // the current nodes even if an initial redraw clears it before
        // Playwright
        // reads it, which would otherwise make this test flaky. The predicate
        // waits
        // until both labels are present as text content, absorbing any redraw
        // that
        // wipes the tooltip between polls.
        Locator tooltip = graph.locator(".canvas .tooltip");
        page.waitForFunction(
                "() => { const el = document.querySelector('#graph-narrow'); const svg = el && el.shadowRoot ? el.shadowRoot.querySelector('.canvas') : null; if (!svg) return false; const ha = svg.querySelector('.hover-area'); if (!ha) return false; const rect = ha.getBoundingClientRect(); const e = new MouseEvent('mousemove', {clientX: Math.round(rect.left + rect.width / 2), clientY: Math.round(rect.top + rect.height / 2)}); ha.dispatchEvent(e); const t = svg.querySelector('.tooltip'); if (!t || t.style.display === 'none') return false; const texts = Array.from(t.querySelectorAll('text')).map(n => n.textContent); return texts.some(s => s.startsWith('k = ')) && texts.some(s => s.startsWith('SC = ')); }");
        List<String> texts = tooltip.locator("text").allTextContents();
        assertTrue(texts.stream().anyMatch(t -> t.startsWith("k = ")),
                "expected a k label in the tooltip: " + texts);
        assertTrue(texts.stream().anyMatch(t -> t.startsWith("SC = ")),
                "expected an SC label in the tooltip: " + texts);
    }

    @Test
    void axisTitlesRenderByDefault() {
        page.navigate(BASE_URL + "/" + TEST_ROUTE);
        waitForGraphsRendered(page);

        Locator graph = graph(page, "graph-narrow");
        List<String> titles = graph.locator(".canvas .axis-title")
                .allTextContents();
        assertTrue(titles.contains("Stochastic complexity"),
                "expected y-axis title: " + titles);
        assertTrue(titles.contains("No. of classes"),
                "expected x-axis title: " + titles);
    }

    @Test
    void responsiveRelayoutOnResize() {
        page.navigate(BASE_URL + "/" + TEST_ROUTE);
        waitForGraphsRendered(page);

        Locator graph = graph(page, "graph-narrow");
        // Resize the container and confirm the SVG canvas width updates. The
        // canvas lives in shadow DOM, so pierce it explicitly rather than using
        // a light-DOM querySelector that would return null.
        graph.evaluate("el => el.style.width = '900px'");
        page.waitForFunction(
                "() => { const el = document.querySelector('#graph-narrow'); const svg = el && el.shadowRoot ? el.shadowRoot.querySelector('.canvas') : null; return svg && parseFloat(svg.getAttribute('width')) >= 899; }");

        // Explicit assertion (SonarQube wants a concrete one, not just a
        // waitForFunction timeout) that the canvas width actually grew.
        // parseFloat
        // returns a number so the cast matches the helpers above.
        int width = ((Number) page.evaluate(
                "() => { const svg = document.querySelector('#graph-narrow')"
                        + ".shadowRoot.querySelector('.canvas'); return svg"
                        + " && parseFloat(svg.getAttribute('width')); }"))
                .intValue();
        assertTrue(width >= 899,
                "SVG canvas width should grow to at least the host size: "
                        + width);
    }

    /** Content-box width of a graph's scroll container (clientWidth). */
    private int scrollContentWidth(Page page) {
        return ((Number) page.evaluate(
                "() => document.querySelector('#graph-narrow').shadowRoot.querySelector('.scroll').clientWidth"))
                .intValue();
    }

    /** Applied style.width of a graph's scroll container (content-box). */
    private int scrollStyleWidth(Page page) {
        return ((Number) page.evaluate(
                "() => parseFloat(document.querySelector('#graph-narrow').shadowRoot.querySelector('.scroll').style.width)"))
                .intValue();
    }

    @Test
    void resizeObserverDoesNotGrowCanvas() {
        page.navigate(BASE_URL + "/" + TEST_ROUTE);
        waitForGraphsRendered(page);

        Locator graph = graph(page, "graph-narrow");
        // Fix the host width so the container settles to a stable size.
        graph.evaluate("el => el.style.width = '900px'");
        page.waitForFunction(
                "() => { const svg = document.querySelector('#graph-narrow'); const s = svg && svg.shadowRoot ? svg.shadowRoot.querySelector('.canvas') : null; return s && parseFloat(s.getAttribute('width')) >= 899; }");

        // Content-box width must equal the applied style.width: this is exactly
        // what the border-box/content-box mismatch in _measure() used to break.
        assertEquals(scrollContentWidth(page), scrollStyleWidth(page),
                "style.width should equal clientWidth after settle: cw="
                        + scrollContentWidth(page) + " sw="
                        + scrollStyleWidth(page));

        int before = scrollContentWidth(page);
        // Force another ResizeObserver-style measurement cycle and let the
        // deferred
        // _draw() run; a still-growing loop would widen the container by ~2px.
        graph.evaluate("el => el._measure()");
        page.waitForTimeout(50);
        int after = scrollContentWidth(page);
        assertEquals(before, after,
                "content width should be stable across resize cycles: before="
                        + before + " after=" + after);
    }

    @Test
    void minimumKnobCarriesTooltipWithTwoDecimals() {
        page.navigate(BASE_URL + "/" + TEST_ROUTE);
        waitForGraphsRendered(page);

        // Interior graph: global minimum SC is 23.1 -> "23.10".
        Locator knob = minKnob(graph(page, "graph-interior")).first();
        assertThat(knob.locator("title")).hasCount(1);
        assertEquals("k = 6\nSC = 23.10", knob.locator("title").textContent());
    }

    @Test
    void svgCarriesFigureRoleAndAccessibilityLabel() {
        page.navigate(BASE_URL + "/" + TEST_ROUTE);
        waitForGraphsRendered(page);

        // Narrow range: x in [1, 10], global minimum SC at k = 10 (y = 22.4).
        Locator canvas = svg(graph(page, "graph-narrow"));
        assertThat(canvas).hasCount(1);
        assertEquals("figure", canvas.getAttribute("role"),
                "SVG should expose the chart as a figure element");

        String label = canvas.getAttribute("aria-label");
        assertNotNull(label, "SVG should carry an aria-label when data exists");
        assertTrue(label.contains("Stochastic complexity"),
                "label should include the y-axis title: " + label);
        assertTrue(label.contains("No. of classes"),
                "label should include the x-axis title: " + label);
        assertTrue(label.contains("[1, 10]"),
                "label should include the cluster range: " + label);
        assertTrue(label.contains("22.40"),
                "label should include the global minimum SC: " + label);
    }
}
