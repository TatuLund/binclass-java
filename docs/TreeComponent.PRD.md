# Tree Component — Product Requirements Document (PRD)

**Status:** Draft for review
**Module area:** `binclass-components` → consumed by `binclass-ui`
**Tech stack:** Java 25, Vaadin 25.3.x, Lit (TypeScript client-side), JUnit 6, Playwright / BrowserlessTests, JSoup, no Lombok, no Spring/CDI

> Companion to `CustomComponents.PRD.md`. This document covers a new **dendrogram**
> component that visualizes the hierarchy produced by
> `org.binclass.algorithms.core.TreeNode` (built via `TreeBuilder`). It reuses the
> same Java ↔ Lit binding conventions as the partition components but draws the
> structure with SVG lines and knob dots rather than cell grids.

---

## 1. Purpose

The UI needs a component to render the **hierarchical clustering result**
(dendrogram) produced by `TreeBuilder.makeTreePnn(...)` / `makeTreePnn2(...)`.
Vaadin's stock components cannot express a binary tree with positioned branches,
so we build a custom web component `binclass-tree` in TypeScript (Lit), exposed to
Java via `@Tag` + `@JsModule`, exactly like `Vector`, `Centroid`, `Cluster`, and
`Partition`.

Requirements from the request:

- Visualize the hierarchy produced by `TreeBuilder`.
- Built with Lit, similar fashion to the Partition components, but simpler.
- **Leaves** carry a label = the most common name of the vector in the cluster
  representing that leaf (most frequent `BinaryVector.getStrain()`).
- **Branches** drawn as lines — SVG is the first option for drawing them.
- **Branching points** rendered as visible dots / knobs.
- **Hover on a knob** shows a tooltip with the info at that point: most common
  name, size of the joint cluster, etc.

---

## 2. Component Inventory

| Web component | Java wrapper | Role | Slots |
|---------------|--------------|------|-------|
| `binclass-tree` | `Tree` | Render a binary dendrogram as SVG: lines for branches, knobs for branching points, labels for leaves, tooltips on knobs. | none (self-contained) |

> The tree web component is named `binclass-tree`. It is intentionally
> self-contained — unlike the partition components it does not slot in child
> wrappers; all nodes are described by a single flat data model and laid out
> client-side.

---

## 3. Input Model (algorithms side)

The source of truth is `org.binclass.algorithms.core.TreeNode` (immutable):

```java
public final class TreeNode {
    double getSC();          // sum-of-costs / merge cost at this level
    String getName();        // label ("C1", "Merged", "Root", or leaf strain name)
    TreeNode getLeft();      // left child (null for leaves)
    TreeNode getRight();     // right child (null for leaves)
    boolean isLeaf();        // true when both children null
}
```

`TreeBuilder` produces a binary dendrogram:

- **Leaves** = the original partition clusters (`"C1" … "Cn"`), each seeded with
  its singleton stochastic complexity.
- **Internal nodes** = merges, labeled `"Merged"` / `"Root"`, carrying the SC of
  the post-merge composition.
- The root is a single node; `makeTreePnn` returns `null` for an empty partition
  and a lone leaf when there is only one cluster.

To render meaningful **leaf labels** and **joint sizes**, the translation layer
aggregates at the cluster level only (no per-vector data is pushed to the client):

- its size (`Partition.getClusterSize(i)`) — number of vectors;
- the most common strain name among its members (most frequent
  `BinaryVector.getStrain()` over `partition.getElements(i)`).

Internal nodes additionally carry their `sc` and a derived **joint size** = sum
of both children's sizes. The same most-common-strain rule is applied to the whole
subtree so an internal node can show its joint-cluster name in its tooltip.

---

## 4. Data Flow (Java ↔ TypeScript)

```
TreeNode (Java, from TreeBuilder) ──translation──▶ TreeModel record (flat node list)
                │                                          │
                │  setProperty / ValueSignal               ▼
                ▼                                          ▼
binclass-tree (TS): computes layout → draws SVG lines + knobs + labels + tooltips
```

