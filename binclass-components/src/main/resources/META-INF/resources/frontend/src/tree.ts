/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
import { css, html, LitElement } from "lit";
import { customElement, property, query } from "lit/decorators.js";
import { ThemableMixin } from "@vaadin/vaadin-themable-mixin/vaadin-themable-mixin.js";

/**
 * A single node of a flat dendrogram. The list index equals {@link id}, so
 * children are referenced by plain int indices ({@code -1} means no child).
 */
interface TreeNodeData {
  id: number;
  name: string;
  sc: number;
  size: number;
  label: string;
  frequency: number;
  isLeaf: boolean;
  leftChild: number;
  rightChild: number;
}

/** Flat, index-addressed tree model pushed by the server. */
interface TreeModel {
  nodes: TreeNodeData[];
}

const SVG_NS = "http://www.w3.org/2000/svg";

/**
 * Renders a binary dendrogram as an orthogonal SVG layout: branches drawn as
 * right-angle polylines, knobs at every branching point and inline labels on the
 * leaves. Internal nodes are described by their knob's native {@code <title>}
 * tooltip. The whole flat node list is pushed once under the {@code model}
 * property; the layout (leaf columns via a tidy-tree pass, merge depth for x) is
 * computed client-side from it.
 */
@customElement("binclass-tree")
export class Tree extends ThemableMixin(LitElement) {
  @property({ type: Object }) model?: TreeModel;

  /** Theme attribute value propagated through the shadow DOM. */
  @property()
  theme: string | null = null;

  /** Fixed width of the leaf-label column in pixels. */
  public static readonly LABEL_WIDTH = 120;
  /** Left margin so the root branch is not drawn on the container's edge. */
  public static readonly LEFT_MARGIN = 24;
  /** Horizontal spacing between a merge level and its children, in layout units. */
  public static readonly MERGE_SPACING = 60;
  /** Vertical spacing between adjacent leaves, in layout units. */
  public static readonly LEAF_GAP = 24;

  /** Canvas SVG element, queried from shadow DOM after first render. */
  @query(".canvas") private _svg?: SVGSVGElement;
  /** Scroll container pinned to the canvas size for responsive sizing. */
  @query(".scroll") private _scroll?: HTMLElement;
  private _layout: TreeLayout | null = null;

  protected render() {
    return html`<div class="scroll"><svg class="canvas"></svg></div>`;
  }

  /** Lays out the tree once and (re)draws it whenever the model changes. */
  updated(changed: Map<string, unknown>) {
    if (changed.has("model")) {
      this._layout = new TreeLayout(this.model ?? { nodes: [] });
      this._draw();
    }
  }

  firstUpdated() {
    // @query populates _svg after the first render, so it is ready here.
    if (this._svg) {
      this._layout = new TreeLayout(this.model ?? { nodes: [] });
      this._draw();
    }
  }

  /** Clears the layout reference when detached so stale data is not redrawn. */
  disconnectedCallback() {
    super.disconnectedCallback();
    this._layout = null;
  }

