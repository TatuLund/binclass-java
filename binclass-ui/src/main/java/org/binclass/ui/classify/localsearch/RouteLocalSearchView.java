/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.localsearch;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.dom.Style.Overflow;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.router.BeforeLeaveEvent;
import com.vaadin.flow.router.BeforeLeaveObserver;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.io.CentroidWriter;
import org.binclass.algorithms.io.PartitionWriter;
import org.binclass.components.download.DownloadAnchor;
import org.binclass.components.graph.Graph;
import org.binclass.components.partition.Partition;
import org.binclass.components.partition.PartitionModel;
import org.binclass.components.report.ReportComponent;
import org.binclass.components.tree.Tree;
import org.binclass.ui.MultiFileMemoryBuffer;
import org.binclass.ui.UploadedDataLoader;
import org.binclass.ui.classify.CentroidType;
import org.binclass.ui.classify.DistanceType;
import org.binclass.ui.classify.EmptyCellFix;
import org.binclass.ui.classify.Heuristic;
import org.binclass.ui.classify.ReportTab;

/**
 * Local Search display — focused on heuristic local search (cycler {@code -r7},
 * adaptive {@code -r8}). Reuses the GLA parameter form shape but narrows scope
 * to heuristic-related flags and shows per-operator progress. Follows the MVVM
 * pattern from {@code docs/WebUI.PRD.md} §1.6: owns a
 * {@link LocalSearchViewModel}, binds its components to the VM's signals once,
 * forwards user actions as commands, and reacts to events through the VM's
 * listener.
 */
