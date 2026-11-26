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

## 0a. Implementation status (read first)

This document is both a **plan** and, where noted, a record of what is already built. The UI currently ships as a
**skeleton**: every display is a `@Route` view that extends `VerticalLayout` and renders an `H1` title plus a single
placeholder `Span` body (`id="<route>-body"`). There are no `ViewModel`s, `FormLayout` parameter forms, `TabSheet`s,
`Graph`/`Partition` components or `EventBus` wiring yet — those chapters (§3 … §16) describe the **full** implementation
each display will grow into. Keep the two states straight:

| Concern | Current skeleton state | Planned (per-display chapters) |
|---------|------------------------|--------------------------------|
| View base class | `VerticalLayout` for every view; only `MainLayout` extends `AppLayout` | unchanged |
| Title | `H1(fullTitle)` inside each view **and** navbar title from the view's `@Menu.title()` (see §2) | unchanged |
| Body placeholder | one `Span("… display goes here.")` with a stable id | real params form + results area |
| Parameters | none yet | `FormLayout` → config record (§3 … §16) |
| Results | none yet | `Graph` / `Partition` / `Tree` / `Centroid` components via translators (§1.3) |
| Concurrency | single-threaded executor per UI/session (§1.7 concept only) | full MVVM + `EventBus` flow (§1.2, §1.6) |

The skeleton exists so routing, the SideNav, and title lookup can be verified end-to-end; each chapter below is
self-contained work to flesh out one display. Ids used in the skeletons (`#gla-body`, etc.) are stable anchors for
Playwright/BrowserlessTests.

### Routes & titles (implemented)

All 15 routes live in `binclass-ui/src/main/java/org/binclass/ui/`. The navbar title comes from each view's
`@Menu.title()`; the body `H1` uses a fuller display name where they differ (see Cumulative, Semi-Cumulative,
Mixture, Bootstrap, Centroids, Compare, Tree, Cut).

| Route | `@Menu.title()` (navbar) | Body `H1` | Order | Icon |
|-------|--------------------------|-----------|:-----:|------|
| `""` | Demo | Demo View | 0 | `vaadin:code` |
| `gla` | GLA Search | GLA Search | 1 | `vaadin:search` |
| `local-search` | Local Search | Local Search | 2 | `vaadin:crosshair` |
| `cumulative` | Cumulative Class. | Cumulative Classification | 3 | `vaadin:cluster` |
| `sclassify` | Semi-Cumulative | Semi-Cumulative Classification | 4 | `vaadin:scale` |
| `fclassify` | Fast Classify | Fast Classify | 5 | `vaadin:bolt` |
| `identify` | Identify | Identify | 6 | `vaadin:eye` |
| `mixture` | Mixture (EM) | Mixture Classifier | 7 | `vaadin:flask` |
| `bootstrap` | Bootstrap | Bootstrap Analysis | 8 | `vaadin:calc` |
| `centroids` | Centroids | Centroid Manager | 9 | `vaadin:grid` |
| `compare` | Compare | Compare Partitions | 10 | `vaadin:table` |
| `function` | Function Plot | Function Plot | 11 | `vaadin:chart-line` |
| `generate` | Data Generator | Data Generator | 12 | `vaadin:code` |
| `tree` | Dendrogram | Dendrogram | 13 | `vaadin:file-tree` |
| `cut` | Cut / Interval | Cut / Interval Analysis | 14 | `vaadin:scissors` |

> **Note:** the tree route's menu title is **"Dendrogram"** (not "Tree / Dendrogram"); keep that spelling when
> referencing it in code, tests, and the SideNav list below.

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
// Pure SC curve point per k; no partition payload (see SearchBestPartition below).
record SearchProgress(String display, int k, double sc, boolean finished) {}
// A new best partition was found mid-search so the Partition tab can refresh live. Carries the full
// snapshot — kept separate from SearchProgress so "SC curve" and "best partition" stay decoupled.
record SearchBestPartition(String display, PartitionResult result) {}
record SearchCompleted(String display, PartitionResult result) {}   // final best partition/centroids + scs[]
record SearchError(String display, String message) {}

// A minimal immutable payload the UI binds to; keep it small (k, sc, centroids-per-k optional).
record PartitionResult(int kmin, double scmin, int clusterCount, double[] scs, int[] ks) {}
```

> **Decision:** both automatic and range searches emit an SC point per `k` (SC curve tab updated after each
> k) **and** push a full `Partition`/`InfiniteCentroids` snapshot whenever a new best partition is detected,
> so the user can browse the improved partition live without waiting for the search to finish. Progress events
> therefore carry scalars only; the best-partition snapshot rides on its own `SearchBestPartition` event (not
> mixed into `SearchProgress`); completion carries the final best result.
> > **Algorithm-module change required:** this UI promise is not yet satisfiable by the current algorithms API.
> > `AutomaticSearch.Result` already exposes per-`k` data (`double[] scs`, `Map<Integer, InfiniteCentroids> bestPerK`),
> > so automatic search can drive both the SC curve and live snapshots. But `RangeSearch.Result(partition, centroids)`
> > carries **no** `scs[]` and no per-`k` record — it returns a single partition/centroids for the searched window. To
> > support the SC-curve tab and live best-partition snapshots on range-only runs, `RangeSearch` must be extended in
> > `binclass-algorithms` to emit per-k SC points (and best-partition snapshots) as it iterates its `[kstart, kstop]`
> > window — either by firing events directly or by returning richer data. This is an algorithm-module change the UI
> > depends on; see "Event wiring in the algorithms module" below.

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

#### Event wiring in the algorithms module

The UI only *consumes* events; the **algorithms module must produce them**, and today it ships neither concrete
event records nor any firing code — only the `AbstractEvent` marker interface exists in
`binclass-algorithms/events/`, while `EventBus.get()` / `post(...)` already work. The following additions are required:

- **Concrete event records.** Add the immutable records from §1.2 (`SearchStarted`, `SearchProgress`,
  `SearchBestPartition`, `SearchCompleted`, `SearchError`) to `binclass-algorithms/events/` as `@NullMarked` records
  implementing `AbstractEvent`. Keep them small and value-typed; `PartitionResult` is the shared payload for best-partition
  snapshots and completion.
- **Fire from each long-running search.** Each entry point that runs on the search-execution pool must connect to the
  singleton `EventBus.get()` and post events at the right points:
  - `AutomaticSearch` — after every `k` in the forward scan and ping-pong phase, post `SearchProgress(k, sc)`; whenever a
    new best SC/partition is recorded, post `SearchBestPartition(partition, centroids)`; on success post `SearchCompleted`;
    on failure post `SearchError`. Its `Result` already carries `scs[]` and `bestPerK`, so the curve + snapshots are derivable.
  - `RangeSearch` — extend it (see the §1.2 note above) to fire `SearchProgress` per `k` and `SearchBestPartition` snapshots
    as it iterates its `[kstart, kstop]` window; today it returns only `(partition, centroids)` with no per-k data.
  - Join-GLA / Split-GLA (Semi-Cumulative, Fast Classify) and Bootstrap — same pattern from their own entry points.
- **Non-blocking dispatch.** `EventBus.post(...)` fans events out on the EventBus's own fixed virtual-thread executor, a pool
  separate from the search-execution pool, so algorithms can call it directly without blocking or needing to marshal back to the
  Vaadin server thread. The UI-side listener (`eventFired`, per the `EventBus.EventBusListener` interface) then updates signals.
- **Testing.** JUnit 6 in `binclass-algorithms`: assert each search posts the expected sequence of events on a captured
  `EventBus`; BrowserlessTests post fabricated records to verify the view reacts (already sketched per display).

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
    @Override public void eventFired(AbstractEvent event) {
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

Root layout rendered around every view via the Vaadin `@Layout` annotation on a single `MainLayout`. It is the only
view that extends `AppLayout`; each display is a plain `VerticalLayout`, so titles live in the shared navbar rather than
in each view.

**Skeleton:**

```
MainLayout extends AppLayout   (@Layout, route "")  implements AfterNavigationObserver
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
        ├─ SideNavItem("Dendrogram",      "tree")        → RouteTreeView
        └─ SideNavItem("Cut / Interval",  "cut")         → RouteCutView
