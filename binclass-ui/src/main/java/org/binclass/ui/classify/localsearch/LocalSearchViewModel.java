/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.localsearch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ListSignal;
import com.vaadin.flow.signals.local.ValueSignal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.events.AbstractEvent;
import org.binclass.algorithms.events.OperatorSuccess;
import org.binclass.algorithms.events.PartitionResult;
import org.binclass.algorithms.events.SearchBestPartition;
import org.binclass.algorithms.events.SearchCompleted;
import org.binclass.algorithms.events.SearchProgress;
import org.binclass.algorithms.eventbus.EventBus;
import org.binclass.algorithms.gla.AutomaticSearch;
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.RangeSearch;
import org.binclass.components.graph.GraphModel;
import org.binclass.components.graph.GraphPoint;
import org.binclass.components.partition.PartitionModel;
import org.binclass.ui.MultiFileMemoryBuffer;
import org.binclass.ui.classify.CentroidType;
import org.binclass.ui.classify.DistanceType;
import org.binclass.ui.classify.EmptyCellFix;
import org.binclass.ui.classify.Heuristic;

/**
 * UI-free view model for the Local Search display. Hosts all state as
 * {@link ValueSignal}s, runs searches on a single-threaded virtual-thread
 * executor so one user's searches are serialized, and implements
 * {@link EventBus#EventBusListener()} so it can react to per-{@code k}
 * progress, best-partition snapshots, and the new per-operator
 * {@link OperatorSuccess} events pushed by {@code LocalSearch.localSearch()}.
 * <p>
 * Follows the MVVM pattern from {@code docs/WebUI.PRD.md} §1.6: the view binds
 * its components to these signals once and forwards user actions as commands;
 * updating a signal here rebuilds the bound component automatically. No Vaadin
 * component is referenced inside, so it stays unit-testable in plain JUnit 6.
 * </p>
 */
@NullMarked
public final class LocalSearchViewModel implements EventBus.EventBusListener {

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
     * Per-operator success entries, one formatted string per successful local
     * search operator. Kept separate from {@link #statusText} so the Search tab
     * can render each improvement as its own list entry instead of a single
     * monolithic block of newline-joined text.
     */
    private final ListSignal<String> operatorLog = new ListSignal<>();

    /**
     * Exposed parameter signals, bound two-way to the form fields by the view.
     */
    private final ValueSignal<Heuristic> heuristic = new ValueSignal<>(
            Heuristic.fromCode(GLAConfig.DEFAULT.heuristic()));
    private final ValueSignal<Integer> heuristicCount = new ValueSignal<>(
            GLAConfig.DEFAULT.heuristicCount());
    private final ValueSignal<Double> epsilon = new ValueSignal<>(
            GLAConfig.DEFAULT.epsilon());
    private final ValueSignal<Boolean> decreasingEpsilon = new ValueSignal<>(
            false);
    private final ValueSignal<EmptyCellFix> emptyCellFix = new ValueSignal<>(
            EmptyCellFix.WORST_MATCH);
    private final ValueSignal<CentroidType> centroidType = new ValueSignal<>(
            CentroidType.fromCode(GLAConfig.DEFAULT.centroidType()));
    private final ValueSignal<DistanceType> distanceType = new ValueSignal<>(
            DistanceType.fromCode(GLAConfig.DEFAULT.distanceType()));

    /** Reactive form validity derived from the parameter signals above. */
    private final Signal<Boolean> formValid = Signal.computed(() -> {
        double eps = epsilon.get();
        return decreasingEpsilon.get() || (eps > 0.0 && eps < 0.5);
    });

    private final ValueSignal<Double> progress = new ValueSignal<>(0.0);
    private final ValueSignal<Boolean> progressIndeterminate = new ValueSignal<>(
            false);
    private final ValueSignal<Boolean> resultAvailable = new ValueSignal<>(
            false);
    private final ValueSignal<Boolean> canDownload = new ValueSignal<>(false);

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().factory());

    /** Lowest cluster count of the current range search, for progress. */
    private int kstart = 1;
    /** Highest cluster count of the current range search, for progress. */
    private int kstop = 1;

    @Nullable
    private LocalSearchParametersForm form;

    /**
     * Centroids matching the best partition found so far, carried by the search
     * events. Set whenever a result carries them and consumed when applying a
     * best-partition snapshot so the Partition tab can render real centroids
     * instead of empty ones.
     */
    @Nullable
    private InfiniteCentroids bestCentroids;

    /**
     * Whether the current search has finished or been cancelled, so late events
     * arriving after {@link #cancel()} are ignored and do not resurrect a dead
     * result on the Partition tab.
     */
    private boolean finished;

    @Nullable
    CompletableFuture<Object> task;

