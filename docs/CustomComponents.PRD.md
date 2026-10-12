# Custom Components — Product Requirements Document (PRD)

**Status:** Draft for review
**Module area:** `binclass-components` → consumed by `binclass-ui`
**Tech stack:** Java 25, Vaadin 25.3.x, Lit (TypeScript client-side), JUnit 6, Playwright / BrowserlessTests, JSoup, no Lombok, no Spring/CDI

---

## 1. Purpose

The UI module needs custom visual components to render the results of binary-vector
clustering in a way that Vaadin's stock components cannot express directly:

- A **vector** as a grid of black/white cells (one cell per bit).
- A **centroid** as a grid of grey-shaded cells (probability per bit).
- A **cluster** grouping one centroid with its assigned vectors, collapsible and lazily populated.
- A **partition** aggregating clusters plus a header summarising global metrics.

These are implemented as web components in TypeScript (Lit) and exposed to Java via
Vaadin's server-side annotations (`@Tag`, `@JsModule`, `@ClientCallable`). Note: `@NpmPackage` is not used since the components are not published to npmjs.

---

## 2. Component Inventory

| Web component | Java wrapper | Role | Slots |
|---------------|--------------|------|-------|
| `binclass-vector` | `Vector` | Render a binary vector as black/white cells; hover highlights mismatch vs. rounded centroid. | default (cells) |
| `binclass-centroid` | `Centroid` | Render a probability centroid as grey-shaded cells; tooltip shows cluster stats. | default (cells) |
| `binclass-cluster` | `Cluster` | Centroid + collapsible, lazily populated block of vectors. | `centroid`, `vectors` |
| `binclass-partition` | `Partition` | Header with global metrics + body of clusters. | `header`, `body` |

> The cluster web component is named `binclass-cluster`.

---

## 3. Rendering Rules

### 3.1 `binclass-vector` (Vector)

- Each bit → one square: **1 = black**, **0 = white**.
- **Grid sizing:** side length `N` scales from `2×2` up to `12×12`. The number of cells is the vector length `L`; remaining cells when `L` is not a perfect square are rendered as empty/gap.
  - `N = clamp(ceil(sqrt(L)), 2, 12)`. Non-perfect-square remainders render as empty/gap cells.
- **Responsive sizing:** the outer UI defines how much space is available for a Vector (this may
  depend on browser window resizing). The Lit component uses a `ResizeObserver` to recompute cell
  size / grid layout when available space changes, keeping squares square and within bounds.
- **Theming:** the application uses Aura theming. Components are styled against Aura; they do not need
  to support Lumo.
