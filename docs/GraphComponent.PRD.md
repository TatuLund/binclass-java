# Graph Component — Product Requirements Document (PRD)

**Status:** Draft for review
**Module area:** `binclass-components` → consumed by `binclass-ui`
**Tech stack:** Java 25, Vaadin 25.3.x, Lit (TypeScript client-side), JUnit 6, Playwright / BrowserlessTests, JSoup, no Lombok, no Spring/CDI

> Companion to `CustomComponents.PRD.md` and `TreeComponent.PRD.md`. This document covers a new
> **line chart** component that plots *Stochastic Complexity (SC)* as a function of the *number of
> clusters*. It reuses the same Java ↔ Lit binding conventions as the partition/tree components but
> renders an SVG line chart with adaptive axes and a highlighted minimum point rather than cell grids
> or dendrogram branches.

---

## 1. Purpose

The UI needs a component to visualize how **Stochastic Complexity** changes as the number of clusters
`k` varies, so an analyst can read off the *global minimum* — the cluster count with the lowest stochastic
complexity. It displays the result of a single search run (range, automatic, or local), exactly the curve
produced by that search:

- `AutomaticSearch.run()` runs GLA for each cluster count `k`, records the best SC per `k`, and keeps
  the global best (`argmin_k SC(k)`).
- `DistanceCalculator.stochasticComplexity(partition, k, l)` (and its Jeffreys / uniform variants) is
  the source of each `SC(k)` value.

The graph turns that `(k, SC(k))` series into a line chart so the minimum and the shape of the curve
are visible at a glance. The reference sketch (`docs/graph.png`) shows the target look: an x-axis
labeled *No. of classes* and a y-axis labeled *Stochastic complexity*, with the curve dropping fast
then flattening. Unlike the sketch, the **minimum point must be explicitly marked** (a knob plus
crosshairs to both axes).

Requirements from the request:

- Line chart of SC vs. number of clusters.
- Built with Lit + SVG, same conventions as `Tree` / partition components (`@Tag` + `@JsModule`).
- **Data model is simple** — effectively a list of numbers (the SC values), indexed by cluster count.
- **Y-axis auto-detects the min/max range** client-side and rescales to the data; no fixed bounds pushed
  from Java unless explicitly requested (see open questions).
- **X-axis labeling is adaptive**: the range can be as small as `1–10` clusters or as large as `1–200`,
  so tick spacing and labels must adapt automatically.
- **Minimum point indicated** with a knob and crosshairs to both axes (unlike the sketch).
- **Small datasets** (< ~200 numbers); no lazy-loading / virtual scrolling needed.

---

## 2. Component Inventory

| Web component | Java wrapper | Role | Slots |
|---------------|--------------|------|-------|
| `binclass-graph` | `Graph` | Render a line chart of SC vs. number of clusters as SVG: adaptive x/y axes, a connected line, and a highlighted minimum point with crosshairs. | none (self-contained) |

> The graph web component is named `binclass-graph`. It is self-contained — it takes one flat series
> of points and lays everything out client-side, exactly like `binclass-tree` takes one flat node list.

---

## 3. Input Model (algorithms side)

The source of truth for each data point is a cluster count `k` and its best stochastic complexity:

```java
// algorithms side (what the UI translation layer already has access to)
double sc = DistanceCalculator.stochasticComplexity(partition, k, l);   // or Jeffreys / uniform variant
int    k    = ...;                                                      // number of clusters (>= 1)
```

`AutomaticSearch` / `RangeSearch` already compute the best `SC(k)` for every `k` in a search range and
track the global minimum. The graph consumes only the resulting **series** — an ordered list of
`(k, SC(k))` pairs. No per-vector data is pushed to the client; the chart is purely scalar.

- **Algorithms model source of truth:** `Partition`, `VectorSet`, `DistanceCalculator`. These live in
  `binclass-algorithms` and are NOT depended upon by `binclass-components` (same rule as §2 / decision #2
  in CustomComponents.PRD).
