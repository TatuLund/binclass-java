/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.demo;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.gla.GLAEngine;
import org.binclass.components.report.ReportComponent;

/**
 * Minimal Vaadin view that hosts the {@link ReportComponent} with
 * representative mock data so Playwright tests can drive it. The partition is
 * small and does not need to be mathematically correct, only structurally
 * valid: two clusters of short binary vectors bound to a report component whose
 * flags the user can toggle.
 */
@Route("report")
public class ReportView extends VerticalLayout {

    public ReportView() {
        setSpacing(true);
        setPadding(true);

        add(createReportSection());
    }

    private VerticalLayout createReportSection() {
        VerticalLayout section = new VerticalLayout();
        section.add("Report — flags and sanitized output");

        Partition partition = buildMockPartition(6, 3);

        ReportComponent report = new ReportComponent();
        report.bindPartition(partition);
        section.add(report);

        return section;
    }

    /**
     * Builds a small mock partition with the given number of clusters and bit
     * length. Each cluster holds two vectors so centroids can be recomputed.
     * The data is not mathematically meaningful, only structurally valid.
     *
     * @param k
     *            number of clusters (1-based)
     * @param l
     *            bit length of each vector
     * @return a populated partition ready to bind
     */
    private static Partition buildMockPartition(int k, int l) {
        Partition partition = new Partition(k);
        for (int cluster = 1; cluster <= k; cluster++) {
            int[] base = new int[l];
            for (int i = 0; i < l; i++) {
                base[i] = (cluster + i) % 2;
            }
            partition.addElement(cluster, new BinaryVector(base));
            int[] shifted = new int[l];
            for (int i = 0; i < l; i++) {
                shifted[i] = (i == 0) ? 1 - base[0] : base[i];
            }
            partition.addElement(cluster, new BinaryVector(shifted));
        }
        return partition;
    }
}
