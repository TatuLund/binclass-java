/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.report;

import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

import org.binclass.algorithms.core.BinaryVector;
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
@StyleSheet("/affinitymatrix.css")
public class ReportComponent extends VerticalLayout {

    @Nullable
    private Partition partition;

    private Checkbox printDigits = new Checkbox("Print digits (-d)");
    private Checkbox affinityMatrix = new Checkbox("Affinity matrix (-a)");
    private Checkbox useHellinger = new Checkbox("Use Hellinger distance (-h)");
    private IntegerField reportParams = new IntegerField(
            "Report params (-p, 0=all)");

    /** The nearness/affinity matrix, rendered inside a {@link Details}. */
    private final AffinityMatrix affinityMatrixTable = new AffinityMatrix();

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

        // Keep the matrix table in the tree from construction so callers can
        // locate it by id even before a partition is bound. It starts empty and
        // is populated whenever nearness output is rendered.
        affinityMatrixTable.setId("report-affinity");

        this.controls = createControls();
        this.reportBody = new VerticalLayout();
        reportBody.setId("report-body");

        reportBody.add(affinityMatrixTable);

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
        reportBody.removeAll();
        // Keep the matrix table attached so it stays locatable by id even when
        // no partition is bound or nearness output is disabled. It starts empty
        // and is populated whenever a nearness section renders.
        reportBody.add(affinityMatrixTable);

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
            VerticalLayout frequenciesContent = new VerticalLayout();
            frequenciesContent.add(new FrequencyTable(model.totalFrequencies(),
                    model.printDigitsEnabled()));
            reportBody.add(details("TOTAL FREQUENCIES:", frequenciesContent));
        }

        VerticalLayout classesContent = new VerticalLayout();
        for (ReportModel.ClassReport classReport : model.classReports()) {
            classesContent.add(new ClassInfoComponent(classReport));
        }
        reportBody.add(details("LIST OF CLASSES:", classesContent));

        if (model.showNearness() && model.matrixValues() != null) {
            affinityMatrixTable.render(model.matrixValues(), k,
                    model.partition());
            VerticalLayout matrixContent = new VerticalLayout();
            matrixContent.add(affinityMatrixTable);
            reportBody.add(details("CLASS NEARNESS MATRIX:", matrixContent));
        } else {
            // Clear any rows left from a previous render so disabling nearness
            // empties the table instead of showing stale values.
            affinityMatrixTable.removeAllRows();
        }
    }

    /**
     * Wraps a section body in an expandable {@link Details} so the report can
     * stay compact instead of forcing extensive scrolling (see PRD §17.4).
     *
     * @param summary
     *            the collapsible label shown when closed
     * @param content
     *            the section body to hide/show
     * @return a {@link Details} holding the content under the given summary
     */
    private static Details details(String summary, VerticalLayout content) {
        Details details = new Details(new H4(summary), content);
        details.addClassName("report-section");
        return details;
    }

    /**
     * Returns the maximum vector length across every non-empty cluster in the
     * partition. The report must span all bit positions present in the data, so
     * it uses the longest vector rather than a single representative's length;
     * otherwise a shorter first vector would drop trailing columns and render
     * fewer cells than the actual data (e.g. 46 instead of 47).
     *
     * @param partition
     *            the partition to inspect
     * @return bit-length of vectors, or 0 if all clusters are empty
     */
    private static int getVectorLength(Partition partition) {
        int max = 0;
        for (int i = 1; i <= partition.size(); i++) {
            VectorSet cluster = partition.getElements(i);
            if (!cluster.isEmpty()) {
                for (BinaryVector bv : cluster) {
                    max = Math.max(max, bv.getLength());
                }
            }
        }
        return max;
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