```

**How the title updates (implemented).** `MainLayout` implements `AfterNavigationObserver`; on every navigation it calls
`getCurrentPageTitle()`, which reads the active view's own `@Menu.title()` via reflection:

```java
@Override public void afterNavigation(AfterNavigationEvent event) {
    title.setText(getCurrentPageTitle());
}
private String getCurrentPageTitle() {
    Component content = getContent();                 // current view in the content area
    if (content == null) return title.getText();      // fallback to "BinClass"
    Menu menu = content.getClass().getAnnotation(Menu.class);
    return menu == null ? title.getText() : menu.title();
}
```

This is a **reflection lookup on the active view class**, not a route-path loop: each display contributes its own label by
carrying an `@Menu` annotation, so adding a display is one new `@Route`/`@Menu` class with no change to `MainLayout`. The
fallback (`"BinClass"`) covers any view without a `@Menu` annotation.

**How the SideNav is built (implemented).** `buildSideNav()` iterates `MenuConfiguration.getMenuEntries()`, which already
returns every `@Route`/`@Menu` pair ordered by `order`. Each entry becomes a `SideNavItem(entry.title(), entry.path(), prefix)`;
the icon prefix is resolved from `entry.icon()` via `VaadinIcon.valueOf(name.toUpperCase(Locale.ROOT)).create()` after stripping a
leading `vaadin:` prefix, and entries without an icon (or with an unresolvable name) are added with no prefix component. The
current-route highlight in the drawer follows routing automatically once each view is registered.

**Notes:**
- `SideNav` + `SideNavItem` are Vaadin 25.3 components; each maps to a `@Route` view. The string arg is the route value.
- `DrawerToggle` auto-appears on narrow viewports (responsive drawer).
- Report generation is **not** a top-level SideNav item — it is embedded as the `ReportComponent` in classifier displays' Report tabs (§17) because its output depends on whichever partition is currently displayed.

---

## 3. Display A — GLA Search (`ClassifyCommand`)

> **Status:** implemented — `RouteGlaView` renders a labelled parameter form with enum selects, tooltips,
> a *Run* button that hides the form and reveals a results tab sheet (Search / SC Graph / Partition /
> Dendrogram / Summary), plus a run-summary tab echoing the resolved configuration. The translator and EventBus wiring
> described below are complete.

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

### 3.2.1 Presentation enums (friendly labels)

The three enum selects expose human-readable names instead of bare numeric codes, so users see e.g.
"L2 (Euclidean)" rather than `3`. Each presentation enum carries its flag `code()` and a display `label()`,
and maps back to the code via `fromCode(int)` (falling back to the default for unknown values):

| Enum | Codes shown in the select | Maps to `GLAConfig` field |
|------|---------------------------|---------------------------|
| `DistanceType` | 1 Hamming, 2 L1 (Manhattan), 3 L2 (Euclidean), 4 Shannon Codelength, 5 L1 + Codelength, 6 L2 + Codelength, 7 Codelength + SR, 8 Codelength + SA | `distanceType` |
| `CentroidType` | 1 Classic, 2 Random Seed, 3 Semi-Random, 4 Random, 5 PNN | `centroidType` |
| `Heuristic` | 1 Split-Join 1, 2 Replace-Worst, 3 Replace-Smallest, 4 Random-Swap, 5 Split-Join 2, 6 Random-Swap 2, 7 Cycler (all operators), 8 Adaptive | `heuristic` |

The enum codes follow the C parser (`original/parser.c`). The view builds a `GlaParametersForm` from the
selected enums by calling `.code()`, so the config translation layer stays untouched.

### 3.2.2 Form UX (labels, tooltips, upload)

- **Labels:** every control carries a `setLabel(...)` matching its flag (`kstart (-b)`, `epsilon (-E)`, etc.).
- **Tooltips:** each field exposes a `setTooltipText(...)` explaining the flag and default behaviour.
- **Filebase:** the standalone *Filebase* `TextField` is removed; data is supplied purely through the
  single Vaadin `Upload` (`id="uploader-gla"`) which reads `.hdr`/`.dat` content into memory via a
  `MemoryBuffer` (no temp files). The upload fires on finish and feeds `UploadedDataLoader.load(header, lines)`.
- **Defaults:** the form seeds sensible values from `GlaParametersForm.defaults()` (`kstart=1`, `kstop=0`,
  `epsilon = GLAConfig.DEFAULT.epsilon()`, etc.) so controls are populated immediately on load.
- **Run enabling:** the *Run* button is enabled by default and bound to `viewModel.canRun()` (data loaded
  and not already running). The view calls `viewModel.setForm(buildForm())` before `run(...)` so the VM has
  a form to build its config from — this fixes the early-return bug where an unbound form left *Run* inert.
  - **Upload enables Run:** uploading a header/data pair (`/test.header` + `/test.data`) through the real
    `Upload` parses the vectors (100 in the test fixture) and sets the status text to "Loaded…"; only then does
    *Run* become usable, and clicking it flips `running()` to `true` synchronously.
  - **Run disabled before upload:** with no data loaded and the upload set not yet validated, *Run* stays
    disabled even though the form itself is valid — a user cannot start a search with nothing to classify.
  - **`kstop` / `kstopwhen` mutual exclusion:** setting both `-s` (`k-stop`) and `-S` (`kstop-when`) to positive
    values marks both fields invalid, each showing *"kstop (-s) and kstopwhen (-S) are mutually exclusive."*, and
    disables *Run*. Clearing one bound restores validity and re-enables *Run*.
  - **`epsilon` range with decreasing-epsilon mode:** an `epsilon` outside `(0, 0.5)` invalidates the form and
    disables *Run*. Toggling *decreasing epsilon* treats `epsilon` as a bare value, so the same out-of-range number
    becomes valid again; returning to normal mode with an in-range `epsilon` keeps it enabled.
- **Summary tab:** clicking *Run* also renders a *Summary* tab that echoes the resolved configuration
  (search type, every flag, effective maxIter) and offers a *New search* button to return to the form.

### 3.3 EventBus events
Per-`k` `SearchProgress("gla", k, sc, false)` updates the SC curve tab after each `k`; whenever a new best partition is
detected a `SearchBestPartition("gla", PartitionResult(...))` snapshot is pushed so the Partition tab refreshes live (not only
on completion). Work is started by calling the ViewModel's `run()` command (which submits to the single-threaded executor); the
optional `SearchStarted` event may be posted but is not required to trigger work. Progress events are consumed by the view's
listener and pushed to the graph model; best-partition snapshots build a `PartitionModel`; completion sets the bound result signal
so the graph/partition rebuild automatically.

### 3.4 UI skeleton

```
RouteGlaView extends VerticalLayout   (@Route("gla"))
├─ FormLayout paramsForm               id="gla-params"
│   ├─ vaadin-upload  uploader         id="uploader-gla"      (reads .hdr/.dat into memory)
│   ├─ IntegerField     kStart         id="k-start"           min=1, label + tooltip
│   ├─ IntegerField     kStop          id="k-stop"            label + tooltip
│   ├─ IntegerField     kStopWhen      id="kstop-when"        label + tooltip
│   ├─ IntegerField     kcStopWhen     id="kc-stop-when"      label + tooltip
│   ├─ NumberField      epsilon        id="epsilon"           label + tooltip
│   ├─ Checkbox         decreasingEps  id="decreasing-eps"    label + tooltip
│   ├─ Select<DistanceType> distanceType id="distance-type"   friendly labels, tooltip
│   ├─ Select<CentroidType> centroidType id="centroid-type"   friendly labels, tooltip
│   ├─ Select<Heuristic> heuristic      id="heuristic"        friendly labels, tooltip
│   ├─ IntegerField     maxIter         id="maxiter"          label + tooltip
│   └─ Button           run            id="run-gla"
├─ TabSheet                   glaTabs      id="gla-tabs"     (Search | SC Graph | Partition | Dendrogram | Summary)
│   ├─ "Search"  → Card searchCard          id="search-panel"
│   │   ├─ ProgressIndicator    progress      id="gla-progress"
│   │   └─ Div          statusText           id="gla-status"
│   ├─ "SC Graph" → graphSC (binclass-graph)  id="sc-graph"
│   ├─ "Partition" → partitionView (binclass-partition)  id="partition-view"
│   ├─ "Dendrogram" → Scroller dendrogramScroller  id="gla-dendrogram-scroller"
│   │   ├─ Button generateTree        id="generate-tree-gla"     (enabled when search complete)
│   │   └─ Tree dendrogram            id="dendrogram-gla"        (bound to treeModel signal)
│   └─ "Summary" → summaryContent             echoes resolved GLAConfig + New search button
```

**Flow (MVVM):** `#run-gla` click → view calls `viewModel.setForm(buildForm())` then `viewModel.run(runButton)`. The VM builds
`GLAConfig` from its form-value record (enum `.code()` values map to `GLAConfig`) and submits the search to the single-threaded
executor, disabling the button with `setDisableOnClick(true)`. Clicking *Run* also renders the *Summary* tab and hides the form,
revealing the results tab sheet. Per-`k` progress events are collected into the graph model (`scs[]` → `GraphPoint(k, sc)`), so
the SC graph tab updates after each k; whenever a new best partition is detected the view builds its own view-specific
`PartitionModel` from the algorithm `Partition` and calls `partitionView.setData(PartitionModel)`, which rebuilds automatically.
The SC Graph tab shows the automatic-search curve with its global minimum (reuse `Graph`); the Partition tab renders the best
partition via `Partition` (clusters → `ClusterData`, lazy vectors). On completion/cancellation the button is re-enabled and the
future is cancelled in `onDetach(...)`. The *New search* button on the summary tab restores the form and hides the tabs.
- **Dendrogram tab.** A *Generate dendrogram* button (`id="generate-tree-gla"`) sits above the Tree component; it is disabled until the search completes (bound to the result signal / not running). Clicking it posts `TreeBuilder.makeTreePnn(partition, centroids, jeffreysPrior)` (or `makeTreePnn2` for Hellinger) to a background task; on completion the flattened `TreeModel` updates the bound signal and the Tree component (`id="dendrogram-gla"`) re-renders. Building is done lazily because it is slower than rendering the Partition component, so the partition shows immediately while the dendrogram is generated only when asked. jeffreysPrior comes from the search config; an optional Hellinger toggle mirrors §15.2.
- **Fixed lower bound still runs (range search):** setting `kStart` (`-b`) above 1 selects range search; *Run* must still start it
even though automatic search is not selected — a fixed lower bound does not disable Run. The same `beforeLeave` guard that
postpones navigation while a search is in flight (see §3.2.2) also applies here, so the user can leave only after confirming.

### 3.5 Translation layer
- `scs[]` + `ks[]` from `AutomaticSearch.Result` → `GraphModel(List<GraphPoint(k, sc))`; drop non-finite points.
- best `Partition`/`InfiniteCentroids` → view-specific `PartitionModel` (+ per-cluster `ClusterData`, rounded centroid for hover).
  The GLA display shapes clusters/vectors to match its partition tab; the cumulative display shapes them differently.

### 3.5.1 Dendrogram tab (on-demand)
- Reuses `TreeBuilder.makeTreePnn(partition, centroids, jeffreysPrior)` / `makeTreePnn2(...)` (Hellinger vs class-nearness) and the §15 translation that flattens a `TreeNode` into an indexed `TreeModel` (leaf names = most-common strain, joint sizes summed). The GLA display passes its best `Partition` + `InfiniteCentroids` straight from the search result — no save/reload.
- The *Generate dendrogram* button is enabled only when a search has completed (`canGenerateTree()`), so it is inert during and immediately after a run until the result signal is set; it can be re-clicked to rebuild if the config (e.g. Hellinger) changes.
- Generation runs on the single-threaded executor like the search; completion sets the `treeModel` signal bound to the Tree component, which re-renders automatically. A running indicator accompanies generation so the user knows the tree is being built.

