/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.cumulative;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ListSignal;
import com.vaadin.flow.signals.local.ValueSignal;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.binclass.algorithms.classify.CumulativeConfig;
import org.binclass.algorithms.core.DynamicPartition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.events.AbstractEvent;
import org.binclass.algorithms.events.CumulativeProgress;
import org.binclass.algorithms.events.NewCluster;
import org.binclass.algorithms.eventbus.EventBus;
import org.binclass.components.partition.PartitionModel;
import org.binclass.components.tree.TreeModel;
import org.binclass.ui.MultiFileMemoryBuffer;

/**
 * UI-free view model for the Cumulative Classification display. Hosts all state
 * as {@link ValueSignal}s, runs classification on a single-threaded
 * virtual-thread executor so one user's runs are serialized, and implements
 * {@link EventBus#EventBusListener()} so it can react to progress and
 * new-cluster events pushed by the algorithm.
 * <p>
 * Follows the MVVM pattern from {@code docs/WebUI.PRD.md} §5: the view binds
 * its components to these signals once and forwards user actions as commands;
 * updating a signal here rebuilds the bound component automatically. No Vaadin
 * component is referenced inside, so it stays unit-testable in plain JUnit 6.
 * </p>
 */
@NullMarked
public final class CumulativeViewModel implements EventBus.EventBusListener {

    /** Loaded vectors plus their configuration, held as a single signal. */
    public record LoadedData(VectorSet vectorSet) {
    }

    private final ValueSignal<LoadedData> data;
    private final ValueSignal<Boolean> running;
    private final ValueSignal<MultiFileMemoryBuffer.UploadValidation> uploadValid;
    private final ValueSignal<PartitionModel> partitionModel;
    private final ValueSignal<String> statusText;
    private final ListSignal<String> logLines;

    /**
     * Exposed parameter signals, bound two-way to the form fields by the view.
     */
    private final ValueSignal<Integer> delta = new ValueSignal<>(1);
    private final ValueSignal<Boolean> fixedDelta = new ValueSignal<>(false);
    private final ValueSignal<Boolean> cumulativeAnalysis = new ValueSignal<>(
            false);
    private final ValueSignal<Integer> cumulativeSamples = new ValueSignal<>(0);
    private final ValueSignal<Boolean> bayesianPredictive = new ValueSignal<>(
            true);
    private final ValueSignal<Boolean> testFeatureSignificance = new ValueSignal<>(
            false);
    private final ValueSignal<Boolean> cumSaveByPf = new ValueSignal<>(true);
    private final ValueSignal<Boolean> cumNoNewClasses = new ValueSignal<>(
            false);
    private final ValueSignal<Boolean> inOrder = new ValueSignal<>(false);
    private final ValueSignal<Double> epsilon = new ValueSignal<>(
            CumulativeConfig.defaults().epsilon());

    /** Reactive form validity derived from the parameter signals above. */
    private final Signal<Boolean> formValid = Signal.computed(() -> {
        double eps = epsilon.get();
        return delta.get() >= 0 && eps > 0.0 && eps < 0.5;
    });

    /**
     * Determinate progress in {@code [0, 1]}, advanced in ten-percent steps.
     */
    private final ValueSignal<Double> progress = new ValueSignal<>(0.0);
    private final ValueSignal<Boolean> resultAvailable = new ValueSignal<>(
            false);

    private final ValueSignal<DynamicPartition> dynamicPartition = new ValueSignal<>(
            null);

    /**
     * Best dynamic partition produced by the current run, kept for on-demand
     * dendrogram generation.
     */
    @Nullable
    private DynamicPartition bestDynamicPartition;

    /**
     * Whether uniform prior was requested for the current run, threaded through
     * to {@code TreeBuilder.makeTreePnn} when a dendrogram is built. Cumulative
     * has no jeffreys-prior toggle, so this stays false.
     */
    private boolean uniformPrior;

    /**
     * Signal holding the flattened dendrogram model, populated when
     * {@link #generateTree()} completes. Bound to the Tree component by the
     * view so it re-renders automatically once generation finishes.
     */
    private final ValueSignal<TreeModel> treeModel = new ValueSignal<>(null);

    /**
     * Whether classification has finished or been cancelled, so late events are
     * ignored.
     */
    private boolean finished;

    @Nullable
    CompletableFuture<DynamicPartition> task;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(
            Thread.ofVirtual().factory());

    @Nullable
    private CumulativeParametersForm form;

    /**
     * Creates a view model with empty data, not running, and no results yet.
     */
    public CumulativeViewModel() {
        this.data = new ValueSignal<>(null);
        this.running = new ValueSignal<>(false);
        this.uploadValid = new ValueSignal<>(
                new MultiFileMemoryBuffer.UploadValidation(false, ""));
        this.partitionModel = new ValueSignal<>(emptyPartition());
        this.statusText = new ValueSignal<>("");
        this.logLines = new ListSignal<>();
    }

