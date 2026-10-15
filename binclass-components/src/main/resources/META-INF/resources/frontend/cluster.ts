/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
import { css, html, LitElement } from "lit";
import { customElement, property } from "lit/decorators.js";
import { Details } from "@vaadin/details/src/vaadin-details.js";

/**
 * Renders a cluster: a centroid block plus a lazily populated, collapsible block of assigned vectors.
 * <p>
 * The caret toggle (provided by {@link Details}) shows or hides the vectors; they are hidden by
 * default. The centroid lives in the {@code centroid} slot and is always visible; vectors live in the
 * {@code vectors} slot.
 * </p>
 */
@customElement("binclass-cluster")
export class Cluster extends Details {
  static get styles() {
    return css`
      :host {
        display: block;
      }

      /* Summary row: caret + label (from Details) carrying the info text. */
      .summary-row {
        display: flex;
        align-items: center;
        gap: var(--vaadin-gap-s, 8px);
      }

      /* Centroid block sits below the summary and above the vectors. Its cells
         start at the same left offset as the vector cell columns (label width
         plus row gap) so both grids line up vertically. The centroid stays
         visible while the details are closed. */
      .centroid-block {
        margin-top: 4px;
        padding-left: calc(96px + var(--vaadin-gap-s, 8px));
      }

      /*
       * The collapsible vectors block is hidden while the details are closed.
       * Uses a dedicated part name so it never collides with the summary's own
       * [part='content'] (which only wraps the caret label and stays visible).
       */
      [part="vectors"] {
        display: flex;
        flex-direction: column;
        gap: 8px;
        border-top: 1px solid hsl(0, 0%, 88%);
        padding-top: 8px;
        padding-bottom: 8px;
      }

      :host(:not([opened])) [part="vectors"] {
        display: none;
      }
    `;
  }

  /** Returns the slotted summary element (the caret toggle) in light DOM. */
  private _summaryElement(): HTMLElement | null {
    for (const child of this.children) {
      if (child.getAttribute("slot") === "summary") {
        return child as HTMLElement;
      }
    }
    return null;
  }

  connectedCallback() {
    super.connectedCallback();
    // The inherited Details click handler relies on detecting the summary via
    // `focusElement`, which is unreliable once the element is renamed to
    // `binclass-cluster`. Add an explicit listener that toggles the host
    // `opened` attribute when the summary caret is clicked.
    this.addEventListener("click", (event) => {
      const target = event.target as Node;
      const summary = this._summaryElement();
      if (summary && (target === summary || summary.contains(target))) {
        this.opened = !this.opened;
      }
    });
  }

  protected render() {
    return html`
      <div class="summary-row">
        <slot name="summary"></slot>
      </div>
      <div class="centroid-block"><slot name="centroid"></slot></div>
      <div part="vectors">
        <slot name="vectors"></slot>
      </div>
    `;
  }
}
