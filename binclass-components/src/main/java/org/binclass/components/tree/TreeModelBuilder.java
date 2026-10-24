/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.tree;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.NullMarked;

/**
 * Flattens a {@link TreeNode} hierarchy into an index-addressed
 * {@link TreeModel}.
 * <p>
 * The builder walks the tree in post-order so every node is appended after its
 * children, giving each node an id equal to its position in the resulting list.
 * It wires left/right child indices, propagates the sum-of-costs value from the
 * source node and sums subtree sizes for internal nodes. Leaf labels and sizes
 * are supplied by optional providers keyed by leaf name; when absent a leaf
 * gets an empty label and size zero.
 * </p>
 * <p>
 * Each node also carries the multiset of its leaves' labels so the most common
 * strain in any subtree can be derived deterministically: the highest count
 * wins, ties broken by first occurrence (insertion order). Internal nodes
 * expose that merged label and frequency for their tooltip.
 * </p>
 */
@NullMarked
public final class TreeModelBuilder {

    private final Function<String, String> labelProvider;
    private final Function<String, Integer> sizeProvider;

    /**
     * Creates a builder with map-based leaf providers keyed by leaf name.
     *
     * @param labels
     *            mapping of each leaf's name to its most common strain label;
     *            leaves absent from this map get an empty label
     * @param sizes
     *            mapping of each leaf's name to its cluster size; leaves absent
     *            from this map get size zero
     */
    public TreeModelBuilder(Map<String, String> labels,
            Map<String, Integer> sizes) {
        this.labelProvider = labels == null ? n -> ""
                : k -> labels.getOrDefault(k, "");
        this.sizeProvider = sizes == null ? n -> 0
                : k -> sizes.getOrDefault(k, 0);
    }

    /**
     * Creates a builder whose leaf providers are the given functions.
     *
     * @param labelProvider
     *            returns the most common strain label for a leaf name, or an
     *            empty string when unknown
     * @param sizeProvider
     *            returns the cluster size for a leaf name, or zero when unknown
     */
    public TreeModelBuilder(Function<String, String> labelProvider,
            Function<String, Integer> sizeProvider) {
        this.labelProvider = labelProvider;
        this.sizeProvider = sizeProvider;
    }

    /**
     * Builds the flat model from a tree root.
     *
     * @param root
     *            the dendrogram root (may itself be a single leaf); must not be
     *            null
     * @return an immutable, index-addressed tree model
     */
    @SuppressWarnings("unused")
    public TreeModel build(TreeNode root) {
        if (root == null) {
            throw new IllegalArgumentException("Tree root must not be null");
        }
        List<TreeNodeData> nodes = new ArrayList<>();
        buildNode(root, nodes);
        return new TreeModel(nodes);
    }

    private NodeInfo buildNode(TreeNode node, List<TreeNodeData> nodes) {
        if (node.isLeaf()) {
            String label = labelProvider.apply(node.getName());
            int size = sizeProvider.apply(node.getName());
            Map<String, Integer> counts = new LinkedHashMap<>();
            if (!label.isEmpty()) {
                counts.put(label, 1);
            }
            TreeNodeData data = new TreeNodeData(
                    nodes.size(), node.getName(), node.getSC(), size, label,
                    frequencyOf(label), true, -1, -1);
            nodes.add(data);
            return new NodeInfo(counts, size, nodes.size() - 1);
        }

        NodeInfo left = buildNode(node.getLeft(), nodes);
        NodeInfo right = buildNode(node.getRight(), nodes);
        // Left/right children were appended before this node in post-order,
        // so their list indices are valid positions to reference.
        TreeNodeData leftData = nodes.get(left.index);
        TreeNodeData rightData = nodes.get(right.index);

        Map<String, Integer> counts = mergeCounts(left.counts, right.counts);
        String label = mostCommon(counts);
        int size = left.size + right.size;

        TreeNodeData data = new TreeNodeData(
                nodes.size(), node.getName(), node.getSC(), size, label,
                frequencyOf(label), false, leftData.id(), rightData.id());
        nodes.add(data);
        return new NodeInfo(counts, size, nodes.size() - 1);
    }

    /** Merges two label-count maps preserving first-occurrence order. */
    private static Map<String, Integer> mergeCounts(Map<String, Integer> a,
            Map<String, Integer> b) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : a.entrySet()) {
            merged.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        for (Map.Entry<String, Integer> e : b.entrySet()) {
            merged.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        return merged;
    }

    /** Returns the label with the highest count, ties broken by first order. */
    private static String mostCommon(Map<String, Integer> counts) {
        String best = null;
        int bestCount = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        return best == null ? "" : best;
    }

    private static int frequencyOf(String label) {
        return label.isEmpty() ? 0 : 1;
    }

    /** Intermediate build result: subtree label counts, size and list index. */
    private record NodeInfo(Map<String, Integer> counts, int size,
            int index) {
        NodeInfo(Map<String, Integer> counts, int size) {
            this(counts, size, -1);
        }
    }
}
