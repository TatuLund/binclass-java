/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
import { css, html, LitElement } from "lit";
import { customElement, property } from "lit/decorators.js";
import { ThemableMixin } from "@vaadin/vaadin-themable-mixin/vaadin-themable-mixin.js";

/**
 * Renders a partition: a header summarising global metrics (Stochastic Complexity, cluster count, vector
 * count) and a body composed of {@code binclass-cluster} components. The header metrics are pushed as
 * properties on the element; clusters arrive in the {@code body} slot.
 */
@customElement("binclass-partition")
export class Partition extends ThemableMixin(LitElement) {
  @property({ type: Number }) stochasticComplexity = Number.NaN;
  @property({ type: Number }) clusterCount = 0;
  @property({ type: Number }) vectorCount = 0;
  @property({ type: Number }) overallDistortion = Number.NaN;

  private _format(value: number): string {
    return Number.isNaN(value) ? "n/a" : value.toFixed(3);
  }

  static get styles() {
    return css`
      :host {
        display: inline-block;
        vertical-align: top;
      }

      /* Partition header metrics. */
      .header {
        display: flex;
        flex-wrap: wrap;
        gap: 16px;
        padding: 12px;
        border: 1px solid hsl(0, 0%, 82%);
        border-radius: 6px;
        margin-bottom: 16px;
      }

      .metric {
        display: flex;
        flex-direction: column;
      }

      .metric label {
        font-size: 0.75rem;
        color: hsl(0, 0%, 42%);
        text-transform: uppercase;
        letter-spacing: 0.05em;
      }

      .metric value {
        font-size: 1.1rem;
        font-weight: 600;
      }

      .body {
        display: flex;
        flex-direction: column;
        gap: 8px;
      }
    `;
  }

  protected render() {
    return html`
      <div class="header" part="header">
        <span class="metric"
          ><label>Stochastic Complexity</label
          ><value>${this._format(this.stochasticComplexity)}</value></span
        >
        <span class="metric"
          ><label>Clusters</label><value>${this.clusterCount}</value></span
        >
        <span class="metric"
          ><label>Vectors</label><value>${this.vectorCount}</value></span
        >
        <span class="metric"
          ><label>Overall Distortion</label
          ><value>${this._format(this.overallDistortion)}</value></span
        >
      </div>
      <div class="body" part="body">
        <slot name="body"></slot>
      </div>
    `;
  }
}