### 3.6 Testing
- **Unit:** build `GLAConfig` from a form-value record; verify flag→config mapping (esp. search-type selection, alternate-mode int code, decreasing-epsilon detection). Also unit-test the translation layer: verify `PartitionModel` is populated correctly from the algorithm objects (`Partition`/`InfiniteCentroids`) — cluster names, per-cluster `ClusterData`, and lazy `VectorData`.
- **BrowserlessTests:** drive the params form and assert the emitted `GLAConfig` is correct; fabricate a `SearchCompleted` event and assert `#sc-graph` accumulates points and the partition tab receives a model. (Graph / Partition rendering itself already covered in `binclass-components`.)
- **Dendrogram tab:** assert the *Generate dendrogram* button is disabled before a search completes and enabled once `SearchCompleted` fires; fabricate a completed result and assert clicking it builds a `TreeModel` that populates `#dendrogram-gla`. (Tree web component SVG rendering already covered in `binclass-components`.)

---

### 3.7 Test coverage

The use cases below are exercised by tests that currently exist. Each maps to a concrete behaviour in §3; keep these green when refactoring.

**Flag → config translation — `GlaParametersFormTest` (`binclass-ui`).**
- `defaultsMatchGlaConfigDefaults`: form defaults seed from `GlaParametersForm.defaults()` (`kstart=1`, `kstop=0`, `epsilon = GLAConfig.DEFAULT.epsilon()`, `distanceType = DEFAULT`).
- `automaticSearchWhenNoRangeOrIterations`: no range bounds / maxIter → `SearchType.AUTO`.
- `rangeSearchWhenKStartExceedsOne`: `kstart > 1` → `SearchType.NAUTO` (fixed lower bound selects range search).
- `rangeSearchWhenKStopSet` / `rangeSearchWhenMaxIterPositive`: a positive `kstop` or `maxIter` also forces `NAUTO`.
- `lcentSearchWhenCentroidFilePresent`: a `-L` centroid file present → `SearchType.LCENT` (load centroids).
- `toConfigCarriesScalarValuesThrough`: every scalar field (`epsilon`, `heuristic`, `alternateMode`, `centroidType`, `distanceType`, `maxIter`, …) and the decreasing-epsilon flag round-trip through `toConfig()`.
- `toConfigSetsVectorCount`: `n` is stored as the vector count on the resolved config.
- `toConfigValidatesEpsilon`: an `epsilon` outside `(0, 0.5)` throws from `toConfig()` (bare mode).
- `decreasingEpsilonSkipsValidation`: with decreasing-epsilon on, a bare out-of-range `epsilon` passes validation unchanged.

**Signal state + search-type resolution + event updates — `GlaViewModelTest` (`binclass-ui`).**
- `initialSignalsHaveExpectedValues`: `running=false`, empty status text, `canRun=false`, non-null graph points / partition model signals.
- `setDataPopulatesDataSignalAndStatus`: uploading data sets the vector set and a `"Loaded …"` status text.
- `canRunTrueAfterDataLoaded` / `canRunFalseWhenUploadInvalid` / `canRunFalseWhileRunning`: `canRun()` requires loaded data **and** a validated upload **and** not running.
- `eventFiredProgressAppendsGraphPoint`: a `SearchProgress("gla", k, sc, false)` appends one `GraphPoint(k, sc)` to the SC model.
- `eventFiredProgressDropsNonFiniteSc`: non-finite (`NaN`) SC values are dropped from the graph.
- `eventFiredBestPartitionUpdatesModel`: a `SearchBestPartition` snapshot rebuilds the partition model (cluster count preserved).
- `eventFiredCompletedUpdatesGraphAndPartition`: a `SearchCompleted` snapshot sets the stochastic complexity on the partition model and finalises the graph.
- `setFormStoresAndReturnsForm`: `setForm`/`getForm` round-trip the form record.
- `runWithoutDataIsNoOp`: running with no data is a no-op (button stays inert).
- `rangeSearchResultBuildsPartitionModel` / `automaticSearchResultBuildsPartitionModel`: both engine entry points produce a non-null partition via the VM's static run helpers.
- `partitionModelStartsNonEmpty`: the model signal starts non-null/empty so the Partition component's initial effect does not NPE.
- `automaticSearchSetsIndeterminateProgress` / `rangeSearchSetsDeterminateProgress`: automatic search animates an indeterminate bar; a fixed-k range search reports a determinate fraction. This is the §3.4 progress-bar distinction.

**Algorithm → presentation mapping — `GlaViewTranslatorTest` (`binclass-ui`).**
- `toPartitionModelBuildsOneClusterPerCluster`: one `ClusterData` per partition cluster; vector count and stochastic complexity carried through.
- `toPartitionModelUsesMostFrequentStrainAsName` / `toPartitionModelFallsBackToIndexWhenNoStrain`: cluster name is the most-frequent strain, falling back to the 1-based index when no strain is set.
- `toPartitionModelRoundsCentroidForHover`: per-cluster rounded centroid for hover (bit rounding of probabilities).
- `toPartitionModelHandlesMissingCentroids`: translation tolerates a null centroid array.
- `toGraphModelMapsIndexToXWhenKsAbsent` / `toGraphModelDropsNonFinitePoints` / `toGraphModelUsesKsWhenProvided`: the SC curve maps index→x when no explicit `ks[]` is given, drops non-finite points, and uses explicit `ks[]` x-values when provided.

**Real component driving — `RouteGlaViewBrowserlessTest` (`binclass-ui`).**
- `uploadEnablesRunAndClickStartsSearch`: uploading `/test.header` + `/test.data` parses 100 vectors, sets the status text to `"Loaded…"`, enables Run, and clicking it flips `running()` to `true` synchronously while hiding the form.
- `runDisabledBeforeUpload`: with no data loaded / upload unvalidated, Run stays disabled even though the form is valid.
- `kstopAndKstopwhenMutuallyExclusiveDisablesRun`: setting both `-s` and `-S` positive marks both fields invalid (message *"kstop (-s) and kstopwhen (-S) are mutually exclusive."*) and disables Run; clearing one restores validity.
- `epsilonOutOfRangeDisablesRunButDecreasingReenables`: out-of-range epsilon disables Run; toggling decreasing-epsilon re-enables it; returning to normal mode with an in-range value keeps it enabled.
- `kStartGreaterThanOneStillRunsSearch`: a fixed lower bound still enables and starts the search (range search).
- `generateDendrogramButtonDisabledUntilCompleteEnabledOnCompletion`: the *Generate dendrogram* button is disabled before completion and enabled once a result exists; clicking it builds a tree model that populates the Tree component.
- `confirmDialogAppearsWhenNavigatingAwayWhileRunning` / `confirmResumesNavigationAndCancelsSearch` / `cancelKeepsSearchRunningAndStaysInView`: the `beforeLeave` guard opens a confirm dialog when navigating away mid-search; *confirm* cancels and proceeds, *cancel* keeps running and stays on the view.

**End-to-end rendering — `GlaViewIT` (`binclass-ui`, Playwright).**
- `glaViewRendersParamsFormAndTabs`: every control renders (range fields, strategy selects incl. `#empty-cell-fix`, Run button, tabs) plus the embedded Report tab (`#report-component-gla`) and each result tab's component once its header is selected.
- `glaViewRendersDefaultParameters`: the default form seeds range/epsilon values; uploading the bundled pair enables Run.
- `glaViewGraphAccumulatesPointsOnCompletion` (marked `@Ignore flaky`): progress events draw a polyline on the SC graph.
- `glaViewPartitionTabReceivesModelOnCompletion`: a fabricated `SearchCompleted` populates the partition tab.
- `glaViewDendrogramTabDisabledUntilCompletion`: the Dendrogram tab's *Generate dendrogram* button is disabled until completion and enabled once a result exists; clicking it builds a tree model that populates the Tree component.

### 3.8 Embedded Report tab (§17)

The GLA display embeds one `ReportComponent` instance (`id="report-component-gla"`) in its own *Report* tab — report output depends on whichever partition is currently displayed, so it recomputes centroids from the bound partition and regenerates on flag change. This is the only classifier display with a dedicated Report component wired today; §4 … §7 embed their own instances once built.

---

## 4. Display B — Local Search (`ClassifyCommand` `-r7`/`-r8`)

> **Status:** skeleton view implemented — `VerticalLayout` with an `H1("Local Search")` title and a placeholder body
> (`id="local-search-body"`). The parameter form, results area (including an on-demand Dendrogram tab), translator and EventBus wiring described below are not yet built.

A focused view on heuristic local search (cycler `-r7`, adaptive `-r8`) which route through
`LocalSearch.localSearch()` instead of a single GLA variant. Shares the GLA parameter form; narrows scope to
heuristic-related flags and shows per-operator progress.

