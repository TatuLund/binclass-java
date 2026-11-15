/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.gla;

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
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.shared.Tooltip;
import com.vaadin.flow.component.upload.Upload;
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
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.SearchType;
import org.binclass.components.graph.Graph;
import org.binclass.components.partition.Partition;
import org.binclass.components.report.ReportComponent;
import org.binclass.ui.MultiFileMemoryBuffer;
import org.binclass.ui.UploadedDataLoader;
import org.binclass.ui.classify.CentroidType;
import org.binclass.ui.classify.DistanceType;
import org.binclass.ui.classify.EmptyCellFix;
import org.binclass.ui.classify.ReportTab;

/**
 * GLA Search display — the flagship view. Mirrors {@code ClassifyCommand} and
 * offers both range and automatic search controlled by the same switches.
 * <p>
 * Follows the MVVM pattern from {@code docs/WebUI.PRD.md} §1.6: owns a
 * {@link GlaViewModel}, binds its components to the VM's signals once, forwards
 * user actions as commands, and reacts to events through the VM's listener. The
 * view does not populate models or handle search lifecycle itself — updating a
 * signal in the VM rebuilds the bound component automatically.
 */
@NullMarked
@Route("gla")
@Menu(title = "GLA Search", order = 1, icon = "vaadin:search")
public class RouteGlaView extends VerticalLayout
        implements BeforeLeaveObserver {

    private final GlaViewModel viewModel = new GlaViewModel();

    private final FormLayout paramsForm = new FormLayout();
    private final MultiFileMemoryBuffer memoryBuffer = new MultiFileMemoryBuffer();
    private final Upload uploader = new Upload(createUploadHandler());
    private final IntegerField kStartField = new IntegerField("kstart (-b)");
    private final IntegerField kStopField = new IntegerField("kstop (-s)");
    private final IntegerField kStopWhenField = new IntegerField(
            "kstopwhen (-S)");
    private final IntegerField kcStopWhenField = new IntegerField(
            "kcStopWhen (-W)");
    private final NumberField epsilonField = new NumberField("epsilon (-E)");
    private final Checkbox decreasingEpsCheckbox = new Checkbox(
            "decreasing-epsilon");
    private final Select<DistanceType> distanceTypeSelect = new Select<>();
    private final Select<CentroidType> centroidTypeSelect = new Select<>();
    private final Select<EmptyCellFix> emptyCellFixSelect = new Select<>();
    private final IntegerField maxIterField = new IntegerField("maxIter (-n)");
    private final Button runButton = new Button("Run");
    private final Button newSearchButton = new Button("New search");

    private final TabSheet glaTabs = new TabSheet();
    private final Card searchCard = new Card();
    private final ProgressBar progress = new ProgressBar(0, 100);
    private final Div statusText = new Div();
    private final VerticalLayout summaryContent = new VerticalLayout();
    private final Graph graphSC = new Graph();
    private final Partition partitionView = new Partition();
    private final ReportComponent reportComponent = new ReportComponent();

    private Registration unregisterRegistration;
    private Registration reportBinding;

    public RouteGlaView() {
        setId("gla-view");
        setSpacing(true);
        setPadding(true);

        buildParamsForm();
        buildTabs();

        add(paramsForm, glaTabs);

        // Bind signals to components.
        runButton.setEnabled(true);
        runButton.bindEnabled(viewModel.canRun());
        graphSC.bindModel(viewModel.graphPoints());
        partitionView.bindPartitionModel(viewModel.partitionModel());
        reportComponent.setId("report-component-gla");
        reportBinding = ReportTab.bind(reportComponent,
                viewModel.partitionModel());

        // Progress bar: indeterminate for automatic searches, a fraction of the
        // evaluated k-range otherwise. The status text Div is driven by its own
        // signal so search progress and completion stay in sync.
        progress.bindValue(viewModel.progress());
        Signal.effect(progress,
                () -> progress.setIndeterminate(
                        viewModel.progressIndeterminate().get()));
        Signal.effect(statusText, () -> statusText.setText(
                viewModel.statusText().get()));

        memoryBuffer.setOnReady(this::loadUploadedData);
        uploader.addAllFinishedListener(_e -> viewModel.uploadValid()
                .update(v -> memoryBuffer.validate()));

        runButton.addClickListener(_e -> {
            viewModel.setForm(buildForm());
            viewModel.run();
            renderSummary();
            paramsForm.setVisible(false);
            glaTabs.setVisible(true);
        });
        newSearchButton.addClickListener(_e -> {
            viewModel.cancel();
            paramsForm.setVisible(true);
            glaTabs.setVisible(false);
        });

        viewModel.register();
        unregisterRegistration = () -> viewModel.unregister();
    }

    @Override
    public void beforeLeave(BeforeLeaveEvent event) {
        if (!viewModel.running().peek()) {
            return;
        }

        BeforeLeaveEvent.ContinueNavigationAction action = event.postpone();
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Search in progress");
        dialog.setText(
                "A GLA search is still running. Cancel it and navigate away?");
        dialog.setConfirmText("Cancel search");
        dialog.setConfirmButtonTheme("error primary");
        dialog.setCancelable(true);
        dialog.addConfirmListener(_e -> {
            viewModel.cancel();
            action.proceed();
        });
        dialog.addCancelListener(_e -> action.cancel());
        dialog.open();
    }

    private void buildParamsForm() {
        paramsForm.setId("gla-params");
        uploader.setId("uploader-gla");
        uploader.setMaxFiles(2);
        // A Receiver is set (MultiFileMemoryBuffer), so server-side extension
        // validation would require an UploadHandler. Use client-side-only hints
        // instead; the view validates the exact file pair on upload finished.
        uploader.setAcceptedFileExtensions(".data", ".hdr", ".header");
        Tooltip.forComponent(uploader).setText(
                "Upload exactly two files: one .data file and one .header file. "
                        + "Selecting both at once is recommended.");
        kStartField.setId("k-start");
        kStartField.setMin(1);
        kStopField.setId("k-stop");
        kStopWhenField.setId("kstop-when");
        kcStopWhenField.setId("kc-stop-when");
        epsilonField.setId("epsilon");
        decreasingEpsCheckbox.setId("decreasing-eps");
        distanceTypeSelect.setId("distance-type");
        centroidTypeSelect.setId("centroid-type");
        emptyCellFixSelect.setId("empty-cell-fix");
        maxIterField.setId("maxiter");
        runButton.setId("run-gla");
        newSearchButton.setId("new-search");

        GlaParametersForm defaults = GlaParametersForm.defaults();

        // Labels and tooltips for every control.
        kStartField.setLabel("kstart (-b)");
        kStartField.setTooltipText(
                "Lowest cluster count to try. 1 means automatic start.");
        kStopField.setLabel("kstop (-s)");
        kStopField.setTooltipText(
                "Highest cluster count to try. 0 means auto-scan up to the dataset size.");
        kStopWhenField.setLabel("kstopwhen (-S)");
        kStopWhenField.setTooltipText(
                "Maximum clusters to search for Split-GLA.");
        kcStopWhenField.setLabel("kcStopWhen (-W)");
        kcStopWhenField.setTooltipText(
                "Steps without SC improvement before stopping the search.");
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
        maxIterField.setLabel("maxIter (-n)");
        maxIterField.setTooltipText(
                "Maximum iterations. Non-zero forces range search.");

        // Seed the form with sensible defaults matching GLAConfig.DEFAULT.
        kStartField.setValue(1);
        kStopField.setValue(0);
        kStopWhenField.setValue(defaults.kStopWhen());
        kcStopWhenField.setValue(defaults.kcStopWhen());
        epsilonField.setValue(defaults.epsilon());
        maxIterField.setValue(0);

        // Enum selects with friendly labels instead of bare numeric codes.
        distanceTypeSelect.setItems(DistanceType.values());
        distanceTypeSelect.setItemLabelGenerator(DistanceType::label);
        distanceTypeSelect.setValue(DistanceType.HAMMING);
        centroidTypeSelect.setItems(CentroidType.values());
        centroidTypeSelect.setItemLabelGenerator(CentroidType::label);
        centroidTypeSelect.setValue(CentroidType.CLASSIC);
        emptyCellFixSelect.setItems(EmptyCellFix.values());
        emptyCellFixSelect.setItemLabelGenerator(EmptyCellFix::label);
        emptyCellFixSelect.setValue(EmptyCellFix.WORST_MATCH);

        paramsForm.add(uploader, kStartField, kStopField, kStopWhenField,
                kcStopWhenField, epsilonField, decreasingEpsCheckbox,
                distanceTypeSelect,
                centroidTypeSelect, emptyCellFixSelect, maxIterField,
                runButton);

        paramsForm.setColspan(uploader, 3);

        // Re-validate the range switches and publish form validity whenever a
        // field that affects it changes.
        kStopField.addValueChangeListener(_e -> refreshFormValidity());
        kStopWhenField.addValueChangeListener(_e -> refreshFormValidity());
        epsilonField.addValueChangeListener(_e -> refreshFormValidity());
        decreasingEpsCheckbox
                .addValueChangeListener(_e -> refreshFormValidity());

        refreshFormValidity();
    }

    /**
     * Highlights the {@code -s} (kstop) and {@code -S} (kstopwhen) fields when
     * both are set to a positive value, since each independently bounds the
     * range search and cannot be combined. Sets an error message on both fields
     * and returns whether they are compatible.
     *
     * @return {@code true} when at most one of kstop/kstopwhen is positive
     */
    private boolean validateRangeFields() {
        int kstop = kStopField.getOptionalValue().orElse(0);
        int kstopwhen = kStopWhenField.getOptionalValue().orElse(0);
        boolean bothSet = kstop > 0 && kstopwhen > 0;
        String message = "kstop (-s) and kstopwhen (-S) are mutually exclusive.";
        if (bothSet) {
            kStopField.setInvalid(true);
            kStopField.setErrorMessage(message);
            kStopWhenField.setInvalid(true);
            kStopWhenField.setErrorMessage(message);
        } else {
            kStopField.setInvalid(false);
            kStopField.setErrorMessage(null);
            kStopWhenField.setInvalid(false);
            kStopWhenField.setErrorMessage(null);
        }
        return !bothSet;
    }

    /**
     * Re-validates the range switches and publishes the combined form validity
     * so the Run button can gate on it. Combines the kstop/kstopwhen mutual
     * exclusion check with the full {@link GlaParametersForm#isValid()} test.
     */
    private void refreshFormValidity() {
        boolean rangeOk = validateRangeFields();
        viewModel.formValid().update(_v -> rangeOk && buildForm().isValid());
    }

    private void buildTabs() {
        glaTabs.setId("gla-tabs");
        glaTabs.setWidthFull();

        searchCard.setId("search-panel");
        progress.setId("gla-progress");
        statusText.setId("gla-status");
        graphSC.setId("sc-graph");
        partitionView.setId("partition-view");
        reportComponent.setId("report-component-gla");

        searchCard.add(new VerticalLayout(progress, statusText));
        glaTabs.add("Search", searchCard);
        glaTabs.add("SC Graph", graphSC);
        glaTabs.add("Partition", partitionView);
        glaTabs.add("Report", reportComponent);
        glaTabs.add("Summary", summaryContent);

        renderSummary();
    }

    /**
     * Builds a {@link GlaParametersForm} snapshot from the current field
     * values, falling back to {@link GlaParametersForm#defaults()} for controls
     * that are not exposed on the form.
     *
     * @return the resolved parameter form used to build the run configuration
     */
    private GlaParametersForm buildForm() {
        GlaParametersForm defaults = GlaParametersForm.defaults();
        DistanceType distanceType = firstNonEmpty(
                distanceTypeSelect.getValue());
        CentroidType centroidType = firstNonEmpty(
                centroidTypeSelect.getValue());
        EmptyCellFix emptyCellFix = firstNonEmpty(
                emptyCellFixSelect.getValue());
        Double epsilon = epsilonField.getOptionalValue()
                .orElse(defaults.epsilon());

        return new GlaParametersForm(
                "",
                kStartField.getOptionalValue().orElse(defaults.kStart()),
                kStopField.getOptionalValue().orElse(defaults.kStop()),
                kStopWhenField.getOptionalValue().orElse(defaults.kStopWhen()),
                kcStopWhenField.getOptionalValue()
                        .orElse(defaults.kcStopWhen()),
                epsilon,
                decreasingEpsCheckbox.getValue(),
                GLAConfig.DEFAULT.heuristic(),
                emptyCellFix.code(),
                false,
                centroidType.code(),
                false,
                false,
                false,
                false,
                false,
                0.0,
                false,
                false,
                distanceType.code(),
                maxIterField.getOptionalValue().orElse(defaults.maxIter()),
                defaults.heuristicCount(),
                defaults.safetyLimit(),
                defaults.iterBase(),
                "");
    }

    @SuppressWarnings("unchecked")
    private static <T extends Enum<T>> T firstNonEmpty(T value) {
        return value != null ? value
                : (T) ((Class<? extends Enum>) value.getClass())
                        .getEnumConstants()[0];
    }

    /**
     * Populates the run-summary tab with the configuration resolved from the
     * form, plus a button that returns to the parameter form.
     */
    private void renderSummary() {
        summaryContent.removeAll();
        summaryContent.add(new H3("Run summary"));

        newSearchButton.addClickListener(_ -> {
            paramsForm.setVisible(true);
            glaTabs.setVisible(false);
        });
        summaryContent.add(newSearchButton);

        GlaParametersForm form = buildForm();
        GLAConfig config = form.toConfig(currentVectorCount());
        SearchType type = form.determineSearchType();

        addSummaryRow("Search type", String.valueOf(type));
        addSummaryRow("kstart (-b)", Integer.toString(form.kStart()));
        addSummaryRow("kstop (-s)", Integer.toString(form.kStop()));
        addSummaryRow("kstopwhen (-S)", Integer.toString(form.kStopWhen()));
        addSummaryRow("kcStopWhen (-W)", Integer.toString(form.kcStopWhen()));
        addSummaryRow("epsilon (-E)", formatDouble(config.epsilon()));
        addSummaryRow("decreasing-epsilon",
                Boolean.toString(form.decreasingEpsilon()));
        addSummaryRow("empty cell fix (-eX)",
                EmptyCellFix.fromCode(form.alternateMode()).label());
        addSummaryRow("centroid type (-c)",
                CentroidType.fromCode(form.centroidType()).label());
        addSummaryRow("distance type (-f)",
                DistanceType.fromCode(form.distanceType()).label());
        addSummaryRow("maxIter (-n)",
                Integer.toString(config.effectiveMaxIter()));
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
                "style", "font-weight:bold;margin-right:0.5em;");
        row.add(labelSpan, new Span(value));
        summaryContent.add(row);
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

    @Override
    protected void onDetach(DetachEvent event) {
        super.onDetach(event);
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