    private static PartitionModel emptyPartition() {
        return new org.binclass.components.partition.PartitionModel(
                Double.NaN, 0, 0, Double.NaN, List.of());
    }

    /**
     * Returns the signal holding the loaded vectors, or {@code null} when none
     * are loaded.
     */
    public ValueSignal<LoadedData> data() {
        return data;
    }

    /**
     * Returns the signal tracking whether classification is currently running.
     */
    public ValueSignal<Boolean> running() {
        return running;
    }

    /**
     * Returns the signal holding the upload validation state for the Run
     * button.
     */
    public ValueSignal<MultiFileMemoryBuffer.UploadValidation> uploadValid() {
        return uploadValid;
    }

    /**
     * Returns the signal holding the partition presentation model, updated live
     * and on completion.
     */
    public ValueSignal<PartitionModel> partitionModel() {
        return partitionModel;
    }

    /**
     * Returns the signal holding human-readable status text for the display.
     */
    public ValueSignal<String> statusText() {
        return statusText;
    }

    /**
     * Returns the list signal holding one formatted log line per new cluster
     * created during classification. Each entry is appended as a
     * {@link NewCluster} event arrives and cleared when a new run starts, so
     * the view can render each line separately from the status text.
     */
    public ListSignal<String> logLines() {
        return logLines;
    }

    /**
     * Returns the signal tracking progress as a fraction in {@code [0, 1]},
     * advanced in ten-percent steps.
     */
    public ValueSignal<Double> progress() {
        return progress;
    }

    /**
     * Returns the signal carrying the finished dynamic partition for download.
     */
    public ValueSignal<DynamicPartition> dynamicPartition() {
        return dynamicPartition;
    }