### 4.1 Algorithm entry point
- `GLAConfig` with `heuristic ∈ {1..8}` (all eight: SPLITJOIN1 … RANDOMSWAP2, plus Cycler/Adaptive) plus `lsCycler`/`lsAdaptive`. Reuse the GLA engine via `RangeSearch`/`AutomaticSearch` with those flags set. The select shows **all 8** codes — code 1 (SPLITJOIN1) is a valid single-operator local-search choice, not excluded.
- The two meta-heuristics are **booleans** on `GLAConfig`, not extra heuristic codes: `-r7` sets `heuristic=7, lsCycler=true` and `-r8` sets `heuristic=8, lsAdaptive=true`. Both keep a base operator (SPLITJOIN1) but call `LocalSearch.localSearch(partition, centroids, count, l, n, jeffreysPrior, random)` after each GLA step instead of a single-shot `GLAEngine.gla*` variant.
- **Activation gate:** local search only refines when the cluster count exceeds three (`k > 3`), matching the CLI's `-j` behaviour — for small fixed-k ranges it still runs but with few operator applications. The driver applies a rotating sequence of operators (`SPLITJOIN1, REPLACEWORST, REPLACESMALLEST, RANDOMSWAP, RANDOMSWAP2, SPLITJOIN2`), tracks per-operator successes with adaptive weights (ALFA/BETA cooling), keeps the best partition found, and performs a final refinement pass.
- `count` = operator applications (`heuristicCount - 1`); `-j` adds one internally. The driver logs each iteration's applied operator and resulting SC; on an improvement it publishes a per-operator success event (see §4.3) so the *Search* tab can surface which operator improved SC.
- **New event type — `OperatorSuccess`.** EventBus already exposes per-k `SearchProgress(k, sc)`; local search additionally posts an `OperatorSuccess("localsearch", k, heuristicCode, scDelta)` whenever a single operator application lowers SC. The view renders them through the styled `OperatorLog` component (`id="local-operator-log"`, see §4.3) placed below the running status text. The component owns no state beyond a bound model signal; it draws three sections from an `OperatorLogModel`: a fixed **legend** (one stacked row per operator with its friendly label and a colored square whose hover title carries a longer description), one **per-`k` row** holding a colored success square for every operator that lowered SC at that cluster count, and a set of proportional **statistic bars** — one horizontally stacked bar per operator, length proportional to how many times it improved SC. The `scDelta` field carries the reduction in SC (`sc - scn`, computed before `sc` is reassigned, so it is never zero for a real improvement). It does **not** feed the SC graph — that stays driven by per-k `SearchProgress`.
- The two meta-heuristics are **booleans** on `GLAConfig`, not extra heuristic codes: `-r7` sets `heuristic=7, lsCycler=true` and `-r8` sets `heuristic=8, lsAdaptive=true`. Both keep a base operator (SPLITJOIN1) but call `LocalSearch.localSearch(partition, centroids, count, l, n, jeffreysPrior, random)` after each GLA step instead of a single-shot `GLAEngine.gla*` variant.
- **Activation gate:** local search only refines when the cluster count exceeds three (`k > 3`), matching the CLI's `-j` behaviour — for small fixed-k ranges it still runs but with few operator applications. The driver applies a rotating sequence of operators (`SPLITJOIN1, REPLACEWORST, REPLACESMALLEST, RANDOMSWAP, RANDOMSWAP2, SPLITJOIN2`), tracks per-operator successes with adaptive weights (ALFA/BETA cooling), keeps the best partition found, and performs a final refinement pass.
- `count` = operator applications (`heuristicCount - 1`); `-j` adds one internally. The driver logs each iteration's applied operator and resulting SC, so the *Search* tab can surface per-operator progress.

### 4.2 Parameters → control
Reuse `GlaParametersForm`; add a `Select<Heuristic>` for heuristic (codes **1–8**, i.e. 2–6 plus "Cycler"/"Adaptive") and a `IntegerField` for `heuristicCount`. Drop the search-type controls (`kstart`/`kstop`/`kstopwhen`/`kcStopWhen`) — local search is always range or automatic, so there is no `kstop`/`kstopwhen` mutual-exclusion branch here. The shared form carries the same edge cases as §3.2.2: uploading a header/data pair enables *Run* only after the vectors parse (status text loads), *Run* stays disabled before any data is loaded, and `epsilon` outside `(0, 0.5)` disables *Run* but re-enables via the decreasing-epsilon toggle and an in-range value. Because search-type controls are dropped, both fixed-k and automatic runs start from the same button.

> **Reuse the existing `Heuristic` presentation enum** (`binclass-ui/src/main/java/org/binclass/ui/classify/Heuristic.java`) rather than building a separate one — it already carries codes 1–8 with labels (Split-Join 1 … Random-Swap 2, Cycler, Adaptive), `.code()`, and `fromCode(int)`. The view builds the config by calling `.code()`, so §3's translation layer stays untouched. This means local search reuses the exact same enum select as GLA Search; it simply always resolves to a heuristic (never a search-type).

### 4.3 UI skeleton

```
RouteLocalSearchView extends VerticalLayout   (@Route("local-search"))
├─ FormLayout paramsForm            id="local-params"
│   ├─ (filebase + uploader)         id="filebase"
│   ├─ Select<Heuristic> heuristic   id="heuristic"          all 8 codes, cycler, adaptive
│   ├─ IntegerField heuristicCount   id="heuristic-count"    (-j; +1 internally)
│   ├─ NumberField  epsilon          id="epsilon"
│   ├─ Checkbox     jeffreysPrior    id="jeffreys-prior"
│   └─ Button       run              id="run-local"
├─ TabSheet             localTabs        id="local-tabs"      (Search | SC Graph | Partition | Dendrogram | Summary | Report)
│   ├─ "Search"  → Card searchCard          id="local-search-panel"
│   │   ├─ ProgressIndicator progress         id="local-progress"
│   │   ├─ Div          statusText           id="local-status"    (running strategy/progress text)
│   │   └─ OperatorLog      operatorLog        id="local-operator-log"  (legend + per-k rows + stat bars, bound to viewModel.operatorLogModel())
│   ├─ "SC Graph" → graphSC                        id="sc-graph"
│   ├─ "Partition" → partitionView              id="partition-view"
│   │   └─ download controls (§18.5)             id="local-downloads"  (partition + centroids)
│   ├─ "Dendrogram" → Scroller dendrogramScroller  id="local-dendrogram-scroller"
│   │   ├─ Button generateTree        id="generate-tree-local"     (enabled when search complete)
│   │   └─ Tree dendrogram            id="dendrogram-local"        (bound to treeModel signal)
│   ├─ "Summary" → summaryContent             echoes resolved GLAConfig + New search button
│   └─ "Report"  → ReportComponent            id="report-component-localsearch"
```

**Flow (MVVM):** mirrors §3.4 — `#run-local` click → view calls `viewModel.setForm(buildForm())` then `viewModel.run(runButton)`. The VM builds `GLAConfig` from the form record with the chosen heuristic code and `lsCycler`/`lsAdaptive` set, submits to the single-threaded executor, disables the button with `setDisableOnClick(true)`, and hides the form revealing the results tab sheet. Per-`k` `SearchProgress("localsearch", k, sc, false)` updates the SC curve; best-partition snapshots (`SearchBestPartition`) refresh the Partition tab live; completion sets the bound result signal so graph/partition rebuild automatically. The *Search* tab's status text echoes the chosen strategy (e.g. *"Cycling all strategies for Local Search"* / *"Adaptive …"*), while per-operator progress flows through the operator-log model signal bound to the `OperatorLog` component: each `OperatorSuccess` event updates that signal and the component re-renders its legend, per-`k` rows and statistic bars. On completion/cancellation the button is re-enabled and the future cancelled in `onDetach(...)`.
- **Per-operator improvement statistics.** While a search runs, every `OperatorSuccess` event also increments a per-operator counter (keyed by the operator name). When the search completes — on the `SearchCompleted` event that follows the best-partition snapshot — the view model publishes an updated `OperatorLogModel` through its bound signal: the component renders one proportional statistic bar per operator, ordered by count descending (`"<operator>: N improvement(s)"`). The stats bars are omitted entirely when no operator lowered SC. They sit below the legend and per-`k` rows and do not affect the SC graph or Partition tab.
- **Dendrogram tab.** A *Generate dendrogram* button (`id="generate-tree-local"`) sits above the Tree component; it is disabled until the search completes (bound to the result signal / not running). Clicking it posts `TreeBuilder.makeTreePnn(partition, centroids, jeffreysPrior)` (or `makeTreePnn2` for Hellinger) to a background task; on completion the flattened `TreeModel` updates the bound signal and the Tree component (`id="dendrogram-local"`) re-renders. Building is done lazily because it is slower than rendering the Partition component, so the partition shows immediately while the dendrogram is generated only when asked. jeffreysPrior comes from the search config; an optional Hellinger toggle mirrors §15.2.

**SC graph (fixed-k):** reuse §3.5 logic — for an automatic run populate the curve from `AutomaticSearch.Result.scs[]`; for a pure range search there is no `scs[]`, so either re-run at successive fixed `k`s to build a curve or show the single best point and disable the graph until data arrives (same approach GLA uses).

**Report tab:** embed one `ReportComponent` instance (`id="report-component-localsearch"`) bound to the currently displayed partition, mirroring §3.8 — it recomputes centroids from that partition and regenerates on flag change.

**Summary tab:** own Summary tab echoing the resolved `GLAConfig` (search type, every flag, effective maxIter, chosen heuristic + cycler/adaptive) with a *New search* button that restores the form and hides the tabs — same contract as §3.4.

**Downloads (§18.5):** beside the Partition tab, expose two download controls — partition → `.partition` via `PartitionWriter.writePartition(partition, name)` and centroids → `.centroids` via `CentroidWriter.save(centroids, name)`. Build content in memory (no temp files), disable while a search is running (bind to the same `running` signal), re-enable once a result exists.

### 4.5 Dendrogram tab (on-demand)
- Reuses `TreeBuilder.makeTreePnn(partition, centroids, jeffreysPrior)` / `makeTreePnn2(...)` and the §15 translation that flattens a `TreeNode` into an indexed `TreeModel` (leaf names = most-common strain, joint sizes summed). Local Search passes its best `Partition` + `InfiniteCentroids` straight from the search result — no save/reload.
- The *Generate dendrogram* button is enabled only when a search has completed (`canGenerateTree()`), so it is inert during and immediately after a run until the result signal is set; it can be re-clicked to rebuild if the config (e.g. Hellinger) changes.
- Generation runs on the single-threaded executor like the search; completion sets the `treeModel` signal bound to the Tree component, which re-renders automatically. A running indicator accompanies generation so the user knows the tree is being built.