- **Name / identifier:** shown once as a prefix of the vector, not per cell/square. Shown inline when the grid is larger than `8×8` (i.e. `N >= 9`). For smaller grids the name/identifier moves into a `vaadin-tooltip`.
- **Tooltip (small grids):** name, identifier, and distance to centroid.
- **Hover highlight:** `vaadin-tooltip` provides built-in hover detection; the component uses the
  tooltip controller helper class from `github.com/vaadin/web-components`. A cell turns **red** when
  its bit differs from the *rounded centroid* value at that position (`>= 0.5`). See the badge example:
  [badge.ts](https://github.com/TatuLund/badge/blob/v25/src/main/resources/META-INF/resources/frontend/src/badge/badge.ts).

### 3.2 `binclass-centroid` (Centroid)

- Each bit → one square shaded grey proportional to probability `el[i] ∈ [0,1]`
  (white at 0, black at 1, linear/graded grey in between).
- **Name:** the most common vector label within the cluster (most frequent `getStrain()`; see §5.1 item 9).
- **Tooltip (`vaadin-tooltip`):** cluster distortion, weight, number of vectors assigned.

### 3.3 `binclass-cluster` (Cluster)

- Structure: a `Centroid` block + a `Vectors` block, separated by a gap.
- **Caret button** toggles show/hide of the vectors; **vectors hidden by default**.
- Collapse/expand implemented with `vaadin-detail` (or equivalent).
- **Lazy population:** when opened, the cluster requests/parses its vector list. The Java side observes the open event and pushes the vector data to the web component.
- **Slots:** `centroid` and `vectors`.

### 3.4 `binclass-partition` (Partition)

- **Header** slot: global metrics — Stochastic Complexity, cluster count, vector count, and other key metrics (§6).
- **Body** slot: a collection of `Cluster` components.
- The Java `Partition` component consumes a presentation-specific model (built from the algorithms
  `Partition`) and propagates clusters/centroids with their data; each cluster in turn populates its
  vectors. Components depend only on their own presentation records, not on `binclass-algorithms`.

---

## 4. Data Flow (Java ↔ TypeScript)

```
Partition (Java) ──creates──▶ Cluster wrappers (one per cluster) ──on open──▶ loads Vectors
      │                              │
      │ model: Partition + InfiniteCentroids   each Vector bound to its rounded centroid
      ▼                              ▼
binclass-partition (TS)        binclass-cluster / binclass-vector (TS)
```

- **Algorithms model source of truth:** `org.binclass.algorithms.core.Partition`,
  `InfiniteCentroids`, `Centroid`, `BinaryVector`, `VectorSet`. These live in
  `binclass-algorithms` and are NOT depended upon by `binclass-components`.
- **Presentation-specific data model:** `binclass-components` defines its own immutable presentation
  records (e.g. `VectorData`, `CentroidData`, `ClusterData`, `PartitionModel`) that carry only what
  the UI needs — bits/probabilities, meta info for highlight/tooltip (name, identifier,
  distance-to-centroid), and cluster stats (distortion, weight, vector count).
- **Translation layer:** `binclass-ui` maps the algorithms model into these presentation records and
  feeds them to the components. Components consume only their own presentation model.
- **Push data to TS:** set properties via `setProperty(...)` — Vaadin handles both scalar values and
  arrays/objects automatically through Jackson serialization internally.
- **TS → Java signals:** open/close of cluster, selection of a vector/cluster — delivered as DOM
  events that the Java wrapper observes (`addEventListener` / property change). `@ClientCallable`
  may be used in some cases; `@BrowserCallable` is NOT used (that annotation is Hilla-only and we are
  not using Hilla).

---

## 5. Lazy Loading Design

- Triggered by the cluster "open" event observed on the Java side.
- **Lazy population:** when a cluster opens, the Java side observes the open event and pushes the
  vector data to the component. No virtual scrolling — vectors are simply populated on demand as they
  become visible / needed.
- **Scale expectation:** whole-partition vector counts can be large; lazy population avoids loading
  all clusters' vectors up front.

---

## 5.1 Decided Design Defaults

Since the user will review autonomously, the following decisions are locked in as defaults
(adjust later if needed):

| # | Topic | Decision |
|---|-------|----------|
| 1 | Web component tags | `binclass-vector`, `binclass-centroid`, `binclass-cluster`, `binclass-partition` (cluster tag = `binclass-cluster`). |
| 2 | Module layout | `binclass-components` defines its own presentation-specific data model and does **not** depend on `binclass-algorithms`. The algorithms model is mapped to presentation records in `binclass-ui`. |
| 3 | Java↔TS binding | Data pushed via `setProperty(...)` — Vaadin auto-serializes scalars and arrays/objects through Jackson internally. Client→server signals observed as DOM events (`addEventListener`/property change). `@ClientCallable` used where convenient; `@BrowserCallable` NOT used (Hilla-only, we don't use Hilla). |
| 4 | Grid sizing | `N = clamp(ceil(sqrt(L)), 2, 12)`; remainder cells as gaps. Outer UI defines space with a Lit `ResizeObserver`; vector name shown once as a prefix when grid > 8×8, else in tooltip. |
| 5 | Hover highlight | Vector's component-specific data model carries rounded-centroid meta (bits); TS rounds at `>= 0.5` and marks mismatched cells red on hover via `vaadin-tooltip`. |
| 6 | Tooltip metrics | Vector (small grid): name, identifier, Hamming distance to centroid. Centroid: class distortion, weight, vector count. |
| 7 | Lazy loading | Lazy population only — Java pushes vector data when a cluster opens / vectors become visible; no virtual scrolling. |
| 8 | Header metrics | Stochastic Complexity (partition), cluster count, vector count. Overall distortion included if cheaply available. |
| 9 | Label definition | Centroid name = most frequent `BinaryVector.getStrain()` in the cluster; fall back to cluster index when empty/unknown. |
| 10 | Interaction events | Hover (tooltip) + click-selection of cells, vectors, and clusters. Selection delivered as custom events to Java. |

## 6. Metrics Mapping (algorithms model → display)

| Display metric | Source in `binclass-algorithms` |
|----------------|----------------------------------|
| Cluster size | `Partition.getClusterSize(i)` |
| Total vector count | `Partition.getNumberOfVectors()` |
| Cluster SC (sum-of-costs) | `InfiniteCentroids.getSC(i)` |
| Partition SC | `DistanceCalculator.stochasticComplexity(partition, k, l)` |
| Class distortion | `DistanceCalculator.classDistortion(vectorSet, centroid)` (avg Hamming to centroid) |
| Overall distortion | `DistanceCalculator.overallDistortion(partition, centroids)` |
| Weight | `Centroid.getWeight()` (frequency ratio class_size / total) or raw count? |
| Vector→centroid distance | `DistanceCalculator.hammingDistance(vector, centroid)` |

> **Open:** which exact metrics belong in the Partition header and per-component tooltips (§6.1, §6.3).

---

## 7. Testing Strategy (per Agents.md)

- **JUnit 6** — Java wrapper logic, property/event mapping helpers, any pure TS→Java data shaping done in Java.
- **Playwright e2e** — every component with client-side (Lit/TypeScript) code, since these require a browser to exercise the web-component behaviour (grid sizing via ResizeObserver, hover red cells, lazy load, collapse). BrowserlessTests have little value here because the components' logic lives in the TypeScript/Lit client side rather than on the Java server.
- HTML shown to users sanitized with JSoup content mode.

### 7.1 Playwright approach (Vaadin docs)

Follow Vaadin's [Testing Applications with Playwright](https://vaadin.com/docs/latest/guide/testing/e2e-playwright) guidance, adapted for this non-Spring Boot setup:

- **Java-based** tests in `binclass-components/src/test/java`, named `*IT.java` so the `it` profile + maven-failsafe picks them up.
- **Server lifecycle:** reuse the existing jetty-ee11-maven-plugin config (`useTestScope=true`) and the `it` profile's `start-jetty`/`stop-jetty` executions bound to `pre-integration-test`/`post-integration-test`. No Spring Boot needed — start the dev build on a fixed port, then drive it from Playwright.
- **Playwright lifecycle:** one shared `static Playwright playwright = Playwright.create()` in `@BeforeAll`, launch Chromium per test, and `playwright.close()` in `@AfterAll`.
- **Locators/assertions:** use XPath/CSS locators (e.g. `//binclass-vector`) with implicit wait; assert on rendered cells, grid dimensions, hover-red state, lazy-populated vectors, and collapse toggling.

### 7.2 Minimal demo application under test scope

A minimal Vaadin view that hosts the components for Playwright to drive. Placed in **test scope** so jetty's `useTestScope=true` serves it without a separate main entry point.

- **Location:** `binclass-components/src/test/java/...` (mirrors how `binclass-ui` copies test-scoped views into the runtime classpath for the client-side compiler).
- **Mock data:** sufficient to exercise every representation edge case — does **not** need to be mathematically correct, only visually representative. Cover: short vectors (2×2 … 8×8), long vectors (>64 bits → up to 12×12 with remainder gaps), missing values, small/large clusters, empty clusters, single-cluster partitions, and a partition spanning many clusters for the header metrics.
- **Edge cases to demonstrate:** grid sizing at each `N` boundary; name-as-prefix vs tooltip switch at `N = 9`; hover-red mismatch cells; centroid grey-shade gradient across probabilities near 0 / 0.5 / 1; lazy vector population on cluster open; collapse/expand caret state.

---

## 7.1 Vaadin APIs (researched for v25.3)

All wrappers extend `com.vaadin.flow.component.Component` and use the three core annotations.
`@NpmPackage` is omitted since components are written locally in `frontend/`.

| Concern | API | Used by |
|---------|-----|---------|
| Web component tag | `@Tag("binclass-vector")` (must match `customElements.define`) | all wrappers |
| Client-side module | `@JsModule("./vector.ts")` (`./` prefix → `frontend/`, not `node_modules/`) | all wrappers |
| Property set/get | `getElement().setProperty(name, value)` / `getProperty(name, default)` | data push (scalars + arrays via Jackson) |
| Event mapping | `@DomEvent("open")` with nested `ComponentEvent` + `@EventData("event.detail.x")` | cluster open/close, selection |
| Child content | `HasComponents` / `HasComponentsOfType<T>` mixin; add children to root element | Partition body (clusters) |
| Named slots | Vaadin `SlotUtils` (e.g. `SlotUtils.setSlot(child, "vectors")`) to set the slot attribute on children, then append to root; TS template declares `<slot name="...">`. Robust way to wire named slots from Java. | Cluster centroid/vectors, Partition header/body |
| Collapse/expand | `vaadin-detail` (`vaadin-details-flow`) — `setExpanded`, `open()`, `close()` | Cluster caret toggle |
| Tooltip | Lit `TooltipController` from `@vaadin/component-base/src/tooltip-controller.js`; `<slot name="tooltip">` + built-in hover detection | Vector, Centroid (see badge.ts example) |
| Responsive sizing | Lit `ResizeObserver` in the TS component recompute cell size on available-space change | Vector grid |

### Locked-in API decisions (reviewed autonomously)

- **Data passing:** Java calls `getElement().setProperty(name, javaObject)` with a `List`/`Map`; Vaadin auto-serializes to JSON via Jackson. No manual string serialization.
- **Cluster collapse:** the `Cluster` wrapper extends `@Tag("vaadin-detail")` (`vaadin-details-flow`) and holds the centroid + vectors as light-DOM children inside it; collapse handled by Vaadin's detail (caret toggle, `setExpanded`/`open()`/`close()`).
- **Lazy load trigger:** primary mechanism is observing the open event via `@DomEvent("open")`; Java then pushes vector batches with `setProperty(...)` as they become visible. `@ClientCallable` remains a fallback for pull-based loading if needed.
- **Slots vs children:** named slots wired from Java with Vaadin's `SlotUtils` (e.g. `SlotUtils.setSlot(child, "vectors")`) which sets the slot attribute on child elements; append to root and TS template declares `<slot name="...">`. Cluster uses `centroid`/`vectors`; Partition uses `header`/`body`.

---

## 8. Open Questions

See the questions collected from the user for full detail. Summary of themes:

1. ✅ **Web component tag names** — confirmed `binclass-cluster`.
2. ✅ **Module layout** — components in `binclass-components` with their own presentation-specific data model; no dependency on `binclass-algorithms`. Translation layer lives in `binclass-ui`.
3. ✅ **Java↔TS binding** — `setProperty(...)` for scalars and arrays (Jackson internally); DOM events observed Java-side; `@ClientCallable` optional; `@BrowserCallable` NOT used (Hilla-only).
4. ✅ **Grid sizing** — `N = clamp(ceil(sqrt(L)), 2, 12)`, remainder cells as gaps; outer UI defines space with a Lit `ResizeObserver`.
5. ✅ **Hover/centroid coupling** — Vector's component-specific data model carries rounded-centroid meta for red-highlight on hover.
6. ✅ **Tooltip content & distance metric** — name/id/distance (vector); distortion/weight/count (centroid).
7. ✅ **Lazy loading** — lazy population only, no virtual scrolling.
8. ✅ **Header metrics** — SC, cluster count, vector count (+ overall distortion if cheap).
9. ✅ **Label definition** — most frequent `getStrain()` in the cluster.
10. ⏳ **Still open** — missing-value rendering, Aura styling details, selection/interaction events, accessibility of many tooltips.

The four API questions asked during review are resolved and locked in §7.1:
- 11. ✅ **Data passing** — `setProperty(name, List/Map)` auto-serialized via Jackson; no manual JSON strings.
- 12. ✅ **Cluster collapse** — wrapper extends `@Tag("vaadin-detail")`; centroid + vectors are light-DOM children inside it.
- 13. ✅ **Lazy load trigger** — observe open via `@DomEvent`, push batches with `setProperty(...)`; `@ClientCallable` as fallback.
- 14. ✅ **Slots vs children** — named slots wired from Java with Vaadin `SlotUtils` (e.g. `SlotUtils.setSlot(child, "vectors")`); append to root; TS template declares `<slot name="...">`.