  private _draw() {
    const svg = this._svg;
    if (!svg || !this._layout) {
      return;
    }
    // Clear previous drawing.
    while (svg.firstChild) {
      svg.removeChild(svg.firstChild);
    }
    const layout = this._layout;
    const width = layout.width();
    const height = layout.height();
    svg.setAttribute("width", String(width));
    svg.setAttribute("height", String(height));
    svg.setAttribute("viewBox", `0 0 ${width} ${height}`);

    // Branches first so knobs and labels sit on top.
    for (const node of layout.nodes) {
      if (node.isLeaf || node.leftChild < 0) {
        continue;
      }
      const left = layout.node(node.leftChild);
      const right = layout.node(node.rightChild);
      this._drawEdge(svg, node, left);
      this._drawEdge(svg, node, right);
    }

    // Knobs for internal nodes.
    for (const node of layout.nodes) {
      if (node.isLeaf || node.leftChild < 0) {
        continue;
      }
      const knob = document.createElementNS(SVG_NS, "circle");
      knob.setAttribute("class", "knob");
      knob.setAttribute("cx", String(node.x));
      knob.setAttribute("cy", String(node.y));
      knob.setAttribute("r", "4");
      knob.appendChild(document.createElementNS(SVG_NS, "title")).textContent =
        this._tooltipText(node);
      svg.appendChild(knob);
    }

    // Inline labels for leaves only.
    for (const node of layout.nodes) {
      if (!node.isLeaf) {
        continue;
      }
      const text = document.createElementNS(SVG_NS, "text");
      text.setAttribute("class", "label");
      text.setAttribute("x", String(node.x - 8));
      text.setAttribute("y", String(node.y + 4));
      text.textContent = this._truncate(node.label || node.name);
      svg.appendChild(text);
    }

    // Size the scroll container to fit the canvas.
    if (this._scroll) {
      this._scroll.style.width = `${width}px`;
      this._scroll.style.height = `${height}px`;
    }
  }

  private _drawEdge(svg: SVGSVGElement, parent: LayoutNode, child: LayoutNode) {
    const path = document.createElementNS(SVG_NS, "path");
    // Orthogonal right-angle polyline: horizontal from the merge knob to the
    // child's column, vertical down/up to the child row.
    const midX = (parent.x + child.x) / 2;
    const d = `M ${parent.x} ${parent.y} H ${midX} V ${child.y} H ${child.x}`;
    path.setAttribute("d", d);
    path.setAttribute("class", "edge");
    svg.appendChild(path);
  }

  private _tooltipText(node: LayoutNode): string {
    const parts: string[] = [];
    if (node.label) {
      parts.push(`Name: ${node.label}`);
    }
    parts.push(`Size: ${node.size}`);
    if (node.frequency > 0) {
      parts.push(`Frequency: ${node.frequency}`);
    }
    return parts.join("\n");
  }

  private _truncate(text: string): string {
    const max = Math.floor((Tree.LABEL_WIDTH - 8) / 7);
    return text.length > max ? `${text.slice(0, Math.max(0, max - 1))}…` : text;
  }

  static get is() {
    return "binclass-tree";
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
        border: 1px solid hsl(0, 0%, 82%);
        border-radius: 6px;
      }

