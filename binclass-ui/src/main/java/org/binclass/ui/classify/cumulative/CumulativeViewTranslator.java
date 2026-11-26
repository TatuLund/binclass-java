/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.cumulative;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.binclass.algorithms.classify.CumulativeClassifier;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.DynamicPartition;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.TreeNode;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.dist.DistanceCalculator;
import org.binclass.algorithms.tree.TreeBuilder;
import org.binclass.components.partition.CentroidData;
import org.binclass.components.partition.ClusterData;
import org.binclass.components.partition.PartitionModel;
import org.binclass.components.partition.VectorData;
import org.binclass.components.tree.TreeModel;
import org.binclass.ui.classify.gla.GlaViewTranslator;

/**
 * Translation layer for the Cumulative Classification display. Maps a dynamic
 * partition from {@code binclass-algorithms} into the presentation records
 * consumed by {@code binclass-components}, following the convention described
 * in {@code docs/WebUI.PRD.md} §5.2.1 — done here in the UI module, never
 * inside the components so they stay free of an algorithms dependency.
 * <p>
 * Cumulative classification carries no separate centroids object, so a cluster
 * centroid is shaped on demand from its frequency table divided by the cluster
 * size: each bit probability is {@code freqs[bit] / clusterSize}. The single
 * whole-partition SC header metric is computed from the static view via
 * {@link DistanceCalculator#stochasticComplexity(Partition, int, int)}.
 * </p>
 */
@NullMarked
public final class CumulativeViewTranslator {

    private CumulativeViewTranslator() {
        // Utility class — prevent instantiation.
    }

    /**
     * Builds a {@link PartitionModel} from an algorithm dynamic partition. Each
     * cluster becomes one {@link ClusterData}: its centroid probabilities are
     * derived from the frequency table divided by the cluster size, the name is
     * the most frequent strain label (falling back to a one-based index), and
     * the assigned vectors are carried as lazy {@link VectorData}. The header
     * metric is the whole-partition SC computed from the static view.
     * <p>
     * Tolerates an empty partition: it returns a model with zero clusters and a
     * {@code NaN} SC so the bound Partition component never dereferences null.
     * </p>
     *
     * @param dynPart
     *            the dynamic partition produced by cumulative classification;
     *            may be {@code null} or empty, in which case an empty model is
     *            returned
     * @return a {@link PartitionModel} ready to bind to the Partition component
     */
    public static PartitionModel toPartitionModel(
            @Nullable DynamicPartition dynPart) {
        if (dynPart == null || dynPart.size() == 0) {
            return new PartitionModel(Double.NaN, 0, 0, Double.NaN, List.of());
        }

        int k = dynPart.size();
        int l = dynPart.getVectorLength();
        int totalVectors = Math.max(1, dynPart.convert().getNumberOfVectors());
        double sc = stochasticComplexity(dynPart.convert(), k, l);
        int vectorCount = dynPart.convert().getNumberOfVectors();

        List<ClusterData> clusters = new ArrayList<>(k);
        for (int i = 1; i <= k; i++) {
            if (dynPart.getClusterSize(i) == 0) {
                continue;
            }

            VectorSet clusterSet = dynPart.getCluster(i);
            @SuppressWarnings("null")
            List<BinaryVector> vectors = toList(clusterSet);

            String name = mostFrequentName(vectors, i);
            int size = Math.max(1, dynPart.getClusterSize(i));
            double weight = clusterSet.size() / (double) totalVectors;
            CentroidData centroid = centroidFromFreqs(dynPart, i, size, l);
            @SuppressWarnings("null")
            List<VectorData> vectorData = vectors.stream()
                    .map(bv -> toVectorData(bv, centroid))
                    .toList();

            clusters.add(new ClusterData(name, Double.NaN, weight,
                    clusterSet.size(), centroid, vectorData));
        }

        return new PartitionModel(sc, clusters.size(), vectorCount,
                Double.NaN, clusters);
    }