- **Presentation-specific data model:** `binclass-components` defines its own immutable records carrying
  only what the UI needs (`GraphPoint`, `GraphModel`).
- **Translation layer:** `binclass-ui` builds the series from whatever search produced it and feeds the
  points to the component. Components consume only their own presentation model.

---

## 4. Data Flow (Java ↔ TypeScript)

```
AutomaticSearch / RangeSearch (best SC per k) ──translation──▶ GraphModel (list of points)
                            │                                          │
                            │  setProperty / ValueSignal             ▼
                            ▼                                          ▼
binclass-graph (TS): computes adaptive axes → draws line + min-point knob + crosshairs
```

- **Push data to TS:** set properties via `setProperty(...)` — Vaadin auto-serializes scalars/arrays/
  objects through Jackson internally (decision #3 in CustomComponents.PRD). A whole-series object pushed
  once is sufficient; a reactive `ValueSignal<GraphModel>` binding is optional but recommended so later
  updates rebuild the chart automatically.
- **TS → Java signals:** none required — the graph is read-only data, not a control input (answer #8), so no
  point/minimum events are pushed back to Java. Hover and the tracking crosshair live entirely client-side.

---

## 5. Presentation Data Model (to define in `binclass-components`)

A flat list of points is simplest and maximally flexible — each point carries its own cluster count `x`
and SC value `y`, so the x-axis range is derived from the data itself and needs no separate "start index".

```java
@NullMarked
public record GraphPoint(int x, double y) {
    /** Cluster count `x` is always an integer; SC value `y` must be finite —
     * NaN / Infinity are rejected at construction. */
    public GraphPoint {
        if (!Double.isFinite(y)) {
            throw new IllegalArgumentException("GraphPoint SC value must be finite");
        }
    }
}

@NullMarked
public record GraphModel(List<GraphPoint> points) {
    public GraphModel {
        this.points = List.copyOf(points);
        if (this.points.isEmpty()) {
            throw new IllegalArgumentException("points must not be empty");
        }
    }

    /** Returns an unmodifiable copy of the point list. */
    public List<GraphPoint> points() {
        return List.copyOf(points);
    }
}
```

Design decisions locked into the model:

- **One finite coordinate per point.** Non-finite SC values (e.g. a `k` that failed to cluster) are
  filtered out by the translation layer before the record is built, so rendering never has to special-case
  `NaN`. The record enforces finiteness of `y` as an invariant; `x` is always a whole integer.
- **Explicit `x` (integer).** Each point stores its cluster count as an `int`, so the x-axis range `[minX, maxX]`
  is derived from the data and fully adaptive — no implicit "index + start" arithmetic required. When counts are
  always a contiguous integer range starting at some `kstart`, Java simply builds points with `x = kstart + i`.
- **Order.** Points arrive in search order; the client sorts by `x` before drawing so the line connects them
  left-to-right regardless of push order.
- **Missing values / gaps.** A cluster count may have no finite SC (a failed run), so points need not form a
  contiguous integer range — `k` is always an integer but some counts can be absent from the series. The chart
  draws only the present points; adaptive x-ticks span whatever `[minX, maxX]` results and skip missing counts.
- **Title / axis labels** (optional) can ride along as metadata on a future extension of the pushed object
  (§6.7); they are not part of the point records themselves.

> **Why pairs and not a plain list (answer #1):** cluster counts are always integers while SC values are
> decimals, and a given `k` may have *no* finite SC (a failed run), so points can be non-contiguous. The
> explicit `GraphPoint(x, y)` model captures that directly — no "index + kstart" arithmetic is needed and gaps
> in the range render naturally. This is locked in as default.

---

## 6. Rendering Rules

### 6.1 Canvas & coordinate system

- Render a single `<svg>` element inside the Lit component's shadow DOM, wrapped in a scrolling container (as
  with `Tree`) so large canvases never overflow the viewport — horizontal/vertical scrollbars appear when the
  plot exceeds available space; the SVG still lays out fully and only its host scrolls.
- **Responsive sizing:** the graph's actual size depends on the container it is placed in (typically set to
  100 % width/height). As a Vaadin component it inherits Java-side width/height settings, and the Lit component
  uses a `ResizeObserver` to recompute pixel dimensions / viewBox when that space changes — same pattern as
  `binclass-vector`. Axes stay square-ish and within bounds.
- A fixed set of margins defines the plot area: left margin for y tick labels, right/top/bottom margins for x
  tick labels (and optional axis titles). See constants below.

### 6.2 Y-axis auto-detection (min/max from data)

- Compute `yMin` / `yMax` from all point `y` values (points are already finite by construction).
- **Zero-range guard:** if `yMin == yMax` (single value or flat series), expand symmetrically around it so a
  usable axis band exists.
- **Headroom:** pad the top and bottom by a fraction of the span (e.g. ~5–10 %) so the minimum knob is not
  pinned to the very edge and there is visual breathing room. Round the padded endpoints outward to "nice"
  numbers before generating ticks.
- Generate tick values with a **friendly-interval** algorithm targeting roughly 5–8 ticks (see §6.4).
- **Fully automatic bounds (answer #3):** axes are derived purely from the data — no Java-side min/max override
  is exposed, since recomputing over < ~200 pairs is cheap and keeps every graph on its own natural scale.

### 6.3 Minimum-point highlight (knob + crosshairs)

- Find the point with the smallest `y` (`argmin`). Ties resolve to the leftmost / smallest-`k` point for
  determinism. This is the global SC minimum — the analyst's target.
- **Knob:** draw a highlighted filled circle at that data point, larger and in an accent colour than any other
  marker (e.g. `hsl(215, 60%, 45%)` like tree knobs, or an Aura accent token).
- **Crosshairs:** draw dashed lines from the knob to both axes — a vertical line down to the x-axis at the
  optimal `k`, and a horizontal line to the y-axis at the minimum SC.
- **Axis annotations:** small labels at the two axis intersections stating the optimal cluster count and the
  minimum SC value (formatted per §6.6). This makes the "elbow" readable without hovering.
- If the series is monotonic (minimum sits on an endpoint), still highlight that endpoint knob; crosshairs run
  to whichever axes are reachable.

### 6.4 Adaptive x-axis labeling

The whole point of this component is that the same code must look right for `1–10`, `1–50`, `1–100`, and
`1–200` clusters. The x-axis step is chosen from a fixed mapping keyed on the maximum cluster count `maxX`:

| Max cluster count (`maxX`) | Tick interval | Example labels |
|---------------------------|---------------|----------------|
| ≤ 10  | 2  | 2, 4, 6, 8, 10 |
| ≤ 50  | 5  | 5, 10, …, 50 |
| ≤ 100 | 10 | 10, 20, …, 100 |
| ≤ 200 | 20 | 20, 40, …, 200 |

Ticks snap to nice values: first tick = `floor(minX / step) * step`, then successive ticks up to `maxX`. When
`minX > 1` the leading labels start at that snapped value rather than at 1. This yields the round tick steps seen
in the reference sketch (interval 10 for a wide range).

The y-axis uses a friendly-interval algorithm over its padded range (§6.2), targeting ~5–8 ticks and rounding to
nice values — matching the reference sketch's spacing on the SC axis.

### 6.5 Line & points

- Sort points by `x`, then draw a single connected **polyline** (`<path>` or `<polyline>`) through them in x
  order, so the curve reads left-to-right even if push order differs.
- Stroke: thin line in a theme-neutral colour (e.g. `hsl(0, 0%, 45%)` like tree edges), matching the sketch's
  simple black line.
- **No area fill and no per-point markers** (answer #9) — only the global-minimum point carries a marker (§6.3).

### 6.6 Number formatting

- **Y-axis SC labels:** rounded to **0 decimal places** (answer #12) — e.g. `23`, `25`.
- **Global-minimum value:** shown with **2 decimal places** wherever it is displayed (axis annotation and hover
  tooltip), so the precise optimum reads as e.g. `24.73` rather than a rounded tick.
- Cluster-count labels on x: rendered as integers (`k` is always an integer).
- English only, so no locale grouping/decimal separators are needed; keep formatting simple and consistent.

### 6.7 Titles & axis labels (optional)

- **Configurable axis labels via a small Java API** (answer #5): y-axis title defaults to *"Stochastic complexity"*
  and x-axis title to *"No. of classes"*, both matching the reference sketch and domain vocabulary, but settable so
  the graph can be reused for other purposes. The application is English only — no localization layer is required.
- A component-level heading/title is optional; keep it out of `GraphModel`/`GraphPoint` and pass it as metadata on
  the pushed object if used.

### 6.8 Container & theming

- **Scrolling container** around the SVG, mirroring `Tree`: up to ~200 points never overflow the viewport; only
  the host scrolls.
- **Theming:** Aura only. Components style against Aura and use Aura CSS custom properties / theme tokens for
  colours, font family, and radii — no Lumo support required (same as other components).

### 6.9 Interaction

- **Tracking crosshair** (answer #6): while the cursor moves over the plot, a vertical guide follows it along the
  line to the nearest data point and shows that point's `(k, SC)` in a tooltip — continuous readout as the cursor
  glides across the curve.
- The minimum-point knob is always visible (not hover-only) so the optimal `k` is readable at a glance; hovering it
  can additionally show its exact `(k, SC)` with 2 decimals (§6.6).
- **Read-only** (answer #8): no interaction events are pushed back to Java — the graph is data, not a control input,
  and carries no area fill or per-point markers (§6.5).

### 6.10 Accessibility

- **Coarse a11y (answer #10):** expose the chart as an SVG `role="figure"` with an `aria-label` summarising it —
  axis titles, cluster range `[minX, maxX]`, and global minimum. Fine-grained per-point exposure is not required.

### 6.10.1 Edge cases

- **Empty model** → render empty axes / placeholder (or throw on `setData(null)` like `Tree`). The record already
  forbids an empty point list; the wrapper handles a null model by clearing the chart.
- **Single point** → plot the knob, derive x/y bands with symmetric padding around that value.
- **Flat series** (`yMin == yMax`) → symmetric y padding so ticks still exist (§6.2).
- **Non-contiguous / unordered x** → client sorts by `x`; adaptive ticks work on whatever `[minX, maxX]` results.

---

## 7. Testing Strategy (per Agents.md)

- **JUnit 6** — Java wrapper logic: flat-list construction from a search result, property/event mapping helpers,
  `setData` / `pushModel` serialization to the `{ points: [...] }` object, and any pure TS→Java data shaping done in
  Java. Test `GraphPoint`/`GraphModel` records (immutability, finiteness guard, empty-list rejection).
- **Playwright e2e (`*IT.java`)** — the SVG rendering needs a browser to exercise client-side layout, so these are
  essential (BrowserlessTests have little value here; the logic lives in Lit/TS), exactly as for `Tree`. Assert on:
  presence of `<svg>`, adaptive y-axis tick count and labels for known ranges, adaptive x-axis labeling matching the
  interval mapping (e.g. `1–10` shows integers while `1–200` uses round intervals), the minimum-point knob + crosshairs
  at the correct data point with a 2-decimal SC value, tracking crosshair readout on hover, and responsive re-layout on
  container resize. Follow the same
  `ComponentIT` base + `it` profile / jetty setup as the partition components (§7.1 in CustomComponents.PRD).
- **Demo view** in test scope (standalone `GraphView`, route `graph`) hosting a `Graph` with mock data covering: a
  narrow range (`1–10`), a wide range (`1–200`), a series whose minimum is interior vs. at an endpoint, and a flat /
  single-value series to exercise the zero-range guard.

### 7.1 Vaadin APIs (researched for v25.3)

| Concern | API | Used by |
|---------|-----|---------|
| Web component tag | `@Tag("binclass-graph")` | `Graph` wrapper |
| Client-side module | `@JsModule("./src/graph.ts")` (`./` → `frontend/`) | `Graph` wrapper |
| Property set/get | `getElement().setProperty(name, value)` / Jackson arrays-objects | data push (whole series) |
| Reactive binding | `ValueSignal` + `bindProperty` + `Signal.effect(...)` rebuild chart on change | optional model binding |
- **Event mapping** | DOM events → `@DomEvent`/`ComponentEvent` (optional; graph is read-only, so none strictly required) | interactions |
| Responsive sizing | Lit `ResizeObserver` recompute canvas/viewBox on available-space change | chart layout |
| Theming base | extend `ThemableMixin(LitElement)` in TS | `binclass-graph` |

---

## 8. Files to Create (tentative)

```
binclass-components/src/main/java/org/binclass/components/graph/
    Graph.java                     // Vaadin wrapper (@Tag + @JsModule)
    GraphModel.java                // presentation record
    GraphPoint.java                // single point record
binclass-components/src/main/resources/META-INF/resources/frontend/src/
    graph.ts                       // Lit web component (SVG line-chart renderer)
binclass-components/src/test/java/org/binclass/components/graph/
    GraphTest.java                 // JUnit 6
    GraphIT.java                   // Playwright e2e
binclass-components/src/test/java/org/binclass/components/demo/
    GraphView.java                 // demo view for Playwright (add to PartitionView or standalone)
docs/GraphComponent.PRD.md         // this file
```

> Package location: a dedicated `org.binclass.components.graph` package is used rather than reusing
> `tree` or `partition`, since the rendering model (adaptive line chart vs. SVG layout / cell grids) and data
> shape differ, even though the Java ↔ TS binding conventions are identical.

---

## 9. Open Questions — All Resolved

All twelve questions raised during review are answered below; each decision is locked into the section it affects.

| # | Topic | Locked-in decision | Section |
|---|-------|--------------------|---------|
| 9.1 | Data model granularity | Explicit `GraphPoint(x = k, y = SC)` pairs; `k` integer, `SC` decimal; gaps for missing values are fine | §5 |
| 9.2 | Minimum semantics | Global minimum only (`argmin_k SC(k)`); no elbow/knee detection | §6.3 |
| 9.3 | Axis bounds | Fully automatic from data; no Java override (cheap over < ~200 pairs) | §6.2 |
| 9.4 | Series count | One series at a time — range, automatic, or local search, one run | §2, §5 |
| 9.5 | Titles & localization | Configurable axis labels via a small Java API; English only, no localization | §6.7 |
| 9.6 | Hover behaviour | Tracking crosshair following the cursor to the nearest point | §6.9 |
| 9.7 | x-axis interval | Interval keyed on max k: 2 (≤10), 5 (≤50), 10 (≤100), 20 (≤200) | §6.4 |
| 9.8 | Interaction events | None required; graph is read-only data, not a control input | §6.9 |
| 9.9 | Area fill / markers | No area fill, no per-point markers; marker only at the global minimum | §6.5 |
| 9.10 | Accessibility | Coarse: `role="figure"` announcing title, axes, cluster range, and global minimum | §6.10 |
| 9.11 | Height / aspect ratio | Size follows the container (typically 100 %); Vaadin width/height inherited in Java | §6.1 |
| 9.12 | Number formatting | SC y-axis labels rounded to 0 decimals; global-minimum value shown with 2 decimals | §6.6 |

### 9.12 Number formatting / locale
Fixed decimal places for SC ticks and tooltips (e.g. 3 decimals), formatted per request `Locale`? Integer vs.
compact formatting for large cluster counts?