    /**
     * Derives whether classification can start: vectors are loaded and no other
     * run is in flight.
     */
    public Signal<Boolean> canRun() {
        return Signal.computed(() -> {
            LoadedData loaded = data.get();
            return loaded != null && loaded.vectorSet() != null
                    && !running.get()
                    && uploadValid.get().valid()
                    && formValid.get();
        });
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
     * and no run is in flight. Bound to the Generate button's enabled state by
     * the view.
     *
     * @return a signal that is {@code true} when a dendrogram can be built
     */
    public Signal<Boolean> canGenerateTree() {
        return Signal.computed(() -> resultAvailable.get()
                && !running.get());
    }

    /**
     * Builds the PNN dendrogram for the best dynamic partition on the
     * single-threaded executor. Reads the stored partition outside a reactive
     * context so it can be called from a click handler, then flattens the
     * resulting {@link org.binclass.algorithms.core.TreeNode} into a
     * {@link TreeModel} that updates the bound signal when generation
     * completes. A no-op when no result is available yet. Cumulative has no
     * jeffreys-prior toggle, so the tree always uses uniform prior to match the
     * partition model.
     */
    public void generateTree() {
        DynamicPartition dynPart = bestDynamicPartition;
        if (dynPart == null) {
            return;
        }
        CompletableFuture<TreeModel> treeTask = CompletableFuture.supplyAsync(
                () -> CumulativeViewTranslator.toTreeModel(dynPart), executor);
        treeTask.whenComplete((model, error) -> {
            if (error != null) {
                statusText.set("Dendrogram failed: " + error.getMessage());
                return;
            }
            treeModel.update(_ -> model);
        });
    }

    /** Returns the signal bound to the epsilon field. */
    public ValueSignal<Double> epsilon() {
        return epsilon;
    }

    /** Returns the signal bound to the delta field. */
    public ValueSignal<Integer> delta() {
        return delta;
    }

    /** Returns the signal bound to the fixedDelta checkbox. */
    public ValueSignal<Boolean> fixedDelta() {
        return fixedDelta;
    }

    /** Returns the signal bound to the cumulativeAnalysis checkbox. */
    public ValueSignal<Boolean> cumulativeAnalysis() {
        return cumulativeAnalysis;
    }

    /** Returns the signal bound to the cumulativeSamples field. */
    public ValueSignal<Integer> cumulativeSamples() {
        return cumulativeSamples;
    }

    /** Returns the signal bound to the bayesianPredictive checkbox. */
    public ValueSignal<Boolean> bayesianPredictive() {
        return bayesianPredictive;
    }

    /** Returns the signal bound to the featureSignificance checkbox. */
    public ValueSignal<Boolean> testFeatureSignificance() {
        return testFeatureSignificance;
    }

    /** Returns the signal bound to the cumSaveByPf checkbox. */
    public ValueSignal<Boolean> cumSaveByPf() {
        return cumSaveByPf;
    }

    /** Returns the signal bound to the cumNoNewClasses checkbox. */
    public ValueSignal<Boolean> cumNoNewClasses() {
        return cumNoNewClasses;
    }

    /** Returns the signal bound to the inOrder checkbox. */
    public ValueSignal<Boolean> inOrder() {
        return inOrder;
    }

    /**
     * Returns the signal tracking whether a result has been produced by the
     * current run.
     */
    public ValueSignal<Boolean> resultAvailable() {
        return resultAvailable;
    }

    /** The form-validity signal, derived from the bound parameter signals. */
    public Signal<Boolean> formValid() {
        return formValid;
    }

    /**
     * Stores the resolved parameter form and publishes its validity so the Run
     * button can gate on it.
     */
    public void setForm(@Nullable CumulativeParametersForm newForm) {
        this.form = newForm;
        if (newForm != null) {
            delta.set(newForm.delta());
            fixedDelta.set(newForm.fixedDelta());
            cumulativeAnalysis.set(newForm.cumulativeAnalysis());
            cumulativeSamples.set(newForm.cumulativeSamples());
            bayesianPredictive.set(newForm.bayesianPredictive());
            testFeatureSignificance.set(newForm.testFeatureSignificance());
            cumSaveByPf.set(newForm.cumSaveByPf());
            cumNoNewClasses.set(newForm.cumNoNewClasses());
            inOrder.set(newForm.inOrder());
            epsilon.set(newForm.epsilon());
        }
    }

    /** Returns the stored parameter form, if any. */
    @Nullable
    public CumulativeParametersForm getForm() {
        return form;
    }

    /**
     * Stores the loaded vectors so subsequent runs have something to classify.
     */
    public void setData(VectorSet vectorSet) {
        data.update(_ -> new LoadedData(vectorSet));
        statusText.update(_ -> "Loaded " + vectorSet.size() + " vectors.");
    }

    /** Runs cumulative classification on the single-threaded executor. */
    public void run() {
        if (data.peek() == null) {
            return;
        }
        running.set(true);
        finished = false;
        progress.set(0.0);
        statusText.set("Cumulative classification started…");

        VectorSet vectorSet = data.peek().vectorSet();
        CumulativeParametersForm boundForm = Signal
                .untracked(() -> buildForm());
        CumulativeConfig config = boundForm.toConfig(vectorSet.size());

        task = CompletableFuture.supplyAsync(
                () -> CumulativeViewTranslator.run(vectorSet, config),
                executor);

        task.whenComplete((result, error) -> {
            running.set(false);
            progress.set(1.0);
            if (error != null) {
                statusText.set("Classification failed: " + error.getMessage());
                resultAvailable.update(_ -> false);
            } else {
                statusText.set("Cumulative classification complete.");
                dynamicPartition.update(_ -> result);
                bestDynamicPartition = result;
                uniformPrior = form != null && !form.bayesianPredictive();
                partitionModel.update(
                        _ -> CumulativeViewTranslator.toPartitionModel(result));
                resultAvailable.update(_ -> true);
            }
        });
    }

    /**
     * Builds the current {@link CumulativeParametersForm} snapshot from the
     * bound parameter signals.
     */
    private CumulativeParametersForm buildForm() {
        return Signal.untracked(() -> new CumulativeParametersForm(
                "",
                delta.get(),
                fixedDelta.get(),
                cumulativeAnalysis.get(),
                cumulativeSamples.get(),
                bayesianPredictive.get(),
                testFeatureSignificance.get(),
                cumSaveByPf.get(),
                cumNoNewClasses.get(),
                inOrder.get(),
                epsilon.get()));
    }

    /** Cancels the in-flight classification and re-enables the Run button. */
    public void cancel() {
        finished = true;
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

    /** Unregisters the listener. Called by the view on detach. */
    public void unregister() {
        EventBus.get().unregisterEventBusListener(this);
    }

    @Override
    public void eventFired(AbstractEvent event) {
        if (finished) {
            return;
        }
        if (event instanceof CumulativeProgress(_, var processed)) {
            updateProgress(processed);
        } else if (event instanceof NewCluster(_, var clusterIndex, var sc)) {
            appendLogLine(clusterIndex, sc);
        }
    }

    private void updateProgress(int processed) {
        VectorSet vectorSet = data.peek() != null ? data.peek().vectorSet()
                : null;
        if (vectorSet == null || vectorSet.size() == 0) {
            return;
        }
        int rounded = Math.round((float) processed / vectorSet.size() * 10f)
                * 10;
        progress.set(Math.min(1.0, Math.max(0.0, (double) rounded / 100.0)));
    }

    private void appendLogLine(int clusterIndex, @Nullable Double sc) {
        String line;
        if (sc != null) {
            line = "Class " + clusterIndex + " created (SC = "
                    + String.format(java.util.Locale.ROOT, "%.3f", sc) + ")";
        } else {
            line = "Class " + clusterIndex + " created";
        }
        logLines.insertLast(line);
    }

    /**
     * Convenience entry point that runs classification and returns the dynamic
     * partition.
     */
    public static DynamicPartition run(VectorSet vectors,
            CumulativeConfig config) {
        return CumulativeViewTranslator.run(vectors, config);
    }
}