    /**
     * Builds a probability centroid from a cluster's frequency table divided by
     * its size. Each bit probability is {@code freqs[bit] / clusterSize},
     * clamped into the closed interval {@code [0, 1]} so the rendered cells
     * stay valid even for tiny clusters.
     *
     * @param dynPart
     *            the dynamic partition carrying the frequency table
     * @param i
     *            the 1-based cluster index
     * @param size
     *            the cluster size, used as the divisor (never zero)
     * @param l
     *            the vector length, matching the frequency-table width
     * @return a {@link CentroidData} whose probabilities are per-bit
     *         frequencies
     */
    /**
     * Computes the whole-partition SC from the static view, tolerating the edge
     * cases that {@link DistanceCalculator#stochasticComplexity} rejects: an
     * empty partition or a single cluster with one vector. Those shapes throw
     * inside {@code stochastic_complexity_u}, so any failure is collapsed to
     * {@code NaN} and the caller keeps rendering.
     *
     * @param partition
     *            the static view of the dynamic partition
     * @param k
     *            the cluster count
     * @param l
     *            the vector length
     * @return the whole-partition SC, or {@code NaN} when it cannot be computed
     */
    private static double stochasticComplexity(Partition partition, int k,
            int l) {
        try {
            return DistanceCalculator.stochasticComplexity(partition, k, l);
        } catch (RuntimeException ex) {
            return Double.NaN;
        }
    }

    private static CentroidData centroidFromFreqs(DynamicPartition dynPart,
            int i, int size, int l) {
        double[] probs = new double[l];
        int[] freqs = dynPart.getFreqs(i);
        for (int bit = 0; bit < l && bit < freqs.length; bit++) {
            probs[bit] = Math.clamp((double) freqs[bit] / size, 0.0, 1.0);
        }
        return new CentroidData(probs);
    }

    /**
     * Converts a cluster vector into {@link VectorData}, carrying its bits,
     * strain/class-name identifiers and an empty rounded centroid for hover
     * highlight (cumulative centroids are probability vectors, not binary).
     *
     * @param bv
     *            the source binary vector (must not be {@code null})
     * @param centroid
     *            the cluster centroid; used only to keep the signature
     *            symmetric with GLA's translator
     * @return a {@link VectorData} snapshot of the vector
     */
    private static VectorData toVectorData(BinaryVector bv,
            CentroidData centroid) {
        int[] bits = bv.getEl();
        return new VectorData(bits, classNameOrNull(bv), strainOrNull(bv), 0,
                new int[bits.length]);
    }

    /**
     * Returns the dominant label inside a cluster, preferring the most frequent
     * class-name (the group label such as {@code "ESCH COLI"}) and falling back
     * to the most common strain identifier when no vector carries a class-name.
     * When neither is present it returns the one-based index.
     *
     * @param vectors
     *            the cluster's binary vectors (may be empty)
     * @param fallbackIndex
     *            the 1-based cluster index used as name when no label exists
     * @return the dominant class-name, or the dominant strain, or
     *         {@code String.valueOf(fallbackIndex)}
     */
    @SuppressWarnings("null")
    private static String mostFrequentName(List<BinaryVector> vectors,
            int fallbackIndex) {
        @SuppressWarnings("null")
        String name = dominantLabel(vectors, BinaryVector::getClassName);
        if (name == null) {
            name = dominantLabel(vectors, BinaryVector::getStrain);
        }
        return (name != null && !name.isBlank()) ? name
                : String.valueOf(fallbackIndex);
    }

