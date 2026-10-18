/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
import { css, html, LitElement, PropertyValues } from "lit";
import { customElement, property } from "lit/decorators.js";
import { TooltipController } from "@vaadin/component-base/src/tooltip-controller.js";
import { ThemableMixin } from "@vaadin/vaadin-themable-mixin/vaadin-themable-mixin.js";

/**
 * Renders a binary vector as a horizontal strip of black (1) / white (0) cells, one per bit.
 * <p>
 * Each cell is a square div; the whole strip scrolls horizontally when it is wider than its container.
 * Mismatching cells turn red on hover when their bit differs from the rounded centroid value. Names are
 * shown inline above the strip and identifiers in a tooltip for small ones.
 * </p>
 */
@customElement("binclass-vector")
export class Vector extends ThemableMixin(LitElement) {
  @property({ type: Array }) bits: number[] = [];
  @property() name = "";
  @property() identifier = "";
  @property({ type: Number }) distanceToCentroid = 0;
  @property({ type: Array }) roundedCentroid: number[] = [];

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
   * Observes the cells container and sizes each cell so every bit fits its width.
   * <p>
   * Sizing is done client-side from the observed container width, so the strip stays responsive
   * without any server-side pixel knowledge. It re-measures whenever the container size changes or
   * the bit count changes.
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
        Vector.MAX_CELL_SIZE,
        Math.max(Vector.MIN_CELL_SIZE, Math.floor(width / this.bits.length)),
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

  private _mismatched(index: number): boolean {
    const bit = this.bits[index];
    if (bit === undefined) {
      return false;
    }
    const expected = this.roundedCentroid[index];
    return expected === undefined ? true : bit !== expected;
  }

  /**
   * Returns whether the vector name is shown inline instead of in a tooltip.
   * Names render inline once the grid side length reaches a 9x9 grid (or larger).
   */
  private _showNameInline(): boolean {
    const len = this.bits.length;
    if (len <= 0) {
      return false;
    }
    const side = Math.max(
      Vector.MIN_CELL_SIZE,
      Math.min(Vector.MAX_CELL_SIZE, Math.ceil(Math.sqrt(len))),
    );
    return side >= 9;
  }

  private _tooltipText(): string {
    const parts: string[] = [];
    if (this.name) {
      parts.push(`Name: ${this.name}`);
    }
    if (this.identifier) {
      parts.push(`Identifier: ${this.identifier}`);
    }
    parts.push(`Distance to centroid: ${this.distanceToCentroid}`);
    return parts.join("\n");
  }

  updated(changed: PropertyValues) {
    if (this._tooltipController && changed.has("name")) {
      this._tooltipController.setContext({ text: this._tooltipText() });
    }
    if (changed.has("bits") || changed.has("roundedCentroid")) {
      this._attachCellsObserver();
    }
  }

  static get styles() {
    return css`
      :host {
        display: block;
        width: 100%;
        /* Fixed-width label column so stacked vectors align their bit strips. */
        --label-width: 96px;
      }

      .row {
        display: flex;
        align-items: flex-start;
        gap: 4px;
      }

      .label {
        font-weight: 600;
        min-width: var(--label-width);
        max-width: var(--label-width);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }

      .cells {
        display: flex;
        gap: 1px;
        /* Grow to fill the available row width so the ResizeObserver measures
           responsive space rather than the content's fixed pixel size. */
        flex-grow: 1;
        min-width: 0;
      }

      /* Fade at the right edge when cells are smaller than available space and
         the strip scrolls, signalling that more bits extend off-screen. */
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

      .cell.black {
        background-color: hsl(0, 0%, 8%);
      }

      /* Pure white so zero cells stay distinct from the page background and
         stand out against neighbouring black cells via the shared outline. */
      .cell.white {
        background-color: hsl(0, 0%, 100%);
      }

      /* Hover highlight for mismatched cells in a vector. */
      .cell.mismatch:hover {
        outline-color: hsl(0, 72%, 52%);
        box-shadow: inset 0 0 0 2px hsl(0, 72%, 52%);
      }
    `;
  }

  protected render() {
    const cells = this.bits.map(
      (bit, i) => html`
        <div
          class="cell ${bit ? "black" : "white"} ${this._mismatched(i)
            ? "mismatch"
            : ""}"
          part="cell cell-${i}"
          data-index="${i}"
        ></div>
      `,
    );
    return html`
      <div class="row">
        <div
          class="label ${this._showNameInline() ? "name-inline" : ""}"
          part="name"
        >
          ${this.name}${this.identifier ? "  " + this.identifier : ""}
        </div>
        <div class="cells" part="grid">${cells}</div>
      </div>
    `;
  }
}