    /**
     * Creates a view model with empty data, not running, and no results yet.
     */
    public LocalSearchViewModel() {
        this.data = new ValueSignal<>(null);
        this.running = new ValueSignal<>(false);
        this.uploadValid = new ValueSignal<>(
                new MultiFileMemoryBuffer.UploadValidation(false, ""));
        // Start with a non-empty model so the Partition component's initial
        // effect run (model.get().clusters()) does not NPE before any result
        // arrives.
        this.partitionModel = new ValueSignal<>(emptyPartition());
        this.graphPoints = new ValueSignal<>(emptyGraph());
        this.statusText = new ValueSignal<>("");
    }

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
     * Returns the list signal holding one formatted per-operator success entry
     * per successful local search operator. Each entry is appended as an
     * {@link OperatorSuccess} event arrives and cleared when a new search
     * starts, so the Search tab can render each improvement separately from the
     * running status text.
     *
     * @return the operator-log list signal (initially empty)
     */
    public ListSignal<String> operatorLog() {
        return operatorLog;
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
     * Returns the signal bound to the heuristic select.
     */
    public ValueSignal<Heuristic> heuristic() {
        return heuristic;
    }

    /**
     * Returns the signal bound to the {@code heuristicCount (-j)} field.
     */
    public ValueSignal<Integer> heuristicCount() {
        return heuristicCount;
    }

    /**
     * Returns the signal bound to the {@code epsilon (-E)} field.
     */
    public ValueSignal<Double> epsilon() {
        return epsilon;
    }

    /**
     * Returns the signal bound to the decreasing-epsilon checkbox.
     */
    public ValueSignal<Boolean> decreasingEpsilon() {
        return decreasingEpsilon;
    }

    /**
     * Returns the signal bound to the empty-cell-fix select.
     */
    public ValueSignal<EmptyCellFix> emptyCellFix() {
        return emptyCellFix;
    }

    /**
     * Returns the signal bound to the centroid-type select.
     */
    public ValueSignal<CentroidType> centroidType() {
        return centroidType;
    }

    /**
     * Returns the signal bound to the distance-type select.
     */
    public ValueSignal<DistanceType> distanceType() {
        return distanceType;
    }

    /**
     * Builds the current {@link LocalSearchParametersForm} snapshot from the
     * bound parameter signals, falling back to {@link GLAConfig#DEFAULT} for
     * values not exposed on the form. Reactive: reads every signal so effects
     * that depend on it recompute when any field changes.
     */
    public Signal<LocalSearchParametersForm> parameters() {
        return Signal.computed(() -> new LocalSearchParametersForm(
                "",
                heuristic.get().code(),
                heuristicCount.get(),
                epsilon.get(),
                decreasingEpsilon.get(),
                emptyCellFix.get().code(),
                centroidType.get().code(),
                distanceType.get().code(),
                false));
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
    public void setForm(@Nullable LocalSearchParametersForm newForm) {
        this.form = newForm;
        if (newForm != null) {
            heuristic.set(Heuristic.fromCode(newForm.heuristic()));
            heuristicCount.set(newForm.heuristicCount());
            epsilon.set(newForm.epsilon());
            decreasingEpsilon.set(newForm.decreasingEpsilon());
            emptyCellFix.set(EmptyCellFix.fromCode(newForm.alternateMode()));
            centroidType.set(CentroidType.fromCode(newForm.centroidType()));
            distanceType.set(DistanceType.fromCode(newForm.distanceType()));
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
    public LocalSearchParametersForm getForm() {
        return form;
    }

    /**
     * Runs a search on the single-threaded executor. The Run button's enabled
     * state is bound to {@link #canRun()} by the view, which disables it while
     * a search runs and re-enables it on completion or cancellation, so no
     * explicit per-button disable is needed here. Builds the config from the
     * stored form and submits either an automatic or range search depending on
     * the resolved {@link org.binclass.algorithms.gla.SearchType}. Progress and
     * best-partition events update the graph and partition signals; completion
     * sets the final result signal.
     */
    public void run() {
        if (data.peek() == null) {
            return;
        }
        running.set(true);
        finished = false;
        bestCentroids = null;
        operatorLog.clear();
        statusText.set("Search started…");

        VectorSet vectorSet = data.peek().vectorSet();
        // Build from the bound parameter signals so a search always reflects
        // the latest field values rather than a stale snapshot. Read outside a
        // reactive context (run() is called from a click handler), so wrap the
        // computed read in Signal.untracked to allow inner signal.get() calls.
        LocalSearchParametersForm boundForm = Signal
                .untracked(() -> parameters().get());
        GLAConfig config = boundForm.toConfig(vectorSet.size());

        this.kstart = 1;
        this.kstop = vectorSet.size();
        progressIndeterminate.set(true);

        if (config.maxIter() > 0) {
            RangeSearch search = new RangeSearch(vectorSet, config,
                    "localsearch");
            task = CompletableFuture
                    .supplyAsync(() -> search.run(kstart, kstop), executor);
        } else {
            AutomaticSearch search = new AutomaticSearch(vectorSet, config,
                    "localsearch");
            task = CompletableFuture.supplyAsync(search::run, executor);
        }
        task.whenComplete((result, error) -> {
            running.set(false);
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
        } else if (event instanceof OperatorSuccess(_, var k, var code,
                var delta, var operator)) {
            appendOperatorLine(k, code, delta, operator);
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
        boolean available = partition != null;
        resultAvailable.update(_ -> available);
        canDownload.update(_ -> available);
        if (available) {
            assert bestCentroids != null
                    : "Best centroids must not be null when partition is available";
            partitionModel
                    .update(_ -> LocalSearchViewTranslator.toPartitionModel(
                            partition, bestCentroids, result.scmin()));
        }
    }

    /**
     * Appends a human-readable per-operator success line to the operator log so
     * the Search tab can surface which strategy lowered SC mid-run as its own
     * list entry. The delta is reported here, so it must be non-zero for an
     * improvement; callers only post {@link OperatorSuccess} when SC dropped.
     *
     * @param k
     *            the cluster count being refined
     * @param code
     *            the {@code -r} heuristic code that selected the driver
     * @param delta
     *            the reduction in stochastic complexity achieved by this
     *            operator
     * @param operator
     *            the name of the applied operator
     */
    private void appendOperatorLine(int k, int code, double delta,
            String operator) {
        operatorLog.insertLast(
                String.format(Locale.ROOT, "k=%d: %s improved SC by %.2f",
                        k, operator.toLowerCase(), delta));
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
     * Returns whether a download may start: a best partition exists and no
     * search is running. Bound to the view's download controls so they stay
     * disabled while a search is in flight.
     *
     * @return the can-download signal (initially {@code false})
     */
    public ValueSignal<Boolean> canDownload() {
        return canDownload;
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