- **Algorithms model source of truth:** `TreeNode`, `Partition`, `VectorSet`,
  `BinaryVector`. Lives in `binclass-algorithms`; NOT depended upon by
  `binclass-components` (same rule as §2 / decision #2 in CustomComponents.PRD).
- **Presentation-specific data model:** `binclass-components` defines its own
  immutable records (`TreeModel`, `TreeNodeData`) carrying only what the UI needs.
- **Translation layer:** `binclass-ui` walks the `TreeNode`, flattens it into an
  indexed node list, and attaches leaf names / sizes computed from the
  `Partition`. Components consume only their own presentation model.
- **Push data to TS:** set properties via `setProperty(...)` — Vaadin auto-serializes
  scalars/arrays/objects through Jackson internally (decision #3). A whole-tree
  object pushed once is sufficient; a reactive `ValueSignal<TreeModel>` binding is
  optional but recommended so later updates rebuild the diagram automatically.

---

## 5. Presentation Data Model (to define in `binclass-components`)

A **flat, index-addressed** list of nodes is simplest for client-side layout and
avoids deep recursive Java↔TS property trees. Each node references its children by
index (`-1` = none).

```java
@NullMarked
public record TreeNodeData(
        /** Unique 0-based id within the tree (list index). */
        int id,
        /** Original identifier from `TreeNode.getName()` (`"C1"`, `"Merged"`,
         * `"Root"`); kept as a fallback when strains are empty. */
        String name,
        /** Sum-of-costs / merge cost at this node (kept for reference; not used
         * to position nodes — see §6.1). */
        double sc,
        /** Number of vectors in the subtree rooted here (joint cluster size). */
        int size,
        /** Most common strain name in that node's subtree — used as the leaf
         * inline label and as the internal-node tooltip name. "" when unknown. */
        @org.jspecify.annotations.Nullable String label,
        /** How many vectors share `label` within this subtree (frequency of the
         * most common name). 0 when `label` is empty. */
        int frequency,
        /** True when this node has no children. */
        boolean isLeaf,
        /** Id of left child, or -1. */
        int leftChild,
        /** Id of right child, or -1. */
        int rightChild) { }

@NullMarked
public record TreeModel(
        /** Flat list of nodes; index == id. */
        List<TreeNodeData> nodes) { }
```

Design decisions locked into the model:

- **Leaf-level data only:** the tree does not receive individual vectors, so each node
  carries just its `label` (most common strain name), its `size` (cluster size) and the
  `frequency` of that name within the subtree.
- **Tie-break:** when two or more strains are equally frequent in a subtree, the one
  that occurs **first** wins. This can happen on inner nodes where children carry
  different names; first occurrence is deterministic given node order.
- **Leaf vs internal:** leaves carry the inline `label`; internal nodes do not render a
  label and rely on their knob tooltip. Their tooltip still shows the subtree's most
  common name, size and frequency.
- **Global metrics / title:** rendered in a separate div/span component outside `Tree`,
  so `TreeModel` carries no title/metric field.

---

## 6. Rendering Rules

### 6.1 Layout (horizontal classic dendrogram)

The reference layout is a **horizontal** dendrogram — root on the left, leaves on
the right, branches drawn as orthogonal right-angle polylines (see the picture in
the PRD review). Node positions are computed client-side from the flat list (no
per-node coordinates pushed from Java):

- **Merge axis (x):** uniform normalization — each node's horizontal position is
  derived from its depth/position in the tree with a fixed scale **not tied to any
  metric** (matching the example picture). The root sits at the smallest x and leaves
  spread evenly toward the right; no cost value drives spacing.
- **Leaf order:** a tidy-tree pass (reingold-tilford-style) assigns leaf columns by
  in-order traversal, packing labels without overlap; internal nodes are centered
  between their children on the x-axis.
- **Row axis (y):** leaves share one row; internal nodes sit vertically between
  their two children. Row assignment only needs to avoid label/edge overlap — it is
  not cost-scaled.

### 6.2 SVG drawing

- Render a single `<svg>` element inside the Lit component's shadow DOM.
- **Branches:** draw each parent→child edge as an orthogonal right-angle polyline,
  matching the classic dendrogram look (root left, leaves right).
- **Knobs:** draw a `<circle>` at every branching point (internal node) position —
  the visible dot / knob where its two branches meet.
- **Labels:** render each leaf's label as `<text>` adjacent to that leaf. Internal
  nodes do **not** get an inline label; they are identified by their knob and fully
  described in the tooltip (see §6.3).
- Sanitize all text with JSoup content mode before inserting into SVG `<text>`.

### 6.3 Knobs and tooltips

- Each internal-node knob is a visible dot carrying hover context.
- **Tooltip content:** most common strain name at that merge, joint cluster size,
  and frequency of that name within the subtree. Format as multi-line text.
- **Tooltip mechanism:** native SVG `<title>` child of each knob circle. The tree
  renders many nodes, each with its own tooltip, so Vaadin's `TooltipController`
  (which is designed for a single component-level tooltip) does not fit — the
  per-node `<title>` element gives the correct hover behaviour out of the box.

### 6.4 Labels

- **Leaf-only labels:** only leaves render an inline label — the most common strain
  name in that leaf's cluster.
- **Fixed width + ellipsis:** each leaf label occupies a fixed-width column; names
  longer than the column are truncated with an ellipsis (CSS `text-overflow: ellipsis`
  on the SVG `<text>`). The full text is always available via the knob/leaf tooltip,
  so truncation never hides information.
- Empty / unknown name falls back to the node `name` (`"C1"` …) or index.

### 6.5 Container & theming

- **Scrolling container:** the tree is rendered inside a scrolling container so up to
  ~150 leaf labels never overflow the viewport — horizontal (and vertical, if needed)
  scrollbars appear when content exceeds the available space. The SVG itself still lays
  out fully; only its host scrolls.
- **Theming:** Aura only. Components style against Aura; no Lumo support required.

### 6.6 Interaction

- **Hover-only tooltips.** No click-to-collapse / expand of subtrees, and zoom/pan is
  minimal or none — the tree is a read-only visualization for up to ~150 leaves.
- Hovering a knob shows its SVG `<title>` tooltip (see §6.3).

---

## 7. Testing Strategy (per Agents.md)

- **JUnit 6** — Java wrapper logic: flat-list construction, child-index wiring,
  `sc`/`size` propagation, leaf-name assignment, property/event mapping helpers, and
  any pure TS→Java data shaping done in Java.
- **Playwright e2e (`*IT.java`)** — the SVG rendering needs a browser to exercise
  client-side layout, so these are essential (BrowserlessTests have little value here;
  the logic lives in Lit/TS). Assert on: presence of `<svg>`, number of edge lines and
  knob circles matching node counts, leaf label text, hover tooltip content per knob,
  and responsive re-layout. Follow the same `ComponentIT` base + `it` profile / jetty
  setup as the partition components (§7.1 there).
- **Demo view** in test scope (standalone `TreeView`, route `tree`) hosting a `Tree`
  with mock data covering: single-cluster tree (renders as a single line/vertex),
  two-cluster merge, multi-level ladder, long strain names, and a wide tree to exercise
  the scrolling container.
- **Edge cases:** single-cluster tree renders as a single line/vertex; empty partition /
  `null` root throws.

### 7.1 Vaadin APIs (researched for v25.3)

| Concern | API | Used by |
|---------|-----|---------|
| Web component tag | `@Tag("binclass-tree")` | `Tree` wrapper |
| Client-side module | `@JsModule("./src/tree.ts")` (`./` → `frontend/`) | `Tree` wrapper |
| Property set/get | `getElement().setProperty(name, value)` / Jackson arrays-objects | data push (whole tree) |
| Reactive binding | `ValueSignal` + `bindProperty` + `Signal.effect(...)` rebuild layout on change | optional model binding |
| Event mapping | DOM events → `@DomEvent`/`ComponentEvent` for knob hover/click if Java needs them | interactions |
| Theming base | extend `ThemableMixin(LitElement)` in TS | `binclass-tree` |

---

## 8. Files to Create (tentative)

```
binclass-components/src/main/java/org/binclass/components/tree/
    Tree.java                     // Vaadin wrapper (@Tag + @JsModule)
    TreeModel.java                // presentation record
    TreeNodeData.java             // flat node record
binclass-components/src/main/resources/META-INF/resources/frontend/src/
    tree.ts                       // Lit web component (SVG renderer)
binclass-components/src/test/java/org/binclass/components/tree/
    TreeTest.java                 // JUnit 6
    TreeIT.java                   // Playwright e2e
binclass-components/src/test/java/org/binclass/components/demo/
    TreeView.java                 // demo view for Playwright (add to PartitionView or standalone)
docs/TreeComponent.PRD.md         // this file
```

> Package location: a dedicated `org.binclass.components.tree` package is used rather
> than reusing `partition`, since the rendering model (SVG layout vs. cell grids) and
> data shape differ.

---

## 9. Open Questions

Two follow-ups remain open:

### 9.1 Tie-break stability
When two strains are equally frequent in a subtree, the first occurrence wins (see §5).
How to make "first" stable across runs is still to be decided.

### 9.2 Accessibility
Keyboard/`a11y` requirements for the SVG `<title>` tooltips — how should focus and
screen-reader exposure work for many nodes?

