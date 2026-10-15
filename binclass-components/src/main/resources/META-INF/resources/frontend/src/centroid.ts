/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
import { css, html, LitElement } from "lit";
import { customElement, property } from "lit/decorators.js";
import { TooltipController } from "@vaadin/component-base/src/tooltip-controller.js";
import { ThemableMixin } from "@vaadin/vaadin-themable-mixin/vaadin-themable-mixin.js";

/**
 * Renders a probability centroid as grey-shaded cells. Each cell is white at 0, black at 1, and graded
 * grey in between proportional to its probability value. A tooltip shows cluster distortion, weight, and
 * vector count.
 */
@customElement("binclass-centroid")
export class Centroid extends ThemableMixin(LitElement) {
  @property({ type: Array }) probabilities: number[] = [];
  @property() name = "";
  @property({ type: Number }) classDistortion = NaN;
  @property({ type: Number }) weight = NaN;
  @property({ type: Number }) vectorCount = 0;

  /** Smallest cell edge in pixels used when the container is narrower than the bit count. */
  private static readonly MIN_CELL_SIZE = 2;

  /** Largest cell edge in pixels so a grid never exceeds a 12x12 pixel cell. */
  private static readonly MAX_CELL_SIZE = 12;

  private _cellsEl?: HTMLElement;
  private _resizeObserver?: ResizeObserver;
  private _tooltipController?: TooltipController;

  firstUpdated() {
    this._attachCellsObserver();
    if (!this._tooltipController) {
      this._tooltipController = new TooltipController(this);
      this.addController(this._tooltipController);
    }
  }

  /**
   * Observes the cells container and sizes each cell so every probability fits its width.
   * <p>
   * Sizing is done client-side from the observed container width, keeping the strip responsive
   * without any server-side pixel knowledge. It re-measures whenever the container size changes or
   * the probability count changes.
   * </p>
   */
  private _attachCellsObserver() {
    const cellsEl = this.renderRoot.querySelector(
      ".cells",
    ) as HTMLElement | null;
    this._cellsEl = cellsEl ?? undefined;
    if (typeof ResizeObserver === "undefined" || !cellsEl) {
      return;
    }
    this._resizeObserver?.disconnect();
    this._resizeObserver = new ResizeObserver((entries) => {
      const width = entries[0]?.contentRect.width;
      if (!width || width <= 0) {
        return;
      }
      const size = Math.min(
        Centroid.MAX_CELL_SIZE,
        Math.max(
          Centroid.MIN_CELL_SIZE,
          Math.floor(width / this.probabilities.length),
        ),
      );
      this._applyCellSize(size);
    });
    this._resizeObserver.observe(cellsEl);
  }

  private _applyCellSize(size: number) {
    if (this._cellsEl) {
      this._cellsEl.style.setProperty("--cell-size", `${size}px`);
      this._refreshOverflow();
    }
  }

  /**
   * Toggles the {@code overflow} class when the strip is wider than its visible
   * area, so a CSS fade can signal that more cells extend off-screen.
   */
  private _refreshOverflow() {
    const cellsEl = this._cellsEl;
    if (!cellsEl) {
      return;
    }
    cellsEl.classList.toggle(
      "overflow",
      cellsEl.scrollWidth > cellsEl.clientWidth,
    );
  }

  private _shade(prob: number): string {
    const value = Math.max(0, Math.min(1, prob));
    return `hsl(0, 0%, ${Math.round(value * 100)}%)`;
  }

  private _tooltipText(): string {
    const parts: string[] = [];
    if (this.name) {
      parts.push(`Name: ${this.name}`);
    }
    if (!Number.isNaN(this.classDistortion)) {
      parts.push(`Class distortion: ${this.classDistortion.toFixed(3)}`);
    }
    if (!Number.isNaN(this.weight)) {
      parts.push(`Weight: ${this.weight.toFixed(3)}`);
    }
    parts.push(`Vectors: ${this.vectorCount}`);
    return parts.join("\n");
  }

  updated(changed: PropertyValues) {
    if (changed.has("probabilities")) {
      this._attachCellsObserver();
    }
    if (this._tooltipController && changed.has("name")) {
      this._tooltipController.setContext({ text: this._tooltipText() });
    }
  }

  static get styles() {
    return css`
      :host {
        display: block;
        width: 100%;
      }

      .label {
        font-weight: 600;
        margin-bottom: 4px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .cells {
        display: flex;
        gap: 1px;
        /* Grow to fill the available width so the ResizeObserver measures
           responsive space rather than the content's fixed pixel size. */
        flex-grow: 1;
        min-width: 0;
      }

      .cells.overflow {
        -webkit-mask-image: linear-gradient(
          to right,
          #000 82%,
          rgba(0, 0, 0, 0) 100%
        );
        mask-image: linear-gradient(to right, #000 82%, rgba(0, 0, 0, 0) 100%);
      }

      .cell {
        width: var(--cell-size, 8px);
        height: var(--cell-size, 8px);
        box-sizing: border-box;
        flex: 0 0 var(--cell-size, 8px);
        min-width: var(--min-cell-size, 2px);
        min-height: var(--min-cell-size, 2px);
        outline: 1px solid hsl(0, 0%, 78%);
      }
    `;
  }

  protected render() {
    const cells = this.probabilities.map(
      (prob, i) => html`
        <div
          class="cell"
          part="cell cell-${i}"
          data-index="${i}"
          style="background-color: ${this._shade(prob)}"
        ></div>
      `,
    );
    return html`
      <div class="label" part="name">${this.name}</div>
      <div class="cells" part="grid">${cells}</div>
    `;
  }
}
