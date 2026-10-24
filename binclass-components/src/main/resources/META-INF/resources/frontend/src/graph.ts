/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
import { css, html, LitElement } from "lit";
import { customElement, property, query } from "lit/decorators.js";
import { ThemableMixin } from "@vaadin/vaadin-themable-mixin/vaadin-themable-mixin.js";

/** A single point of the SC-vs-clusters line chart. */
interface GraphPoint {
  x: number;
  y: number;
}

/** Flat point list pushed by the server under the {@code model} property. */
interface GraphModel {
  points: GraphPoint[];
}

const SVG_NS = "http://www.w3.org/2000/svg";

/**
 * Renders a line chart of Stochastic Complexity as a function of the number of
 * clusters as an SVG: adaptive x/y axes, a connected polyline through the
 * points and a highlighted global-minimum point with crosshairs to both axes.
 * The whole flat point list is pushed once under the {@code model} property;
 * axis bounds are derived from the data client-side so no fixed range needs to
 * be pushed from Java. A tracking crosshair follows the cursor over the plot.
 */
@customElement("binclass-graph")
export class Graph extends ThemableMixin(LitElement) {
  /** Point list pushed from Java under {@code model}; drives rendering. */
  private _model?: GraphModel;

  /** The point list pushed from Java; changing it schedules a redraw so the
   * chart stays in sync even when the first draw runs before the value lands. */
  @property({ type: Object })
  get model(): GraphModel | undefined {
    return this._model;
  }
  set model(value: GraphModel | undefined) {
    const previous = this._model;
    this._model = value;
    if (previous !== value && this.hasUpdated) {
      this._scheduleDraw();
    }
  }

  /** Optional y-axis title, defaults to "Stochastic complexity". */
  @property({ attribute: "y-axis-title", type: String }) yAxisTitle =
    "Stochastic complexity";

  /** Optional x-axis title, defaults to "No. of classes". */
  @property({ attribute: "x-axis-title", type: String }) xAxisTitle =
    "No. of classes";

  /** Theme attribute value propagated through the shadow DOM. */
  @property()
  theme: string | null = null;

  // Margins are instance fields (not static) because _draw() reads them via
  // this.LEFT_MARGIN etc.; a static member is not reachable through an instance
  // in JavaScript, which would otherwise make every position NaN.
  /** Left margin in pixels for y tick labels and the y-axis title. */
  private readonly LEFT_MARGIN = 72;
  /** Right margin in pixels for x tick labels and the optional axis title. */
  private readonly RIGHT_MARGIN = 24;
  /** Top margin in pixels above the plot area. */
  private readonly TOP_MARGIN = 20;
  /** Bottom margin in pixels for x tick labels and the x-axis title. */
  private readonly BOTTOM_MARGIN = 64;

  /** Fraction of the y span used as headroom at top and bottom. */
  private readonly HEADROOM = 0.08;

  /** Canvas SVG element, queried from shadow DOM after first render. */
  @query(".canvas")
  private _svg?: SVGSVGElement;
  /** Scroll container pinned to the canvas size for responsive sizing. */
  @query(".scroll")
  private _scroll?: HTMLElement;
  private _size = { width: 0, height: 0 };
  /** Last canvas size applied to the scroll container, so a redraw that does
   * not change it is skipped and does not feed the ResizeObserver again. */
  private _appliedSize = { width: 0, height: 0 };
  private _resizeObserver?: ResizeObserver;
  private _rafId?: number;

  protected render() {
    return html`<div class="scroll"><svg class="canvas"></svg></div>`;
  }

  firstUpdated() {
    // @query populates _svg and _scroll after the first render, so they are
    // ready here. Measure and kick off a draw when the canvas exists.
    if (this._svg) {
      this._measure();
      this._scheduleDraw();
    }
    // Recompute pixel dimensions / viewBox when the container space changes. We
    // observe the host element itself: the scroll container is pinned by its own
    // inline width once drawn, so observing it alone would miss host-driven
    // resizes (the very change this component must react to).
    this._resizeObserver = new ResizeObserver(() => {
      this._measure();
      this._scheduleDraw();
    });
    this._resizeObserver.observe(this);
  }

  disconnectedCallback() {
    super.disconnectedCallback();
    if (this._rafId !== undefined) {
      cancelAnimationFrame(this._rafId);
      this._rafId = undefined;
    }
    this._resizeObserver?.disconnect();
  }

