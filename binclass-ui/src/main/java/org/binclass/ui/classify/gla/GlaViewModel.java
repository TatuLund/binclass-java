/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.gla;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.TreeNode;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.events.AbstractEvent;
import org.binclass.algorithms.events.PartitionResult;
import org.binclass.algorithms.events.SearchBestPartition;
import org.binclass.algorithms.events.SearchCompleted;
import org.binclass.algorithms.events.SearchProgress;
import org.binclass.algorithms.eventbus.EventBus;
import org.binclass.algorithms.gla.AutomaticSearch;
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.RangeSearch;
import org.binclass.algorithms.gla.SearchType;
import org.binclass.algorithms.tree.TreeBuilder;
import org.binclass.components.graph.GraphModel;
import org.binclass.components.graph.GraphPoint;
import org.binclass.components.partition.PartitionModel;
import org.binclass.components.tree.TreeModel;
import org.binclass.ui.MultiFileMemoryBuffer;
import org.binclass.ui.classify.CentroidType;
import org.binclass.ui.classify.DistanceType;
import org.binclass.ui.classify.EmptyCellFix;

/**
 * UI-free view model for the GLA Search display. Hosts all state as
 * {@link ValueSignal}s, runs searches on a single-threaded virtual-thread
 * executor so one user's searches are serialized, and implements
 * {@link EventBus#EventBusListener()} so it can react to per-{@code k} progress
 * and best-partition snapshots pushed by the algorithms.
 * <p>
 * Follows the MVVM pattern from {@code docs/WebUI.PRD.md} §1.6: the view binds
 * its components to these signals once and forwards user actions as commands;
 * updating a signal here rebuilds the bound component automatically. No Vaadin
 * component is referenced inside, so it stays unit-testable in plain JUnit 6.
 * </p>
 */
@NullMarked
public final class GlaViewModel implements EventBus.EventBusListener {

    /** Loaded vectors plus their configuration, held as a single signal. */
    public record LoadedData(VectorSet vectorSet) {
    }

    private final ValueSignal<LoadedData> data;
    private final ValueSignal<Boolean> running;
    private final ValueSignal<MultiFileMemoryBuffer.UploadValidation> uploadValid;
    private final ValueSignal<PartitionModel> partitionModel;
    private final ValueSignal<GraphModel> graphPoints;
    private final ValueSignal<String> statusText;

    /**
     * Exposed parameter signals, bound two-way to the form fields by the view.
     */
    private final ValueSignal<Integer> kStart = new ValueSignal<>(1);
    private final ValueSignal<Integer> kStop = new ValueSignal<>(0);
    private final ValueSignal<Integer> kStopWhen = new ValueSignal<>(
            GLAConfig.DEFAULT.kstopwhen());
    private final ValueSignal<Integer> kcStopWhen = new ValueSignal<>(
            GLAConfig.DEFAULT.kcStopWhen());
    private final ValueSignal<Double> epsilon = new ValueSignal<>(
            GLAConfig.DEFAULT.epsilon());
    private final ValueSignal<Boolean> decreasingEpsilon = new ValueSignal<>(
            false);
    private final ValueSignal<DistanceType> distanceType = new ValueSignal<>(
            DistanceType.HAMMING);
    private final ValueSignal<CentroidType> centroidType = new ValueSignal<>(
            CentroidType.CLASSIC);
    private final ValueSignal<EmptyCellFix> emptyCellFix = new ValueSignal<>(
            EmptyCellFix.WORST_MATCH);
    private final ValueSignal<Integer> maxIter = new ValueSignal<>(0);

    /** Reactive form validity derived from the parameter signals above. */
    private final Signal<Boolean> formValid = Signal.computed(() -> {
        boolean rangeOk = !(kStop.get() > 0 && kStopWhen.get() > 0);
        double eps = epsilon.get();
        boolean epsOk = decreasingEpsilon.get() || (eps > 0.0 && eps < 0.5);
        return rangeOk && epsOk;
    });

    private final ValueSignal<Double> progress = new ValueSignal<>(0.0);
    private final ValueSignal<Boolean> progressIndeterminate = new ValueSignal<>(
            false);
    private final ValueSignal<Boolean> resultAvailable = new ValueSignal<>(
            false);

