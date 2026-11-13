/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.report;

import java.util.List;
import java.util.Locale;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Table;
import com.vaadin.flow.component.html.TableCell;
import com.vaadin.flow.component.html.TableRow;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.gla.GLAEngine;
import org.binclass.algorithms.report.ReportModel;
import org.binclass.algorithms.report.ReportOptions;

/**
 * Reusable report component that each classifier display embeds in its own
 * Report tab. It owns the four reporting flags described in PRD §17.2 and
 * renders a structured HTML view of the statistical report built directly from
 * the partition it is bound to — an {@code 
 * 
<h3>} title, per-section {@code 
 * 
<h4>} sub-headers, frequency tables and one {@link ClassInfoComponent} per
 * cluster — instead of flattening the generator's monospace string.
 * <p>
 * The component recomputes centroids from the partition it is bound to and
 * regenerates on every flag change, so a display only needs to add one instance
 * to its tab and bind its current partition via {@link #bindPartition}.
 * </p>
 */
@NullMarked
public class ReportComponent extends VerticalLayout {

    /** Text for a diagonal matrix cell (self-distance), right-aligned. */
    private static final String DIAGONAL_CELL = "-";

    @Nullable
    private Partition partition;

    private Checkbox printDigits = new Checkbox("Print digits (-d)");
    private Checkbox affinityMatrix = new Checkbox("Affinity matrix (-a)");
    private Checkbox useHellinger = new Checkbox("Use Hellinger distance (-h)");
    private IntegerField reportParams = new IntegerField(
            "Report params (-p, 0=all)");

    private Table matrixTable = new Table();
    private VerticalLayout controls;
    private VerticalLayout reportBody;

    public ReportComponent() {
        setId("report-component");
        setSpacing(true);
        setPadding(false);

        printDigits.setId("print-digits");
        affinityMatrix.setId("affinity-matrix");
        useHellinger.setId("use-hellinger");
        reportParams.setId("report-params");

        matrixTable.setId("report-affinity");
        matrixTable.addClassName("matrix-table");

        this.controls = createControls();
        this.reportBody = new VerticalLayout();
        reportBody.setId("report-body");

        // Keep the matrix table in the tree from construction so callers can
        // locate it by id even before a partition is bound. It starts empty and
        // is populated whenever nearness output is rendered.
        reportBody.add(matrixTable);

        add(controls);
        add(reportBody);
        regenerate();

        // Regenerate whenever a flag changes.
        printDigits.addValueChangeListener(e -> regenerate());
        affinityMatrix.addValueChangeListener(e -> regenerate());
        useHellinger.addValueChangeListener(e -> regenerate());
        reportParams.addValueChangeListener(e -> regenerate());
    }

    private VerticalLayout createControls() {
        VerticalLayout controls = new VerticalLayout(printDigits,
                affinityMatrix,
                useHellinger, reportParams);
        controls.setId("report-controls");
        return controls;
    }

    /**
     * Binds the partition this component reports on. Recomputes centroids from
     * the bound partition and regenerates the report immediately.
     *
     * @param partition
     *            the partition to report on, or {@code null} to clear
     */
    public void bindPartition(@Nullable Partition partition) {
        this.partition = partition;
        regenerate();
    }

    private void regenerate() {
        matrixTable.removeAllRows();
        reportBody.removeAll();
        // Keep the matrix table attached so it stays locatable by id even when
        // no partition is bound or nearness output is disabled. It starts empty
        // and is populated whenever a nearness section renders.
        reportBody.add(matrixTable);

        if (partition == null || partition.size() <= 0) {
            return;
        }
        Partition bound = partition;
        if (bound == null) {
            return;
        }

        int k = bound.size();
        int l = getVectorLength(bound);
        if (l <= 0) {
            return; // No vectors to compute centroids from
        }

        InfiniteCentroids centroids = new InfiniteCentroids(k, l);
        GLAEngine.recomputeCentroids(bound, centroids, false,
                bound.getNumberOfVectors());

        ReportOptions options = new ReportOptions(
                printDigits.getValue(), affinityMatrix.getValue(),
                useHellinger.getValue(), reportParams.getValue() == null ? 0
                        : reportParams.getValue());

        ReportModel model = ReportModel.of(bound, centroids, options);
        render(model, k);
    }

