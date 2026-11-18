/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.gla;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.components.graph.GraphModel;
import org.binclass.components.graph.GraphPoint;
import org.binclass.components.partition.CentroidData;
import org.binclass.components.partition.ClusterData;
import org.binclass.components.partition.PartitionModel;
import org.binclass.components.partition.VectorData;

/**
 * Translation layer for the GLA Search display. Maps algorithm objects from
 * {@code binclass-algorithms} into the presentation records consumed by
 * {@code binclass-components}, following the convention described in
 * {@code docs/WebUI.PRD.md} §1.3 — done here in the UI module, never inside the
 * components so they stay free of an algorithms dependency.
 * <p>
 * Two mappings are provided:
 * </p>
 * <ul>
 * <li>{@link #toPartitionModel(Partition, InfiniteCentroids, double)} turns a
 * best partition plus its centroids into a {@link PartitionModel} with one
 * {@link ClusterData} per cluster (centroid probabilities, rounded centroid for
 * hover, and lazy {@link VectorData}).</li>
 * <li>{@link #toGraphModel(double[], int[])} turns the per-{@code k} SC curve
 * into a {@link GraphModel}, dropping non-finite points so the chart never
 * renders {@code NaN}/{@code Inf}.</li>
 * </ul>
 */
@NullMarked
public final class GlaViewTranslator {

    private GlaViewTranslator() {
        // Utility class — prevent instantiation.
    }

    /**
     * Builds a {@link PartitionModel} from an algorithm partition and its
     * centroids. Each cluster becomes one {@link ClusterData}: the centroid's
     * probability array, the rounded centroid used for hover highlight, and the
     * assigned vectors as {@link VectorData}.
     * <p>
     * The partition is 1-based in size; clusters are read with
     * {@link Partition#getCluster(int)} using 0-based indices. Cluster names
     * use the most frequent strain label inside the cluster, falling back to a
     * one-based index when no strain is present.
     * </p>
     *
     * @param partition
     *            the best partition found (must not be {@code null})
     * @param centroids
     *            the matching centroid array; may be {@code null} when only a
     *            partition is available, in which case each cluster gets an
     *            empty-probability centroid of the right length
     * @param stochasticComplexity
     *            the SC value for the whole partition (header metric)
     * @return a {@link PartitionModel} ready to bind to the Partition component
     */
    public static PartitionModel toPartitionModel(Partition partition,
            @Nullable InfiniteCentroids centroids,
            double stochasticComplexity) {
        int k = partition.size();
        int totalVectors = Math.max(1, partition.getNumberOfVectors());

        List<ClusterData> clusters = new ArrayList<>(k);
        for (int i = 0; i < k; i++) {
            var clusterSet = partition.getCluster(i);
            List<BinaryVector> vectors = toList(clusterSet);
            Centroid centroid = centroids != null && i < centroids.size()
                    ? centroids.get(i)
                    : null;

            String name = mostFrequentStrain(vectors, i + 1);
            double weight = clusterSet.size() / (double) totalVectors;
            double distortion = averageDistortion(vectors, centroid);

            CentroidData centroidData = new CentroidData(
                    centroid != null ? centroid.getEl() : emptyArray(vectors),
                    name, distortion, weight, clusterSet.size());

            List<VectorData> vectorData = vectors.stream()
                    .map(bv -> toVectorData(bv, centroid))
                    .toList();

            clusters.add(new ClusterData(name, distortion, weight,
                    clusterSet.size(), centroidData, vectorData));
        }

        return new PartitionModel(stochasticComplexity, k,
                partition.getNumberOfVectors(), Double.NaN, clusters);
    }

    /**
     * Builds a {@link GraphModel} from the per-{@code k} SC curve. Drops points
     * whose SC is non-finite so the chart never renders
     * {@code NaN}/{@code Inf}.
     * <p>
     * Iterates by array position so compact arrays produced by the searches
     * (finite values only, no index-0 placeholder) render their first point.
     * When {@code ks} is supplied it provides each point's x coordinate; when
     * {@code ks} is absent or shorter than {@code scs}, the array position is
     * used as the x coordinate. Callers that pass an index-aligned array with a
     * placeholder at index 0 still work because that slot holds the unused
     * {@code k = 0} value (typically {@code NaN}).
     *
     * @param scs
     *            the per-{@code k} SC values (must not be {@code null})
     * @param ks
     *            optional cluster counts paired with each SC value; when
     *            {@code null} or shorter than {@code scs}, the array position
     *            is used as the x coordinate
     * @return a non-empty {@link GraphModel}, or an empty model list wrapped so
     *         callers can always bind it
     */
    public static GraphModel toGraphModel(double[] scs, int[] ks) {
        List<GraphPoint> points = new ArrayList<>();
        for (int i = 0; i < scs.length; i++) {
            double sc = scs[i];
            // Drop non-finite values and the uncomputed sentinels the searches
            // leave in place: AutomaticSearch uses Double.MAX_VALUE and
            // RangeSearch
            // uses 1000.0 for cluster counts it never scored, so they would
            // otherwise stretch the adaptive y-axis and squash the real curve.
            if (!Double.isFinite(sc) || sc == 1000.0
                    || sc == Double.MAX_VALUE) {
                continue;
            }
            int x = ks != null && i < ks.length ? ks[i] : i;
            points.add(new GraphPoint(x, sc));
        }
        return new GraphModel(points);
    }