### 4.6 Testing
- **Unit:** heuristic→config mapping; cycler/adaptive flags propagate (`lsCycler`/`lsAdaptive` set when code is 7/8, single operator otherwise). Also unit-test the translation layer exactly as §3.6: `PartitionModel` populated from the algorithm `Partition`/`InfiniteCentroids` (cluster names via most-frequent strain → index fallback, per-cluster `ClusterData`, rounded centroid for hover) and the SC curve graph model (`scs[]` → `GraphPoint(k, sc)`, dropping non-finite points).
- **BrowserlessTests:** drive the params form and assert the emitted `GLAConfig` carries all 8 heuristic codes plus `lsCycler`/`lsAdaptive`; verify *Run* disables while a search is in flight and re-enables on completion; fabricate an `OperatorSuccess` event and assert `#local-status` appends a line; fabricate a `SearchCompleted` event and assert `#sc-graph` accumulates points, the partition tab receives a model, downloads enable, and the Summary tab renders. (SC graph / partition rendering already covered in `binclass-components`.)
- **Playwright:** run a local search, assert status text mentions the chosen strategy (cycler/adaptive) plus per-operator success lines, the graph renders, and each download control produces its file; the Dendrogram tab's *Generate dendrogram* button is disabled until completion then enabled, and clicking it builds a tree that populates the Tree component.
- **Dendrogram tab:** assert the *Generate dendrogram* button is disabled before a search completes and enabled once `SearchCompleted` fires; fabricate a completed result and assert clicking it builds a `TreeModel` that populates `#dendrogram-local`. (Tree web component SVG rendering already covered in `binclass-components`.)

### 4.7 Test coverage (reference from §3 + engine)

The Local Search view should follow the **same test structure as §3.7** — BrowserlessTests driving the real Vaadin components are the primary pattern, with algorithm-level engine tests kept only as a reference for driver behaviour. The Mockito `mockStatic` approach used in `ClassifyCommandTest` (CLI dispatch) is *not* the right fit here; the view asserts config emission and Run behaviour through its own BrowserlessTests instead.

**Flag → config translation — `LocalSearchParametersFormTest` (`binclass-ui`).**
- heuristic→config mapping with cycler/adaptive propagation: code 7 sets `lsCycler=true`, code 8 sets `lsAdaptive=true`, codes 1–6 keep a single operator; all eight codes round-trip through `toConfig()`.
- Reuses the shared form's validation edge cases from §3.2.2 (upload enables Run, epsilon range with decreasing-epsilon) and confirms there is **no** `kstop`/`kstopwhen` mutual-exclusion branch in local search.

**Signal state + event updates — `LocalSearchViewModelTest` (`binclass-ui`).**
- Mirrors `GlaViewModelTest`: `canRun()` after upload, progress → graph points (dropping non-finite), best-partition/completed snapshots → partition model, indeterminate vs determinate progress bars.
- New: listens for the `OperatorSuccess` event and publishes an updated `OperatorLogModel` through its bound signal, which drives the `OperatorLog` component (`#local-operator-log`) — legend, per-`k` rows and proportional statistic bars; also tallies per-operator improvement counts and, on `SearchCompleted`, sets the stats bars ordered by count descending (`<operator>: N improvement(s)`); clears both the model and the counters when a new search starts; exposes `canDownload()` bound to the `running`/result signals for §4.3 downloads.

**Algorithm → presentation mapping — reuse `GlaViewTranslatorTest` patterns.**
- The translation layer is shared with GLA, so assert `PartitionModel` populated from `Partition`/`InfiniteCentroids` (cluster names via most-frequent strain → index fallback, per-cluster `ClusterData`, rounded centroid for hover) and the SC curve graph model (`scs[]` → `GraphPoint(k, sc)`, dropping non-finite points).

**Real component driving — `RouteLocalSearchViewBrowserlessTest` (`binclass-ui`).**
- Mirrors `RouteGlaViewBrowserlessTest`: uploading `/test.header` + `/test.data` parses vectors and enables Run; clicking *Run* flips `running()` to `true` synchronously while hiding the form; *Run* stays disabled before upload; the all-8-code heuristic select emits the right config (cycler/adaptive); a fabricated `OperatorSuccess` event updates the operator-log signal so `#local-operator-log` re-renders its per-`k` rows; a fabricated `SearchCompleted` event makes `#sc-graph` accumulate points, gives the partition tab a model, enables downloads, and renders the Summary tab; a *Generate dendrogram* button is disabled until completion then enabled, and clicking it builds a tree model that populates `#dendrogram-local`.

**End-to-end rendering — `LocalSearchViewIT` (`binclass-ui`, Playwright).**
- Mirrors `GlaViewIT`: every control renders (heuristic select showing all 8 codes, heuristic-count, epsilon, jeffreysPrior) plus each result tab incl. Report, Summary and Dendrogram; a real run shows the chosen strategy text in the status element, updates the operator-log signal so `#local-operator-log` renders legend + per-`k` rows + statistic bars, draws the SC graph, enables the Dendrogram tab's *Generate dendrogram* button after completion (clicking it builds the tree), and each download control produces its file.

**Engine reference (algorithm-level, not view tests).**
- `AutomaticSearchTest` (`binclass-algorithms`): `configWithHeuristic(...)` helper + `testHeuristic7RunsLocalSearch` / `testHeuristic8RunsLocalSearch` verify the driver runs after GLA for every k > 3 and that every input vector is covered — the behaviour §4's view drives.
- `LocalSearchTest` (`binclass-algorithms`): operators/helpers, `successIndex`, and the driver itself — the per-operator success tracking that `OperatorSuccess` events are derived from.

