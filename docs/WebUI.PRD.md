# BinClass Web UI — Product Requirements Document (Plan)

**Status:** Plan for review · implement one display at a time
**Module area:** `binclass-ui` (depends on `binclass-algorithms` + `binclass-components`)
**Tech stack:** Java 25, Vaadin 25.3.x, Lit (TypeScript client-side), JUnit 6, Playwright / BrowserlessTests, JSoup, JSpecify, no Lombok, no Spring/CDI

> Companion to `CustomComponents.PRD.md`, `TreeComponent.PRD.md` and `GraphComponent.PRD.md`.
> This document plans the **Vaadin Flow Web UI** that exposes every `binclass-cli` command as an
> interactive *display* (route). It is written so each display can be implemented, tested and merged
> independently. The CLI stays the reference implementation for behaviour; the web layer depends on
> `binclass-algorithms` **directly** (it does **not** depend on `binclass-cli`) and only adds a thin
> translation + presentation layer on top of `binclass-components`.

---

## 0. How to read this document

Each display chapter follows the same template so they can be implemented one at a time:

1. **Purpose & CLI origin** — which command(s) it replaces or embeds.
2. **Algorithm entry point(s)** — the concrete `binclass-algorithms` class/method that does the work.
3. **Parameters → Vaadin control mapping** — every flag becomes a typed field; flags become toggles,
   enums become `Select`/`RadioButtonGroup`, numeric ranges become `IntegerField`/`NumberField`.
4. **EventBus events** — records to add in `binclass-algorithms/events` for decoupling the long-running
   search from the UI (see §1).
5. **UI skeleton** — an indented sketch of the Vaadin component tree with ids, so a display can be
   assembled bottom-up and commented on without guessing layout.
6. **Translation layer notes** — how algorithm models map to `binclass-components` presentation records.
7. **Testing notes** — JUnit 6 unit + Playwright/BrowserlessTest scope for that display.

### The skeleton technique

A *UI skeleton* is a plain-text tree of the Vaadin components that make up a view, with stable ids and
the container type on each line. It is deliberately framework-agnostic (no imports, no logic) so it can
be commented, refactored or handed to an implementer as a checklist. Example:

```
MainLayout (AppLayout, @Layout)
├─ addToNavbar: DrawerToggle + H1("BinClass")
└─ addToDrawer: Scroller(SideNav)
     └─ SideNavItem("GLA Search", "gla")  → RouteGlaView
        ...

RouteGlaView extends VerticalLayout (route "gla")
├─ FormLayout paramsForm          id="params"
│   ├─ TextField filebase         id="filebase"
│   ├─ IntegerField kStart        id="k-start"      min=1
│   └─ Button run                 id="run-gla"
├─ TabSheet                       id="gla-tabs"     (Search | SC Graph | Partition)
└─ ... tab panels ...
```

Implement a display by turning each skeleton line into real code, top-down. Ids are the anchors for
Playwright tests (`#run-gla`, `#params`), so keep them stable across iterations.

---

## 1. Architecture & shared infrastructure

### 1.1 Module dependency graph

```
binclass-algorithms   (pure Java, no Vaadin)  ← source of truth
        ▲                exposes EventBus + events package
        │
binclass-components   (Vaadin @Tag/@JsModule web components; NO dep on algorithms)
        ▲               Vector, Centroid, Cluster, Partition, Tree, Graph
        │
binclass-ui         depends on BOTH above (NOT binclass-cli); WAR; jetty-ee11; browserless-test-junit6
                  ├─ routing: MainLayout + one View per display
                  ├─ translation layer: algorithms model → components presentation records
                  └─ background tasks via CompletableFuture on virtual threads
```

`binclass-ui/pom.xml` already declares both dependencies (`binclass-components`, `binclass-algorithms`)
and test scope `browserless-test-junit6`. No pom change is required except adding new views.

> **No dependency on `binclass-cli`:** the UI consumes algorithms from `binclass-algorithms` directly and
> reuses its own eventbus/events for progress. `binclass-cli` stays a separate leaf module; the web layer
> never calls CLI command classes.

### 1.2 Decoupling: EventBus events

Long-running GLA searches (automatic / range / bootstrap) run on the search-execution pool and must not
block the UI thread. The existing singleton `EventBus.get()` posts `AbstractEvent`s that registered
listeners receive asynchronously (`EventBusImpl` uses its own fixed virtual-thread executor for event
dispatch — a pool **separate** from the search-execution pool, used only to fan events out to listeners and
never mixed into algorithm background execution).

**Convention (per Agents.md):** EventBus events are immutable **records**. Define them in
`binclass-algorithms/events/`. The marker interface is `AbstractEvent`; concrete events extend it.

Records to add (one per long-running display, or a small shared set):

```java
// binclass-algorithms/events — all @NullMarked records implementing AbstractEvent
record SearchStarted(String display, String filebase, int expectedK) {}
record SearchProgress(String display, int k, double sc, boolean finished) {}
// Carries the best partition + centroids whenever a new best is detected so the Partition tab can be
// refreshed live (not only on completion); progress also appends one SC point per k to the SC curve.
record SearchCompleted(String display, PartitionResult result) {}   // carries best partition/centroids + scs[]
record SearchError(String display, String message) {}

// A minimal immutable payload the UI binds to; keep it small (k, sc, centroids-per-k optional).
record PartitionResult(int kmin, double scmin, int clusterCount, double[] scs, int[] ks) {}
```

> **Decision:** both automatic and range searches emit an SC point per `k` (SC curve tab updated after each
> k) **and** push a full `Partition`/`InfiniteCentroids` snapshot whenever a new best partition is detected,
> so the user can browse the improved partition live without waiting for the search to finish. Progress events
> therefore carry scalars plus an optional best-partition snapshot; completion carries the final best result.

**Flow for a display (MVVM):**
1. View creates its `*ViewModel` — which implements `EventBusListener` and hosts all signals — in its constructor; the VM
   registers itself with `EventBus.get()` once (in its own constructor / `@PostConstruct`). The View stays thin: it only binds
   components to the VM's signals and forwards user actions as commands.
2. User clicks *Run* → view calls `viewModel.run()`, which launches `CompletableFuture.supplyAsync(search, THREAD_FACTORY)`
   on a virtual-thread pool and flips the `running` signal. Work starts from the command call — **not** by posting a
   `SearchStarted` event.
3. The search posts events; the VM's listener picks each one up and acts on its type: for an SC point it appends to the graph
   model, for a new best partition it calls the display's translator to build a `PartitionModel`, then updates the bound
   `ValueSignal`. Because the View is bound reactively, updating the signal rebuilds the component automatically — no manual
   `setProperty`/`setValue` from the View. On completion the final result sets the same signals so graph/partition/tree rebuild.
4. View unregisters on detach (`beforeClientResolution` or a `@Before`).

### 1.3 Translation layer pattern (per display)

Mirrors the existing convention in `binclass-components`: algorithms model → presentation records, done in
`binclass-ui`, never inside components. For every display define a small *translator* (static factory methods
or a record builder) that maps:

| Algorithms model | → | Components presentation record |
|------------------|--|--------------------------------|
| `Partition` + `InfiniteCentroids` | → | `PartitionModel` (+ `ClusterData`, `VectorData`, `CentroidData`) |
| `AutomaticSearch.Result` (best SC per k) | → | `GraphModel(List<GraphPoint>)` |
| `TreeNode` (dendrogram) | → | `TreeModel(List<TreeNodeData>)` |

Each display's model builds its **own view-specific** `PartitionModel` from the algorithm `Partition` — there is
no shared partition translator; a classifier display and the cumulative display shape `ClusterData`/`VectorData`
differently to match what their tab shows. The existing `Partition` web component is agnostic enough to render both
a regular search result and a dynamic (cumulative) partition, so no new `DynamicPartitionModel`/component is needed
(see the Cumulative chapter).