    /**
     * Returns the most frequent non-blank label produced by {@code labelOf} for
     * the given vectors, or {@code null} when every vector carries a blank
     * label. Ties keep whichever label was seen first.
     *
     * @param vectors
     *            the cluster's binary vectors (may be empty)
     * @param labelOf
     *            extracts a label from each vector
     * @return the dominant label, or {@code null} when none is present
     */
    @SuppressWarnings("null")
    private static @Nullable String dominantLabel(List<BinaryVector> vectors,
            Function<BinaryVector, String> labelOf) {
        Map<String, Integer> counts = new HashMap<>();
        for (BinaryVector bv : vectors) {
            String label = labelOf.apply(bv);
            if (label == null || label.isBlank()) {
                continue;
            }
            counts.merge(label, 1, Integer::sum);
        }
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    /**
     * Returns the vector's strain label, or {@code null} when blank so the UI
     * can distinguish "no name" from an empty string.
     *
     * @param bv
     *            the source binary vector (must not be {@code null})
     * @return the strain label, or {@code null}
     */
    private static String strainOrNull(BinaryVector bv) {
        String strain = bv.getStrain();
        return (strain == null || strain.isBlank()) ? null : strain;
    }

    /**
     * Returns the vector's class-name label, or {@code null} when blank.
     *
     * @param bv
     *            the source binary vector (must not be {@code null})
     * @return the class name, or {@code null}
     */
    private static String classNameOrNull(BinaryVector bv) {
        String className = bv.getClassName();
        return (className == null || className.isBlank()) ? null : className;
    }

    /**
     * Converts a cluster's vectors into an ordered list for iteration.
     *
     * @param set
     *            the cluster's vector set (must not be {@code null})
     * @return an immutable snapshot of its vectors
     */
    private static List<BinaryVector> toList(VectorSet set) {
        return new ArrayList<>(set.getElements());
    }

    /**
     * Builds an {@link InfiniteCentroids} from a dynamic partition's frequency
     * tables, so the PNN dendrogram algorithm can operate on cumulative
     * clusters. Each cluster's centroid is its frequency table divided by the
     * cluster size, matching how the Partition tab renders centroids. The
     * uniform prior used by the tree mirrors the uniform-prior SC header metric
     * of the partition model.
     *
     * @param dynPart
     *            the dynamic partition (must not be {@code null} and non-empty)
     * @return an InfiniteCentroids carrying one probability centroid per
     *         cluster
     */
    public static InfiniteCentroids buildCentroids(DynamicPartition dynPart) {
        int k = dynPart.size();
        int l = dynPart.getVectorLength();
        double[][] data = new double[k][l];
        for (int i = 1; i <= k; i++) {
            int size = Math.max(1, dynPart.getClusterSize(i));
            int[] freqs = dynPart.getFreqs(i);
            for (int bit = 0; bit < l && bit < freqs.length; bit++) {
                data[i - 1][bit] = Math.clamp((double) freqs[bit] / size, 0.0,
                        1.0);
            }
        }
        return new InfiniteCentroids(data, k);
    }

    /**
     * Flattens a dendrogram built from a dynamic partition into an indexed
     * {@link TreeModel}. Leaf labels come from each cluster's most-frequent
     * strain (falling back to its one-based index) and leaf sizes are the
     * original cluster sizes, captured before {@code TreeBuilder.makeTreePnn}
     * mutates the partition in place. The PNN merge uses uniform prior,
     * consistent with the partition model's SC header metric.
     *
     * @param dynPart
     *            the dynamic partition to build a dendrogram from (must not be
     *            {@code null} and non-empty)
     * @return a non-empty {@link TreeModel} ready to bind to the Tree component
     */
    public static TreeModel toTreeModel(DynamicPartition dynPart) {
        Partition partition = dynPart.convert();
        int k = dynPart.size();
        Map<String, String> labels = new HashMap<>();
        Map<String, Integer> sizes = new HashMap<>();
        for (int i = 1; i <= k; i++) {
            List<BinaryVector> vectors = toList(dynPart.getCluster(i));
            labels.put("C" + i, mostFrequentName(vectors, i));
            sizes.put("C" + i, dynPart.getClusterSize(i));
        }
        TreeNode root = TreeBuilder.makeTreePnn(partition,
                buildCentroids(dynPart), false);
        return GlaViewTranslator.toTreeModel(partition, root, labels, sizes);
    }

    /**
     * Runs cumulative classification on the given vectors and translates the
     * result into a {@link PartitionModel}. Exposed as a convenience so tests
     * can drive the full algorithm-to-presentation path without wiring the view
     * model. Posts progress and new-cluster events through the event bus while
     * running, exactly like the classifier entry point used by the view.
     *
     * @param vectors
     *            the vectors to classify (must not be {@code null})
     * @param config
     *            the cumulative configuration (must not be {@code null})
     * @return the translated partition model, never {@code null}
     */
    public static PartitionModel runAndTranslate(VectorSet vectors,
            org.binclass.algorithms.classify.CumulativeConfig config) {
        DynamicPartition result = CumulativeClassifier
                .doCumulativeClassification(vectors, config);
        return toPartitionModel(result);
    }

    /**
     * Runs cumulative classification on the given vectors and returns the raw
     * dynamic partition so callers can stream it for download. Progress and
     * new-cluster events are posted through the event bus while running.
     *
     * @param vectors
     *            the vectors to classify (must not be {@code null})
     * @param config
     *            the cumulative configuration (must not be {@code null})
     * @return the dynamic partition produced by classification, never
     *         {@code null} for a non-empty set
     */
    public static DynamicPartition run(VectorSet vectors,
            org.binclass.algorithms.classify.CumulativeConfig config) {
        return CumulativeClassifier.doCumulativeClassification(vectors, config);
    }
}
