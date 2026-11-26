/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.cumulative;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.card.Card;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.server.streams.UploadHandler;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import com.vaadin.flow.router.BeforeLeaveEvent;
import com.vaadin.flow.router.BeforeLeaveObserver;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import org.jsoup.Jsoup;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.binclass.algorithms.classify.CumulativeConfig;
import org.binclass.algorithms.core.DynamicPartition;
import org.binclass.algorithms.io.DynamicPartitionWriter;
import org.binclass.components.download.DownloadAnchor;
import org.binclass.components.partition.Partition;
import org.binclass.components.report.ReportComponent;
import org.binclass.components.tree.Tree;
import org.binclass.ui.MultiFileMemoryBuffer;
import org.binclass.ui.UploadedDataLoader;
import org.binclass.ui.classify.ReportTab;

/**
 * Cumulative Classification display — processes vectors one at a time, growing
 * a dynamic partition via Bayesian predictive identification with stochastic
 * complexity as the criterion.
 * <p>
 * Follows the MVVM pattern from {@code docs/WebUI.PRD.md} §5: owns a
 * {@link CumulativeViewModel}, binds its components to the VM's signals once,
 * forwards user actions as commands, and reacts to events through the VM's
 * listener. The view does not populate models or handle classification
 * lifecycle itself — updating a signal in the VM rebuilds the bound component
 * automatically. Unlike GLA/Local Search it has no SC Graph tab and exposes
 * only a partition download, since cumulative centroids are derived from
 * frequencies on demand rather than carried as a standalone object.
 * </p>
 */