  /**
   * Defers the draw to the next animation frame so a synchronous ResizeObserver
   * callback does not feed its own layout change back into the same frame, which
   * would otherwise produce "ResizeObserver loop completed with undelivered
   * notifications" and make the canvas keep growing.
   */
  private _scheduleDraw() {
    this._rafId ??= requestAnimationFrame(() => {
      this._rafId = undefined;
      // Re-measure right before drawing so the final draw always uses freshly
      // settled layout dimensions. A late ResizeObserver callback may have set
      // a stale size between scheduling and now; re-reading defeats it and keeps
      // responsive sizing correct without an extra draw cycle.
      this._measure();
      this._draw();
    });
  }

  /** Reads the available space of the host element for responsive sizing. */
  private _measure() {
    // Measure the host rect (the true available space) rather than the scroll
    // container: once drawn, the scroll container is pinned by its own inline
    // width/height and would otherwise report a stale size that never grows.
    const rect = this.getBoundingClientRect();
    this._size.width = Math.max(0, rect.width);
    this._size.height = Math.max(0, rect.height);
  }

  private _draw() {
    const svg = this._svg;
    if (!svg) {
      return;
    }
    // Clear previous drawing.
    while (svg.firstChild) {
      svg.removeChild(svg.firstChild);
    }
    const points = this._sortedPoints();
    if (points.length === 0) {
      return;
    }

    const width = Math.max(this._size.width, 320);
    const height = Math.max(this._size.height, 200);
    svg.setAttribute("width", String(width));
    svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
    // Coarse accessibility summary per §6.10 of the graph PRD: expose the chart
    // as a figure with an aria-label covering axis titles, cluster range and the
    // global minimum; fine-grained per-point exposure is intentionally omitted.
    svg.setAttribute("role", "figure");
    const label = this._accessibilityLabel();
    if (label) {
      svg.setAttribute("aria-label", label);
    }

    const plotLeft = this.LEFT_MARGIN;
    const plotRight = width - this.RIGHT_MARGIN;
    const plotTop = this.TOP_MARGIN;
    const plotBottom = height - this.BOTTOM_MARGIN;
    if (plotRight <= plotLeft || plotBottom <= plotTop) {
      return;
    }

    const xRange = this._xAxis(points);
    const yRange = this._yAxis(points);
    const minPoint = this._minimumPoint(points);

    // Axes.
    this._drawAxes(
      svg,
      plotLeft,
      plotTop,
      plotRight,
      plotBottom,
      xRange,
      yRange,
    );
    // Adaptive tick labels.
    this._drawXTicks(
      svg,
      plotLeft,
      plotBottom,
      plotRight,
      xRange.min,
      xRange.max,
      xRange.step,
    );
    this._drawYTicks(svg, plotLeft, plotTop, plotBottom, yRange);
    // Axis titles.
    if (this.yAxisTitle) {
      this._drawYAxisTitle(svg, plotLeft, plotTop, plotBottom, this.yAxisTitle);
    }
    if (this.xAxisTitle) {
      this._drawXAxisTitle(
        svg,
        plotLeft,
        plotBottom,
        plotRight,
        this.xAxisTitle,
      );
    }

    // Line through the points in x order.
    const scaleX = (x: number) =>
      plotLeft +
      ((x - xRange.min) / Math.max(1e-9, xRange.max - xRange.min)) *
        (plotRight - plotLeft);
    const scaleY = (y: number) =>
      plotBottom -
      ((y - yRange.min) / Math.max(1e-9, yRange.max - yRange.min)) *
        (plotBottom - plotTop);

    let path = "";
    for (const p of points) {
      const px = scaleX(p.x);
      const py = scaleY(p.y);
      path += (path === "" ? "M" : "L") + `${px.toFixed(2)} ${py.toFixed(2)} `;
    }
    if (path !== "") {
      const line = document.createElementNS(SVG_NS, "path");
      line.setAttribute("d", path.trim());
      line.setAttribute("class", "line");
      svg.appendChild(line);
    }

    // Tracking crosshair element, updated on hover.
    const guide = document.createElementNS(SVG_NS, "line");
    guide.setAttribute("class", "guide");
    guide.style.display = "none";
    svg.appendChild(guide);
    const guideDot = document.createElementNS(SVG_NS, "circle");
    guideDot.setAttribute("class", "guide-dot");
    guideDot.style.display = "none";
    svg.appendChild(guideDot);
    const tooltip = document.createElementNS(SVG_NS, "g");
    tooltip.setAttribute("class", "tooltip");
    tooltip.style.display = "none";
    svg.appendChild(tooltip);

    // Minimum-point knob and crosshairs (always visible).
    if (minPoint) {
      const mx = scaleX(minPoint.x);
      const my = scaleY(minPoint.y);
      this._line(svg, plotLeft, my, plotRight, my, "min-hair");
      this._line(svg, mx, plotTop, mx, plotBottom, "min-hair");
      const knob = document.createElementNS(SVG_NS, "circle");
      knob.setAttribute("class", "knob");
      knob.setAttribute("cx", String(mx));
      knob.setAttribute("cy", String(my));
      knob.setAttribute("r", "5");
      this._title(knob, `k = ${minPoint.x}\nSC = ${this.formatSc(minPoint.y)}`);
      svg.appendChild(knob);

      // Axis annotations at the two intersections.
      this._annotation(
        svg,
        plotLeft,
        my - 8,
        `SC = ${this.formatSc(minPoint.y)}`,
      );
      this._annotation(svg, mx + 6, plotBottom + 16, `k = ${minPoint.x}`);
    }

    // Hover handling for the tracking crosshair.
    const hoverArea = document.createElementNS(SVG_NS, "rect");
    hoverArea.setAttribute("x", String(plotLeft));
    hoverArea.setAttribute("y", String(plotTop));
    hoverArea.setAttribute("width", String(plotRight - plotLeft));
    hoverArea.setAttribute("height", String(plotBottom - plotTop));
    hoverArea.setAttribute("class", "hover-area");
    svg.appendChild(hoverArea);

    const nearest = (clientX: number) => {
      const rect = svg.getBoundingClientRect();
      const px = clientX - rect.left;
      let best = 0;
      let bestDist = Infinity;
      for (let i = 0; i < points.length; i++) {
        const d = Math.abs(scaleX(points[i].x) - px);
        if (d < bestDist) {
          bestDist = d;
          best = i;
        }
      }
      return best;
    };

    const showGuide = (clientX: number) => {
      if (points.length === 0) {
        return;
      }
      const idx = nearest(clientX);
      const p = points[idx];
      const px = scaleX(p.x);
      const py = scaleY(p.y);
      guide.setAttribute("x1", String(px));
      guide.setAttribute("y1", String(plotTop));
      guide.setAttribute("x2", String(px));
      guide.setAttribute("y2", String(plotBottom));
      guide.style.display = "";
      guideDot.setAttribute("cx", String(px));
      guideDot.setAttribute("cy", String(py));
      guideDot.style.display = "";

      const labelK = `k = ${p.x}`;
      const labelSc = `SC = ${this.formatSc(p.y)}`;
      while (tooltip.firstChild) {
        tooltip.removeChild(tooltip.firstChild);
      }
      const kText = document.createElementNS(SVG_NS, "text");
      kText.setAttribute("class", "tooltip-k");
      kText.textContent = labelK;
      const scText = document.createElementNS(SVG_NS, "text");
      scText.setAttribute("class", "tooltip-sc");
      scText.textContent = labelSc;
      tooltip.appendChild(kText);
      tooltip.appendChild(scText);

      // Keep the tooltip box inside the plot horizontally.
      const boxLeft = px + 12 > plotRight - 90 ? px - 104 : px + 12;
      kText.setAttribute("x", String(boxLeft));
      kText.setAttribute("y", String(Math.max(plotTop + 16, py - 8)));
      scText.setAttribute("x", String(boxLeft));
      scText.setAttribute("y", String(Math.max(plotTop + 32, py + 8)));
      tooltip.style.display = "";
    };

    const hideGuide = () => {
      guide.style.display = "none";
      guideDot.style.display = "none";
      tooltip.style.display = "none";
    };

    hoverArea.addEventListener("mousemove", (event) => {
      showGuide((event as MouseEvent).clientX);
    });
    hoverArea.addEventListener("mouseleave", hideGuide);

    // Size the scroll container to fit the canvas, skipping when unchanged so
    // the ResizeObserver does not fire again for an identical layout.
    if (
      this._scroll &&
      (width !== this._appliedSize.width || height !== this._appliedSize.height)
    ) {
      this._scroll.style.width = `${width}px`;
      this._scroll.style.height = `${height}px`;
      this._appliedSize.width = width;
      this._appliedSize.height = height;
    }
  }