    @SuppressWarnings("null")
    private void render(ReportModel model, int k) {
        H3 header = new H3("STATISTICAL REPORT");
        header.setId("report-header");
        reportBody.add(header);

        if (model.showTotalFrequencies()) {
            reportBody.add(sectionTitle("TOTAL FREQUENCIES:"));
            reportBody.add(new FrequencyTable(model.totalFrequencies(),
                    model.printDigitsEnabled()));
        }

        reportBody.add(sectionTitle("LIST OF CLASSES:"));
        for (ReportModel.ClassReport classReport : model.classReports()) {
            reportBody.add(new ClassInfoComponent(classReport));
        }

        if (model.showNearness() && model.matrixValues() != null) {
            reportBody.add(sectionTitle("CLASS NEARNESS MATRIX:"));
            renderMatrix(model.matrixValues(), k);
            reportBody.add(matrixTable);
        }
    }

    private H4 sectionTitle(String text) {
        H4 title = new H4(text);
        title.addClassName("report-section-title");
        return title;
    }

    private void renderMatrix(double[][] matrix, int k) {
        // Header row: empty corner + column numbers 1..k
        String[] header = new String[k + 1];
        for (int j = 0; j <= k; j++) {
            header[j] = j == 0 ? "" : Integer.toString(j);
        }
        TableRow headerRow = matrixTable.addHeaderRow(header);
        // Bottom border under the header line, plus a right border on the
        // corner cell so the first column reads as a continuous vertical rule.
        for (TableCell cell : headerRow.getCells()) {
            cell.getStyle()
                    .set("border-bottom", "1px solid currentColor")
                    .set("padding", "0 8px");
        }
        headerRow.getCells().get(0).getStyle()
                .set("border-right", "1px solid currentColor");

        // Data rows: "Class i" label + formatted values
        for (int i = 1; i <= k; i++) {
            String[] row = new String[k + 1];
            row[0] = "Class " + i;
            for (int j = 1; j <= k; j++) {
                if (i == j) {
                    row[j] = DIAGONAL_CELL;
                } else {
                    double value = matrix[i][j];
                    row[j] = String.format(Locale.ROOT, "%6.2f", value);
                }
            }
            TableRow dataRow = matrixTable.addRow(row);
            List<TableCell> cells = dataRow.getCells();
            // Right border on the first (label) column.
            cells.get(0).getStyle()
                    .set("border-right", "1px solid currentColor");
            // Right-align the diagonal dash so it lines up with the numeric
            // columns instead of sitting flush left.
            cells.get(i).getStyle().set("text-align", "right");
        }
    }

    /**
     * Returns the vector length from the first non-empty cluster in the
     * partition.
     *
     * @param partition
     *            the partition to inspect
     * @return bit-length of vectors, or 0 if all clusters are empty
     */
    private static int getVectorLength(Partition partition) {
        for (int i = 1; i <= partition.size(); i++) {
            VectorSet cluster = partition.getElements(i);
            if (!cluster.isEmpty()) {
                return cluster.getVectorLength();
            }
        }
        return 0;
    }

    /**
     * Sanitizes a raw report string for safe display in a {@code Div}. HTML
     * tags are stripped while newlines and internal spacing are kept so the
     * monospace frequency tables stay aligned when rendered with
     * {@code white-space: pre}. Retained for callers that still need to show a
     * plain-text snapshot.
     *
     * @param raw
     *            the raw report text; may be null or empty
     * @return sanitized text, never null
     */
    @SuppressWarnings("null")
    public static String sanitize(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(raw.length());
        for (String line : raw.split("\n", -1)) {
            cleaned.append(line.replaceAll("<[^>]*>", ""));
            cleaned.append('\n');
        }
        if (!raw.endsWith("\n")) {
            cleaned.setLength(cleaned.length() - 1);
        }
        return cleaned.toString();
    }

    /**
     * Generates a report model from the given partition and centroids. Exposed
     * for callers that want to inspect or unit-test the structured data without
     * rebuilding centroids themselves.
     *
     * @param partition
     *            the partition to report on, may be {@code null}
     * @param centroids
     *            the centroid array defining cluster probabilities
     * @param options
     *            reporting options controlling which sections are emitted
     * @return a computed {@link ReportModel}, never null
     */
    public static ReportModel buildReportModel(@Nullable Partition partition,
            InfiniteCentroids centroids, ReportOptions options) {
        return ReportModel.of(partition, centroids, options);
    }
}