    /**
     * Best partition found so far, kept for on-demand dendrogram generation.
     */
    @Nullable
    private Partition bestPartition;

    /**
     * Centroids matching the best partition found so far, carried by the search
     * events. Set whenever a result carries them and consumed when applying a
     * best-partition snapshot so the Partition tab can render real centroids
     * instead of empty ones.
     */
    @Nullable
    private InfiniteCentroids bestCentroids;

    /**
     * Whether jeffreys prior was requested for the current search, threaded
     * through to {@code TreeBuilder.makeTreePnn} when a dendrogram is built.
     */
    private boolean jeffreysPrior;

    private final ValueSignal<TreeModel> treeModel = new ValueSignal<>(null);

    /**
     * Whether the current search has finished or been cancelled, so late events
     * arriving after {@link #cancel()} are ignored and do not resurrect a dead
     * result on the Partition tab.
     */
    private boolean finished;

    @Nullable
    CompletableFuture<Object> task;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().factory());

    /** Lowest cluster count of the current range search, for progress. */
    private int kstart = 1;
    /** Highest cluster count of the current range search, for progress. */
    private int kstop = 1;

    @Nullable
    private GlaParametersForm form;

    /**
     * Creates a view model with empty data, not running, and no results yet.
     */
    public GlaViewModel() {
        this.data = new ValueSignal<>(null);
        this.running = new ValueSignal<>(false);
        this.uploadValid = new ValueSignal<>(
                new MultiFileMemoryBuffer.UploadValidation(false, ""));
        // Start with a non-empty model so the Partition component's initial
        // effect run (model.get().clusters()) does not NPE before any result
        // arrives, mirroring how graphPoints starts as an empty-but-valid
        // model.
        this.partitionModel = new ValueSignal<>(emptyPartition());
        this.graphPoints = new ValueSignal<>(emptyGraph());
        this.statusText = new ValueSignal<>("");
    }

    /**
     * Returns a valid non-empty placeholder graph model used before any data
     * has been loaded, so the bound chart never sees {@code null}.
     *
     * @return an empty-but-valid {@link GraphModel}
     * 
     * 
     *         /** Returns a valid non-empty placeholder partition model used
     *         before any result arrives, so the bound Partition component's
     *         initial effect run does not dereference a {@code null} value.
     *
     * @return an empty-but-valid {@link PartitionModel}
     */
    /**
     * Returns a valid non-empty placeholder graph model used before any data
     * has been loaded, so the bound chart never sees {@code null}.
     *
     * @return an empty-but-valid {@link GraphModel}
     */
    private static GraphModel emptyGraph() {
        return GraphModel.empty();
    }

    /**
     * Returns a valid non-empty placeholder partition model used before any
     * result arrives, so the bound Partition component's initial effect run
     * does not dereference a {@code null} value.
     *
     * @return an empty-but-valid {@link PartitionModel}
     */
    private static PartitionModel emptyPartition() {
        return new PartitionModel(Double.NaN, 0, 0, Double.NaN, List.of());
    }

    /**
     * Returns the signal holding the loaded vectors, or {@code null} when none
     * are loaded yet.
     *
     * @return the loaded-data signal
     */
    public ValueSignal<LoadedData> data() {
        return data;
    }

    /**
     * Returns the signal tracking whether a search is currently running.
     *
     * @return the running signal (initially {@code false})
     */
    public ValueSignal<Boolean> running() {
        return running;
    }

    /**
     * Returns the signal holding the best-partition presentation model, updated
     * live during a search and on completion.
     *
     * @return the partition-model signal (initially {@code null})
     */
    public ValueSignal<PartitionModel> partitionModel() {
        return partitionModel;
    }

    /**
     * Returns the signal holding the ordered SC-curve points, appended to as
     * each {@code k} is evaluated.
     *
     * @return the graph-model signal (initially an empty placeholder)
     */
    public ValueSignal<GraphModel> graphPoints() {
        return graphPoints;
    }

    /**
     * Returns the signal holding human-readable status text for the Search tab.
     *
     * @return the status-text signal (initially blank)
     */
    public ValueSignal<String> statusText() {
        return statusText;
    }

    /**
     * Derives whether a search can start: vectors are loaded and no other
     * search is running. Bound to the Run button's enabled state by the view.
     *
     * @return a signal that is {@code true} when a search may run
     */
    public Signal<Boolean> canRun() {
        return Signal.computed(() -> {
            // Read each leaf via tracked get() so the effect that binds this
            // signal registers a dependency on all four; peek() would leave
            // zero usages and Vaadin throws MissingSignalUsageException.
            LoadedData loaded = data.get();
            return loaded != null && loaded.vectorSet() != null
                    && !running.get()
                    && uploadValid.get().valid()
                    && formValid.get();
        });
    }

    /**
     * Returns the signal tracking whether every exposed field holds a valid
     * value. Bound to the Run button's enabled state by the view so a search
     * can start only once the form is valid, not merely after upload.
     *
     * @return the form-validity signal (initially invalid)
     */
    public Signal<Boolean> formValid() {
        return formValid;
    }

    /**
     * Returns the signal bound to the {@code kstart (-b)} field.
     */
    public ValueSignal<Integer> kStart() {
        return kStart;
    }

    /**
     * Returns the signal bound to the {@code kstop (-s)} field.
     */
    public ValueSignal<Integer> kStop() {
        return kStop;
    }

    /**
     * Returns the signal bound to the {@code kstopwhen (-S)} field.
     */
    public ValueSignal<Integer> kStopWhen() {
        return kStopWhen;
    }

    /**
     * Returns the signal bound to the {@code kcStopWhen (-W)} field.
     */
    public ValueSignal<Integer> kcStopWhen() {
        return kcStopWhen;
    }

    /**
     * Returns the signal bound to the {@code epsilon (-E)} field.
     */
    public ValueSignal<Double> epsilon() {
        return epsilon;
    }

    /**
     * Returns the signal bound to the {@code decreasing-epsilon} checkbox.
     */
    public ValueSignal<Boolean> decreasingEpsilon() {
        return decreasingEpsilon;
    }

    /**
     * Returns the signal bound to the distance-type select.
     */
    public ValueSignal<DistanceType> distanceType() {
        return distanceType;
    }

    /**
     * Returns the signal bound to the centroid-type select.
     */
    public ValueSignal<CentroidType> centroidType() {
        return centroidType;
    }

    /**
     * Returns the signal bound to the empty-cell-fix select.
     */
    public ValueSignal<EmptyCellFix> emptyCellFix() {
        return emptyCellFix;
    }

    /**
     * Returns the signal bound to the {@code maxIter (-n)} field.
     */
    public ValueSignal<Integer> maxIter() {
        return maxIter;
    }

    /**
     * Builds the current {@link GlaParametersForm} snapshot from the bound
     * parameter signals, falling back to {@link GLAConfig#DEFAULT} for values
     * not exposed on the form. Reactive: reads every signal so effects that
     * depend on it recompute when any field changes.
     */
    public Signal<GlaParametersForm> parameters() {
        return Signal.computed(() -> new GlaParametersForm(
                "",
                kStart.get(),
                kStop.get(),
                kStopWhen.get(),
                kcStopWhen.get(),
                epsilon.get(),
                decreasingEpsilon.get(),
                GLAConfig.DEFAULT.heuristic(),
                emptyCellFix.get().code(),
                false,
                centroidType.get().code(),
                false,
                false,
                false,
                false,
                false,
                0.0,
                false,
                false,
                distanceType.get().code(),
                maxIter.get(),
                GLAConfig.DEFAULT.heuristicCount(),
                GLAConfig.DEFAULT.safetyLimit(),
                GLAConfig.DEFAULT.iterBase(),
                ""));
    }

    /**
     * Returns the signal carrying search progress as a fraction in
     * {@code [0, 1]} for range searches; automatic searches keep an
     * indeterminate bar and this value is ignored while running.
     *
     * @return the progress signal (initially {@code 0})
     */
    public ValueSignal<Double> progress() {
        return progress;
    }

    /**
     * Returns whether the progress bar should animate indeterminate while a
     * search runs, as opposed to reporting a fixed fraction. True for automatic
     * searches whose end cluster count is unknown.
     *
     * @return {@code true} for automatic (indeterminate) searches
     */
    public ValueSignal<Boolean> progressIndeterminate() {
        return progressIndeterminate;
    }

    /**
     * Stores the resolved parameter form and publishes its validity so the Run
     * button can gate on it. Called by the view after the user fills in (or
     * resets) the controls.
     *
     * @param newForm
     *            the current form values, or {@code null} to clear them
     */
    public void setForm(@Nullable GlaParametersForm newForm) {
        this.form = newForm;
        if (newForm != null) {
            kStart.set(newForm.kStart());
            kStop.set(newForm.kStop());
            kStopWhen.set(newForm.kStopWhen());
            kcStopWhen.set(newForm.kcStopWhen());
            epsilon.set(newForm.epsilon());
            decreasingEpsilon.set(newForm.decreasingEpsilon());
            distanceType.set(DistanceType.fromCode(newForm.distanceType()));
            centroidType.set(CentroidType.fromCode(newForm.centroidType()));
            emptyCellFix.set(EmptyCellFix.fromCode(newForm.alternateMode()));
            maxIter.set(newForm.maxIter());
        }
    }

    /**
     * Returns the signal tracking whether exactly one header and one data file
     * were uploaded. Bound to the Run button's enabled state by the view so a
     * search can start only once the upload set is valid.
     *
     * @return the upload-validation signal (initially invalid)
     */
    public ValueSignal<MultiFileMemoryBuffer.UploadValidation> uploadValid() {
        return uploadValid;
    }

    /**
     * Stores the loaded vectors so subsequent searches have something to
     * cluster.
     *
     * @param vectorSet
     *            the parsed vectors (must not be {@code null})
     */
    public void setData(VectorSet vectorSet) {
        data.update(_ -> new LoadedData(vectorSet));
        statusText.update(_ -> "Loaded " + vectorSet.size() + " vectors.");
    }

    /**
     * Returns the stored parameter form, if any.
     *
     * @return the last form passed to {@link #setForm}, or {@code null}
     */
    @Nullable
    public GlaParametersForm getForm() {
        return form;
    }

    /**
     * Runs a search on the single-threaded executor. The Run button's enabled
     * state is bound to {@link #canRun()} by the view, which disables it while
     * a search runs and re-enables it on completion or cancellation, so no
     * explicit per-button disable is needed here. Builds the config from the
     * stored form and submits either an automatic or range search depending on
     * the resolved {@link SearchType}. Progress and best-partition events
     * update the graph and partition signals; completion sets the final result
     * signal.
     */
    public void run() {
        if (data.peek() == null) {
            return;
        }
        running.set(true);
        finished = false;
        bestCentroids = null;
        bestPartition = null;
        treeModel.update(_ -> null);
        statusText.set("Search started…");

        VectorSet vectorSet = data.peek().vectorSet();
        // Build from the bound parameter signals so a search always reflects
        // the latest field values rather than a stale snapshot. Read outside a
        // reactive context (run() is called from a click handler), so wrap the
        // computed read in Signal.untracked to allow inner signal.get() calls.
        GlaParametersForm boundForm = Signal
                .untracked(() -> parameters().get());
        GLAConfig config = boundForm.toConfig(vectorSet.size());
        SearchType type = boundForm.determineSearchType();

        // Track the evaluated range so the progress bar can report a fraction
        // for range searches; automatic (kstopwhen) searches stay
        // indeterminate.
        this.kstart = Math.max(1, boundForm.kStart());
        this.kstop = (type == SearchType.NAUTO && boundForm.kStop() > 0)
                ? boundForm.kStop()
                : vectorSet.size();
        // A bounded k-range (-b/-s) lets the bar report a fraction of the
        // work left; everything else (automatic scan, centroids, or an
        // iteration cap without an explicit range) stays indeterminate so a
        // maxIter value alone does not change how progress is shown.
        boolean hasExplicitRange = boundForm.kStart() > 1
                || boundForm.kStop() > 0;
        boolean indeterminate = !hasExplicitRange;
        if (!indeterminate) {
            progress.set(0.0);
        }
        progressIndeterminate.set(indeterminate);

        if (type == SearchType.NAUTO) {
            RangeSearch search = new RangeSearch(vectorSet, config);
            task = CompletableFuture
                    .supplyAsync(() -> search.run(kstart, kstop), executor);
        } else {
            AutomaticSearch search = new AutomaticSearch(vectorSet, config);
            task = CompletableFuture.supplyAsync(search::run, executor);
        }

        task.whenComplete((result, error) -> {
            running.set(false);
            // A finished search always reports a full bar, never a still-
            // animating indeterminate one. The view's ProgressBar spans
            // [0, 1] so 1.0 maps to 100%.
            progressIndeterminate.set(false);
            progress.set(1.0);
            if (error != null) {
                statusText.set("Search failed: " + error.getMessage());
            } else {
                statusText.set("Search complete.");
            }
        });
    }

    /**
     * Cancels the in-flight search, if any, and re-enables the Run button.
     * Called by the view's {@code onDetach} so no stray task outlives the UI.
     */
    public void cancel() {
        finished = true;
        bestCentroids = null;
        bestPartition = null;
        treeModel.update(_ -> null);
        if (task != null) {
            task.cancel(true);
            task = null;
        }
        running.set(false);
    }

    /**
     * Registers this view model as an event-bus listener. Called by the view on
     * attach.
     */
    public void register() {
        EventBus.get().registerEventBusListener(this);
    }

    /**
     * Unregisters the listener. Called by the view on detach.
     */
    public void unregister() {
        EventBus.get().unregisterEventBusListener(this);
    }

    @Override
    public void eventFired(AbstractEvent event) {
        if (finished) {
            return;
        }
        if (event instanceof SearchBestPartition(_, var result)) {
            applyResult(result);
        } else if (event instanceof SearchCompleted(_, var result)) {
            applyResult(result);
        } else if (event instanceof SearchProgress(_, var k, var sc, _)) {
            appendPoint(k, sc);
            statusText.set("Evaluated k=" + k + " (SC = "
                    + String.format(Locale.ROOT, "%.2f", sc) + ")");
            updateProgress(k);
        }
    }

    /**
     * Applies a best-partition snapshot: builds the partition model from the
     * carried {@link Partition} and updates both the partition and graph
     * signals.
     *
     * @param result
     *            the event payload (must not be {@code null})
     */
    private void applyResult(PartitionResult result) {
        if (finished) {
            return;
        }
        Partition partition = result.partition();
        // The search events now carry the best-partition centroids alongside
        // the partition, so remember them for the next model rebuild.
        bestCentroids = result.centroids();
        bestPartition = partition;
        jeffreysPrior = form != null && form.jeffreysPrior();
        boolean available = partition != null;
        resultAvailable.update(_ -> available);
        if (available) {
            assert bestCentroids != null
                    : "Best centroids must not be null when partition is available";
            partitionModel.update(_ -> GlaViewTranslator.toPartitionModel(
                    partition, bestCentroids, result.scmin()));
        }
    }

    /**
     * Returns whether a best partition has been produced by the current search,
     * so the view can show or hide its download controls. Bound to the same
     * signal that drives the Partition tab.
     *
     * @return the result-availability signal (initially {@code false})
     */
    public ValueSignal<Boolean> resultAvailable() {
        return resultAvailable;
    }

    /**
     * Returns the best-partition centroids captured from the most recent search
     * event, or {@code null} when none were carried. Used to build a centroid
     * download after completion.
     *
     * @return the best partition's centroids, or {@code null} if unavailable
     */
    @Nullable
    public InfiniteCentroids bestCentroids() {
        return bestCentroids;
    }

    /**
     * Returns the signal holding the flattened dendrogram model, populated when
     * {@link #generateTree()} completes. Bound to the Tree component by the
     * view so it re-renders automatically once generation finishes.
     *
     * @return the tree-model signal (initially {@code null})
     */
    public ValueSignal<TreeModel> treeModel() {
        return treeModel;
    }

    /**
     * Derives whether a dendrogram may be generated: a best partition exists
     * and no search is running. Bound to the Generate button's enabled state by
     * the view.
     *
     * @return a signal that is {@code true} when a dendrogram can be built
     */
    public Signal<Boolean> canGenerateTree() {
        return Signal.computed(() -> resultAvailable.get()
                && !running.get());
    }

    /**
     * Builds the dendrogram for the best partition on the single-threaded
     * executor. Reads the stored partition and centroids outside a reactive
     * context so it can be called from a click handler, then flattens the
     * resulting {@link TreeNode} into a {@link TreeModel} that updates the
     * bound signal when generation completes. A no-op when no result is
     * available yet.
     */
    public void generateTree() {
        Partition partition = bestPartition;
        if (partition == null || bestCentroids == null) {
            return;
        }
        boolean prior = jeffreysPrior;
        // Snapshot each cluster's label and size before makeTreePnn mutates the
        // partition in place, so leaf sizes reflect the true dataset rather
        // than
        // the post-merge counts makeTreePnn leaves behind.
        Map<String, String> labels = new HashMap<>();
        Map<String, Integer> sizes = new HashMap<>();
        GlaViewTranslator.buildLeafMaps(partition, labels, sizes);
        task = CompletableFuture.supplyAsync(
                () -> TreeBuilder.makeTreePnn(partition, bestCentroids, prior),
                executor);
        task.whenComplete((root, error) -> {
            if (error != null) {
                statusText.set("Dendrogram failed: " + error.getMessage());
                return;
            }
            treeModel.update(_ -> GlaViewTranslator.toTreeModel(partition,
                    (TreeNode) root, labels, sizes));
        });
    }

    /**
     * Appends a single SC point to the curve signal when its value is finite.
     *
     * @param k
     *            the cluster count just evaluated
     * @param sc
     *            the stochastic complexity for that count
     */
    private void appendPoint(int k, double sc) {
        if (!Double.isFinite(sc)) {
            return;
        }
        graphPoints.update(model -> {
            List<GraphPoint> next = new ArrayList<>(model.points());
            next.add(new GraphPoint(k, sc));
            return new GraphModel(next);
        });
    }

    /**
     * Advances the progress bar for a range search to reflect the current
     * cluster count within the evaluated {@code kstart..kstop} interval.
     * Automatic searches keep an indeterminate bar and are left untouched here.
     *
     * @param k
     *            the cluster count just evaluated
     */
    private void updateProgress(int k) {
        // Ignore progress events that arrive late (after the search finished),
        // so a completed bar stays at 100% instead of being reset to a stale
        // fraction by an event still draining from the event bus.
        if (!running.peek() || progressIndeterminate.peek()) {
            return;
        }
        int span = Math.max(1, kstop - kstart);
        double fraction = Math.min(1.0,
                Math.max(0.0, (k - kstart) / (double) span));
        progress.set(fraction);
    }

    /**
     * Convenience entry point mirroring {@link AutomaticSearch#run()} that
     * posts events through the event bus and returns the finished result.
     *
     * @param vectorSet
     *            the vectors to cluster (must not be {@code null})
     * @param config
     *            the GLA configuration (must not be {@code null})
     * @return the automatic-search result
     */
    public static AutomaticSearch.Result runAutomatic(VectorSet vectorSet,
            GLAConfig config) {
        return new AutomaticSearch(vectorSet, config).run();
    }

    /**
     * Convenience entry point mirroring {@link RangeSearch#run(int, int)} that
     * posts events through the event bus and returns the finished result.
     *
     * @param vectorSet
     *            the vectors to cluster (must not be {@code null})
     * @param config
     *            the GLA configuration (must not be {@code null})
     * @param kstart
     *            lowest cluster count to try (>= 1)
     * @param kstop
     *            highest cluster count to try (inclusive)
     * @return the range-search result
     */
    public static RangeSearch.Result runRange(VectorSet vectorSet,
            GLAConfig config,
            int kstart, int kstop) {
        return new RangeSearch(vectorSet, config).run(kstart, kstop);
    }
}