  /** Sorts a copy of the points by x so the line connects left-to-right. */
  private _sortedPoints(): GraphPoint[] {
    if (!this.model || !Array.isArray(this.model.points)) {
      return [];
    }
    // Coerce to numbers and drop entries with missing/invalid coordinates so a
    // slightly different shape pushed from Java cannot produce NaN in the SVG.
    const points = this.model.points
      .map((p) => ({ x: Number(p.x), y: Number(p.y) }))
      .filter((p) => Number.isFinite(p.x) && Number.isFinite(p.y));
    return points.sort((a, b) => a.x - b.x);
  }

  /** Adaptive x-axis range and tick step keyed on the maximum cluster count. */
  private _xAxis(points: GraphPoint[]) {
    const minX = Math.min(...points.map((p) => p.x));
    const maxX = Math.max(...points.map((p) => p.x));
    const step = this._xStep(maxX);
    const minTick = Math.floor(minX / step) * step;
    return { min: Math.min(minTick, minX), max: maxX, step };
  }

  /** Tick interval for the x-axis keyed on the maximum cluster count. */
  private _xStep(maxX: number): number {
    if (maxX <= 10) {
      return 2;
    }
    if (maxX <= 50) {
      return 5;
    }
    if (maxX <= 100) {
      return 10;
    }
    if (maxX <= 200) {
      return 20;
    }
    // Larger ranges fall back to a friendly interval.
    const range = Math.max(1, maxX);
    const exponent = Math.floor(Math.log10(range));
    const fraction = range / Math.pow(10, exponent);
    let niceFraction = 10;
    if (fraction < 3) {
      niceFraction = 2;
    } else if (fraction < 7) {
      niceFraction = 5;
    }
    return niceFraction * Math.pow(10, exponent);
  }

