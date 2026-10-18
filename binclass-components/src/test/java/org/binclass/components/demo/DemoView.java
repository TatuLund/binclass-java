/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.demo;

import java.util.List;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import org.binclass.components.partition.Centroid;
import org.binclass.components.partition.CentroidData;
import org.binclass.components.partition.Cluster;
import org.binclass.components.partition.ClusterData;
import org.binclass.components.partition.Partition;
import org.binclass.components.partition.PartitionModel;
import org.binclass.components.partition.Vector;
import org.binclass.components.partition.VectorData;

/**
 * Minimal Vaadin view that hosts the four custom components with representative
 * mock data so Playwright tests can drive them. The data does not need to be
 * mathematically correct, only visually representative: it covers grid-size
 * boundaries, name-prefix vs. tooltip switch, centroid grey gradients, lazy
 * cluster population, and a multi-cluster partition header.
 */
@Route("partition")
public class DemoView extends VerticalLayout {

    /**
     * Builds a bit array of the given length with an alternating 0/1 pattern.
     */
    private static int[] alternating(int length) {
        int[] bits = new int[length];
        for (int i = 0; i < length; i++) {
            bits[i] = i % 2;
        }
        return bits;
    }

    /**
     * Builds a rounded-centroid array of the given length with an alternating
     * 0/1 pattern.
     */
    private static int[] alternatingCentroid(int length) {
        int[] centroid = new int[length];
        for (int i = 0; i < length; i++) {
            centroid[i] = (i % 2 == 0) ? 1 : 0;
        }
        return centroid;
    }

    /**
     * Builds a probability array of the given length with an alternating 0.0 /
     * 1.0 pattern, so the centroid cells render as pure white/black and line up
     * visually with the vectors below them.
     */
    private static double[] alternatingProbabilities(int length) {
        double[] probs = new double[length];
        for (int i = 0; i < length; i++) {
            probs[i] = (i % 2 == 0) ? 1.0 : 0.0;
        }
        return probs;
    }

    public DemoView() {
        setSpacing(true);
        setPadding(true);

        add(createVectorSection());
        add(createCentroidSection());
        add(createClusterSection());
        add(createPartitionSection());
    }

    private VerticalLayout createVectorSection() {
        VerticalLayout section = new VerticalLayout();
        section.add(
                "Vectors — grid sizes from 2x2 up to 12x12 with remainder gaps");

        // 4 bits -> 2x2, no name inline.
        Vector small = new Vector();
        small.getElement().setAttribute("id", "vector-small");
        small.setData(new VectorData(alternating(4), "v-2x2"));
        section.add(small);

        // 9 bits -> 3x3 perfect square, name shown inline (N >= 9 is false here
        // so tooltip).
        Vector medium = new Vector();
        medium.getElement().setAttribute("id", "vector-medium");
        medium.setData(new VectorData(alternating(9), "v-medium", "id-9", 2,
                alternatingCentroid(9)));
        section.add(medium);

        // 100 bits -> 10x10 with name inline (N >= 9).
        Vector large = new Vector();
        large.setData(new VectorData(alternating(100), "v-10x10", "id-100", 7,
                alternatingCentroid(100)));
        section.add(large);

        // 145 bits -> 12x12 with one remainder gap (clamped at MAX_SIDE).
        Vector big = new Vector();
        big.getElement().setAttribute("id", "vector-big");
        big.setData(new VectorData(alternating(145), "v-12x12", "id-145", 3,
                alternatingCentroid(145)));
        section.add(big);

        return section;
    }

    private VerticalLayout createCentroidSection() {
        VerticalLayout section = new VerticalLayout();
        section.add(
                "Centroids — grey gradient across probabilities near 0 / 0.5 / 1");

        Centroid shortCentroid = new Centroid();
        shortCentroid.getElement().setAttribute("id", "centroid-short");
        shortCentroid.setData(new CentroidData(
                new double[] { 0.0, 0.25, 0.5, 0.75, 1.0 },
                "centroid-5",
                1.5,
                0.3,
                42));
        section.add(shortCentroid);

        // A long centroid (600 bits) so the minimum cell size and the
        // right-edge
        // overflow fade are visible: even at 2px per bit the strip scrolls.
        Centroid longCentroid = new Centroid();
        longCentroid.getElement().setAttribute("id", "centroid-long");
        longCentroid.setData(new CentroidData(
                alternatingProbabilities(600),
                "centroid-300",
                1.5,
                0.3,
                42));
        section.add(longCentroid);

        return section;
    }

    private VerticalLayout createClusterSection() {
        VerticalLayout section = new VerticalLayout();
        section.add(
                "Cluster — centroid always visible, vectors lazily populated on open");

        Cluster cluster = new Cluster(new ClusterData(
                "cluster-1",
                2.5,
                0.4,
                3,
                new CentroidData(
                        alternatingProbabilities(16),
                        "centroid-16", 2.5, 0.4, 3),
                List.of(
                        new VectorData(alternating(16), "vec-a", null, 2,
                                alternatingCentroid(16)),
                        new VectorData(alternating(16), "vec-b", null, 3,
                                alternatingCentroid(16)),
                        new VectorData(alternating(16), "vec-c", null, 0,
                                alternatingCentroid(16)))));

        section.add(cluster);
        section.add("Vectors render when the caret is opened");

        return section;
    }

    /**
     * Builds the set of vectors used by the demo clusters. All vectors share
     * the same length so their responsive grids size identically and line up
     * vertically with the centroid block above them.
     */
    private static List<VectorData> demoVectors() {
        return List.of(
                new VectorData(alternating(16), "vec-a", null, 2,
                        alternatingCentroid(16)),
                new VectorData(alternating(16), "vec-b", null, 3,
                        alternatingCentroid(16)),
                new VectorData(alternating(16), "vec-c", null, 0,
                        alternatingCentroid(16)));
    }

    private VerticalLayout createPartitionSection() {
        VerticalLayout section = new VerticalLayout();
        section.add("Partition — header metrics plus a body of clusters");

        List<VectorData> clusterVectors = demoVectors();
        PartitionModel model = new PartitionModel(
                1234.5,
                2,
                6,
                2.0,
                List.of(
                        new ClusterData(
                                "cluster-a", 1.0, 0.5, 3,
                                new CentroidData(
                                        new double[] { 1.0, 0.0 }),
                                clusterVectors),
                        new ClusterData(
                                "cluster-b", 2.0, 0.2, 3,
                                new CentroidData(
                                        new double[] { 0.0, 1.0 }),
                                clusterVectors)));

        Partition partition = new Partition();
        partition.setModel(model);
        section.add(partition);

        // Each cluster buffers its vectors and renders them when the caret is
        // opened. Uniform-length vectors keep every grid sized identically so
        // the centroid and all vector cells align vertically across clusters.
        section.add("Vectors render when a caret is opened");

        return section;
    }
}