Components consume only their own presentation records. This keeps `binclass-components` free of an
algorithms dependency (decision #2 in `CustomComponents.PRD`).

### 1.4 File input / data loading

Every display starts from a *filebase* (`.hdr` + `.dat`, or existing `.partition`/`.centroids`). Reuse
`DataLoader.loadVectors(filebase)` and the format readers/writers already in `binclass-algorithms/io`.
For the UI, provide an **upload area** (`vaadin-upload`) that buffers the uploaded files into an **in-memory**
byte buffer keyed by a generated filebase string (the Electron desktop shell has no multi-user memory pressure,
so we keep vectors in memory rather than writing temp files to disk), then calls `DataLoader`. Downloads reuse the
same in-memory buffer.

Because the UI and the Vaadin session are **one-to-one**, scoped data lives in that single session: store it in a
session attribute or attach it with `ComponentUtil.setData(...)` so it is tied to the component/UI lifecycle (and can
be read back with `ComponentUtil.getData`). A small `DataStore` helper maps filebase → loaded `VectorSet`; multiple
displays on the same UI share one load through it.

### 1.5 Shared parameter controls

Every display that takes an algorithm **config record** builds it directly from its form inputs — `GLAConfig`
for GLA-style searches, `CumulativeConfig` for cumulative classification, etc. The same "form → config record"
pattern is used on every such view; a small immutable *form-value* record per display holds the raw field values
and a static translator maps it to the algorithm's config record (DRY, per Agents.md).

GLA-style searches share ~20 flags. Build **reusable sub-components** (plain Vaadin views / `Component`
wrappers) to avoid repetition:

- `GlaParametersForm` — wraps the common numeric/enum/toggle fields and exposes a method returning a
  `GLAConfig`-shaped value or individual getters. Reused by GLA Search, Local Search, Bootstrap, Semi-Cumulative.
- Enum mapping helpers for `distanceType`, `centroidType`, `heuristic`, `searchType`.

### 1.6 View pattern: MVVM + background work

Prefer Vaadin's **Model-View-ViewModel** pattern for the displays (see
[How to structure complex Vaadin views with the MVVM pattern](https://vaadin.com/docs/latest/building-apps/views/mvvm).
Each display view owns a small, UI-free `*ViewModel` class:

| Role | In BinClass | Notes |
|------|-------------|-------|
| **Model** | `binclass-algorithms` classes (`AutomaticSearch`, `CumulativeClassifier`, …) + `binclass-components` presentation records. Knows nothing about the view. | |
| **ViewModel** | one `*ViewModel` per display (e.g. `GlaViewModel`) | Implements `EventBusListener` and hosts all state as `ValueSignal`s; exposes commands (`run()`, `setKStart(...)`), derives rules (`canRun()`). Picks up each EventBus event, populates a presentation model via the translator (e.g. builds `PartitionModel` from the algorithm `Partition`), then updates its bound signal so the View reacts automatically. No Vaadin components inside → unit-testable in plain JUnit 6. |
| **View** | the `@Route` `VerticalLayout` (e.g. `RouteGlaView`) | Creates the ViewModel, binds UI components to its signals via `bindValue(...)` / `bindEnabled(...)`, forwards user actions as commands. Reactive: it does not populate models or handle events itself — updating a signal in the VM rebuilds the bound component automatically. |

Why MVVM here: a display has state shared across parts (params form → graph → partition tab), rules over
combined state (e.g. *Run* enabled only when data is loaded and k-start ≤ k-stop), background work that
updates the UI without clicks, and behaviour worth unit-testing — all four MVVM triggers from the docs.

**Background work:** each display keeps a `Future<?>` in UI/session scope and submits its search to a **single-threaded
executor** (`Executors.newSingleThreadExecutor(Thread.ofVirtual().factory())`) that lives either in the Vaadin UI or in the
session. Because it is single-threaded, searches on one UI are naturally serialized — GLA Search and Local Search cannot
overlap; this is what takes care of concurrency management. The `run()` command disables its own button with
`button.setDisableOnClick(true)`, submits to the executor, and flips a `running` signal; on completion (or cancellation)
the button is re-enabled and the bound signals update. Work starts from calling the ViewModel's `run()` command — **not** by
first posting a `SearchStarted` event (that record remains available but is optional). Progress is pushed back through EventBus
`SearchProgress` events, which are dispatched on the EventBus's own separate dispatch pool and consumed on the Vaadin server
thread; completion flows through `.thenApply(...)` / `.thenAccept(...)` or a `SearchCompleted` event.

```java
// GlaViewModel — UI-free, unit-testable; hosts signals AND implements EventBusListener
class GlaViewModel implements EventBusListener {
    private final ValueSignal<LoadedData> data;          // loaded vectors + centroids
    private final ValueSignal<Boolean> running;
    private final ValueSignal<PartitionModel> partitionModel;  // bound to the Partition tab component
    private final ValueSignal<List<GraphPoint>> graphPoints;   // bound to the SC Graph component
    private CompletableFuture<GlaOutcome> future;        // cancelled in view.onDetach()

    Signal<Boolean> canRun() { return data.map(d -> d.vectorSet() != null && !running.peek()); }
    void run(Button runButton) {
        runButton.setDisableOnClick(true);               // re-enabled on completion/cancellation
        running.update(true);
        future = CompletableFuture.supplyAsync(() -> new AutomaticSearch(data.peek().vectorSet(), config()).run(),
                                               SINGLE_THREADED_EXECUTOR);
        future.thenAccept(out -> { data.update(d -> d.withOutcome(out)); running.update(false); });
    }

    // EventBusListener: react to each event by populating a presentation model, then update its signal.
    @Override public void onEvent(AbstractEvent event) {
        if (event instanceof SearchCompleted(var display, var completed)) {
            partitionModel.update(m -> buildPartitionModel(completed.partition(), completed.centroids()));
        } else if (event instanceof SearchProgress(var k, var sc, _ finished)) {
            graphPoints.update(list -> appendPoint(list, new GraphPoint(k, sc)));
        }
    }

    // translator: algorithm Partition/InfiniteCentroids → view-specific PartitionModel
    private PartitionModel buildPartitionModel(Partition p, InfiniteCentroids c) { ... }
}
```

The View binds once (`partitionView.bindSignal(viewModel.partitionModel())`, `graphSC.bindSignal(viewModel.graphPoints())`,
`runButton.bindEnabled(viewModel.canRun())`) and forwards user actions as commands — it does not populate models or handle
events itself. When the VM's listener updates a signal, the bound component rebuilds automatically (reactive). The view
cancels its `future` in `onDetach(...)` and re-enables its Run button, ensuring no stray background task outlives the UI.

### 1.7 Concurrency: one single-threaded executor per UI/session

The algorithms have **not** been tested to run in parallel (a GLA trial resampling its own `VectorSet` can race
with another search on the same instance), so the UI must guarantee that a given *user* runs at most one long-running
search at a time — e.g. GLA Search and Local Search must not overlap.

- **One single-threaded executor per UI/session.** Each display submits its `CompletableFuture` to a single-threaded
  executor (`Executors.newSingleThreadExecutor(Thread.ofVirtual().factory())`) that lives either in the Vaadin UI or in the
  session scope (one-to-one with the session). Being single-threaded, searches on one UI are serialized automatically — this
  is what takes care of concurrency management. The executor is shut down on detach.
- **Per-button disable.** Each `run()` disables its own button via `button.setDisableOnClick(true)` and re-enables it when its
  task completes or is cancelled, so the user sees exactly which search is running. Other displays' buttons stay enabled; their
  searches simply queue behind the running one on the shared single-threaded executor.
- **Cancel on detach.** Each view holds its `Future<?>` and cancels it in `onDetach(...)`, ensuring no stray background task
  outlives the UI.

This keeps the "one search at a time" rule even if two displays are opened, without needing a separate gate helper.

---

## 2. MainLayout (AppLayout + SideNav)

Root layout rendered around every view via the Vaadin `@Layout` annotation on a single `MainLayout`.

**Skeleton:**

```
MainLayout extends AppLayout   (@Layout, route "")
├─ addToNavbar: DrawerToggle(), H1("BinClass")          id="app-title"
└─ addToDrawer: Scroller(SideNav)                        id="sidenav"
     └─ SideNav (id="sidenav")
        ├─ SideNavItem("GLA Search",      "gla")         → RouteGlaView
        ├─ SideNavItem("Local Search",    "local-search")→ RouteLocalSearchView
        ├─ SideNavItem("Cumulative Class.", "cumulative")→ RouteCumulativeView
        ├─ SideNavItem("Semi-Cumulative", "sclassify")   → RouteSemiCumulativeView
        ├─ SideNavItem("Fast Classify",   "fclassify")   → RouteFastClassifyView
        ├─ SideNavItem("Identify",        "identify")    → RouteIdentifyView
        ├─ SideNavItem("Mixture (EM)",    "mixture")     → RouteMixtureView
        ├─ SideNavItem("Bootstrap",       "bootstrap")   → RouteBootstrapView
        ├─ SideNavItem("Centroids",       "centroids")   → RouteCentroidView
        ├─ SideNavItem("Compare",         "compare")     → RouteCompareView
        ├─ SideNavItem("Function Plot",   "function")    → RouteFunctionView
        ├─ SideNavItem("Data Generator",  "generate")    → RouteGenerateView
        ├─ SideNavItem("Tree / Dendrogram","tree")       → RouteTreeView
        └─ SideNavItem("Cut / Interval",  "cut")         → RouteCutView
```

Notes:
- `SideNav` + `SideNavItem` are Vaadin 25.3 components; each maps to a `@Route` view. The string arg is the route value.
- `DrawerToggle` auto-appears on narrow viewports (responsive drawer).
- Report generation is **not** a top-level SideNav item — it is embedded as the `ReportComponent` in classifier displays' Report tabs (§17) because its output depends on whichever partition is currently displayed.

---

## 3. Display A — GLA Search (`ClassifyCommand`)

The flagship display. Mirrors `ClassifyCommand.execute()`: range search **and** automatic search,
controlled by the same switches. Produces a best partition + centroids and per-`k` SC values.

### 3.1 Algorithm entry points
- Automatic: `new AutomaticSearch(vectorSet, config).run()` → `Result(partition, centroids, scmin, kmin, lastk, double[] scs, Map<Integer,InfiniteCentroids> bestPerK)`. The `scs[]` array holds the per-`k` SC values (the automatic curve); `bestPerK` maps each `k` to its centroids.
- Range (fixed k): `new RangeSearch(vectorSet, config).run(kstart, kstop)` → `Result(partition, centroids)`. Note: range search's `Result` carries **no** `scs[]` array — it returns a single partition/centroids for the searched `k`, so the SC graph tab is populated from automatic search (or by re-running at successive fixed `k`s if a curve is desired). Confirm whether range-only runs need a "new best" hook to emit live snapshots.
- Search type selection (`determineSearchType`): `-n` → NAUTO; `-L` → LCENT (load centroids); `-b/-s` present → NAUTO; else AUTO.

### 3.2 Parameters → Vaadin control mapping

| Flag | Type | Control | Notes / default |
|------|------|---------|-----------------|
| filebase | String | `TextField` + upload | required |
| `-b` kstart | int | `IntegerField` min=1 | default 1 |
| `-s` kstop | int | `IntegerField` | 0 = auto (use kmax) |
| `-S` kstopwhen | int | `IntegerField` | max clusters to search |
| `-W` kcStopWhen | int | `IntegerField` | steps without SC improvement, default 5 |
| `-E` epsilon | double | `NumberField` 0<ε<0.5 | bare → decreasing-epsilon mode (checkbox toggle) |
| `-r` heuristic | enum | `Select` (1 GLA,2 SR,3 SA…) | default 1 |
| `-eX` alternateMode | int 1–4 | `RadioButtonGroup` / `Select` | worst-match/empty-cell-fix combos |
| `-m` analyseMissing | bool | `Checkbox` | |
| `-c` centroidType | enum | `Select` (1 CLASSIC …) | |
| `-l` logCentroids | bool | `Checkbox` | |
| `-J` jeffreysPrior | bool | `Checkbox` | |
| `-w` classWeights | bool | `Checkbox` | |
| `-t` trashcan | bool | `Checkbox` | |
| `-B` requireBetter/firstD | bool+double | `Checkbox` + `NumberField` | |
| `-C` bestCodeLength | bool | `Checkbox` | |
| `-R` roundedCentroids | bool | `Checkbox` | |
| `-f` distanceType | enum | `Select` (1 HAM … 8 SA) | default 5 (L1/CL hybrid) |
| `-n` maxIter | int | `IntegerField` | → NAUTO when set |
| `-j` heuristicCount | int | `IntegerField` | +1 internally |
| `-F` safetyLimit / `-a` iterBase | int | `IntegerField` | safety default 500 |
| `-L` centroidFile | String | `TextField` | LCENT search type |
| `-P` partitionFile | String | `TextField` | output path (optional in UI) |

The form-value record maps onto the confirmed `GLAConfig` fields: `epsilon`, `pnnThreshold`, `heuristic`, `alternateMode`, `centroidType`, `maxIter`, `safetyLimit`, `iterBase`, `kstopwhen`, `kcStopWhen`, `weights`, `rounded`, `jeffreysPrior`, `trashcan`, `analyseMissing`, `logCentroids`, plus the first-distance init (`firstD`) and any remaining fields. Verify the full field list against `GLAConfig` when building the translator.

### 3.3 EventBus events
Per-`k` `SearchProgress("gla", k, sc, false)` updates the SC curve tab after each `k`; whenever a new best partition is
detected a snapshot is pushed so the Partition tab refreshes live (not only on completion). Work is started by calling the
ViewModel's `run()` command (which submits to the single-threaded executor); the optional `SearchStarted` event may be posted
but is not required to trigger work. Progress events are consumed by the view's listener and pushed to the graph model; completion
sets the bound result signal so the graph/partition rebuild automatically.

### 3.4 UI skeleton

```
RouteGlaView extends VerticalLayout   (@Route("gla"))
├─ DataStore dataStore                 (session-scoped filebase→VectorSet)
├─ FormLayout paramsForm               id="gla-params"
│   ├─ TextField        filebase       id="filebase"
│   ├─ vaadin-upload  uploader         id="uploader-gla"      (writes temp filebase)
│   ├─ IntegerField     kStart         id="k-start"           min=1
│   ├─ IntegerField     kStop          id="k-stop"
│   ├─ IntegerField     kStopWhen      id="kstop-when"
│   ├─ IntegerField     kcStopWhen     id="kc-stop-when"
│   ├─ NumberField      epsilon        id="epsilon"           min>0 max<0.5
│   ├─ Checkbox         decreasingEps  id="decreasing-eps"
│   ├─ Select           distanceType   id="distance-type"
│   ├─ Select           centroidType   id="centroid-type"
│   ├─ Select           heuristic      id="heuristic"
│   ├─ RadioButtonGroup alternateMode  id="alternate-mode"
│   ├─ Checkbox         analyseMissing id="analyse-missing"
│   ├─ Checkbox         logCentroids   id="log-centroids"
│   ├─ Checkbox         jeffreysPrior  id="jeffreys-prior"
│   ├─ Checkbox         classWeights   id="class-weights"
│   ├─ Checkbox         trashcan       id="trashcan"
│   ├─ Checkbox         bestCodeLength id="best-code-length"
│   ├─ Checkbox         rounded        id="rounded-centroids"
│   ├─ Checkbox         requireBetter  id="require-better"
│   └─ Button           run            id="run-gla"
├─ TabSheet                   glaTabs      id="gla-tabs"     (Search | SC Graph | Partition)
│   ├─ "Search"  → Card searchCard          id="search-panel"
│   │   ├─ ProgressIndicator    progress      id="gla-progress"
│   │   └─ Div          statusText           id="gla-status"
│   ├─ "SC Graph" → graphSC (binclass-graph)  id="sc-graph"
│   └─ "Partition" → partitionView (binclass-partition)  id="partition-view"
```

**Flow (MVVM):** `#run-gla` click → view calls `viewModel.run()`. The ViewModel builds `GLAConfig` from its form-value record
(which maps to `GLAConfig`) and submits the search to the single-threaded executor, disabling the button with
`setDisableOnClick(true)`. Per-`k` progress events are collected into the graph model (`scs[]` → `GraphPoint(k, sc)`), so the SC
graph tab updates after each k; whenever a new best partition is detected the view builds its own view-specific `PartitionModel`
from the algorithm `Partition` and calls `partitionView.setData(PartitionModel)`, which rebuilds automatically. The SC Graph tab
shows the automatic-search curve with its global minimum (reuse `Graph`); the Partition tab renders the best partition via
`Partition` (clusters → `ClusterData`, lazy vectors). On completion/cancellation the button is re-enabled and the future is
cancelled in `onDetach(...`).

### 3.5 Translation layer
- `scs[]` + `ks[]` from `AutomaticSearch.Result` → `GraphModel(List<GraphPoint(k, sc))`; drop non-finite points.
- best `Partition`/`InfiniteCentroids` → view-specific `PartitionModel` (+ per-cluster `ClusterData`, rounded centroid for hover).
  The GLA display shapes clusters/vectors to match its partition tab; the cumulative display shapes them differently.

### 3.6 Testing
- **Unit:** build `GLAConfig` from a form-value record; verify flag→config mapping (esp. search-type selection, alternate-mode int code, decreasing-epsilon detection). Also unit-test the translation layer: verify `PartitionModel` is populated correctly from the algorithm objects (`Partition`/`InfiniteCentroids`) — cluster names, per-cluster `ClusterData`, and lazy `VectorData`.
- **BrowserlessTests:** drive the params form and assert the emitted `GLAConfig` is correct; fabricate a `SearchCompleted` event and assert `#sc-graph` accumulates points and the partition tab receives a model. (Graph / Partition rendering itself already covered in `binclass-components`.)

---

## 4. Display B — Local Search (`ClassifyCommand` `-r7`/`-r8`)

A focused view on heuristic local search (cycler `-r7`, adaptive `-r8`) which route through
`LocalSearch.localSearch()` instead of a single GLA variant. Shares the GLA parameter form; narrows scope to
heuristic-related flags and shows per-operator progress.

### 4.1 Algorithm entry point
- `GLAConfig` with `heuristic ∈ {2..6}` (split/join variants, replace-worst/smallest, random-swap) plus `lsCycler`/`lsAdaptive`. Reuse the GLA engine via `RangeSearch`/`AutomaticSearch` with those flags set.

### 4.2 Parameters → control
Reuse `GlaParametersForm`; add a `Select` for heuristic (2–6, or "cycler"/"adaptive") and a `IntegerField`
for `heuristicCount`. Drop the search-type controls (always range/automatic).

### 4.3 UI skeleton

```
RouteLocalSearchView extends VerticalLayout   (@Route("local-search"))
├─ FormLayout paramsForm            id="local-params"
│   ├─ (filebase + uploader)         id="filebase"
│   ├─ Select       heuristic        id="heuristic"          2..6, cycler, adaptive
│   ├─ IntegerField heuristicCount   id="heuristic-count"
│   ├─ NumberField  epsilon          id="epsilon"
│   ├─ Checkbox     jeffreysPrior    id="jeffreys-prior"
│   └─ Button       run              id="run-local"
├─ TabSheet             localTabs        id="local-tabs"      (Search | SC Graph | Partition)
│   ├─ "Search"  → Card searchCard          id="local-search-panel"
│   │   └─ ProgressIndicator progress         id="local-progress"
│   ├─ "SC Graph" → graphSC                        id="sc-graph"
│   └─ "Partition" → partitionView              id="partition-view"
```

### 4.6 Testing
- **Unit:** heuristic→config mapping; cycler/adaptive flags propagate.
- **BrowserlessTests:** drive the params form and assert the emitted `GLAConfig` carries the chosen heuristic plus `lsCycler`/`lsAdaptive`; verify *Run* disables while a search is in flight and re-enables on completion. (SC graph / partition rendering already covered in `binclass-components`.)
- **Playwright:** run a local search, assert status text mentions the chosen strategy and graph renders.

---

## 5. Display C — Cumulative Classification (`CumulativeCommand`)

Processes vectors one at a time; each may create a new class or join an existing one, using Bayesian
predictive identification with SC as criterion. Output is a `DynamicPartition` (grows over time).

### 5.1 Algorithm entry point
- `CumulativeClassifier.doCumulativeClassification(vectorSet, CumulativeConfig)` → `DynamicPartition`.

### 5.2 Parameters → control (from `CumulativeConfig`)

| Flag | Type | Control | Default |
|------|------|---------|---------|
| delta `-D`/`-d` | int | `IntegerField` | 1 |
| fixedDelta | bool | derived from `-D` presence | — |
| cumulativeAnalysis `-N` | bool | `Checkbox` | false |
| cumulativeSamples `-s` | int | `IntegerField` | 0 (unbounded) |
| bayesianPredictive (`!-S`) | bool | `Checkbox` (default on, toggle off) | true |
| testFeatureSignificance `-F` | bool | `Checkbox` | false |
| cumSaveByPf (`!-c`) | bool | `Checkbox` (default on) | true |
| cumNoNewClasses `-n` | bool | `Checkbox` | false |
| cumulativeInOrder `-O` | bool | `Checkbox` | false |
| epsilon `-E` | double | `NumberField` | EPSILON |

### 5.3 EventBus events
Cumulative classification runs fast, so pushing an update after every processed vector is too frequent — the view
shows a single **indeterminate** progress bar for the whole run and does not update it per vector. The ViewModel posts
`SearchCompleted("cumulative", PartitionResult)` (and `SearchError`) only; no per-item `SearchProgress` events are needed.
Work is started by the ViewModel's `run()` command (a `CompletableFuture` on the single-threaded executor); the optional
`SearchStarted` event may be posted but is not required. The existing agnostic `Partition` web component renders the final
dynamic partition directly — no new `DynamicPartitionModel`/component is needed.

### 5.4 UI skeleton

```
RouteCumulativeView extends VerticalLayout   (@Route("cumulative"))
├─ FormLayout paramsForm            id="cumulative-params"
│   ├─ TextField        filebase     id="filebase"
│   ├─ IntegerField     delta        id="delta"             min=1
│   ├─ Checkbox         fixedDelta   id="fixed-delta"
│   ├─ Checkbox         cumulativeAnalysis id="cum-analysis"
│   ├─ IntegerField     cumSamples   id="cum-samples"
│   ├─ Checkbox         bayesian     id="bayesian-predictive"
│   ├─ Checkbox         featureSig   id="feature-significance"
│   ├─ Checkbox         saveByPf     id="save-by-pf"
│   ├─ Checkbox         noNewClasses id="no-new-classes"
│   ├─ Checkbox         inOrder      id="in-order"
│   ├─ NumberField      epsilon      id="epsilon"
│   └─ Button           run          id="run-cumulative"
├─ ProgressIndicator progress         id="cumulative-progress"   (indeterminate while running)
├─ Div          logText               id="cumulative-log"     (sanitized with JSoup)
└─ Partition partitionView            id="partition-view"    (final DynamicPartition)
```

### 5.6 Testing
- **Unit:** `CumulativeConfig` mapping from a form record; `defaults()` sanity. Also unit-test the translation layer: verify `PartitionModel` is populated correctly from the algorithm `DynamicPartition` — cluster names, per-cluster `ClusterData`, and lazy `VectorData`.
- **BrowserlessTests:** drive the params form and assert the emitted `CumulativeConfig` is correct; fabricate `SearchCompleted` and assert the partition tab receives the dynamic partition model. (Partition rendering already covered in `binclass-components`.)

---

## 6. Display D — Semi-Cumulative Classification (`SemiCumulativeCommand`, `sclassify`)

Join-GLA search: grow from a 2-cluster seed using join operations with SC selection.

### 6.1 Algorithm entry point
- `JoinGLA.joinGLA(vectorSet, scmin[], scs[], glaConfig)` → `Partition`; returns optimal k + min SC via the arrays.

### 6.2 Parameters → control

| Flag | Type | Control | Default |
|------|------|---------|---------|
| `-A` useAbsMatch | bool | `Checkbox` | false |
| `-E` epsilon | double | `NumberField` | 0.001 (bare → decreasing) |
| `-J` jeffreysPrior | bool | `Checkbox` | false |
| `-j` joinTarget | int | `IntegerField` | 2 |
| `-T` glaThreshold | double | `NumberField` | 1.0 |

### 6.4 UI skeleton

```
RouteSemiCumulativeView extends VerticalLayout   (@Route("sclassify"))
├─ FormLayout paramsForm            id="sclassify-params"
│   ├─ TextField        filebase     id="filebase"
│   ├─ Checkbox         useAbsMatch  id="use-abs-match"
│   ├─ NumberField      epsilon      id="epsilon"
│   ├─ Checkbox         jeffreysPrior id="jeffreys-prior"
│   ├─ IntegerField     joinTarget   id="join-target"
│   ├─ NumberField      glaThreshold id="gla-threshold"
│   └─ Button           run          id="run-sclassify"
├─ ProgressIndicator progress         id="sclassify-progress"
├─ Graph graphSC                        id="sc-graph"       (scs[] vs k)
└─ Partition partitionView            id="partition-view"
```

### 6.6 Testing
- **Unit:** `GLAConfig` build for join-GLA (rounded=true, heuristicCount=joinTarget).
- **BrowserlessTests:** drive the params form and assert the emitted `GLAConfig` is correct; fabricate a `SearchCompleted` event and assert the partition tab receives k = optimal. (SC graph / Partition rendering already covered in `binclass-components`.)

---

## 7. Display E — Fast Classify / Split-GLA (`FastClassifyCommand`, `fclassify`)

Split-GLA hybrid for a fast k search seeded from the first two vectors.

### 7.1 Algorithm entry point
- `SplitGLA.splitGLA(vectorSet, scmin[], scs[], config)` → `Partition`.

### 7.2 Parameters → control

| Flag | Type | Control | Default |
|------|------|---------|---------|
| `-A` useAbsMatch | bool | `Checkbox` | false |
| `-S` kstopwhen | int | `IntegerField` | max clusters to search |
| `-J` jeffreysPrior | bool | `Checkbox` | false |
| `-E` epsilon | double | `NumberField` 0<ε<0.5 | 0.001 |

### 7.4 UI skeleton (same shape as Display D)

```
RouteFastClassifyView extends VerticalLayout   (@Route("fclassify"))
├─ FormLayout paramsForm            id="fclassify-params"
│   ├─ TextField        filebase     id="filebase"
│   ├─ Checkbox         useAbsMatch  id="use-abs-match"
│   ├─ IntegerField     kStopWhen    id="kstop-when"
│   ├─ Checkbox         jeffreysPrior id="jeffreys-prior"
│   ├─ NumberField      epsilon      id="epsilon"
│   └─ Button           run          id="run-fclassify"
├─ ProgressIndicator progress         id="fclassify-progress"
├─ Graph graphSC                        id="sc-graph"
└─ Partition partitionView            id="partition-view"

### 7.6 Testing
- **Unit:** `GLAConfig` build for split-GLA from a form-value record; useAbsMatch / kstopwhen / epsilon mapping.
- **BrowserlessTests:** drive the params form and assert the emitted config is correct; fabricate a `SearchCompleted` event and assert the partition tab receives a model. (Graph / Partition rendering already covered in `binclass-components`.)
```

---

## 8. Display F — Identify nearest centroid (`IdentifyCommand`)

Classify a fresh set of vectors against an existing centroid model (no search). Useful as a "classify new
samples" panel embedded in other displays, but also standalone.

### 8.1 Algorithm entry point
- `Classifier.identifyVectors(vectorSet, partition, centroids, epsilon)` (Shannon codelength) or
  `Classifier.identifyVectorsFast(vectorSet, partition, centroids)` (Hamming).

### 8.2 Parameters → control

| Flag | Type | Control | Default |
|------|------|---------|---------|
| `-E` epsilon | double | `NumberField` <0.5 | 0.001 |
| `-f` distanceType | enum | `Select` (1 HAM, else Shannon) | 1 |
| `-J` jeffreysPrior | bool | `Checkbox` | false |
| `-w` classWeights | bool | `Checkbox` | false |
| `-t` trashcan | bool | `Checkbox` | false |
| `-M` exactMatchesOnly | bool | `Checkbox` | false |

### 8.4 UI skeleton

```
RouteIdentifyView extends VerticalLayout   (@Route("identify"))
├─ FormLayout paramsForm            id="identify-params"
│   ├─ TextField        filebase     id="filebase"          (vectors to classify)
│   ├─ TextField        centroidFile id="centroid-file"     (existing model, -L style)
│   ├─ NumberField      epsilon      id="epsilon"
│   ├─ Select           distanceType id="distance-type"
│   ├─ Checkbox         jeffreysPrior id="jeffreys-prior"
│   ├─ Checkbox         classWeights id="class-weights"
│   ├─ Checkbox         trashcan     id="trashcan"
│   ├─ Checkbox         exactMatches id="exact-matches"
│   └─ Button           run          id="run-identify"
├─ Partition partitionView            id="partition-view"    (assigned classes)
└─ Div          logText               id="identify-log"      (JSoup-sanitized)
```

### 8.6 Testing
- **Unit:** distanceType→`identifyVectors`/`Fast` branch; epsilon validation.
- **BrowserlessTests:** drive the params form and assert the emitted config is correct; fabricate a result and assert the partition tab receives the assigned classes. (Partition rendering already covered in `binclass-components`.)

---

## 9. Display G — Mixture Classifier / EM (`MixtureCommand`)

Fit a Gaussian-mixture-like (Bernoulli) model via EM and return fitted centroids.

### 9.1 Algorithm entry point
- `MixtureClassifier.applyMixtureClassifier(vectorSet, centroids, sampleMixture)` → `InfiniteCentroids`.

### 9.2 Parameters → control

| Flag | Type | Control | Default |
|------|------|---------|---------|
| `-E` epsilon | double | `NumberField` | 0.001 |
| `-k` mixtureClasses | int | `IntegerField` (+1 internally) | 2 |
| `-s` sampleMixture | int | `IntegerField` | 2 |

### 9.4 UI skeleton

```
RouteMixtureView extends VerticalLayout   (@Route("mixture"))
├─ FormLayout paramsForm            id="mixture-params"
│   ├─ TextField        filebase     id="filebase"
│   ├─ NumberField      epsilon      id="epsilon"
│   ├─ IntegerField     mixtureClasses id="mixture-classes"
│   ├─ IntegerField     sampleMixture  id="sample-mixture"
│   └─ Button           run          id="run-mixture"
├─ ProgressIndicator progress         id="mixture-progress"
└─ Centroid centroidView              id="centroid-view"      (fitted centroids)
```

### 9.6 Testing
- **Unit:** `-k`+1 mapping; sampleMixture default.
- **BrowserlessTests:** drive the params form and assert the emitted config is correct; fabricate fitted centroids and assert the centroid view receives them. (Centroid rendering already covered in `binclass-components`.)

---

## 10. Display H — Bootstrap Analysis (`BootstrapCommand`)

Run many GLA trials from resampled data and statistically analyse SC values; keep the best partition.

### 10.1 Algorithm entry point
- `BootstrapCommand.runBootstrapTrials(...)` + `performStatisticalAnalysis(scValues, nTrials, bootstrapI)`. Internally calls `GLAEngine.gla/glaSr/glaSa` per trial via `GLAConfig.heuristic()`.

### 10.2 Parameters → control

| Flag | Type | Control | Default |
|------|------|---------|---------|
| `-v` vecsToGen | int | `IntegerField` | — |
| `-P` saveBestBoots | bool | `Checkbox` | false |
| `-J` jeffreysPrior | bool | `Checkbox` | false |
| `-r` heuristic | enum | `Select` (1/2/3) | 1 |
| `-N` bootstrapSize | int | `IntegerField` | 50 |
| `-K` bootstrapK | int | `IntegerField` (+1) | — |
| `-E` epsilon | double | `NumberField` | see parseEpsilon |
| `-I` bootstrapI | int | `IntegerField` | parsed separately |
| `-w` classWeights | bool | `Checkbox` | false |
| `-c` centroidType | enum | `Select` | CLASSIC |

### 10.4 UI skeleton

```
RouteBootstrapView extends VerticalLayout   (@Route("bootstrap"))
├─ FormLayout paramsForm            id="bootstrap-params"
│   ├─ TextField        filebase     id="filebase"
│   ├─ IntegerField     vecsToGen    id="vecs-to-gen"
│   ├─ Checkbox         saveBestBoots id="save-best-boots"
│   ├─ Checkbox         jeffreysPrior id="jeffreys-prior"
│   ├─ Select           heuristic    id="heuristic"          1/2/3
│   ├─ IntegerField     bootstrapSize id="bootstrap-size"
│   ├─ IntegerField     bootstrapK   id="bootstrap-k"
│   ├─ NumberField      epsilon      id="epsilon"
│   ├─ IntegerField     bootstrapI   id="bootstrap-i"
│   ├─ Checkbox         classWeights id="class-weights"
│   └─ Button           run          id="run-bootstrap"
├─ ProgressIndicator progress         id="bootstrap-progress"
├─ Div          statsText            id="bootstrap-stats"    (JSoup-sanitized)
├─ Graph graphSC                        id="sc-graph"         (SC distribution per k)
└─ Partition partitionView            id="partition-view"     (best partition)
```

### 10.6 Testing
- **Unit:** `GLAConfig` build for bootstrap; trial-count = max(1, size).
- **BrowserlessTests:** drive the params form and assert the emitted config is correct; fabricate results and assert stats text updates and the best partition tab receives a model. (Graph / Partition rendering already covered in `binclass-components`.)

---

## 11. Display I — Centroid Manager (`CentroidCommand`)

Compute centroids from an existing `.partition` file (per-bit frequency average), optionally rounded.

### 11.1 Algorithm entry point
- `GLAEngine.recomputeCentroids(partition, centroids, rounded, n)` + `InfiniteCentroids(k, length)`. Reads partition via `PartitionReader.readPartition(filebase)`.

### 11.2 Parameters → control
| Flag | Type | Control | Default |
|------|------|---------|---------|
| `-R` roundedCentroids | bool | `Checkbox` | false |

### 11.4 UI skeleton

```
RouteCentroidView extends VerticalLayout   (@Route("centroids"))
├─ FormLayout paramsForm            id="centroid-params"
│   ├─ TextField        filebase     id="filebase"          (partition filebase)
│   ├─ Checkbox         rounded      id="rounded-centroids"
│   └─ Button           run          id="run-centroids"
├─ Div          summaryText          id="centroid-summary"  (JSoup-sanitized)
└─ Grid centroidsGrid                 id="centroids-grid"    (probability table, Vaadin stock grid)
```

Notes: a simple probability table is enough here — use the stock `Grid` rather than the custom `Centroid`
web component (the web component exists for partition rendering; this display shows one centroid at a time).
If visual fidelity matters, reuse `Centroid` per row.

### 11.6 Testing
- **Unit:** recompute centroids match frequency averages; rounding behaviour.
- **BrowserlessTests:** drive the params form and assert the emitted config is correct; fabricate a result and assert the grid populates with k × length cells in [0,1]. (Stock Vaadin `Grid`, not a custom component.)

---

## 12. Display J — Compare Partitions (`CompareCommand`)

Build a contingency matrix between two partitions and compute the nearness distance.

### 12.1 Algorithm entry point
- `PartitionComparator.comparePartitions(p1, p2, printMode, exactMatches)` → `ComparisonResult(matrix, distance, printMode)` with `.render()`. Reads `<filebase>.partition1` / `.partition2`.

### 12.2 Parameters → control

| Flag | Type | Control | Default |
|------|------|---------|---------|
| `-M` exactMatchesOnly | bool | `Checkbox` | false |
| `-V` printMode | enum 1–3 | `Select` (1 nearness, 2 totalfreq, 3 partition) | 1 |

### 12.4 UI skeleton

```
RouteCompareView extends VerticalLayout   (@Route("compare"))
├─ FormLayout paramsForm            id="compare-params"
│   ├─ TextField        filebase     id="filebase"          (…partition1 / …partition2)
│   ├─ Checkbox         exactMatches id="exact-matches"
│   ├─ Select           printMode    id="print-mode"        1/2/3
│   └─ Button           run          id="run-compare"
├─ Div          distanceText         id="compare-distance"  (JSoup-sanitized)
└─ Table matrixTable                  id="compare-matrix"    (contingency table, HTML <table>)
```

**Why `Table` not `Grid`:** a contingency matrix is dense and square-ish with both row and column headers; an HTML
`<table>` keeps cells aligned and reads as a real matrix. `Table` renders plain HTML so it inherits nothing from the theme —
style it in `styles.css` (see §18.5). Build rows via `matrixTable.getBody().addRow()` with header/data cells, or reuse the
structured `ComparisonResult(matrix, …)` rather than stringifying it.

### 12.6 Testing
- **Unit:** printMode validation; matrix indexing (`matrix[p2-1][p1-1]`); distance value vs CLI output.
- **BrowserlessTests:** drive the params form and assert the emitted config is correct; fabricate a `ComparisonResult` and assert distance text and the contingency table (`matrixTable`) populates with the right rows/columns. (HTML `<table>` via `Table`, styled in CSS — not a custom component, not `Grid`.)

---

## 13. Display K — Function Plot / SC vs k (`FunctionCommand`)

Render SC (and Shannon entropy) as a function of k from a `.centroids` file. Naturally reuses the `Graph`
component; this display is the consumer that feeds it.

### 13.1 Algorithm entry point
- `InfoFunctions.calculateFunctions(records, vectors, distanceType, classWeights)` → String table. Reads centroids via `CentroidReader.loadAll(ctrfile)`.

### 13.2 Parameters → control

| Flag | Type | Control | Default |
|------|------|---------|---------|
| `-w` classWeights | bool | `Checkbox` | false |
| `-f` distanceType | enum | `Select` (default 5) | 5 |

### 13.4 UI skeleton

```
RouteFunctionView extends VerticalLayout   (@Route("function"))
├─ FormLayout paramsForm            id="function-params"
│   ├─ TextField        filebase     id="filebase"          (…centroids expected)
│   ├─ Checkbox         classWeights id="class-weights"
│   ├─ Select           distanceType id="distance-type"
│   └─ Button           run          id="run-function"
├─ Graph graphSC                        id="sc-graph"        (SC vs k)
└─ Div          tableText              id="function-table"    (JSoup-sanitized raw output)
```

### 13.6 Testing
- **Unit:** `calculateFunctions` produces expected rows for a small centroid set.
- **BrowserlessTests:** drive the params form and assert the emitted config is correct; fabricate function output and assert the graph accumulates points and table text updates. (Graph rendering already covered in `binclass-components`.)

---

## 14. Display L — Data Generator (`GenerateCommand`)

Produce synthetic binary vectors (RAND / MARKOV / BERN) from the reference data. Output feeds other displays.

### 14.1 Algorithm entry point
- `DataGenerator.vectorGen(vectorSet, n)` (RAND), `.markovGen(...)` (MARKOV), `.bernoulliGen(partition, n)` (BERN). Write via `VectorWriter.writeWithHeader`.

### 14.2 Parameters → control

| Flag | Type | Control | Default |
|------|------|---------|---------|
| `-v` vecsToGen | int | `IntegerField` | — |
| `-u` uniqueVectors | bool | `Checkbox` | false |
| `-G` dataGenType | enum 1–3 | `RadioButtonGroup` (RAND/MARKOV/BERN) | 1 |

### 14.4 UI skeleton

```
RouteGenerateView extends VerticalLayout   (@Route("generate"))
├─ FormLayout paramsForm            id="generate-params"
│   ├─ TextField        filebase     id="filebase"
│   ├─ IntegerField     vecsToGen    id="vecs-to-gen"
│   ├─ Checkbox         uniqueVectors id="unique-vectors"
│   ├─ RadioButtonGroup dataGenType  id="data-gen-type"      1/2/3
│   └─ Button           run          id="run-generate"
├─ Div          summaryText          id="generate-summary"   (JSoup-sanitized)
└─ Grid vectorsGrid                   id="generate-preview"   (first N vectors as cells)
```

### 14.6 Testing
- **Unit:** each generator type produces `n` vectors of correct length; unique flag respected.
- **BrowserlessTests:** drive the params form and assert the emitted config is correct; fabricate generated vectors and assert the preview grid populates with expected rows/columns. (Stock Vaadin `Grid`, not a custom component.)

---

## 15. Display M — Tree / Dendrogram (`TreeCommand`)

Build a hierarchical dendrogram from a partition (or by classifying raw vectors when none exists). Reuses the
existing `Tree` web component and `TreeModel`.

### 15.1 Algorithm entry point
- With partition: `TreeBuilder.makeTreePnn(partition, centroids, jeffreysPrior)` / `makeTreePnn2(...)` (Hellinger vs class-nearness). Reads partition via `ReportCommand.readPartition` fallback; else classify each vector into its own cluster.

### 15.2 Parameters → control

| Flag | Type | Control | Default |
|------|------|---------|---------|
| `-H` useHellinger | enum 1/2/4 | `Select` | 0 (class-nearness) |
| `-J` jeffreysPrior | bool | `Checkbox` | false |

### 15.4 UI skeleton

```
RouteTreeView extends VerticalLayout   (@Route("tree"))
├─ FormLayout paramsForm            id="tree-params"
│   ├─ TextField        filebase     id="filebase"
│   ├─ Select           useHellinger id="use-hellinger"      0/1/2/4
│   ├─ Checkbox         jeffreysPrior id="jeffreys-prior"
│   └─ Button           run          id="run-tree"
├─ Scroller treeScroller              id="tree-scroller"     (scroll container)
└─ Tree dendrogram                    id="dendrogram"        (Tree component)
```

### 15.6 Testing
- **Unit:** translation flattens `TreeNode` into indexed `TreeModel`; leaf names = most-common strain; joint sizes summed.
- **BrowserlessTests:** drive the params form and assert the emitted config is correct (useHellinger enum, jeffreysPrior); fabricate a tree model and assert the dendrogram view updates when fabricated events arrive. (Tree web component SVG rendering already covered in `binclass-components`.)

---

## 16. Display N — Cut / Interval Analysis (`CutCommand`)

Trim/partition via interval intersection strategies (relative/minimal/maximal/stability). Reuses `Partition`.

### 16.1 Algorithm entry point
- Dispatch in `CutCommand.runAnalysis(...)`: `performAnalyseIntStab` → `CutEngine.simpleIntersection`; `minimalCut` → `CutEngine.minimalIntersection`; standard cut fallback. All operate on generated partitions from the same `VectorSet`.

### 16.2 Parameters → control

| Flag | Type | Control | Default |
|------|------|---------|---------|
| `-r` relativeInt | bool | `Checkbox` | — |
| `-s` minimalInt | bool | `Checkbox` | — |
| `-m` maximalInt | bool | `Checkbox` | — |
| `-A` analyseStab | bool | `Checkbox` (+ kstart) | — |
| `-a` analyseInt | bool | `Checkbox` (+ kstart) | — |
| `-D`/`-d` delta | double | `NumberField` | 0 |

### 16.4 UI skeleton

```
RouteCutView extends VerticalLayout   (@Route("cut"))
├─ FormLayout paramsForm            id="cut-params"
│   ├─ TextField        filebase     id="filebase"
│   ├─ Checkbox         relativeInt  id="relative-int"
│   ├─ Checkbox         minimalInt   id="minimal-int"
│   ├─ Checkbox         maximalInt   id="maximal-int"
│   ├─ Checkbox         analyseStab  id="analyse-stab"
│   ├─ IntegerField     kStart1      id="kstart-1"
│   ├─ Checkbox         analyseInt   id="analyse-int"
│   ├─ IntegerField     kStart2      id="kstart-2"
│   ├─ NumberField      delta        id="delta"
│   └─ Button           run          id="run-cut"
├─ Div          summaryText          id="cut-summary"       (JSoup-sanitized)
└─ Partition partitionView            id="partition-view"    (resulting partition)
```

### 16.6 Testing
- **Unit:** each strategy branch returns a valid partition; intersection reduces cluster count as expected.
- **BrowserlessTests:** drive the params form and assert the emitted config is correct; fabricate a result and assert the partition tab receives the resulting partition. (Partition rendering already covered in `binclass-components`.)

---

## 17. Embedded — Report Generation (`ReportCommand`, no separate display)

Per the request, *report* is not a top-level SideNav item. Its output (frequency tables, class-nearness
matrix, summary stats) belongs to whichever classifier produced the partition. Provide a **Report tab** on the
classifier displays that share a partition (GLA Search, Local Search, Semi-Cumulative, Fast Classify, Cut).

### 17.1 Algorithm entry point
- `ReportOptions` is a record whose boolean fields map to the four flags in §17.2; it exposes `showTotalFrequencies()`, `showPerClassFrequencies()` and `showNearness()` (plus a `DEFAULT`). Pass one into `ReportGenerator.generateReport(partition, centroids, ReportOptions)` → String.
- Recompute centroids via `GLAEngine.recomputeCentroids`.

### 17.2 Parameters → control (per display's Report tab)

| Flag | Type | Control | Default |
|------|------|---------|---------|
| `-d` printDigits | bool | `Checkbox` | false |
| `-a` affinityMatrix | bool | `Checkbox` | false |
| `-h` useHellinger | bool | `Checkbox` | false |
| `-p` reportParams | int bitmask | `IntegerField` / checkboxes | 0 (all) |

### 17.4 Report component (reused across classifier displays)

Per your request, report is implemented as a **component** that each classifier display embeds in its own
Report tab — not inline markup copied per view. The component owns the four flags and renders sanitized output;
a display just adds one instance to its `TabSheet`.

```
ReportComponent (extends VerticalLayout)
id="report-component"
├─ Checkbox     printDigits    id="print-digits"
├─ Checkbox     affinityMatrix id="affinity-matrix"
├─ Checkbox     useHellinger   id="use-hellinger"
├─ IntegerField reportParams  id="report-params"
├─ Table        matrixTable     id="report-affinity"    (nearness/affinity table, HTML <table>)
└─ Div          reportText     id="report-text"    (JSoup-sanitized frequency tables / text)
```

Report text is rendered as sanitized content (`SoupParser`/JSoup content mode, per Agents.md) so the raw
report string cannot inject markup. When nearness or affinity output is requested, render it as a real HTML
`<table>` via `Table` (styled in CSS, see §18.5) instead of only sanitized text — a square class-nearness or
affinity matrix reads far better as aligned cells than as a monospace string block. The component recomputes
centroids from the partition it is bound to and regenerates on flag change; each display binds it to its own
currently displayed partition.

---

## 18. Cross-cutting concerns

### 18.1 Routing & `@Layout`
- `MainLayout extends AppLayout` annotated `@Layout`; every display view is a `@Route("…")` `VerticalLayout`.
- Mobile drawer handled by `AppLayout`/`DrawerToggle`; desktop shows the `SideNav`.
- **Vaadin 25 component swaps:** `Panel` was removed in Vaadin 25 — use `Card` for grouped-content containers (the direct replacement used in these skeletons). `Tabs`/`Tab` are replaced by the single `TabSheet` component, which owns its own tab strip and auto-switches content areas (`tabSheet.add("Label", component)`); it ships in `vaadin-core`, so no extra dependency is required.
- **Theme:** the UI uses the **Aura** theme (see the Aura skill / `Config.java`), not Lumo.
- **Electron / desktop shell:** the app is ultimately wrapped in an Electron frame as a desktop application, so there is **no login screen and no access control** — routing is flat (SideNav → view) and each session/UI owns its own state. This also means per-user concurrency (§1.7) maps naturally to one Electron window = one Vaadin UI = one `SearchGate`/thread pool.

### 18.2 Reactive model binding & single active search
Follow the existing pattern in `binclass-components`: bind whole presentation objects through a Vaadin local
`ValueSignal<T>` and rebuild on change (see `Graph.setData` / `PartitionModel` binding). Progress updates push
scalar events; final results set the bound signal so graph/partition/tree rebuild automatically. Each display's
`running` signal drives its own progress UI, but the **per-user search gate** (§1.7) is what disables *Run* across
all displays while any one search is in flight — this is the observable effect of "GLA and Local Search cannot run
at the same time".

### 18.3 Sanitization
Display raw report/function strings through JSoup content mode (`org.jsoup.Jsoup.parse(...).text()` or a
`ComponentRenderer`) to keep user-provided data safe (Agents.md rule).

### 18.4 Nullability & records
- `@NullMarked` on public view classes and translators; `@Nullable` for optional fields.
- Form values → immutable records; EventBus events are records; translation helpers static.

### 18.5 Design system

The skeletons define *structure*; this section defines *how each view looks*. It is a plan of decisions, not yet applied CSS — the concrete values live in `binclass-ui/src/main/resources/META-INF/resources/styles.css` (to be created) and are imported by `Config.java`. Current state: `Config.java` applies Aura via `@StyleSheet(Aura.STYLESHEET)` with **no** `@ColorScheme`, so the app renders in Aura's default light scheme only. Add a `styles.css` here and, if dark mode is wanted later, an `@ColorScheme("light dark")` on `Config`.

Decisions below are grouped by how they map to Aura tokens (see the `aura-theme` skill / Vaadin 25.3 docs). Values marked **for approval** can be changed before implementation; everything else is a firm convention so every view looks consistent.

#### Color scheme & accent (firm)
- **Scheme:** light only for now (`color-scheme: light` implied by default). If dark mode is added later, switch to `light dark` and set `--aura-content-color-scheme` on the App Layout content area so the nav stays dark while views follow the OS.
- **Accent:** keep Aura's default blue (`--aura-accent-color-light` / `--aura-accent-color-dark`, both `var(--aura-blue)`). BinClass is a scientific tool; the neutral accent keeps tables, graphs and partitions legible without a strong brand color. To rebrand, set only those two tokens — buttons, selection and focus ring all derive from it.
- **Contrast:** leave `--aura-contrast-level` at its default (`1`) so text/borders meet WCAG 2.1; raise to `2` only if a higher-contrast look is desired.

#### Surfaces & spacing (firm)
- **Cards:** the tabbed classifier views use `Card`; give them subtle elevation with `--aura-surface-level: 0.5` and rounded corners via `--aura-base-radius` (default `3`). Avoid heavy shadows — this is a data tool, not marketing.
- **Spacing scale:** use a single gap unit across every view so layouts feel uniform. Prefer `gap` on `VerticalLayout`/`FormLayout` in multiples of `8px` (`8 / 12 / 16`) rather than ad-hoc padding. Keep the params form and its results separated by at least `16px`.
- **Density:** keep the default base size (`--aura-base-size: 16`). BinClass forms are long (GLA has ~20 flags); comfortable spacing beats cramming them in.
- **Tables (§12, §17):** `Table` renders a plain HTML `<table>` and inherits nothing from Aura, so give it an explicit look via a class hook (`matrix-table`) in `styles.css`: `border-collapse: collapse`, 1px borders on `th/td` using `var(--vaadin-border-color)`, padding from `--vaadin-padding-xs/s`, and right-aligned numeric cells. Sticky the first header cell (`position: sticky; left: 0`) so wide contingency/affinity matrices stay readable while scrolling.

#### Typography (firm)
- **Font family:** set a single modern sans-serif globally, e.g. `--vaadin-font-family: "Inter", system-ui, sans-serif` (or another from the skill's list). Do not vary fonts per view.
- **Hierarchy:** section titles inside a view use Vaadin `H2`; tab labels and field groups stay at default body size. Keep label text on form fields at the default — do not shrink them to fit more flags.

#### Per-view visual notes (where they differ from the shared defaults)
Most views inherit the conventions above unchanged. Only these need extra attention:
- **GLA Search (§3):** three-tab `TabSheet` (Search | SC Graph | Partition). The SC Graph tab should give the `Graph` a fixed aspect ratio and center its legend; the Partition tab should show a compact header (cluster count, k) above the component. This is the flagship — spend design effort here first.
- **Tree / Dendrogram (§15):** the `Tree` SVG can be wide; wrap it in the scroll container (`Scroller`) with a minimum height so the dendrogram is visible before scrolling. Consider a light surface behind it for contrast.
- **Semi-Cumulative / Fast Classify (§6, §7):** the SC curve is the hero — give `#sc-graph` generous width and ensure the global-minimum point reads clearly (reuse the Graph legend/annotation conventions from GLA).
- **Report tab (§17):** render sanitized report text in a monospace-friendly block with left alignment so frequency tables stay aligned; keep it on the shared surface.

#### Illustrations (for approval)
The skeletons are sufficient for routing and testing anchors, but one rendered mockup per flagship view would align everyone faster than prose. Recommended minimum:
- **GLA Search** — full tabbed layout with params form + SC graph + partition header.
- **Tree / Dendrogram** — scrollable canvas showing a small dendrogram on a surface.
These can be screenshots of the running Vaadin view once §3 and §15 are implemented, or clean wireframes now. Add more (Graph-heavy views) only if layout decisions prove ambiguous during implementation.

### 18.6 Testing matrix (per Agents.md)

Algorithms are **non-deterministic**, so the test strategy targets *UI functions* rather than full end-to-end integration:

- **JUnit 6 — model logic.** Every translator + form→config mapping + search-type selection logic; e.g. verify that a
  `PartitionModel` is produced correctly from its algorithm counterparts (`Partition`/`InfiniteCentroids`, `scs[]` →
  `GraphPoint`). Also the per-display config-record builders and any deterministic rule (`canRun()`, gate helper). These are
  plain unit tests on UI-free classes.
- **BrowserlessTests — UI functions.** Each display's view with mock data (server-scoped demo views, like `binclass-components`
  demo views) asserts *UI behaviour*, not a full integration run: e.g. form input emits a correctly populated config record;
  *Run* stays disabled while a search is in flight and re-enables on completion/cancellation. Two useful techniques:
  - **Fabricate EventBus events:** post fabricated `SearchProgress`/`SearchCompleted` records to the singleton `EventBus.get()`
    and assert the view updates accordingly — e.g. the Partition tab's component receives a new model, the SC graph accumulates
    points, etc. This exercises the reactive binding without depending on non-deterministic algorithm output.
  - **Form → config:** drive the form controls directly and assert the emitted config record matches expectations.
- **Playwright *IT — custom web components only.** No longer needed to test the custom components themselves (those are already
  covered in `binclass-components`); reserve Playwright IT for anything that genuinely requires a browser beyond component behaviour,
  if at all.

Per-display testing notes (§3.6 … §16.6) follow this split: model/config assertions go to JUnit 6, UI-function checks (including
fabricated-event updates) go to BrowserlessTests, and Playwright IT is used sparingly for custom-component behaviour that the
`binclass-components` module does not already cover.

### 18.7 Implementation order (recommended)
1. `MainLayout` + GLA Search display (§3) — establishes EventBus flow, translation layer, and demo view for tests.
2. Report tab embedded in GLA (§17), then copy to other classifier displays.
3. Local Search (§4), Semi-Cumulative (§6), Fast Classify (§7) — reuse `GlaParametersForm`.
4. Cumulative (§5), Identify (§8), Mixture (§9), Bootstrap (§10).
5. Centroids (§11), Compare (§12), Function Plot (§13), Data Generator (§14), Tree (§15), Cut (§16).

Each chapter is self-contained: implement, add its JUnit + Playwright tests, merge, then move on.
