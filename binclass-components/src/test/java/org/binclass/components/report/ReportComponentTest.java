/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Table;
import com.vaadin.flow.component.html.TableCell;
import com.vaadin.flow.component.html.TableRow;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.report.ReportModel;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ReportComponent}. Verifies the component tree, flag
 * bindings and structured HTML report (headings, frequency tables, per-class
 * blocks) described in PRD §17.4.
 */
@SuppressWarnings("null")
class ReportComponentTest {

    /** Builds a small two-cluster partition of three-bit vectors. */
    private static Partition mockPartition() {
        Partition partition = new Partition(2);
        partition.addElement(1, new BinaryVector(new int[] { 0, 0, 0 }));
        partition.addElement(1, new BinaryVector(new int[] { 0, 0, 1 }));
        partition.addElement(2, new BinaryVector(new int[] { 1, 1, 1 }));
        return partition;
    }

    /**
     * Recursively searches the component subtree rooted at {@code root} for a
     * child carrying the given id. Vaadin 25.3 exposes no
     * {@code getComponentById}, so the tree is walked via
     * {@link Component#getChildren()}. The search is depth-first and checks
     * {@code root} itself before descending into its children.
     *
     * @param root
     *            the component to search from (inclusive)
     * @param id
     *            the element id to match
     * @return the first matching component, or {@code null} if none matches
     */
    private static Component findById(Component root, String id) {
        if (root.getId().filter(id::equals).isPresent()) {
            return root;
        }
        for (Component child : root.getChildren().toList()) {
            Component found = findById(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * Counts components in the subtree rooted at {@code root} whose class
     * attribute contains {@code className}. Used to assert that exactly one
     * per-class block is rendered for each cluster.
     *
     * @param root
     *            the component to search from (inclusive)
     * @param className
     *            the CSS class substring to match
     * @return number of matching components
     */
    private static long countClass(Component root, String className) {
        int count = 0;
        String classes = root.getElement().getAttribute("class");
        if (classes != null && classes.contains(className)) {
            count++;
        }
        for (Component child : root.getChildren().toList()) {
            count += countClass(child, className);
        }
        return count;
    }

    /**
     * Collects every component in the subtree rooted at {@code root} whose
     * class attribute contains {@code className}. Used to inspect each rendered
     * frequency table individually.
     *
     * @param root
     *            the component to search from (inclusive)
     * @param className
     *            the CSS class substring to match
     * @return a mutable list of matching components
     */
    private static List<Component> collectClass(Component root,
            String className) {
        List<Component> found = new ArrayList<>();
        String classes = root.getElement().getAttribute("class");
        if (classes != null && classes.contains(className)) {
            found.add(root);
        }
        for (Component child : root.getChildren().toList()) {
            found.addAll(collectClass(child, className));
        }
        return found;
    }

    /**
     * Counts components in the subtree rooted at {@code root} whose tag name
     * equals {@code tag}.
     *
     * @param root
     *            the component to search from (inclusive)
     * @param tag
     *            the HTML tag name to match (e.g. {@code h3})
     * @return number of matching components
     */
    private static long countTag(Component root, String tag) {
        int count = 0;
        if (root.getElement().getTag().equals(tag)) {
            count++;
        }
        for (Component child : root.getChildren().toList()) {
            count += countTag(child, tag);
        }
        return count;
    }

    @Test
    void componentTreeHasExpectedIds() {
        ReportComponent report = new ReportComponent();

        assertEquals("report-component", report.getId().orElse(null));
        assertTrue(report instanceof VerticalLayout);

        // Flag controls carry the ids from PRD §17.4.
        Component printDigits = findById(report, "print-digits");
        Component affinityMatrix = findById(report, "affinity-matrix");
        Component useHellinger = findById(report, "use-hellinger");
        Component reportParams = findById(report, "report-params");

        assertNotNull(printDigits);
        assertNotNull(affinityMatrix);
        assertNotNull(useHellinger);
        assertNotNull(reportParams);

        // Matrix table and report body carry their ids.
        Table matrixTable = (Table) findById(report, "report-affinity");
        VerticalLayout reportBody = (VerticalLayout) findById(report,
                "report-body");

        assertTrue(matrixTable.getId().isPresent());
        assertTrue(reportBody.getId().isPresent());
    }

    @Test
    void boundPartitionPopulatesReportHeader() {
        ReportComponent report = new ReportComponent();
        report.bindPartition(mockPartition());

        Component header = findById(report, "report-header");
        assertNotNull(header);
        assertTrue(header instanceof H3);
        assertTrue(((H3) header).getText().contains("STATISTICAL REPORT"));
    }

    @Test
    void sectionTitlesAreH4() {
        ReportComponent report = new ReportComponent();
        report.bindPartition(mockPartition());

        // Default options enable every section, so there are at least the two
        // always-present sub-headers (total frequencies and list of classes).
        assertTrue(countTag(report, "h4") >= 2);
    }

    @Test
    void oneClassInfoBlockPerCluster() {
        ReportComponent report = new ReportComponent();
        report.bindPartition(mockPartition());

        // Two clusters in the mock partition -> two class-info-block elements.
        assertEquals(2, countClass(report, "class-info-block"));
    }

    @Test
    void frequencyTablesRenderedForTotalAndClasses() {
        ReportComponent report = new ReportComponent();
        report.bindPartition(mockPartition());

        // One total-frequencies table plus one per-class table (two clusters).
        assertEquals(3, countClass(report, "frequency-table"));
    }

    /**
     * Builds a partition whose first cluster holds a short vector while another
     * cluster holds the longest one. Used to prove the report spans every bit
     * position rather than only those of a single representative vector.
     *
     * @param shortLength
     *            length of the representative (first-cluster) vector
     * @param maxLength
     *            length of the longest vector in the partition
     * @return a two-cluster partition with one short and one long vector
     */
    private static Partition mixedLengthPartition(int shortLength,
            int maxLength) {
        Partition partition = new Partition(2);
        int[] shortBits = new int[shortLength];
        for (int p = 0; p < shortLength; p++) {
            shortBits[p] = 1;
        }
        int[] maxBits = new int[maxLength];
        for (int p = 0; p < maxLength; p++) {
            maxBits[p] = 1;
        }
        // Cluster 1 first so its shorter vector is the representative picked by
        // a naive "first non-empty cluster" length lookup.
        partition.addElement(1, new BinaryVector(shortBits));
        partition.addElement(2, new BinaryVector(maxBits));
        return partition;
    }

    @Test
    void frequencyTableSpansMaxVectorLength() {
        ReportComponent report = new ReportComponent();
        // Representative (cluster 1) is 46 bits while the longest vector is 47,
        // so a length lookup that returns only the representative would render
        // 46 columns and drop the final bit position.
        report.bindPartition(mixedLengthPartition(46, 47));

        int expectedColumns = 47;
        boolean sawFullWidthTable = false;
        for (Component child : collectClass(report, "frequency-table")) {
            Table table = (Table) child;
            // Header row carries one cell per bit plus the leading corner cell.
            assertEquals(expectedColumns + 1,
                    table.getHeaderRows().get(0).getCells().size());
            sawFullWidthTable = true;
        }
        assertTrue(sawFullWidthTable, "at least one frequency table rendered");
    }

    /**
     * Builds a partition where the longest vector shares a cluster with shorter
     * ones. Used to prove the report spans every bit position even when a
     * cluster's first (representative) vector is shorter than a later member of
     * that same cluster — the deeper off-by-one behind the 46-vs-47 bug.
     *
     * @param shortLength
     *            length of the representative (first-in-cluster) vector
     * @param maxLength
     *            length of the longest vector in the partition
     * @return a two-cluster partition with the long vector sharing cluster 1
     */
    private static Partition mixedLengthInSameCluster(int shortLength,
            int maxLength) {
        Partition partition = new Partition(2);
        int[] shortBits = new int[shortLength];
        for (int p = 0; p < shortLength; p++) {
            shortBits[p] = 1;
        }
        int[] maxBits = new int[maxLength];
        for (int p = 0; p < maxLength; p++) {
            maxBits[p] = 1;
        }
        // Both vectors in cluster 1, short first so a per-cluster "first
        // vector"
        // lookup reports the shorter length.
        partition.addElement(1, new BinaryVector(shortBits));
        partition.addElement(1, new BinaryVector(maxBits));
        partition.addElement(2, new BinaryVector(shortBits));
        return partition;
    }

    @Test
    void frequencyTableSpansMaxWithinCluster() {
        ReportComponent report = new ReportComponent();
        // Cluster 1's first vector is 46 bits but a later member is 47. A
        // lookup
        // that returns only the cluster's representative (first vector) renders
        // 46 columns and drops the final bit position.
        report.bindPartition(mixedLengthInSameCluster(46, 47));

        int expectedColumns = 47;
        boolean sawFullWidthTable = false;
        for (Component child : collectClass(report, "frequency-table")) {
            Table table = (Table) child;
            assertEquals(expectedColumns + 1,
                    table.getHeaderRows().get(0).getCells().size());
            sawFullWidthTable = true;
        }
        assertTrue(sawFullWidthTable, "at least one frequency table rendered");
    }

    @Test
    void nearnessDisabledHidesMatrix() {
        ReportComponent report = new ReportComponent();
        report.bindPartition(mockPartition());

        // A non-zero mask without RP_NEARNESS disables the matrix. Use a value
        // that selects only total frequencies (RP_TOTALFREQ = 1).
        Component params = findById(report, "report-params");
        if (params != null && params instanceof IntegerField) {
            ((IntegerField) params).setValue(1);
        }

        Table matrixTable = (Table) findById(report, "report-affinity");
        // Nearness disabled, so the table has no rows.
        assertEquals(0, matrixTable.getAllRows().size());
    }

    @Test
    void enablingNearnessPopulatesMatrix() {
        ReportComponent report = new ReportComponent();
        report.bindPartition(mockPartition());

        // Enable nearness via the -p bitmask (RP_NEARNESS = 2).
        Component params = findById(report, "report-params");
        if (params != null && params instanceof IntegerField) {
            ((IntegerField) params).setValue(2);
        }

        Table matrixTable = (Table) findById(report, "report-affinity");
        // Header row plus one data row per cluster.
        assertEquals(3, matrixTable.getAllRows().size());
    }

    @Test
    void matrixHeaderHasBottomBorderClass() {
        ReportComponent report = new ReportComponent();
        report.bindPartition(mockPartition());

        Table matrixTable = (Table) findById(report, "report-affinity");
        TableRow headerRow = matrixTable.getHeaderRows().get(0);
        for (TableCell cell : headerRow.getCells()) {
            assertTrue(cell.hasClassName("matrix-header-cell"));
        }
    }

    @Test
    void matrixFirstColumnHasRightBorderClass() {
        ReportComponent report = new ReportComponent();
        report.bindPartition(mockPartition());

        Table matrixTable = (Table) findById(report, "report-affinity");
        // First data row's leading label cell carries the vertical rule.
        TableRow firstData = matrixTable.getBodyRows().get(0);
        assertTrue(firstData.getCells().get(0)
                .hasClassName("matrix-label"));
    }

    @Test
    void diagonalCellIsSingleDashAndStyledClass() {
        ReportComponent report = new ReportComponent();
        report.bindPartition(mockPartition());

        Table matrixTable = (Table) findById(report, "report-affinity");
        // Row for class 1: label at index 0, diagonal at index 1.
        TableRow rowOne = matrixTable.getBodyRows().get(0);
        TableCell diagonal = rowOne.getCells().get(1);
        assertEquals("-", diagonal.getText());
        assertTrue(diagonal.hasClassName("matrix-diagonal"));
    }

    @Test
    void clearingPartitionEmptiesReportBody() {
        ReportComponent report = new ReportComponent();
        report.bindPartition(mockPartition());

        Component header = findById(report, "report-header");
        assertNotNull(header);
        assertTrue(((H3) header).getText().contains("STATISTICAL REPORT"));

        report.bindPartition(null);
        assertEquals(0, countClass(report, "class-info-block"));
    }

    @Test
    void sanitizeStripsMarkupButKeepsLineBreaks() {
        String sanitized = ReportComponent.sanitize("<b>hello</b>\nworld");
        assertTrue(sanitized.contains("hello"));
        assertTrue(sanitized.contains("world"));
        assertFalse(sanitized.contains("<"));
    }

    @Test
    void sanitizeHandlesNullAndEmpty() {
        assertEquals("", ReportComponent.sanitize(null));
        assertEquals("", ReportComponent.sanitize(""));
    }

    @Test
    void frequencyTableHeaderHasEmptyCornerCell() {
        List<ReportModel.BitFrequency> frequencies = List.of(
                new ReportModel.BitFrequency(1, 5, 40.0),
                new ReportModel.BitFrequency(2, 3, 24.0),
                new ReportModel.BitFrequency(3, 1, 8.0));

        FrequencyTable table = new FrequencyTable(frequencies);

        // Header row: an empty corner cell at index 0 keeps the bit-position
        // headers aligned with their data columns below.
        TableRow headerRow = table.getHeaderRows().get(0);
        assertEquals(frequencies.size() + 1, headerRow.getCells().size());

        TableCell corner = headerRow.getCells().get(0);
        assertTrue(corner.hasClassName("frequency-header-cell"));
        assertEquals("", corner.getText());

        // Bit-position headers fill the remaining cells, labelled by their
        // 1-based column position so a length-N table reads "1..N".
        for (int b = 0; b < frequencies.size(); b++) {
            TableCell cell = headerRow.getCells().get(b + 1);
            assertTrue(cell.hasClassName("frequency-header-cell"));
            assertEquals(Integer.toString(b + 1), cell.getText());
        }

        // Body rows must share the header's width so their numeric cells line
        // up under the bit-position headers instead of shifting by one.
        for (TableRow bodyRow : table.getBodyRows()) {
            assertEquals(frequencies.size() + 1, bodyRow.getCells().size());
        }
    }
}
