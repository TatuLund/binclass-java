/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.tree;

import org.jspecify.annotations.NullMarked;

/**
 * A lightweight, dependency-free tree node used by {@link TreeModelBuilder} to
 * flatten a dendrogram into a flat {@link TreeModel}. It mirrors the shape of
 * the algorithms {@code TreeNode} without depending on that module, so
 * {@code binclass-components} stays decoupled from {@code binclass-algorithms}.
 * <p>
 * A node is either a leaf (both children {@code null}) or an internal merge
 * node with exactly two children. Names are unique identifiers within the tree
 * ({@code "C1"}, {@code "Merged"}, {@code "Root"} and so on).
 * </p>
 */
@NullMarked
public interface TreeNode {

    /**
     * Returns the identifier string for this node.
     *
     * @return the node name; never null
     */
    String getName();

    /**
     * Returns the sum-of-costs value at this level.
     *
     * @return the stochastic complexity of this node
     */
    double getSC();

    /**
     * Returns whether this node is a leaf (has no children).
     *
     * @return true when both children are null
     */
    boolean isLeaf();

    /**
     * Returns the left child, or {@code null} for leaves.
     *
     * @return the left child node
     */
    TreeNode getLeft();

    /**
     * Returns the right child, or {@code null} for leaves.
     *
     * @return the right child node
     */
    TreeNode getRight();
}