    /**
     * Converts a cluster's vectors into {@link VectorData}, carrying each
     * vector's bits, strain/class-name identifiers, Hamming distance to the
     * centroid, and the rounded centroid used for hover highlight.
     *
     * @param bv
     *            the source binary vector (must not be {@code null})
     * @param centroid
     *            the cluster centroid; may be {@code null} when unavailable
     * @return a {@link VectorData} snapshot of the vector
     */
    private static VectorData toVectorData(BinaryVector bv, Centroid centroid) {
        int[] bits = bv.getEl();
        int distance = centroid != null
                ? bv.hammingDistance(centroidToBinary(centroid))
                : 0;
        return new VectorData(bits, strainOrNull(bv), classNameOrNull(bv),
                distance, roundedCentroid(centroid, bits.length));
    }

    /**
     * Returns the most frequent strain label inside a cluster, falling back to
     * the one-based index when no vector carries a strain.
     *
     * @param vectors
     *            the cluster's binary vectors (may be empty)
     * @param fallbackIndex
     *            the 1-based cluster index used as name when no strain exists
     * @return the dominant strain label, or {@code "k" + fallbackIndex}
     */
    private static String mostFrequentStrain(List<BinaryVector> vectors,
            int fallbackIndex) {
        Map<String, Integer> counts = new HashMap<>();
        for (BinaryVector bv : vectors) {
            String strain = bv.getStrain();
            if (strain == null || strain.isBlank()) {
                continue;
            }
            counts.merge(strain, 1, Integer::sum);
        }
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return (best != null && !best.isBlank()) ? best
                : String.valueOf(fallbackIndex);
    }

    /**
     * Computes the average Hamming distance from each vector to its centroid,
     * or {@code NaN} when no centroid is available.
     *
     * @param vectors
     *            the cluster's binary vectors (may be empty)
     * @param centroid
     *            the cluster centroid; may be {@code null}
     * @return the mean distance, or {@code NaN} when unavailable
     */
    private static double averageDistortion(List<BinaryVector> vectors,
            Centroid centroid) {
        if (vectors.isEmpty() || centroid == null) {
            return Double.NaN;
        }
        long total = 0;
        for (BinaryVector bv : vectors) {
            total += bv.hammingDistance(centroidToBinary(centroid));
        }
        return (double) total / vectors.size();
    }

    /**
     * Rounds a centroid's probability array into {@code 0}/{@code 1} values for
     * hover highlight, returning an empty array when the centroid is absent.
     *
     * @param centroid
     *            the cluster centroid; may be {@code null}
     * @param length
     *            the expected rounded-array length (used when centroid is
     *            absent)
     * @return a rounded centroid array matching {@code length}
     */
    private static int[] roundedCentroid(Centroid centroid, int length) {
        if (centroid == null) {
            return new int[length];
        }
        int[] rounded = new int[centroid.getLength()];
        for (int i = 0; i < rounded.length; i++) {
            rounded[i] = centroid.get(i) >= 0.5 ? 1 : 0;
        }
        return rounded;
    }

    /**
     * Returns an empty probability array matching the vectors' length, used
     * when a cluster has no centroid to render.
     *
     * @param vectors
     *            the cluster's binary vectors (may be empty)
     * @return an all-zero double array of the shared vector length, or empty
     */
    private static double[] emptyArray(List<BinaryVector> vectors) {
        if (vectors.isEmpty()) {
            return new double[0];
        }
        return new double[vectors.get(0).length()];
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
     * Converts a centroid into a binary vector of the same length so its
     * Hamming distance can be compared against cluster members. Values round to
     * 0/1 at the 0.5 boundary.
     *
     * @param centroid
     *            the cluster centroid (must not be {@code null})
     * @return a binary vector of matching length
     */
    private static BinaryVector centroidToBinary(Centroid centroid) {
        int len = centroid.getLength();
        int[] bits = new int[len];
        for (int i = 0; i < len; i++) {
            bits[i] = centroid.get(i) >= 0.5 ? 1 : 0;
        }
        return new BinaryVector(bits);
    }

    /**
     * Copies a {@link VectorSet} into an ordered list for iteration.
     *
     * @param set
     *            the cluster's vector set (must not be {@code null})
     * @return an immutable snapshot of its vectors
     */
    private static List<BinaryVector> toList(VectorSet set) {
        return new ArrayList<>(set.getElements());
    }
}
