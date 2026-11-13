/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.report;

import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.binclass.algorithms.report.ReportModel.ClassReport;

/**
 * Renders a single class's portion of the statistical report as structured
 * HTML: an {@code 
 * 
<h4>} heading naming the class, paragraphs for its size and nearest neighbour,
 * and a per-bit frequency table. Each instance carries the
 * {@code class-info-block} CSS class so tests can assert exactly one block is
 * present per cluster in the partition.
 */
@NullMarked
public class ClassInfoComponent extends VerticalLayout {

    /** CSS class applied to every instance for styling and test selection. */
    public static final String CLASS_NAME = "class-info-block";

    private final H4 heading;
    private Paragraph sizeParagraph;
    @Nullable
    private Paragraph nearestParagraph;

    /**
     * Builds the component from a {@link ClassReport}. When nearness output is
     * disabled the class has no nearest neighbour and that paragraph is
     * omitted.
     *
     * @param report
     *            the per-class data to render
     */
    public ClassInfoComponent(ClassReport report) {
        addClassName(CLASS_NAME);

        this.heading = new H4("Class " + report.classNumber() + " / "
                + report.totalClusters());
        this.sizeParagraph = new Paragraph(
                "Size: " + report.size());
        if (report.hasNearest()) {
            this.nearestParagraph = new Paragraph(String.format(
                    java.util.Locale.ROOT, "Nearest: %d (%.2f)",
                    report.nearestClass(), report.nearestDistance()));
        } else {
            this.nearestParagraph = null;
        }

        add(heading, sizeParagraph);
        if (nearestParagraph != null) {
            add(nearestParagraph);
        }
        add(new FrequencyTable(report.frequencies()));
    }

    /** Returns the class heading element. */
    public H4 getHeading() {
        return heading;
    }

    /** Returns the size paragraph, or {@code null} when absent. */
    @Nullable
    public Paragraph getSizeParagraph() {
        return sizeParagraph;
    }

    /** Returns the nearest-neighbour paragraph, or {@code null} when absent. */
    @Nullable
    public Paragraph getNearestParagraph() {
        return nearestParagraph;
    }
}