@NullMarked
@Route("cumulative")
@Menu(title = "Cumulative Class.", order = 3, icon = "vaadin:cluster")
public class RouteCumulativeView extends VerticalLayout
        implements BeforeLeaveObserver {

    private final CumulativeViewModel viewModel = new CumulativeViewModel();

    private final FormLayout paramsForm = new FormLayout();
    private final MultiFileMemoryBuffer memoryBuffer = new MultiFileMemoryBuffer();
    private final Upload uploader = new Upload(createUploadHandler());
    private final IntegerField deltaField = new IntegerField(
            "delta (-D/-d)");
    private final Checkbox fixedDeltaCheckbox = new Checkbox("fixed-delta");
    private final Checkbox cumulativeAnalysisCheckbox = new Checkbox(
            "cumulative-analysis");
    private final IntegerField cumSamplesField = new IntegerField(
            "cumulative-samples (-s)");
    private final Checkbox bayesianCheckbox = new Checkbox(
            "bayesian-predictive");
    private final Checkbox featureSigCheckbox = new Checkbox(
            "feature-significance");
    private final Checkbox saveByPfCheckbox = new Checkbox("save-by-pf");
    private final Checkbox noNewClassesCheckbox = new Checkbox(
            "no-new-classes");
    private final Checkbox inOrderCheckbox = new Checkbox("in-order");
    private final NumberField epsilonField = new NumberField("epsilon (-E)");
    private final Button runButton = new Button("Run");
    private final Button newSearchButton = new Button("New search");

    private final TabSheet cumTabs = new TabSheet();
    private final Card searchCard = new Card();
    // Range [0, 1] so the view model's fractional progress maps directly to a
    // percentage; setValue(1.0) shows a full bar on completion.
    private final ProgressBar progress = new ProgressBar(0, 1);
    private final Div statusText = new Div();
    private final Div logText = new Div();
    private final VerticalLayout summaryContent = new VerticalLayout();

    private final Partition partitionView = new Partition();
    private final ReportComponent reportComponent = new ReportComponent();
    private final Tree dendrogram = new Tree();
    private final Button generateTreeButton = new Button("Generate dendrogram");
    private final Scroller dendrogramScroller = new Scroller();

    // Download controls for the dynamic partition, shown once a result
    // arrives. Built lazily so they only exist after a run completes.
    private DownloadAnchor partitionDownload;
    private HorizontalLayout downloadControls;

    @Nullable
    private Registration unregisterRegistration;

    @Nullable
    private Registration reportBinding;

    public RouteCumulativeView() {
        setId("cumulative-view");
        setSpacing(true);
        setPadding(true);
        setSizeFull();

        buildParamsForm();
        buildTabs();

        add(paramsForm, cumTabs);

        // Bind signals to components.
        runButton.setEnabled(true);
        runButton.bindEnabled(viewModel.canRun());
        partitionView.bindPartitionModel(viewModel.partitionModel());
        reportComponent.setId("report-component-cumulative");
        reportBinding = ReportTab.bind(reportComponent,
                viewModel.partitionModel());

        generateTreeButton.setEnabled(true);
        generateTreeButton.setId("generate-tree-cumulative");
        generateTreeButton.bindEnabled(viewModel.canGenerateTree());
        generateTreeButton.addClickListener(_ -> viewModel.generateTree());
        dendrogram.setId("dendrogram-cumulative");
        dendrogram.bindModel(viewModel.treeModel());

        progress.bindValue(viewModel.progress());
        statusText.bindText(viewModel.statusText());

        // Render one component per new-cluster log line. bindChildren handles
        // additions, removals and reordering without recreating all children,
        // so the log stays in sync with the view model's list signal even as
        // the TabSheet detaches and reattaches its content.
        logText.bindChildren(viewModel.logLines(), this::renderLogLine);

        // Refresh the download controls whenever a result appears or is
        // cleared. The controls are built lazily on first refresh so they only
        // exist after a run completes.
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
            cumTabs.setVisible(true);
        });
        newSearchButton.addClickListener(_ -> {
            viewModel.cancel();
            paramsForm.setVisible(true);
            cumTabs.setVisible(false);
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
        dialog.setHeader("Classification in progress");
        dialog.setText(
                "Cumulative classification is still running. Cancel it and navigate away?");
        dialog.setConfirmText("Cancel classification");
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
        paramsForm.setId("cumulative-params");
        uploader.setId("uploader-cumulative");
        uploader.setMaxFiles(2);
        // A Receiver is set (MultiFileMemoryBuffer), so server-side extension
        // validation would require an UploadHandler. Use client-side-only hints
        // instead; the view validates the exact file pair on upload finished.
        uploader.setAcceptedFileExtensions(".data", ".hdr", ".header");
        Tooltip.forComponent(uploader).setText(
                "Upload exactly two files: one .data file and one .header file. "
                        + "Selecting both at once is recommended.");
        deltaField.setId("delta");
        fixedDeltaCheckbox.setId("fixed-delta");
        cumulativeAnalysisCheckbox.setId("cum-analysis");
        cumSamplesField.setId("cum-samples");
        bayesianCheckbox.setId("bayesian-predictive");
        featureSigCheckbox.setId("feature-significance");
        saveByPfCheckbox.setId("save-by-pf");
        noNewClassesCheckbox.setId("no-new-classes");
        inOrderCheckbox.setId("in-order");
        epsilonField.setId("epsilon");
        runButton.setId("run-cumulative");
        newSearchButton.setId("new-search");

        // Labels and tooltips for every control.
        deltaField.setLabel("delta (-D/-d)");
        deltaField.setMin(1);
        deltaField.setTooltipText(
                "Predictive-fit delta. -D sets a fixed value, -d lets it decrement.");
        fixedDeltaCheckbox.setLabel("fixed-delta");
        fixedDeltaCheckbox.setTooltipText(
                "When checked, delta is treated as a bare value (-D) instead of decrementing.");
        cumulativeAnalysisCheckbox.setLabel("cumulative-analysis");
        cumulativeAnalysisCheckbox.setTooltipText(
                "Enable cumulative analysis mode (-N).");
        cumSamplesField.setLabel("cumulative-samples (-s)");
        cumSamplesField.setTooltipText(
                "Number of cumulative samples to draw (0 means unbounded).");
        bayesianCheckbox.setLabel("bayesian-predictive");
        bayesianCheckbox.setTooltipText(
                "Use Bayesian predictive identification. Default on; toggle off for the SC-optimising variant.");
        featureSigCheckbox.setLabel("feature-significance");
        featureSigCheckbox.setTooltipText(
                "Test feature significance during classification (-F).");
        saveByPfCheckbox.setLabel("save-by-pf");
        saveByPfCheckbox.setTooltipText(
                "Save by probability factor. Default on; toggle off with -c.");
        noNewClassesCheckbox.setLabel("no-new-classes");
        noNewClassesCheckbox.setTooltipText(
                "Allow vectors to join existing classes only, never create new ones (-n).");
        inOrderCheckbox.setLabel("in-order");
        inOrderCheckbox.setTooltipText(
                "Process vectors in input order instead of shuffled (-O/-I).");
        epsilonField.setLabel("epsilon (-E)");
        epsilonField.setTooltipText(
                "Convergence threshold in the open interval (0, 0.5). A bare value validated here.");

        // Bind each field two-way to its view-model signal so edits update the
        // source of truth directly. The signals already hold CumulativeConfig
        // defaults, and bindValue propagates those initial values onto the
        // fields on bind; items are set above before binding so they are not
        // cleared.
        bindParameterFields();

        paramsForm.add(uploader, deltaField, fixedDeltaCheckbox,
                cumulativeAnalysisCheckbox, cumSamplesField, bayesianCheckbox,
                featureSigCheckbox, saveByPfCheckbox, noNewClassesCheckbox,
                inOrderCheckbox, epsilonField, runButton);

        paramsForm.setColspan(uploader, 3);
    }

    /**
     * Binds every exposed form field two-way to its view-model signal. The
     * write callback ({@code signal::set}) keeps the field and signal in sync,
     * so editing a field updates the source of truth and updating a signal
     * repaints the field.
     */
    private void bindParameterFields() {
        deltaField.bindValue(viewModel.delta(), viewModel.delta()::set);
        fixedDeltaCheckbox.bindValue(viewModel.fixedDelta(),
                viewModel.fixedDelta()::set);
        cumulativeAnalysisCheckbox.bindValue(
                viewModel.cumulativeAnalysis(),
                viewModel.cumulativeAnalysis()::set);
        cumSamplesField.bindValue(viewModel.cumulativeSamples(),
                viewModel.cumulativeSamples()::set);
        bayesianCheckbox.bindValue(viewModel.bayesianPredictive(),
                viewModel.bayesianPredictive()::set);
        featureSigCheckbox.bindValue(
                viewModel.testFeatureSignificance(),
                viewModel.testFeatureSignificance()::set);
        saveByPfCheckbox.bindValue(viewModel.cumSaveByPf(),
                viewModel.cumSaveByPf()::set);
        noNewClassesCheckbox.bindValue(viewModel.cumNoNewClasses(),
                viewModel.cumNoNewClasses()::set);
        inOrderCheckbox.bindValue(viewModel.inOrder(),
                viewModel.inOrder()::set);
        epsilonField.bindValue(viewModel.epsilon(),
                viewModel.epsilon()::set);
    }

    private void buildTabs() {
        cumTabs.setId("cumulative-tabs");
        cumTabs.setWidthFull();

        searchCard.setId("search-panel");
        progress.setId("cumulative-progress");
        statusText.setId("cumulative-status");
        logText.setId("cumulative-log");

        partitionView.setId("partition-view");
        buildDownloadControls();

        HorizontalLayout searchPanel = new HorizontalLayout(
                new VerticalLayout(progress, statusText, logText),
                downloadControls);
        searchCard.add(searchPanel);
        cumTabs.add("Search", searchPanel);
        cumTabs.add("Partition", partitionView);
        cumTabs.add("Report", reportComponent);

        dendrogramScroller.setId("cumulative-dendrogram-scroller");
        VerticalLayout dendrogramPanel = new VerticalLayout(
                generateTreeButton, dendrogram);
        dendrogramPanel.setSpacing(true);
        dendrogramScroller.setContent(dendrogramPanel);
        cumTabs.add("Dendrogram", dendrogramScroller);

        cumTabs.add("Summary", summaryContent);

        renderSummary();
    }

    /**
     * Populates the run-summary tab with the configuration resolved from the
     * form, plus a button that returns to the parameter form.
     */
    private void renderSummary() {
        summaryContent.removeAll();
        summaryContent.add(new H3("Run summary"));

        // The constructor already attaches the single "New search" handler,
        // which cancels the in-flight future and returns to the parameter
        // form. Reuse the existing button so only one listener accumulates.
        summaryContent.add(newSearchButton);

        CumulativeParametersForm form = buildForm();
        CumulativeConfig config = form.toConfig(currentVectorCount());

        addSummaryRow("delta (-D/-d)", Integer.toString(form.delta()));
        addSummaryRow("fixed-delta", Boolean.toString(form.fixedDelta()));
        addSummaryRow("cumulative-analysis",
                Boolean.toString(form.cumulativeAnalysis()));
        addSummaryRow("cumulative-samples (-s)",
                Integer.toString(form.cumulativeSamples()));
        addSummaryRow("bayesian-predictive",
                Boolean.toString(form.bayesianPredictive()));
        addSummaryRow("feature-significance",
                Boolean.toString(form.testFeatureSignificance()));
        addSummaryRow("save-by-pf", Boolean.toString(form.cumSaveByPf()));
        addSummaryRow("no-new-classes",
                Boolean.toString(form.cumNoNewClasses()));
        addSummaryRow("in-order", Boolean.toString(form.inOrder()));
        addSummaryRow("epsilon (-E)", formatDouble(config.epsilon()));
    }

    private int currentVectorCount() {
        var loaded = viewModel.data().peek();
        return loaded != null && loaded.vectorSet() != null
                ? loaded.vectorSet().size()
                : 0;
    }

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
     * Rebuilds the log area from the view model's one-line-per-new-cluster
     * signal. Each line is JSoup-sanitized so any markup carried by a strain or
     * class-name is stripped before display, and the stochastic-complexity
     * portion is rendered in bold when the SC-optimising variant produced it.
     */
    /**
     * Renders a single new-cluster log line as a component, bolding the
     * stochastic-complexity portion when present. Called by
     * {@link Div#bindChildren} for each entry in the view model's list signal;
     * must not return {@code null}.
     *
     * @param entry
     *            the value signal holding the raw log line text
     * @return a component rendering the sanitized line, never {@code null}
     */
    private Component renderLogLine(ValueSignal<String> entry) {
        // peek() reads the value without registering a reactive dependency,
        // which is what bindChildren expects from a child factory callback.
        String clean = sanitize(entry.peek());
        int idx = clean.indexOf("(SC = ");
        if (idx >= 0) {
            Div line = new Div();
            Span prefix = new Span(clean.substring(0, idx));
            Span sc = new Span(clean.substring(idx));
            sc.getElement().getStyle().set("font-weight", "bold");
            line.add(prefix, sc);
            return line;
        }
        return new Div(clean);
    }

    /**
     * Sanitizes a raw log line with JSoup content mode so any markup carried by
     * strain or class-name text is stripped before it is shown to the user.
     *
     * @param raw
     *            the raw log line; may be {@code null} or blank
     * @return the sanitized plain-text line, never {@code null}
     */
    private static String sanitize(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return Jsoup.parseBodyFragment(raw).body().text();
    }

    /**
     * Builds the partition download control shown on the Search tab once a run
     * completes. Streams its content in memory from
     * {@link DynamicPartitionWriter#toBytes(DynamicPartition)}, so no temp file
     * is written to disk. The control stays disabled until
     * {@link #refreshDownloadState()} runs after a result arrives.
     */
    private void buildDownloadControls() {
        if (downloadControls != null) {
            return;
        }

        String base = downloadFileName();
        partitionDownload = new DownloadAnchor("Download partition", () -> {
            DynamicPartition dynPart = viewModel.dynamicPartition().peek();
            byte[] bytes = dynPart == null ? new byte[0]
                    : DynamicPartitionWriter.toBytes(dynPart);
            return bytes;
        });
        partitionDownload.setDownloadFileName(base + ".partition");
        partitionDownload.setId("cum-partition-download");

        downloadControls = new HorizontalLayout(partitionDownload);
        downloadControls.setVisible(false);
    }

    /**
     * Refreshes the download controls' visibility and enabled state after a
     * result arrives. The control appears only when a dynamic partition exists;
     * it is disabled while running and enabled once a run completes.
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
     * Builds a {@link CumulativeParametersForm} snapshot from the current field
     * values, falling back to {@link CumulativeParametersForm#defaults()} for
     * controls that are not exposed on the form.
     *
     * @return the resolved parameter form used to build the run configuration
     */
    private CumulativeParametersForm buildForm() {
        // Build from the bound parameter signals so a run always reflects the
        // latest field values rather than reading fields directly. Read outside
        // a reactive context (called from the constructor and click handlers),
        // so wrap the computed read in Signal.untracked.
        return Signal.untracked(() -> new CumulativeParametersForm(
                "",
                deltaField.getValue(),
                fixedDeltaCheckbox.getValue(),
                cumulativeAnalysisCheckbox.getValue(),
                cumSamplesField.getValue(),
                bayesianCheckbox.getValue(),
                featureSigCheckbox.getValue(),
                saveByPfCheckbox.getValue(),
                noNewClassesCheckbox.getValue(),
                inOrderCheckbox.getValue(),
                epsilonField.getValue()));
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