  /** Auto-detects the y-axis range from data with padding and nice rounding. */
  private _yAxis(points: GraphPoint[]) {
    const ys = points.map((p) => p.y);
    let yMin = Math.min(...ys);
    let yMax = Math.max(...ys);
    if (yMin === yMax) {
      // Zero-range guard: expand symmetrically so a usable band exists.
      const zero = yMin === 0 ? 1 : Math.abs(yMin);
      yMin -= zero * 0.5;
      yMax += zero * 0.5;
    } else {
      const headroom = (yMax - yMin) * this.HEADROOM;
      yMin -= headroom;
      yMax += headroom;
    }
    return this._niceTicks(yMin, yMax, 6);
  }

  /** Friendly-interval tick range targeting roughly five to eight ticks. */
  private _niceTicks(min: number, max: number, desiredTicks: number) {
    const range = this._niceNum(max - min, false);
    const spacing = this._niceNum(range / Math.max(1, desiredTicks - 1), true);
    const niceMin = Math.floor(min / spacing) * spacing;
    const niceMax = Math.ceil(max / spacing) * spacing;
    const ticks: number[] = [];
    for (let x = niceMin; x <= niceMax + spacing / 2; x += spacing) {
      ticks.push(x);
    }
    return { min: niceMin, max: niceMax, spacing, ticks };
  }

  /** Rounds a numeric range to a friendly number. */
  private _niceNum(range: number, round: boolean): number {
    if (range <= 0) {
      return 1;
    }
    const exponent = Math.floor(Math.log10(range));
    const fraction = range / Math.pow(10, exponent);
    let niceFraction: number;
    if (round) {
      if (fraction < 1.5) {
        niceFraction = 1;
      } else if (fraction < 3) {
        niceFraction = 2;
      } else if (fraction < 7) {
        niceFraction = 5;
      } else {
        niceFraction = 10;
      }
    } else {
      if (fraction <= 1) {
        niceFraction = 1;
      } else if (fraction <= 2) {
        niceFraction = 2;
      } else if (fraction <= 5) {
        niceFraction = 5;
      } else {
        niceFraction = 10;
      }
    }
    return niceFraction * Math.pow(10, exponent);
  }

