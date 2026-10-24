/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.tree;

import org.jspecify.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

/**
 * Immutable presentation data for a single node of a dendrogram rendered by the
 * {@link Tree} component.
 * <p>
 * Nodes are stored in a flat, index-addressed list (see {@link TreeModel}), so
 * each node references its children by their list index ({@code -1} means no
 * child). A leaf carries the inline label used for rendering; internal nodes
 * rely on their knob tooltip but still expose the subtree's most common name,
 * size and frequency. Independent of {@code binclass-algorithms}; the
 * translation layer in {@code binclass-ui} maps an algorithm {@code TreeNode}
 * into this record.
 * </p>
 */
@NullMarked
public record TreeNodeData(
        /** Unique 0-based id within the tree (list index). */
        int id,
        /**
         * Original identifier from {@code TreeNode.getName()} ({@code "C1"},
         * {@code "Merged"}, {@code "Root"}); kept as a fallback when strains
         * are empty.
         */
        String name,
        /**
         * Sum-of-costs / merge cost at this node (kept for reference; not used
         * to position nodes).
         */
        double sc,
        /**
         * Number of vectors in the subtree rooted here (joint cluster size).
         */
        int size,
        /**
         * Most common strain name in that node's subtree — used as the leaf
         * inline label and as the internal-node tooltip name. Empty when
         * unknown.
         */
        @Nullable String label,
        /**
         * How many vectors share {@link #label()} within this subtree
         * (frequency of the most common name). Zero when {@link #label()} is
         * empty.
         */
        int frequency,
        /** True when this node has no children. */
        boolean isLeaf,
        /** Id of left child, or {@code -1}. */
        int leftChild,
        /** Id of right child, or {@code -1}. */
        int rightChild) {

    /**
     * Creates a leaf node with no children.
     *
     * @param id
     *            the unique 0-based index within the tree
     * @param name
     *            the fallback identifier string
     * @param sc
     *            the sum-of-costs value at this level
     * @param size
     *            the number of vectors in the subtree (one for a singleton)
     * @param label
     *            the most common strain name, or {@code null} when unknown
     */
    public TreeNodeData(int id, String name, double sc, int size,
            @Nullable String label) {
        this(id, name, sc, size, label, frequencyOf(label), true, -1, -1);
    }

    /**
     * Creates an internal (merged) node with two children.
     *
     * @param id
     *            the unique 0-based index within the tree
     * @param name
     *            the fallback identifier string ({@code "Merged"} /
     *            {@code "Root"})
     * @param sc
     *            the sum-of-costs value at this level
     * @param size
     *            the joint number of vectors in both subtrees
     * @param label
     *            the most common strain name across both subtrees, or
     *            {@code null} when unknown
     * @param frequency
     *            how many vectors share {@link #label()} within this subtree
     * @param leftChild
     *            id of the left child, or {@code -1}
     * @param rightChild
     *            id of the right child, or {@code -1}
     */
    public TreeNodeData(int id, String name, double sc, int size,
            @Nullable String label, int frequency, boolean isLeaf,
            int leftChild, int rightChild) {
        if (id < 0) {
            throw new IllegalArgumentException("id must not be negative");
        }
        this.id = id;
        this.name = name != null ? name : "";
        this.sc = sc;
        this.size = size;
        this.label = label;
        this.frequency = frequency;
        this.isLeaf = isLeaf;
        this.leftChild = leftChild;
        this.rightChild = rightChild;
    }

    /**
     * Returns the number of vectors sharing the given label, or zero when the
     * label is empty.
     *
     * @param label
     *            the most common strain name, may be null or empty
     * @return the frequency of that name within the subtree, or zero
     */
    private static int frequencyOf(@Nullable String label) {
        return (label == null || label.isEmpty()) ? 0 : 1;
    }

    /**
     * Returns the display label for this node, falling back to its identifier
     * when no strain name is known.
     *
     * @return the non-empty label or identifier string
     */
    public String displayName() {
        return (label != null && !label.isEmpty()) ? label : name;
    }
}
