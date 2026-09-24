/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.cut;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;

/**
 * Partition cutting and trimming utilities.
 * <p>
 * Mirrors the C functions from {@code cut.c} in the original codebase: performs
 * interval analysis by intersecting two partitions to produce a more stable
 * result partition. Three intersection strategies are supported, each
 * corresponding to one of the {@code cut} command switches:
 * </p>
 * <ul>
 * <li>{@link #simpleIntersection} — relative interval ({@code do_simple_int}),
 * keeps elements that appear in any cluster of both partitions.</li>
 * <li>{@link #minimalInterval} — minimal interval ({@code do_min_int}), keeps
 * the best-matching (unique maximum overlap) clusters.</li>
 * <li>{@link #maximalInterval} — maximal interval ({@code do_max_int}), keeps
 * every cluster achieving the maximum overlap.</li>
 * </ul>
 * <p>
 * All methods operate on 1-based cluster indices to match the C
 * {@code Partition} struct and trim empty clusters from the result.
 * </p>
 */
public final class CutEngine {

    private static final String P1_MUST_NOT_BE_NULL = "Partition p1 must not be null";
    private static final String P2_MUST_NOT_BE_NULL = "Partition p2 must not be null";

    private CutEngine() {
        // Utility class — prevent instantiation
    }

    /**
     * Performs a relative interval intersection.
     * <p>
     * Equivalent to C function {@code do_simple_int()} from {@code cut.c}. For
     * every cluster of {@code p1}, an element is copied into the matching
     * result cluster when it also belongs to some cluster of {@code p2}. Empty
     * result clusters are removed.
     * </p>
     *
     * @param p1
     *            the first partition (its cluster count drives the result)
     * @param p2
     *            the second partition used for membership checks
     * @return a new trimmed partition holding the intersected elements
     */
    public static Partition simpleIntersection(Partition p1, Partition p2) {
        Objects.requireNonNull(p1, P1_MUST_NOT_BE_NULL);
        Objects.requireNonNull(p2, P2_MUST_NOT_BE_NULL);

        int k1 = p1.size();
        Partition result = new Partition(k1);

        for (int i = 1; i <= k1; i++) {
            for (BinaryVector bv : p1.getElements(i)) {
                if (belongsToAnyCluster(p2, bv)) {
                    result.addElement(i, bv);
                }
            }
        }

        return trim(result);
    }

    /**
     * Performs a minimal interval intersection.
     * <p>
     * Equivalent to C function {@code do_min_int()} from {@code cut.c}. For
     * each cluster of {@code p1} the best-matching cluster of {@code p2} is the
     * one with the largest overlap; when that maximum is unique, only the
     * elements shared with that single cluster are copied to the matching
     * result cluster. Empty result clusters are removed.
     * </p>
     *
     * @param p1
     *            the first partition (its cluster count drives the result)
     * @param p2
     *            the second partition used for overlap comparison
     * @return a new trimmed partition holding the best-matching elements
     */
    public static Partition minimalInterval(Partition p1, Partition p2) {
        Objects.requireNonNull(p1, P1_MUST_NOT_BE_NULL);
        Objects.requireNonNull(p2, P2_MUST_NOT_BE_NULL);

        int k1 = p1.size();
        Partition result = new Partition(k1);

        for (int i = 1; i <= k1; i++) {
            var elementsP1 = p1.getElements(i);
            if (elementsP1.isEmpty()) {
                continue;
            }

            int bestMatch = bestUniqueMatch(elementsP1, p2);
            if (bestMatch > 0) {
                var elementsP2 = p2.getElements(bestMatch);
                for (BinaryVector bv : elementsP1) {
                    if (elementsP2.contains(bv)) {
                        result.addElement(i, bv);
                    }
                }
            }
        }

        return trim(result);
    }

    /**
     * Finds the cluster of {@code p2} with the largest overlap against the
     * given elements. Returns that cluster only when its maximum is unique;
     * otherwise returns {@code -1}.
     *
     * @param elementsP1
     *            the reference cluster elements
     * @param p2
     *            the partition to search for matches
     * @return the 1-based index of the best-matching cluster, or {@code -1}
     *         when no unique maximum overlap exists
     */
    private static int bestUniqueMatch(Iterable<BinaryVector> elementsP1,
            Partition p2) {
        int bestMatch = -1;
        int maxOverlap = 0;
        int maximalCount = 0;

        for (int j = 1; j <= p2.size(); j++) {
            int overlap = countOverlap(elementsP1, p2.getElements(j));
            if (overlap > maxOverlap) {
                maxOverlap = overlap;
                bestMatch = j;
                maximalCount = 1;
            } else if (overlap == maxOverlap && maxOverlap > 0) {
                maximalCount++;
            }
        }

        return maximalCount == 1 ? bestMatch : -1;
    }

