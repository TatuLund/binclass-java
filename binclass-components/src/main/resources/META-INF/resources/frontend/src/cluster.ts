/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
import { css, html, LitElement } from "lit";
import { customElement, property, query } from "lit/decorators.js";
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

      /* Centroid block sits below the summary and above the vectors. Its cells
         start at the same left offset as the vector cell columns (label width
         plus row gap) so both grids line up vertically. The centroid stays
         visible while the details are closed. */
      .centroid-block {
        margin-top: 4px;
        padding-left: calc(92px + var(--vaadin-gap-s, 8px));
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

  /**
   * Runs the inherited Details/DetailsBaseMixin setup (which registers the
   * content, summary and tooltip controllers). Cluster relies on the single
   * click-to-toggle handler registered by CollapsibleMixin during
   * `super.ready()` instead of adding its own, so clicking the caret toggles
   * `opened` exactly once rather than twice and cancelling itself out.
   */
  protected ready() {
    super.ready();
  }

  protected render() {
    // super.render() (from Details) provides the summary, default content and
    // tooltip slots that its SummaryController, ContentController and
    // TooltipController observe. Without them those controllers call
    // shadowRoot.querySelector(...) -> null and SlotObserver.connect throws,
    // which also prevents the caret toggle handler from being registered.
    return html`
      ${super.render()}
      <div class="centroid-block"><slot name="centroid"></slot></div>
      <div part="vectors">
        <slot name="vectors"></slot>
      </div>
    `;
  }
}