  /** Returns the global-minimum point (smallest y), ties to smallest x. */
  private _minimumPoint(points: GraphPoint[]): GraphPoint | null {
    if (points.length === 0) {
      return null;
    }
    let best = points[0];
    for (const p of points) {
      if (p.y < best.y || (p.y === best.y && p.x < best.x)) {
        best = p;
      }
    }
    return best;
  }

  /** Formats a stochastic-complexity value with exactly two decimals, keeping
   * trailing zeros so e.g. 23.1 renders as "23.10" rather than "23.1". */
  private formatSc(value: number): string {
    return value.toFixed(2);
  }

  /** Builds a coarse accessibility summary covering the axis titles, the
   * cluster range and the global-minimum SC value. Returns an empty string so
   * callers can skip setting {@code aria-label} when there is no data yet. */
  private _accessibilityLabel(): string {
    const points = this._sortedPoints();
    if (points.length === 0) {
      return "";
    }
    const minX = Math.min(...points.map((p) => p.x));
    const maxX = Math.max(...points.map((p) => p.x));
    const minPoint = this._minimumPoint(points);
    return `${this.yAxisTitle} for ${this.xAxisTitle}, cluster range ` +
      `[${Math.round(minX)}, ${Math.round(maxX)}], global minimum SC ` +
      `${minPoint ? this.formatSc(minPoint.y) : "n/a"}`;
  }

  // --- SVG helpers -------------------------------------------------------

  private _line(
    svg: SVGSVGElement,
    x1: number,
    y1: number,
    x2: number,
    y2: number,
    className: string,
  ) {
    const line = document.createElementNS(SVG_NS, "line");
    line.setAttribute("x1", String(x1));
    line.setAttribute("y1", String(y1));
    line.setAttribute("x2", String(x2));
    line.setAttribute("y2", String(y2));
    line.setAttribute("class", className);
    svg.appendChild(line);
  }

  private _title(element: SVGElement, text: string) {
    const title = document.createElementNS(SVG_NS, "title");
    title.textContent = text;
    element.appendChild(title);
  }

  private _annotation(svg: SVGSVGElement, x: number, y: number, text: string) {
    const t = document.createElementNS(SVG_NS, "text");
    t.setAttribute("class", "annotation");
    t.setAttribute("x", String(x));
    t.setAttribute("y", String(y));
    t.textContent = text;
    svg.appendChild(t);
  }

  private _drawAxes(
    svg: SVGSVGElement,
    left: number,
    top: number,
    right: number,
    bottom: number,
    xRange: { min: number; max: number },
    yRange: { min: number; max: number },
  ) {
    this._line(svg, left, top, left, bottom, "axis");
    this._line(svg, left, bottom, right, bottom, "axis");
    // Scale labels on the axis lines.
    const xLabel = document.createElementNS(SVG_NS, "text");
    xLabel.setAttribute("class", "scale-label");
    xLabel.setAttribute("x", String(left));
    xLabel.setAttribute("y", String(top - 6));
    xLabel.textContent = `k ${Math.round(xRange.min)}–${Math.round(
      xRange.max,
    )}`;
    svg.appendChild(xLabel);
    const yLabel = document.createElementNS(SVG_NS, "text");
    yLabel.setAttribute("class", "scale-label");
    yLabel.setAttribute("x", String(right));
    yLabel.setAttribute("y", String(top));
    yLabel.textContent = `SC ${yRange.min.toFixed(0)}–${yRange.max.toFixed(0)}`;
    svg.appendChild(yLabel);
  }

  private _drawXTicks(
    svg: SVGSVGElement,
    left: number,
    bottom: number,
    right: number,
    minTick: number,
    maxTick: number,
    step: number,
  ) {
    for (
      let x = Math.ceil(minTick / step) * step;
      x <= maxTick + 1e-9;
      x += step
    ) {
      const px =
        left +
        ((x - minTick) / Math.max(1e-9, maxTick - minTick)) * (right - left);
      this._line(svg, px, bottom, px, bottom - 4, "tick");
      const label = document.createElementNS(SVG_NS, "text");
      label.setAttribute("class", "x-tick");
      label.setAttribute("x", String(px));
      label.setAttribute("y", String(bottom + 16));
      label.setAttribute("text-anchor", "middle");
      label.textContent = String(Math.round(x));
      svg.appendChild(label);
    }
  }