**UI module (to be added alongside §3's four files).** Add a dedicated `LocalSearchViewModel` (not a reuse of `GlaViewModel`) mirroring `GlaViewModelTest`'s signal/event patterns for local search: `canRun()` after upload, progress → graph points (dropping non-finite), best-partition/completed snapshots → partition model, indeterminate vs determinate progress bars. The VM listens for the new `OperatorSuccess` event and publishes an updated `OperatorLogModel` through its bound signal that drives the `OperatorLog` component (`#local-operator-log`); it also exposes `canDownload()` bound to the `running`/result signals for §4.3 downloads. Add a dedicated `RouteLocalSearchViewBrowserlessTest` mirroring `RouteGlaViewBrowserlessTest` (upload enables Run, Run disabled before upload, all-8-code strategy select emits the right config).

---

## 5. Display C — Cumulative Classification (`CumulativeCommand`)

> **Status:** skeleton view implemented — `VerticalLayout` with an `H1("Cumulative Classification")` title and a placeholder body
> (`id="cumulative-body"`). The parameter form, progress bar, log area, translator and EventBus wiring described below are now built.
> Resolved decisions: events posted at 10% intervals (ProgressBar advances in 10% steps); log appends one line per new cluster with bold
> SC when the SC-optimising variant is used; single `-I`/`-O` input; no SC Graph tab; DownloadAnchor for partition only; shared Partition +
> ReportComponent. The `#cumulative-log` Div renders one line per new-cluster event via `ListSignal<String>` + `Div.bindChildren`, with the
> SC portion wrapped in a bold span; the child factory reads the value with `.peek()` (not `.get()`) so Vaadin's usage tracker does not flag
> it, and tests verify each event appends exactly one DOM child to the container.

Processes vectors one at a time; each may create a new class or join an existing one, using Bayesian
predictive identification with SC as criterion. Output is a `DynamicPartition` (grows over time).

### 5.1 Algorithm entry point
- `CumulativeClassifier.doCumulativeClassification(VectorSet, CumulativeConfig)` → `DynamicPartition`. The first vector always seeds class 1; each subsequent vector either joins an existing class or extends the partition by one (so the final cluster count is between 1 and `n`).
- **No centroids object.** Unlike GLA/Local Search there is no `InfiniteCentroids` — a cumulative centroid for cluster `i` is derived from its frequency table (`dynPart.getFreqs(i)`) divided by the cluster size. The translator builds per-cluster centroid probabilities directly from these frequencies, so `PartitionModel` carries real (non-empty) centroids without a separate centroid pass.
- **Final SC.** The dynamic partition exposes no single "whole-partition SC" method (only per-vector `calculateStochasticComplexityX(new)`), so the header metric shown on the Partition tab is computed from the static view via `DistanceCalculator.stochasticComplexity(dynPart.convert(), k, l)` with `k = dynPart.size()` and `l = dynPart.getVectorLength()`. (Cumulative's own incremental SC helpers exist but are per-vector, not a final score.)
- **Null return.** The method can return `null` (the CLI checks for it), so the VM maps a non-null result to `SearchCompleted` and an exception/empty set to `SearchError`.
- **Delta 3-state** (mirrors `CumulativeCommand`): `-D value` → `fixedDelta=true, delta=value`; `-d value` with no `-D` → `fixedDelta=false, delta=value`; neither flag → `delta=1, fixedDelta=false`. When both are present the lowercase `-d` wins for a non-fixed delta. Note the Java classifier currently applies `config.delta()` directly and does **not** apply C's non-fixed decrement `delta_value(k) = real_delta_value - k`, so that behaviour is out of scope unless explicitly added.
- **`-I` vs `-O`.** The CLI parses both `cumulativeInputOrder (-I)` and `cumulativeInOrder (-O)`; only `inOrder` maps to a `CumulativeConfig` field. Treat `-I` as an alias that also forces in-order processing (documented below) rather than a separate signal.

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
| epsilon `-E` | double | `NumberField` | EPSILON (0.001) |

> **No decreasing-epsilon mode.** Unlike GLA/Local Search, `CumulativeConfig` has no `decreasingEpsilon` field, so the epsilon field here is a plain bare value validated in `(0, 0.5)` — there is no checkbox toggle and no out-of-range re-enable path.

The raw values are held by a dedicated immutable form record (mirroring `GlaParametersForm` / `LocalSearchParametersForm`):

```
record CumulativeParametersForm(
    String filebase,
    int delta, boolean fixedDelta,
    boolean cumulativeAnalysis, int cumulativeSamples,
    boolean bayesianPredictive, boolean testFeatureSignificance,
    boolean cumSaveByPf, boolean cumNoNewClasses, boolean inOrder,
    double epsilon) {
    CumulativeConfig toConfig(int vectorCount);   // n unused by the algorithm
    boolean isValid();                            // delta >= 0 && epsilon in (0,0.5)
}
```

`toConfig()` maps straight onto the `CumulativeConfig` positional constructor; `fixedDelta` is set only when `-D` was provided (so `-d` and "no flag" both yield `fixedDelta=false`). `isValid()` gates *Run* on `delta >= 0` and epsilon in `(0, 0.5)`.

### 5.2.1 Translation layer (`CumulativeViewTranslator`)
- `DynamicPartition` → view-specific `PartitionModel`: one `ClusterData` per cluster (k = `dynPart.size()`), centroid probabilities from `getFreqs(i) / getClusterSize(i)` (no `InfiniteCentroids` needed), most-frequent-strain name with 1-based index fallback, lazy `VectorData` from each cluster's `VectorSet`, and the final SC computed via `DistanceCalculator.stochasticComplexity(convert(), k, l)` as the header metric.
- No graph model is required — cumulative is inherently single-k and the SC Graph tab is intentionally omitted (see §5 answer 6). The translator is its own class — no shared partition translator (§1.3) because cumulative shapes centroids from frequencies rather than a centroid array.

### 5.3 EventBus events
Cumulative classification runs fast, so an event is posted at **10% progress intervals** rather than after every vector; the
ProgressBar updates in matching **10% steps** (determinate). The ViewModel posts `SearchCompleted("cumulative", PartitionResult)`
(and `SearchError`) on completion/failure. Work is started by the ViewModel's `run()` command (a `CompletableFuture` on the
single-threaded executor); the optional `SearchStarted` event may be posted but is not required. The existing agnostic `Partition`
web component renders the final dynamic partition directly — no new `DynamicPartitionModel`/component is needed.

> **Event model.** GLA and Local Search post their events from the *algorithms* module (`AutomaticSearch`, `RangeSearch`,
> `LocalSearch`) and the VM only listens; cumulative follows the same pattern so it stays consistent. Add lightweight event posting
> to `doCumulativeClassification`:
> - **Progress** — post a progress event at 10% intervals (after roughly every 10% of vectors processed) carrying the number of
>   vectors processed so far; the VM sets the ProgressBar determinately to that percentage, rounding to the nearest 10%. The bar is
>   not indeterminate and does not move per vector — it advances in discrete 10% steps.
> - **New cluster** — post an event each time a vector *extends* the partition (creates a new class). The VM appends one line to the
>   log for each such event; joins are not logged.

**Log content.** The C command emits a per-vector decision trace (`"i: strain classname (d=…, pf=…, sc=…)"`) when logging is on.
Cumulative logs only **new-cluster decisions** — one line per new class, carrying the cluster index and (optionally) the vector's
assigned distance/probability. The classification has two variants: one that does not optimise SC and one that uses SC as its decision
mechanism. When the **SC-optimising variant** is used, the log line prints the resulting stochastic complexity in **bold**; the
non-optimising variant logs without it. Log lines are sanitized with JSoup content mode (Agents.md) since they may carry
strain/class-name text from the data.

### 5.4 UI skeleton

```
RouteCumulativeView extends VerticalLayout   (@Route("cumulative"))
├─ FormLayout paramsForm            id="cumulative-params"
│   ├─ (filebase + uploader)         id="filebase"          (.hdr/.dat into memory)
│   ├─ IntegerField     delta        id="delta"             min=1
│   ├─ Checkbox         fixedDelta   id="fixed-delta"
│   ├─ Checkbox         cumulativeAnalysis id="cum-analysis"
│   ├─ IntegerField     cumSamples   id="cum-samples"
│   ├─ Checkbox         bayesian     id="bayesian-predictive"
│   ├─ Checkbox         featureSig   id="feature-significance"
│   ├─ Checkbox         saveByPf     id="save-by-pf"
│   ├─ Checkbox         noNewClasses id="no-new-classes"
│   ├─ Checkbox         inOrder      id="in-order"
│   ├─ NumberField      epsilon      id="epsilon"           (0, 0.5)
│   └─ Button           run          id="run-cumulative"
├─ TabSheet             cumTabs        id="cumulative-tabs"  (Partition | Report | Summary)
│   ├─ "Partition" → partitionView              id="partition-view"    (final DynamicPartition)
│   │   └─ download controls (§18.5)             id="cum-downloads"     (.partition via DynamicPartitionWriter)
│   ├─ "Report"  → ReportComponent            id="report-component-cumulative"
│   └─ "Summary" → summaryContent             echoes resolved CumulativeConfig + New search button
├─ ProgressIndicator progress         id="cumulative-progress"   (determinate, advances in 10% steps)
└─ Div          logText               id="cumulative-log"     (JSoup-sanitized; one line per new cluster, bold SC when optimising variant)
```

**Flow (MVVM):** mirrors §3.4 — `#run-cumulative` click → view calls `viewModel.setForm(buildForm())` then
`viewModel.run(runButton)`. The VM builds a `CumulativeConfig` from the form record, submits to the single-threaded
executor, disables the button with `setDisableOnClick(true)`, and hides the form revealing the results tab sheet. On
completion/cancellation the button is re-enabled and the future cancelled in `onDetach(...)`.
- **Progress.** Determinate bar that advances in **10% steps**: each progress event (posted at ~10% intervals, see §5.3) sets the
  ProgressBar to the corresponding percentage of input vectors processed. The bar is not indeterminate — it moves in discrete 10%
  increments as classification proceeds.
- **Final SC metric.** The dynamic partition exposes no whole-partition SC method, so the header metric shown on the Partition tab is
  computed from the static view via `DistanceCalculator.stochasticComplexity(dynPart.convert(), k, l)` with `k = dynPart.size()` and
  `l = dynPart.getVectorLength()`, using **uniform prior** by default. This applies to both classification variants; when the variant
  that uses SC as its decision mechanism is selected this final SC is the meaningful optimum, while the non-optimising variant's value
  is reported for reference. If a jeffreysPrior toggle is added later it can be threaded through; the config's `bayesianPredictive`
  field governs class selection, not SC scoring.
- **Log area.** The `#cumulative-log` Div appends one line each time cumulative classification creates a **new cluster** (a vector that
  extends the partition); joins are not logged. Each line carries the new cluster index and, when the SC-optimising variant is used,
  the resulting stochastic complexity printed in **bold**. Log lines are sanitized with JSoup content mode (Agents.md) since they may
  carry strain/class-name text from the data. If no new cluster beyond the first is created, the log stays empty.
- **Partition tab.** The agnostic `Partition` component renders the final dynamic partition directly via its own translator; no new
  `DynamicPartitionModel`/component is needed. A *Download* control (see §5.4.1) sits beside it, disabled while running and enabled on result.
- **Report tab.** Embed one `ReportComponent` (`id="report-component-cumulative"`) bound to the displayed partition via the shared
  `ReportTab.bind(reportComponent, viewModel.partitionModel())`, mirroring §3.8/§17 — it recomputes centroids from the dynamic
  partition's frequencies and regenerates on flag change.
- **Downloads method (recommendation).** Reuse the in-memory byte-producer pattern (§18.5 DownloadAnchor). Extend
  `DynamicPartitionWriter` with a `toBytes(DynamicPartition)` variant so the download control streams content without a temp file,
  matching GLA/Local Search; disable while running, enable on result.
- **Summary tab.** Own Summary tab echoing the resolved config (delta/fixedDelta, every flag, epsilon) with a *New search* button that
  restores the form and hides the tabs — same contract as §3.4.

#### 5.4.1 Downloads (§18.5)
Beside the Partition tab, expose one `DownloadAnchor` (see §18.5): dynamic partition → `.partition`. Build content in memory (no temp
files) via a `toBytes(DynamicPartition)` variant of `DynamicPartitionWriter`; disable while a search is running (bind to the same
`running` signal), enable once a result exists. Unlike GLA/Local Search there is no separate centroids download — cumulative centroids
are derived from frequencies on demand and are not valid as a standalone file.

### 5.6 Testing
- **Unit — `CumulativeParametersFormTest` (`binclass-ui`).**
  - defaults seed from `CumulativeConfig.defaults()` (bayesianPredictive=true, cumSaveByPf=true, inOrder=false, delta=1, epsilon=EPSILON); `-d`/no-delta both start at `fixedDelta=false`.
  - delta 3-state mapping: `-D value` → `fixedDelta=true, delta=value`; `-d value` → `fixedDelta=false, delta=value`; neither → `delta=1, fixedDelta=false`.
  - flag→config propagation: every boolean and the epsilon round-trip through `toConfig()`; vector count stored as `n` (unused by the algorithm).
  - `isValid()`: `delta >= 0` and epsilon in `(0, 0.5)` gate *Run*; out-of-range epsilon disables it.
- **Unit — `CumulativeViewTranslatorTest` (`binclass-ui`).** Verify `PartitionModel` is populated correctly from the algorithm `DynamicPartition` — cluster names (most-frequent strain → index fallback), per-cluster `ClusterData`, centroid probabilities derived from frequencies, lazy `VectorData`, and a non-null final SC header metric. Tolerate an empty partition.
- **BrowserlessTests — `RouteCumulativeViewBrowserlessTest` (`binclass-ui`).** Drive the params form and assert the emitted `CumulativeConfig` is correct; fabricate a progress event at 10% intervals and assert the ProgressBar advances in 10% steps, asserting both the signal value and the component's `getValue()`; fabricate new-cluster log events and assert one line is appended per cluster — capture the container's initial DOM child count, verify each event adds exactly one child incrementally (`initialChildren + n`), and confirm the SC-optimising variant renders its bold SC portion via recursive text (bold SC when the SC-optimising variant is selected); fabricate `SearchCompleted` and assert the Partition tab receives the dynamic partition model, downloads enable, and the Summary tab renders. Assert *Run* disables while running and re-enables on completion. (Partition rendering already covered in `binclass-components`.)
- **Playwright — `CumulativeViewIT` (`binclass-ui`).** Every control renders (delta field, fixedDelta checkbox, cumulativeAnalysis, cumSamples, bayesian, featureSig, saveByPf, noNewClasses, inOrder, epsilon) plus each result tab incl. Report and Summary; a real run shows the partition, advances the ProgressBar in 10% steps, appends new-cluster decisions to `#cumulative-log` (bold SC when the SC-optimising variant is used), and the download control produces its file.

### 5.7 Resolved open questions
All ten previously-open questions have been resolved ahead of implementation; the decisions above (event model, 10% progress/log
granularity, two SC variants, single `-I`/`-O` input, delta left out of the UI, no SC Graph tab, DownloadAnchor for partition only,
shared Partition + ReportComponent, determinate 10% progress, and `defaults()` seeding) make §5 self-contained.

---

## 6. Display D — Semi-Cumulative Classification (`SemiCumulativeCommand`, `sclassify`)

> **Status:** skeleton view implemented — `VerticalLayout` with an `H1("Semi-Cumulative Classification")` title and a placeholder body
> (`id="sclassify-body"`). The parameter form, progress bar, SC graph, partition area, translator and EventBus wiring described below are not yet built.
> Applies the same MVVM/skeleton approach as §5; join-GLA also emits new-cluster events, so the `ListSignal<String>` + `Div.bindChildren` log-area
> pattern (child factory reading with `.peek()`, bold SC when optimising) is reusable if a per-k decision log is added.

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
- **BrowserlessTests:** drive the params form and assert the emitted `GLAConfig` is correct; fabricate a progress event and assert the ProgressBar advances in steps, asserting both the signal value and the component's `getValue()` (mirroring §5); fabricate new-cluster/SearchCompleted events and assert the partition tab receives k = optimal. (SC graph / Partition rendering already covered in `binclass-components`.)

---

## 7. Display E — Fast Classify / Split-GLA (`FastClassifyCommand`, `fclassify`)

> **Status:** skeleton view implemented — `VerticalLayout` with an `H1("Fast Classify")` title and a placeholder body
> (`id="fclassify-body"`). The parameter form, progress bar, SC graph, partition area, translator and EventBus wiring described below are not yet built.

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

> **Status:** skeleton view implemented — `VerticalLayout` with an `H1("Identify")` title and a placeholder body
> (`id="identify-body"`). The parameter form, partition area, log area, translator and EventBus wiring described below are not yet built.

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

> **Status:** skeleton view implemented — `VerticalLayout` with an `H1("Mixture Classifier")` title and a placeholder body
> (`id="mixture-body"`). The parameter form, progress bar, centroid area, translator and EventBus wiring described below are not yet built.

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

> **Status:** skeleton view implemented — `VerticalLayout` with an `H1("Bootstrap Analysis")` title and a placeholder body
> (`id="bootstrap-body"`). The parameter form, progress bar, stats text, SC graph, partition area, translator and EventBus wiring described below are not yet built.

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

> **Status:** skeleton view implemented — `VerticalLayout` with an `H1("Centroid Manager")` title and a placeholder body
> (`id="centroids-body"`). The parameter form, summary text, grid and translator described below are not yet built.

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

> **Status:** skeleton view implemented — `VerticalLayout` with an `H1("Compare Partitions")` title and a placeholder body
> (`id="compare-body"`). The parameter form, distance text, contingency table and translator described below are not yet built.

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
style it in `styles.css` (see §18.7). Build rows via `matrixTable.getBody().addRow()` with header/data cells, or reuse the
structured `ComparisonResult(matrix, …)` rather than stringifying it.

### 12.6 Testing
- **Unit:** printMode validation; matrix indexing (`matrix[p2-1][p1-1]`); distance value vs CLI output.
- **BrowserlessTests:** drive the params form and assert the emitted config is correct; fabricate a `ComparisonResult` and assert distance text and the contingency table (`matrixTable`) populates with the right rows/columns. (HTML `<table>` via `Table`, styled in CSS — not a custom component, not `Grid`.)

---

## 13. Display K — Function Plot / SC vs k (`FunctionCommand`)

> **Status:** skeleton view implemented — `VerticalLayout` with an `H1("Function Plot")` title and a placeholder body
> (`id="function-body"`). The parameter form, SC graph, table area and translator described below are not yet built.

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

> **Status:** skeleton view implemented — `VerticalLayout` with an `H1("Data Generator")` title and a placeholder body
> (`id="generate-body"`). The parameter form, summary text, preview grid and translator described below are not yet built.

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

> **Status:** skeleton view implemented — `VerticalLayout` with an `H1("Dendrogram")` title and a placeholder body
> (`id="tree-body"`). The parameter form, scroller/tree area and translator described below are not yet built.

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

> **Status:** skeleton view implemented — `VerticalLayout` with an `H1("Cut / Interval Analysis")` title and a placeholder body
> (`id="cut-body"`). The parameter form, summary text, partition area and translator described below are not yet built.

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

> **Status:** not yet built. No `ReportComponent` exists and none of the displays currently expose a Report tab; this chapter
> describes the component each classifier display will embed once implemented.

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
└─ VerticalLayout reportBody   id="report-body"
    ├─ H3         header        id="report-header"
    ├─ Details    TOTAL FREQUENCIES (id="report-section")
    │   └─ FrequencyTable (one column per bit, metric rows)
    ├─ Details    LIST OF CLASSES (id="report-section")
    │   └─ VerticalLayout of ClassInfoComponent per cluster
    └─ Details    CLASS NEARNESS MATRIX (id="report-section")
        └─ AffinityMatrix  id="report-affinity"
```

Report text is rendered as sanitized content (`SoupParser`/JSoup content mode, per Agents.md) so the raw
report string cannot inject markup. When nearness or affinity output is requested, render it as a real HTML
`<table>` (styled in CSS, see §18.7) instead of only sanitized text — a square class-nearness or affinity
matrix reads far better as aligned cells than as a monospace string block. The component recomputes centroids
from the partition it is bound to and regenerates on flag change; each display binds it to its own currently
displayed partition.

#### 17.4.1 Report UX layout (reorganized)

The report was reorganized so dense tables stay compact instead of forcing extensive scrolling:

- **Frequency tables are transposed.** `FrequencyTable` renders one column per bit position and one row per
  metric (`Count`, `Percentage`, and optionally `Digit`). The old tall layout (one row per bit, few columns)
  became a short wide table — far better when the vector length is large. It is used both for the total
  frequencies section and inside each `ClassInfoComponent`. A leading empty corner cell keeps the bit-position
  headers aligned with their data columns; each metric row carries a style hook (`frequency-count-row`,
  `frequency-percentage-row`, and `frequency-digit-row` when shown) so CSS can border it like the affinity
  matrix and right-align the numeric cells.
- **Affinity matrix extracted into its own component.** `AffinityMatrix` replaces the inline `renderMatrix`
  logic in `ReportComponent`. Instead of per-cell `getStyle()` calls, every logical cell carries a CSS class
  hook (`matrix-header-cell`, `matrix-corner`, `matrix-label`, `matrix-cell`, `matrix-diagonal`) and all
  styling lives in `binclass-components/src/main/resources/META-INF/resources/affinitymatrix.css`, imported via
  `@StyleSheet("/affinitymatrix.css")` on `ReportComponent`. The table also sets `white-space: nowrap` on its
  cells so values never wrap — otherwise a wide matrix becomes too sparse. Diagonal cells render as `-`; the
  default Hamming distance is an integer, but Hellinger (`-h`) and affinity-transform (`-a`, `(k-d)/k`)
  distances are fractional.
- **Row labels name each cluster.** Each row's leading label uses the `X: NAME` form where `X` is the
  one-based cluster index and `NAME` is the most common vector name in that cluster. The name is taken from
  each vector's class-name first, falling back to its strain identifier, then to the bare index when a cluster
  carries no names — mirroring `GlaViewTranslator.mostFrequentName`. The label column stays sticky so it stays
  visible while scrolling wide matrices.
- **Distance heat-map coloring.** Every off-diagonal cell gets an inline `background-color` interpolated across
  three anchors scaled to the matrix's observed range: green (`#a0f0a0`) at the smallest distance, orange
  (`#f0f0a0`) at the midpoint, and red (`#f0a0a0`) at the largest. The min/max are computed by scanning every
  cell except the diagonal and any zero-distance cells (so a self-distance or an exact-zero similarity does not
  skew the scale), then each value maps to `t = (v - min) / (max - min)` with linear interpolation between the
  anchors. Diagonal cells are left uncolored so their `-` marker keeps its default background.
- **Adaptive number formatting.** Matrix values no longer always print `.00`. Integer values (the default
  Hamming distance) render without a decimal point; fractional values keep two decimals with trailing zeros
  stripped, so `1.50` reads as `1.5` and `2.00` reads as `2`. This removes wasted space in the common case.
- **Sections wrapped in `Details`.** Each report section (total frequencies, list of classes, class nearness
  matrix) is placed inside a Vaadin `Details` element so it collapses by default; the always-listed classes
  stay expanded. This keeps the report compact and reduces scrolling until a user expands a section.

---

## 18. Cross-cutting concerns

> **Status:** the mechanisms below are *planned*, not yet wired into the skeleton views — none of the displays currently
> expose a ViewModel, `ValueSignal` binding, `canRun()` validation, download controls or a search gate. Each is described
> here so it can be added per display as §3 … §16 are implemented.

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

### 18.5 Result persistence — download button
Every display that produces a **persistable result** offers a *Download* control so the user can save it to disk;
there is no file-pick dialog in the Electron shell, so the browser's native "save" prompt is how the file leaves the
machine. The control generates its content **in memory** (no temp files on disk — consistent with §1.4) by reusing the
`binclass-algorithms/io` writers directly.

- **What each display exposes:**
  - Classification displays (§3, §4, §6, §7): partition → `.partition`; centroids → `.centroids`. Expose **two**
    downloads (a small dropdown or two buttons), since the user may want either.
  - Cumulative (§5): dynamic partition → `.partition` via `DynamicPartitionWriter`.
  - Cut / Interval (§16): the resulting intersection partition → `.partition`.
  - Report tab (§17): sanitized report text → `.txt` (or `.csv` for frequency tables).
- **Shared component — `DownloadAnchor`.** Rather than wiring a raw `Anchor` + `DownloadHandler` in every view, reuse
  the reusable styled download link in `binclass-components`:
  `org.binclass.components.download.DownloadAnchor` (extends `Anchor`, annotated `@StyleSheet("/downloadanchor.css")`).
  It binds a `DownloadHandler` that streams bytes produced by a caller-supplied `ByteProducer`, fixes the
  `"text/plain; charset=UTF-8"` content type, and carries the `download-anchor` CSS hook so all styling lives in one file.
  Callers only supply a label, a byte supplier and a file name:

```java
// build content in-memory, never write a temp file; producer runs server-side at click time
DownloadAnchor partitionDownload = new DownloadAnchor("Download partition", () -> PartitionWriter.toBytes(partition));
partitionDownload.setDownloadFileName(filebase + ".partition");
```

  The `ByteProducer` functional interface exposes `byte[] produce()`, which runs on the server at click time and may read
  live view-model state. `setDownloadFileName(...)` sets both the response file name and the content type; centroids use
  `.centroids` and the report uses the sanitized string as its byte array. The component is already used by the GLA and
  local-search views (best partition + logged centroids), which is exactly the DRY extraction §18.5 was designed for.
- **Styling.** `DownloadAnchor` renders button-like via the Aura theme tokens in
  `binclass-components/src/main/resources/META-INF/resources/downloadanchor.css` (`display: inline-flex`, accent border,
  subtle shadow, hover elevation). The look follows how plain buttons render in this app; no per-view CSS is required. A
  `.download-anchor-label` hook keeps the label from wrapping on narrow links.
- **Placement.** Put the download control(s) next to the result area — e.g. beside the Partition tab in §3, under the cut
  partition in §16, inside each Report tab in §17. Disable it while a search is running (bind to the same `running` signal)
  and re-enable once a result exists.
- **Testing.** BrowserlessTests: assert the download control is present/disabled-before-result/enabled-after-result; JUnit 6:
  verify each writer produces the expected bytes/extension from the algorithm model. (The browser actually saving the file is
  covered by Playwright IT if needed.)

### 18.6 Parameter validation — Run enabled only when the form is valid
Every display validates its parameters **before** allowing the algorithm to run, so the user cannot start a search with
missing or conflicting inputs. Validation has two layers: per-field validity (which field is wrong and why) and an overall
`canRun()` rule (is it safe to launch). Both live in the ViewModel so they are unit-testable; the View only reflects them.

- **Per-field validity.** Each control uses Vaadin's built-in validation for its own constraints:
  - `asRequired("…")` on fields that must be present — most importantly the filebase/upload, and any flag with no sensible
    default. A required indicator appears next to the label.
  - `setMin(...)` / `setMax(...)` (and a custom validator via `withValidator(...)`) for numeric ranges — e.g. `epsilon` in
    `(0, 0.5)`, `kstart ≥ 1`. On violation the field turns red and shows an error message under it (`setErrorMessage`).
  - Custom validators for cross-field or enum rules that a single field cannot express on its own.
- **Overall rule — `canRun()`.** The ViewModel exposes `Signal<Boolean> canRun()` derived from all fields, e.g.:
  - *Required present:* vectors (or an existing `.partition`/`.centroids`) are loaded; the filebase/upload is set.
  - *Range order:* for range search `kstart ≤ kstop`; for a fixed-`k` run `kstart == kstop`.
  - *Conflicting params:* e.g. LCENT (`-L`) requires a centroid file and a matching search type; decreasing-epsilon mode
    (bare `-E`) is incompatible with an explicit epsilon value; bootstrap needs both a size and a k. Each display lists its
    own conflicts in §3 … §16.
  - *Running:* `canRun()` also returns false while any search on this UI is in flight (§1.7), so the button stays disabled
    until the current one finishes or is cancelled.
- **Reflecting validity in the View.** The Run button binds to `canRun()` (`runButton.bindEnabled(viewModel.canRun())`)
  and, whenever it changes, its tooltip text is updated with `button.setTooltipText(reason)` so the user learns *why* it is
  disabled — e.g. "Upload vectors first", "k-start must be ≤ k-stop", or "Provide a centroid file for LCENT". The same
  reason drives any visible hint next to the form. Because the button is reactive, enabling/disabling and the tooltip update
  happen together with no manual wiring.
- **Testing.**
  - **JUnit 6 — rules.** Unit-test `canRun()` (and each display's conflict checks) for the key cases: missing filebase,
    `kstart > kstop`, out-of-range epsilon, LCENT without a centroid file. These are plain assertions on the UI-free VM.
  - **BrowserlessTests — UI behaviour.** Drive the form and assert: *Run* is disabled before any input and enabled once the
    filebase/upload is provided; setting `kstart > kstop` disables it again and shows the expected tooltip text; an invalid
    field is marked (`isInvalid()`) with its error message. (Use the same fabricated-event technique as elsewhere to confirm
    validation still holds while a search runs.)

### 18.7 Design system

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
- **Tables (§12, §17):** `Table` renders a plain HTML `<table>` and inherits nothing from Aura, so give it an explicit look via class hooks in CSS: `border-collapse: collapse`, 1px borders on `th/td` using `var(--vaadin-border-color)`, padding from `--vaadin-padding-xs/s`, and right-aligned numeric cells. Sticky the first header cell (`position: sticky; left: 0`) so wide contingency/affinity matrices stay readable while scrolling.
  - **Report tables:** the report's frequency tables and affinity matrix live in their own files rather than `styles.css`. The affinity matrix uses class hooks (`matrix-header-cell`, `matrix-corner`, `matrix-label`, `matrix-cell`, `matrix-diagonal`) defined in `binclass-components/src/main/resources/META-INF/resources/affinitymatrix.css` (imported via `@StyleSheet` on `ReportComponent`); the frequency table uses the `frequency-table` hook. Both set `white-space: nowrap` so cells never wrap and stay dense. The affinity matrix additionally colors each off-diagonal cell by distance — a green/orange/red heat map scaled to the observed min/max (excluding diagonal and zero-distance cells) — and labels each row `X: NAME` with the cluster's most common vector name.

#### Typography (firm)
- **Font family:** set a single modern sans-serif globally, e.g. `--vaadin-font-family: "Inter", system-ui, sans-serif` (or another from the skill's list). Do not vary fonts per view.
- **Hierarchy:** section titles inside a view use Vaadin `H2`; tab labels and field groups stay at default body size. Keep label text on form fields at the default — do not shrink them to fit more flags.

#### Per-view visual notes (where they differ from the shared defaults)
Most views inherit the conventions above unchanged. Only these need extra attention:
- **GLA Search (§3):** three-tab `TabSheet` (Search | SC Graph | Partition). The SC Graph tab should give the `Graph` a fixed aspect ratio and center its legend; the Partition tab should show a compact header (cluster count, k) above the component. This is the flagship — spend design effort here first.
- **Tree / Dendrogram (§15):** the `Tree` SVG can be wide; wrap it in the scroll container (`Scroller`) with a minimum height so the dendrogram is visible before scrolling. Consider a light surface behind it for contrast.
- **Semi-Cumulative / Fast Classify (§6, §7):** the SC curve is the hero — give `#sc-graph` generous width and ensure the global-minimum point reads clearly (reuse the Graph legend/annotation conventions from GLA).
- **Report tab (§17):** render sanitized report text in a monospace-friendly block with left alignment so frequency tables stay aligned; keep it on the shared surface.
- **Local Search (§4):** the `OperatorLog` component (`id="local-operator-log"`) renders its legend, per-`k` success rows and operator statistic bars from an external stylesheet `binclass-ui/src/main/resources/META-INF/resources/operatorlog.css`, imported via `@StyleSheet("/operatorlog.css")` on the component — same pattern as `affinitymatrix.css`. Keep the root scrollable (`max-height: 500px; overflow: auto`) so long runs stay compact, and leave dynamic values (swatch/background colors, bar widths) inline since they change per run.

#### Illustrations (for approval)
The skeletons are sufficient for routing and testing anchors, but one rendered mockup per flagship view would align everyone faster than prose. Recommended minimum:
- **GLA Search** — full tabbed layout with params form + SC graph + partition header.
- **Tree / Dendrogram** — scrollable canvas showing a small dendrogram on a surface.
These can be screenshots of the running Vaadin view once §3 and §15 are implemented, or clean wireframes now. Add more (Graph-heavy views) only if layout decisions prove ambiguous during implementation.

### 18.8 Testing matrix (per Agents.md)

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

### 18.9 Implementation order (recommended)
1. `MainLayout` + GLA Search display (§3) — the `GlaViewModel` implements `EventBusListener`, establishing the EventBus flow, translation layer, and demo view for tests.
2. Report tab embedded in GLA (§17), then copy to other classifier displays.
3. Local Search (§4), Semi-Cumulative (§6), Fast Classify (§7) — reuse `GlaParametersForm`.
4. Cumulative (§5), Identify (§8), Mixture (§9), Bootstrap (§10).
5. Centroids (§11), Compare (§12), Function Plot (§13), Data Generator (§14), Tree (§15), Cut (§16).

Each chapter is self-contained: implement, add its JUnit + Playwright tests, merge, then move on.

---
