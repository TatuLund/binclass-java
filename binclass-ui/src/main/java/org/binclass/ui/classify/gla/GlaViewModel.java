/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.gla;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
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
import org.binclass.components.graph.GraphModel;
import org.binclass.components.graph.GraphPoint;
import org.binclass.components.partition.PartitionModel;
import org.binclass.ui.MultiFileMemoryBuffer;

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
    private final ValueSignal<Boolean> formValid;
    private final ValueSignal<Double> progress = new ValueSignal<>(0.0);
    private final ValueSignal<Boolean> progressIndeterminate = new ValueSignal<>(
            false);

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().factory());
    @Nullable
    private CompletableFuture<Object> future;

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
        this.partitionModel = new ValueSignal<>(null);
        this.graphPoints = new ValueSignal<>(emptyGraph());
        this.statusText = new ValueSignal<>("");
        // The default form is valid (epsilon in range, kstop/kstopwhen not both
        // set), so the Run button starts enabled until the user changes a
        // field.
        this.formValid = new ValueSignal<>(
                GlaParametersForm.defaults().isValid());
    }

    /**
     * Returns a valid non-empty placeholder graph model used before any data
     * has been loaded, so the bound chart never sees {@code null}.
     *
     * @return an empty-but-valid {@link GraphModel}
     */
    private static GraphModel emptyGraph() {
        return new GraphModel(List.of(new GraphPoint(1, 0.0)));
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
    public ValueSignal<Boolean> formValid() {
        return formValid;
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
        this.formValid.update(_v -> newForm != null && newForm.isValid());
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
        data.update(_d -> new LoadedData(vectorSet));
        statusText.update(_s -> "Loaded " + vectorSet.size() + " vectors.");
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
        if (form == null || data.peek() == null) {
            return;
        }
        running.set(true);
        statusText.set("Search started…");

        VectorSet vectorSet = data.peek().vectorSet();
        // Capture the form in a local so its non-null state is preserved for
        // every access below, even though run() may outlive field narrowing.
        GlaParametersForm boundForm = form;
        GLAConfig config = boundForm.toConfig(vectorSet.size());
        SearchType type = boundForm.determineSearchType();

        // Track the evaluated range so the progress bar can report a fraction
        // for range searches; automatic (kstopwhen) searches stay
        // indeterminate.
        this.kstart = Math.max(1, form.kStart());
        this.kstop = (type == SearchType.NAUTO && form.kStop() > 0)
                ? form.kStop()
                : vectorSet.size();
        progress.set(0.0);
        progressIndeterminate.set(type != SearchType.NAUTO);

        CompletableFuture<Object> task;
        if (type == SearchType.NAUTO) {
            RangeSearch search = new RangeSearch(vectorSet, config);
            task = CompletableFuture
                    .supplyAsync(() -> search.run(kstart, kstop), executor);
        } else {
            AutomaticSearch search = new AutomaticSearch(vectorSet, config);
            task = CompletableFuture.supplyAsync(search::run, executor);
        }

        future = task.whenComplete((result, error) -> {
            running.set(false);
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
        if (future != null) {
            future.cancel(true);
            future = null;
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
        Partition partition = result.partition();
        if (partition != null) {
            InfiniteCentroids centroids = bestCentroidsFor(partition);
            partitionModel.update(_ -> GlaViewTranslator.toPartitionModel(
                    partition, centroids, result.scmin()));
        }
        graphPoints.update(_ -> GlaViewTranslator.toGraphModel(result.scs(),
                result.ks()));
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
        if (progressIndeterminate.peek()) {
            return;
        }
        int span = Math.max(1, kstop - kstart);
        double fraction = Math.min(1.0,
                Math.max(0.0, (k - kstart) / (double) span));
        progress.set(fraction);
    }

    /**
     * Returns the centroids matching a partition's cluster count, falling back
     * to an empty array per centroid when none are available.
     *
     * @param partition
     *            the best partition (must not be {@code null})
     * @return the matching centroid array, or {@code null} if unavailable
     */
    @Nullable
    private InfiniteCentroids bestCentroidsFor(Partition partition) {
        // The search events carry only scalar results plus the partition; the
        // full centroids travel with the Result. When absent, render an empty
        // centroid per cluster so the Partition tab still shows structure.
        return null;
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
