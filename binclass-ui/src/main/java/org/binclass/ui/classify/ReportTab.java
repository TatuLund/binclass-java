/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify;

import com.vaadin.flow.signals.Signal;
import com.vaadin.flow.signals.local.ValueSignal;
import com.vaadin.flow.shared.Registration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Partition;
import org.binclass.components.partition.ClusterData;
import org.binclass.components.partition.PartitionModel;
import org.binclass.components.partition.VectorData;
import org.binclass.components.report.ReportComponent;

/**
 * Shared helper that turns a presentation {@link PartitionModel} into the
 * algorithm {@link Partition} a {@link ReportComponent} reports on, and binds
 * that component reactively to the model signal.
 * <p>
 * Both the GLA Search and Local Search displays expose only a
 * {@link PartitionModel} (the presentation record consumed by the partition
 * tab). The report needs an algorithm {@link Partition} of {@link BinaryVector}
 * instances, so this helper reconstructs one from each cluster's vectors and
 * feeds it to {@link ReportComponent#bindPartition}. Keeping the reconstruction
 * in one place avoids duplicating the model-to-partition mapping across views.
 * </p>
 */
@NullMarked
public final class ReportTab {

    private ReportTab() {
        // Utility class — prevent instantiation.
    }

    /**
     * Creates a fresh report component for embedding in a view's Report tab.
     *
     * @return a new {@link ReportComponent} with default reporting flags
     */
    public static ReportComponent create() {
        return new ReportComponent();
    }

    /**
     * Reconstructs an algorithm {@link Partition} from a presentation
     * {@link PartitionModel}. Each cluster becomes one partition bucket; every
     * {@link VectorData} in the cluster becomes a {@link BinaryVector} built
     * from its bits, carrying the vector's identifier (falling back to its
     * name) as the strain label. Clusters without vectors still occupy their
     * position so the report keeps one class per cluster.
     *
     * @param model
     *            the presentation partition, or {@code null} when no result is
     *            available yet
     * @return a freshly built partition with one bucket per cluster in the
     *         model; never empty of buckets even when the model has none
     */
    public static Partition toPartition(@Nullable PartitionModel model) {
        if (model == null || model.clusters().isEmpty()) {
            return new Partition(1);
        }
        int k = model.clusters().size();
        Partition partition = new Partition(k);
        for (int i = 0; i < model.clusters().size(); i++) {
            ClusterData cluster = model.clusters().get(i);
            for (VectorData vector : cluster.vectors()) {
                int[] bits = vector.bits();
                String strain = identifierOrName(vector);
                BinaryVector bv = new BinaryVector(bits, 0, bits.length, 0,
                        strain);
                partition.addElement(i + 1, bv);
            }
        }
        return partition;
    }

    /**
     * Returns the vector's identifier when present, otherwise its name, and an
     * empty string when neither is available. Used as the strain label for a
     * reconstructed {@link BinaryVector}.
     *
     * @param vector
     *            the presentation vector data
     * @return the identifier or name, never null
     */
    private static String identifierOrName(VectorData vector) {
        if (vector.identifier() != null && !vector.identifier().isBlank()) {
            return vector.identifier();
        }
        if (vector.name() != null && !vector.name().isBlank()) {
            return vector.name();
        }
        return "";
    }

    /**
     * Binds the given report component to a partition-model signal. Whenever
     * the model changes, the effect rebuilds an algorithm {@link Partition}
     * from it and feeds it to {@link ReportComponent#bindPartition}. The
     * returned registration is removed when the bound component detaches.
     *
     * @param report
     *            the report component to bind (must not be null)
     * @param model
     *            the partition-model signal to observe (must not be null)
     * @return a registration that unbinds the effect when removed
     */
    public static Registration bind(ReportComponent report,
            ValueSignal<PartitionModel> model) {
        return Signal.effect(report,
                () -> report.bindPartition(toPartition(model.get())));
    }
}