    /**
     * Performs a maximal interval intersection.
     * <p>
     * Equivalent to C function {@code do_max_int()} from {@code cut.c}. For
     * each cluster of {@code p1} every cluster of {@code p2} that reaches the
     * maximum overlap contributes its shared elements to a fresh result
     * cluster, so ties produce multiple result clusters instead of discarding
     * them.
     * </p>
     *
     * @param p1
     *            the first partition driving the number of result clusters
     * @param p2
     *            the second partition used for overlap comparison
     * @return a new trimmed partition holding every maximal-matching element
     */
    public static Partition maximalInterval(Partition p1, Partition p2) {
        Objects.requireNonNull(p1, P1_MUST_NOT_BE_NULL);
        Objects.requireNonNull(p2, P2_MUST_NOT_BE_NULL);

        int k1 = p1.size();
        Partition result = new Partition(k1 * p2.size());
        int nextCluster = 1;

        for (int i = 1; i <= k1; i++) {
            var elementsP1 = p1.getElements(i);
            int maxOverlap = maximumOverlap(elementsP1, p2);
            if (elementsP1.isEmpty() || maxOverlap == 0) {
                continue;
            }

            for (int j : maximalMatchClusters(elementsP1, p2, maxOverlap)) {
                var elementsP2 = p2.getElements(j);
                for (BinaryVector bv : elementsP1) {
                    if (elementsP2.contains(bv)) {
                        result.addElement(nextCluster++, bv);
                    }
                }
            }
        }

        return trim(result);
    }

    /**
     * Computes the largest overlap between the given elements and any cluster
     * of {@code p2}.
     *
     * @param elementsP1
     *            the reference cluster elements
     * @param p2
     *            the partition to search for matches
     * @return the maximum shared-element count across all clusters, or
     *         {@code 0} when none share any element
     */
    private static int maximumOverlap(Iterable<BinaryVector> elementsP1,
            Partition p2) {
        int maxOverlap = 0;
        for (int j = 1; j <= p2.size(); j++) {
            int overlap = countOverlap(elementsP1, p2.getElements(j));
            if (overlap > maxOverlap) {
                maxOverlap = overlap;
            }
        }
        return maxOverlap;
    }

    /**
     * Collects the 1-based cluster indices of {@code p2} that reach the given
     * maximum overlap.
     *
     * @param elementsP1
     *            the reference cluster elements
     * @param p2
     *            the partition to search for matches
     * @param maxOverlap
     *            the overlap threshold a cluster must match
     * @return the list of matching cluster indices (empty when none qualify)
     */
    private static List<Integer> maximalMatchClusters(
            Iterable<BinaryVector> elementsP1, Partition p2, int maxOverlap) {
        List<Integer> matches = new ArrayList<>();
        for (int j = 1; j <= p2.size(); j++) {
            if (countOverlap(elementsP1, p2.getElements(j)) == maxOverlap) {
                matches.add(j);
            }
        }
        return matches;
    }

    /**
     * Counts how many vectors are shared between two clusters.
     *
     * @param a
     *            the first cluster elements
     * @param b
     *            the second cluster elements
     * @return the number of vectors present in both clusters
     */
    private static int countOverlap(
            Iterable<BinaryVector> a, VectorSet b) {
        int overlap = 0;
        for (BinaryVector bv : a) {
            if (b.contains(bv)) {
                overlap++;
            }
        }
        return overlap;
    }

    /**
     * Checks whether the vector belongs to any cluster of the partition.
     *
     * @param partition
     *            the partition to search
     * @param bv
     *            the vector to look for
     * @return {@code true} when at least one cluster contains the vector
     */
    private static boolean belongsToAnyCluster(Partition partition,
            BinaryVector bv) {
        for (int j = 1; j <= partition.size(); j++) {
            if (partition.contains(j, bv)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Removes empty clusters from a partition by compacting the cluster array.
     *
     * @param partition
     *            the partition to trim in place
     * @return the same partition with its size reduced to the number of
     *         non-empty clusters
     */
    private static Partition trim(Partition partition) {
        int k = partition.size();
        int writeIdx = 0;

        for (int readIdx = 1; readIdx <= k; readIdx++) {
            if (partition.getSize(readIdx) > 0) {
                if (writeIdx != readIdx) {
                    for (BinaryVector bv : partition.getElements(readIdx)) {
                        partition.addElement(writeIdx + 1, bv);
                    }
                }
                writeIdx++;
            }
        }

        partition.setSize(writeIdx > 0 ? writeIdx : 1);
        return partition;
    }
}