@NullMarked
@Route("local-search")
@Menu(title = "Local Search", order = 2, icon = "vaadin:crosshairs")
public class RouteLocalSearchView extends VerticalLayout
        implements BeforeLeaveObserver {

    private final LocalSearchViewModel viewModel = new LocalSearchViewModel();

    private final FormLayout paramsForm = new FormLayout();
    private final MultiFileMemoryBuffer memoryBuffer = new MultiFileMemoryBuffer();
    private final Upload uploader = new Upload(createUploadHandler());
    private final Select<Heuristic> heuristicSelect = new Select<>();
    private final IntegerField heuristicCountField = new IntegerField(
            "heuristicCount (-j)");
    private final NumberField epsilonField = new NumberField("epsilon (-E)");
    private final Checkbox decreasingEpsCheckbox = new Checkbox(
            "decreasing-epsilon");
    private final Select<DistanceType> distanceTypeSelect = new Select<>();
    private final Select<CentroidType> centroidTypeSelect = new Select<>();
    private final Select<EmptyCellFix> emptyCellFixSelect = new Select<>();
    private final Checkbox jeffreysPriorCheckbox = new Checkbox(
            "jeffreysPrior (-J)");
    private final Button runButton = new Button("Run");
    private final Button newSearchButton = new Button("New search");

    private final TabSheet localTabs = new TabSheet();
    private final Card searchCard = new Card();
    // Range [0, 1] so the view model's fractional progress maps directly to a
    // percentage; setValue(1.0) shows a full bar on completion.
    private final ProgressBar progress = new ProgressBar(0, 1);
    private final Div statusText = new Div();
    private final Span operatorLabel = new Span("Operator improvements");
    private final OperatorLog operatorLog = new OperatorLog();
    private final VerticalLayout summaryContent = new VerticalLayout();
    private final Graph graphSC = new Graph();
    private final Partition partitionView = new Partition();
    private final ReportComponent reportComponent = new ReportComponent();
    private final Tree dendrogram = new Tree();
    private final Button generateTreeButton = new Button("Generate dendrogram");
    private final Scroller dendrogramScroller = new Scroller();

    // Download controls for the best partition and its logged centroids, shown
    // once a search completes. Built lazily so they only exist after a result.
    private DownloadAnchor partitionDownload;

    private DownloadAnchor centroidsDownload;
    private HorizontalLayout downloadControls;

    @Nullable
    private Registration unregisterRegistration;

    @Nullable
    private Registration reportBinding;

    @SuppressWarnings("null")
    public RouteLocalSearchView() {
        setId("local-search-view");
        setSizeFull();
        setSpacing(true);
        setPadding(true);

        buildParamsForm();
        buildTabs();

        add(paramsForm, localTabs);

        // Bind signals to components.
        runButton.setEnabled(true);
        runButton.bindEnabled(viewModel.canRun());
        graphSC.bindModel(viewModel.graphPoints());
        partitionView.bindPartitionModel(viewModel.partitionModel());
        reportComponent.setId("report-component-localsearch");
        reportBinding = ReportTab.bind(reportComponent,
                viewModel.partitionModel());

        generateTreeButton.setEnabled(true);
        generateTreeButton.setId("generate-tree-local");
        generateTreeButton.bindEnabled(viewModel.canGenerateTree());
        generateTreeButton.addClickListener(_ -> viewModel.generateTree());
        dendrogram.setId("dendrogram-local");
        dendrogram.bindModel(viewModel.treeModel());

        // Progress bar: indeterminate for automatic searches, a fraction of the
        // evaluated k-range otherwise. The status text Div is driven by its own
        // signal so search progress and completion stay in sync.
        progress.bindValue(viewModel.progress());
        Signal.effect(progress,
                () -> progress.setIndeterminate(
                        viewModel.progressIndeterminate().get()));
        statusText.bindText(viewModel.statusText());

        // Visual operator log: legend, per-{@code k} success squares and the
        // completion statistic bars. Bound to its own model signal so it
        // re-renders live as operators succeed and finalises on completion.
        operatorLog.setId("local-operator-log");
        operatorLog.bindModel(viewModel.operatorLogModel());

        // Refresh the download controls whenever a best partition appears or is
        // cleared. The controls are built lazily on first refresh so they only
        // exist after a result arrives.
        Signal.effect(this, () -> {
            viewModel.resultAvailable().get();
            refreshDownloadState();
        });

        memoryBuffer.setOnReady(this::loadUploadedData);
        uploader.addAllFinishedListener(_ -> viewModel.uploadValid()
                .update(v -> memoryBuffer.validate()));

        runButton.addClickListener(_ -> {
            viewModel.setForm(buildForm());
            viewModel.run();
            renderSummary();
            paramsForm.setVisible(false);
            localTabs.setVisible(true);
        });
        newSearchButton.addClickListener(_ -> {
            viewModel.cancel();
            paramsForm.setVisible(true);
            localTabs.setVisible(false);
        });

        viewModel.register();
        unregisterRegistration = () -> viewModel.unregister();
    }

    @Override
    public void beforeLeave(BeforeLeaveEvent event) {
        if (!viewModel.running().peek()) {
            return;
        }

        BeforeLeaveEvent.ContinueNavigationAction action = event
                .postpone();
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Search in progress");
        dialog.setText(
                "A local search is still running. Cancel it and navigate away?");
        dialog.setConfirmText("Cancel search");
        dialog.setConfirmButtonTheme("error primary");
        dialog.setCancelable(true);
        dialog.addConfirmListener(_ -> {
            viewModel.cancel();
            action.proceed();
        });
        dialog.addCancelListener(_ -> action.cancel());
        dialog.open();
    }

    private void buildParamsForm() {
        paramsForm.setId("local-params");
        uploader.setId("uploader-gla");
        uploader.setMaxFiles(2);
        // A Receiver is set (MultiFileMemoryBuffer), so server-side extension
        // validation would require an UploadHandler. Use client-side-only hints
        // instead; the view validates the exact file pair on upload finished.
        uploader.setAcceptedFileExtensions(".data", ".hdr", ".header");
        Tooltip.forComponent(uploader).setText(
                "Upload exactly two files: one .data file and one .header file. "
                        + "Selecting both at once is recommended.");
        heuristicSelect.setId("heuristic");
        heuristicCountField.setId("heuristic-count");
        epsilonField.setId("epsilon");
        decreasingEpsCheckbox.setId("decreasing-eps");
        distanceTypeSelect.setId("distance-type");
        centroidTypeSelect.setId("centroid-type");
        emptyCellFixSelect.setId("empty-cell-fix");
        jeffreysPriorCheckbox.setId("jeffreys-prior");
        runButton.setId("run-local");
        newSearchButton.setId("new-search");

        // Labels and tooltips for every control.
        heuristicSelect.setLabel("heuristic (-r)");
        heuristicSelect.setTooltipText(
                "Local search operator (1..8). 7 cycles through all operators, "
                        + "8 adapts selection by success rate.");
        heuristicCountField.setLabel("heuristicCount (-j)");
        heuristicCountField.setTooltipText(
                "Number of operator applications. One is added internally.");
        epsilonField.setLabel("epsilon (-E)");
        epsilonField.setTooltipText(
                "Convergence threshold in the open interval (0, 0.5). Toggle "
                        + "decreasing-epsilon to reset and halve it each iteration.");
        decreasingEpsCheckbox.setLabel("decreasing-epsilon");
        decreasingEpsCheckbox.setTooltipText(
                "When checked, epsilon is treated as a bare value that resets and "
                        + "halves each GLA iteration instead of being validated.");
        distanceTypeSelect.setLabel("distance type (-f)");
        distanceTypeSelect.setTooltipText(
                "Distance metric used to measure vectors. Hybrids combine a metric "
                        + "with Shannon codelength.");
        centroidTypeSelect.setLabel("centroid type (-c)");
        centroidTypeSelect.setTooltipText(
                "Strategy used to build initial centroids.");
        emptyCellFixSelect.setLabel("empty cell fix (-eX)");
        emptyCellFixSelect.setTooltipText(
                "How to fill an empty cluster: replace its centroid by the worst-match "
                        + "vector, a random vector, or either plus an extra iteration.");
        jeffreysPriorCheckbox.setLabel("jeffreysPrior (-J)");
        jeffreysPriorCheckbox.setTooltipText(
                "Use the Jeffreys prior for stochastic complexity instead of a "
                        + "uniform prior.");

        // Enum selects with friendly labels instead of bare numeric codes.
        heuristicSelect.setItems(Heuristic.values());
        heuristicSelect.setItemLabelGenerator(Heuristic::label);
        distanceTypeSelect.setItems(DistanceType.values());
        distanceTypeSelect.setItemLabelGenerator(DistanceType::label);
        centroidTypeSelect.setItems(CentroidType.values());
        centroidTypeSelect.setItemLabelGenerator(CentroidType::label);
        emptyCellFixSelect.setItems(EmptyCellFix.values());
        emptyCellFixSelect.setItemLabelGenerator(EmptyCellFix::label);

        // Bind each field two-way to its view-model signal so edits update the
        // source of truth directly. The signals already hold GLAConfig.DEFAULT
        // values, and bindValue propagates those initial values onto the fields
        // on bind; items are set above before binding so they are not cleared.
        bindParameterFields();

        paramsForm.add(uploader, heuristicSelect, heuristicCountField,
                epsilonField, decreasingEpsCheckbox, distanceTypeSelect,
                centroidTypeSelect, emptyCellFixSelect, jeffreysPriorCheckbox,
                runButton);

        paramsForm.setColspan(uploader, 3);
    }

    private void buildTabs() {
        localTabs.setId("local-tabs");
        localTabs.setWidthFull();

        searchCard.setId("local-search-panel");
        progress.setId("local-progress");
        statusText.setId("local-status");
        graphSC.setId("sc-graph");
        partitionView.setId("partition-view");
        reportComponent.setId("report-component-localsearch");
        dendrogramScroller.setId("local-dendrogram-scroller");

        // Build the download controls up front so they are non-null when the
        // search panel is assembled; refreshDownloadState() later toggles their
        // visibility once a result arrives.
        buildDownloadControls();

        HorizontalLayout searchPanel = new HorizontalLayout(
                new VerticalLayout(progress, statusText, operatorLabel,
                        operatorLog),
                downloadControls);
        searchCard.add(searchPanel);
        localTabs.add("Search", searchCard);
        // A taller chart makes the SC curve easier to read and lets the global
        // minimum stand out; the web component clamps its own height lower.
        graphSC.setWidthFull();
        graphSC.setHeight("500px");
        localTabs.add("SC Graph", graphSC);
        localTabs.add("Partition", partitionView);

        VerticalLayout dendrogramPanel = new VerticalLayout(
                generateTreeButton, dendrogram);
        dendrogramPanel.setSpacing(true);
        dendrogramScroller.setContent(dendrogramPanel);
        localTabs.add("Dendrogram", dendrogramScroller);
        localTabs.add("Summary", summaryContent);
        localTabs.add("Report", reportComponent);

        renderSummary();
    }

    /**
     * Binds every exposed form field two-way to its view-model signal. The
     * write callback ({@code signal::set}) keeps the field and signal in sync,
     * so editing a field updates the source of truth and updating a signal
     * repaints the field.
     */
    private void bindParameterFields() {
        heuristicSelect.bindValue(viewModel.heuristic(),
                viewModel.heuristic()::set);
        heuristicCountField.bindValue(viewModel.heuristicCount(),
                viewModel.heuristicCount()::set);
        epsilonField.bindValue(viewModel.epsilon(),
                viewModel.epsilon()::set);
        decreasingEpsCheckbox
                .bindValue(viewModel.decreasingEpsilon(),
                        viewModel.decreasingEpsilon()::set);
        distanceTypeSelect
                .bindValue(viewModel.distanceType(),
                        viewModel.distanceType()::set);
        centroidTypeSelect
                .bindValue(viewModel.centroidType(),
                        viewModel.centroidType()::set);
        emptyCellFixSelect
                .bindValue(viewModel.emptyCellFix(),
                        viewModel.emptyCellFix()::set);
    }

    /**
     * Populates the run-summary tab with the configuration resolved from the
     * form, plus a button that returns to the parameter form.
     */
    @SuppressWarnings("null")
    private void renderSummary() {
        summaryContent.removeAll();
        summaryContent.add(new H3("Run summary"));

        // The constructor already attaches the single "New search" handler,
        // which cancels the in-flight future and returns to the parameter form.
        // Adding another listener here would accumulate across runs and leave a
        // copy that does not cancel the future, so reuse the existing button.
        summaryContent.add(newSearchButton);

        LocalSearchParametersForm form = buildForm();
        GLAConfig config = form.toConfig(currentVectorCount());

        addSummaryRow("Heuristic",
                Heuristic.fromCode(form.heuristic()).label());
        addSummaryRow("heuristicCount (-j)",
                Integer.toString(form.heuristicCount()));
        addSummaryRow("epsilon (-E)", formatDouble(config.epsilon()));
        addSummaryRow("decreasing-epsilon",
                Boolean.toString(form.decreasingEpsilon()));
        addSummaryRow("distance type (-f)",
                DistanceType.fromCode(form.distanceType()).label());
        addSummaryRow("centroid type (-c)",
                CentroidType.fromCode(form.centroidType()).label());
        addSummaryRow("empty cell fix (-eX)",
                EmptyCellFix.fromCode(form.alternateMode()).label());
        addSummaryRow("maxIter (-n)",
                Integer.toString(config.maxIter()));
    }

    private int currentVectorCount() {
        var loaded = viewModel.data().peek();
        return loaded != null && loaded.vectorSet() != null
                ? loaded.vectorSet().size()
                : 0;
    }

    @SuppressWarnings("null")
    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private void addSummaryRow(String label, String value) {
        Div row = new Div();
        Span labelSpan = new Span(label + ": ");
        labelSpan.getElement().setAttribute(
                "style",
                "font-weight:bold;margin-right:0.5em;");
        row.add(labelSpan, new Span(value));
        summaryContent.add(row);
    }

    /**
     * Builds the partition and centroids download controls shown on the Search
     * tab once a search completes. Each control streams its content in memory
     * from the algorithm writers, so no temp file is written to disk. The
     * controls stay disabled until {@link #refreshDownloadState()} runs after a
     * result arrives.
     */
    private void buildDownloadControls() {
        if (downloadControls != null) {
            return;
        }

        String base = downloadFileName();
        partitionDownload = new DownloadAnchor("Download partition", () -> {
            PartitionModel model = viewModel.partitionModel().peek();
            byte[] bytes = model == null ? new byte[0]
                    : PartitionWriter.toBytes(ReportTab.toPartition(model));
            return bytes;
        });
        partitionDownload.setDownloadFileName(base + ".partition");
        partitionDownload.setId("local-partition-download");

        centroidsDownload = new DownloadAnchor("Download centroids", () -> {
            InfiniteCentroids c = viewModel.bestCentroids();
            byte[] bytes = c == null ? new byte[0] : CentroidWriter.toBytes(c);
            return bytes;
        });
        centroidsDownload.setDownloadFileName(base + ".centroids");
        centroidsDownload.setId("local-centroids-download");

        downloadControls = new HorizontalLayout(partitionDownload,
                centroidsDownload);
        downloadControls.setVisible(false);
    }

    /**
     * Refreshes the download controls' visibility and enabled state after a
     * result arrives. The controls appear only when a best partition exists;
     * the centroids control additionally requires carried centroids.
     */
    private void refreshDownloadState() {
        if (downloadControls == null) {
            buildDownloadControls();
        }
        boolean available = viewModel.resultAvailable().peek();
        downloadControls.setVisible(available);
        if (partitionDownload != null) {
            partitionDownload.setEnabled(available);
        }
        if (centroidsDownload != null) {
            centroidsDownload.setEnabled(
                    available && viewModel.bestCentroids() != null);
        }
    }

    /**
     * Derives a download file base name from the loaded vectors, falling back
     * to "binclass" when none are loaded yet.
     */
    private String downloadFileName() {
        String header = memoryBuffer.getHeader();
        if (!header.isBlank()) {
            String firstLine = header.lines().findFirst().orElse("");
            String base = firstLine.trim().replaceAll("\\s+", "_");
            if (!base.isBlank()) {
                return base;
            }
        }
        return "binclass";
    }

    /**
     * Builds the {@link UploadHandler} that buffers each uploaded file's bytes
     * so the view can pair the header and data files before parsing. Replaces
     * the deprecated {@code Receiver}-based upload constructor.
     *
     * @return an upload handler storing each uploaded file under its name
     */
    @SuppressWarnings("null")
    private UploadHandler createUploadHandler() {
        return event -> {
            try (InputStream in = event.getInputStream()) {
                memoryBuffer.receiveBytes(event.getFileName(),
                        in.readAllBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private void loadUploadedData() {
        try {
            String header = memoryBuffer.getHeader();
            List<String> lines = memoryBuffer.getDataLines();
            var vectorSet = UploadedDataLoader.load(header, lines);
            viewModel.setData(vectorSet);
        } catch (Exception e) {
            statusText.setText("Upload failed: " + e.getMessage());
        }
    }

    /**
     * Builds a {@link LocalSearchParametersForm} snapshot from the current
     * field values, falling back to
     * {@link LocalSearchParametersForm#defaults()} for controls that are not
     * exposed on the form.
     *
     * @return the resolved parameter form used to build the run configuration
     */
    @SuppressWarnings("null")
    private LocalSearchParametersForm buildForm() {
        // Build from the bound parameter signals so a search always reflects
        // the
        // latest field values rather than reading fields directly. Read outside
        // a
        // reactive context (called from the constructor and click handlers), so
        // wrap the computed read in Signal.untracked to allow inner
        // signal.get()
        // calls.
        return Signal.untracked(() -> viewModel.parameters().get());
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        super.onDetach(detachEvent);
        if (unregisterRegistration != null) {
            unregisterRegistration.remove();
            unregisterRegistration = null;
        }
        if (reportBinding != null) {
            reportBinding.remove();
            reportBinding = null;
        }
        viewModel.cancel();
    }
}