  private _drawYTicks(
    svg: SVGSVGElement,
    left: number,
    top: number,
    bottom: number,
    yRange: { min: number; max: number; ticks: number[] },
  ) {
    for (const tick of yRange.ticks) {
      const py =
        bottom -
        ((tick - yRange.min) / Math.max(1e-9, yRange.max - yRange.min)) *
          (bottom - top);
      this._line(svg, left, py, left + 4, py, "tick");
      const label = document.createElementNS(SVG_NS, "text");
      label.setAttribute("class", "y-tick");
      label.setAttribute("x", String(left - 8));
      label.setAttribute("y", String(py + 4));
      label.setAttribute("text-anchor", "end");
      label.textContent = String(Math.round(tick));
      svg.appendChild(label);
    }
  }

  private _drawYAxisTitle(
    svg: SVGSVGElement,
    left: number,
    top: number,
    bottom: number,
    title: string,
  ) {
    const t = document.createElementNS(SVG_NS, "text");
    t.setAttribute("class", "axis-title");
    t.setAttribute("x", String((top + bottom) / 2));
    t.setAttribute("y", String(left - 50));
    t.setAttribute("text-anchor", "middle");
    t.setAttribute(
      "transform",
      `rotate(-90 ${left - 50} ${(top + bottom) / 2})`,
    );
    t.textContent = title;
    svg.appendChild(t);
  }

  private _drawXAxisTitle(
    svg: SVGSVGElement,
    left: number,
    bottom: number,
    right: number,
    title: string,
  ) {
    const t = document.createElementNS(SVG_NS, "text");
    t.setAttribute("class", "axis-title");
    t.setAttribute("x", String((left + right) / 2));
    t.setAttribute("y", String(bottom + 48));
    t.setAttribute("text-anchor", "middle");
    t.textContent = title;
    svg.appendChild(t);
  }

  static get is() {
    return "binclass-graph";
  }

  /** Stores the theme attribute value so it propagates through shadow DOM. */
  _set_theme(theme: string) {
    this.theme = theme;
  }

  static get styles() {
    return css`
      :host {
        display: block;
      }

      .scroll {
        overflow: auto;
        box-sizing: border-box;
        outline: 1px solid hsl(0, 0%, 82%);
        border-radius: 6px;
        background: var(--vaadin-background-color, #fff);
      }

      .canvas {
        display: block;
        background: var(--vaadin-background-color, #fff);
      }

      .line {
        fill: none;
        stroke: hsl(0, 0%, 45%);
        stroke-width: 1.5;
      }

      .axis {
        stroke: hsl(0, 0%, 30%);
        stroke-width: 1;
      }

      .tick {
        stroke: hsl(0, 0%, 30%);
        stroke-width: 1;
      }

      .x-tick,
      .y-tick {
        font-size: 11px;
        font-family: var(--vaadin-font-family, sans-serif);
        fill: var(--vaadin-text-color-secondary, #455063);
      }

      .axis-title {
        font-size: 13px;
        font-weight: 600;
        font-family: var(--vaadin-font-family, sans-serif);
        fill: var(--vaadin-text-color, #1d2330);
      }

      .scale-label {
        font-size: 10px;
        font-family: var(--vaadin-font-family, sans-serif);
        fill: var(--vaadin-text-color-secondary, #455063);
      }

      .annotation {
        font-size: 11px;
        font-family: var(--vaadin-font-family, sans-serif);
        fill: hsl(215, 60%, 35%);
      }

      .min-hair {
        stroke: hsl(215, 60%, 45%);
        stroke-width: 1;
        stroke-dasharray: 4 3;
      }

      .knob {
        fill: hsl(215, 60%, 45%);
        stroke: #fff;
        stroke-width: 1;
        cursor: default;
      }

      .guide {
        stroke: hsl(215, 60%, 45%);
        stroke-width: 1;
        stroke-dasharray: 2 3;
        opacity: 0.7;
      }

      .guide-dot {
        fill: hsl(215, 60%, 45%);
        r: 3;
      }

      .tooltip-k,
      .tooltip-sc {
        font-size: 11px;
        font-family: var(--vaadin-font-family, sans-serif);
        fill: hsl(215, 60%, 35%);
      }

      .hover-area {
        fill: transparent;
        cursor: crosshair;
      }
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    "binclass-graph": Graph;
  }
}