      .canvas {
        display: block;
        background: var(--vaadin-background-color, #fff);
      }

      .edge {
        fill: none;
        stroke: hsl(0, 0%, 45%);
        stroke-width: 1.5;
      }

      .knob {
        fill: hsl(215, 60%, 45%);
        cursor: default;
      }

      .label {
        font-size: 12px;
        font-family: var(--vaadin-font-family, sans-serif);
        fill: var(--vaadin-text-color, #1d2330);
        text-anchor: end;
        dominant-baseline: middle;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
      }
    `;
  }
}

/** A laid-out node with computed pixel coordinates. */
interface LayoutNode extends TreeNodeData {
  x: number;
  y: number;
}

/**
 * Computes a tidy-tree layout for a flat node list. Leaf columns are assigned by
 * in-order traversal (packing labels without overlap); internal nodes are
 * centered between their children on the row axis, and merge depth drives the
 * column axis with a fixed scale so spacing is not cost-scaled.
 */
class TreeLayout {
  private readonly nodeList: LayoutNode[];
  private readonly byId = new Map<number, LayoutNode>();

  constructor(model: TreeModel) {
    this.nodeList = [];
    for (const raw of model.nodes) {
      const node: LayoutNode = { ...raw, x: 0, y: 0 };
      this.nodeList.push(node);
      this.byId.set(node.id, node);
    }
    if (this.nodeList.length === 0) {
      return;
    }

    // Assign leaves to columns by in-order traversal so siblings pack without
    // overlap. Internal nodes are centered between their children afterwards.
    const leafColumns = new Map<number, number>();
    let columnCursor = 0;
    const assignLeaves = (id: number): void => {
      const node = this.byId.get(id);
      if (!node) {
        return;
      }
      if (node.isLeaf || node.leftChild < 0) {
        leafColumns.set(id, columnCursor++);
        return;
      }
      assignLeaves(node.leftChild);
      assignLeaves(node.rightChild);
    };
    // The root is the single node with no parent.
    const parents = new Set<number>();
    for (const node of this.nodeList) {
      if (node.leftChild >= 0) {
        parents.add(node.leftChild);
      }
      if (node.rightChild >= 0) {
        parents.add(node.rightChild);
      }
    }
    const root = this.nodeList.find((n) => !parents.has(n.id));
    assignLeaves(root?.id ?? this.nodeList[0].id);

    // Depth for the merge axis via BFS from the root.
    const depths = new Map<number, number>();
    const queue: number[] = [root?.id ?? this.nodeList[0].id];
    depths.set(queue[0], 0);
    while (queue.length > 0) {
      const id = queue.shift() as number;
      const node = this.byId.get(id);
      if (!node) {
        continue;
      }
      for (const child of [node.leftChild, node.rightChild]) {
        if (child >= 0 && !depths.has(child)) {
          depths.set(child, (depths.get(id) as number) + 1);
          queue.push(child);
        }
      }
    }

    // All leaves align on the deepest merge column so they share one level.
    const maxDepth = depths.size ? Math.max(...depths.values()) : 0;

    // Assign pixel coordinates. Leaves get their column; internal nodes are
    // centered between children on the row axis and placed by depth on the
    // column axis.
    const position = (id: number): void => {
      const node = this.byId.get(id);
      if (!node) {
        return;
      }
      if (node.isLeaf || node.leftChild < 0) {
        node.y = (leafColumns.get(id) as number) * Tree.LEAF_GAP;
        node.x = maxDepth * Tree.MERGE_SPACING;
        return;
      }
      position(node.leftChild);
      position(node.rightChild);
      const left = this.byId.get(node.leftChild) as LayoutNode;
      const right = this.byId.get(node.rightChild) as LayoutNode;
      node.y = (left.y + right.y) / 2;
      node.x = (depths.get(id) ?? 0) * Tree.MERGE_SPACING;
    };
    position(root?.id ?? this.nodeList[0].id);

    // Center the root's column so leaves spread evenly to the right.
    const maxLeafColumn =
      leafColumns.size > 0 ? Math.max(...leafColumns.values()) : 0;
    for (const node of this.nodeList) {
      if (node.isLeaf || node.leftChild < 0) {
        continue;
      }
      // Root sits at the smallest x.
      const rootDepth = depths.get(root?.id ?? this.nodeList[0].id) ?? 0;
      node.x = rootDepth * Tree.MERGE_SPACING;
    }

    // Shift every node right so the root branch is not drawn on the edge.
    for (const node of this.nodeList) {
      node.x += Tree.LEFT_MARGIN;
    }
  }

  /** Returns every laid-out node. */
  get nodes(): LayoutNode[] {
    return this.nodeList;
  }

  /** Looks up a node by id, throwing when absent. */
  node(id: number): LayoutNode {
    const found = this.byId.get(id);
    if (!found) {
      throw new Error(`No node with id ${id}`);
    }
    return found;
  }

  /** Total canvas width in pixels, accounting for the label column.
   */
  width(): number {
    const maxX = this.nodes.reduce((max, n) => Math.max(max, n.x), 0);
    return maxX + Tree.LABEL_WIDTH + 24;
  }

  /** Total canvas height in pixels. */
  height(): number {
    return (
      this.nodes.reduce((max, n) => Math.max(max, n.y), 0) + Tree.LEAF_GAP + 16
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    "binclass-tree": Tree;
  }
}
