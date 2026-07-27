/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.core;

import java.util.Objects;

/**
 * An immutable tree node representing a cluster hierarchy, mirroring the C
 * {@code TreeNode} struct from {@code binset.h}.
 * <p>
 * Used to represent dendrograms and hierarchical clustering results. Each node
 * contains:
 * <ul>
 * <li>{@code sc} — sum-of-costs value at this level</li>
 * <li>{@code name} — identifier string (cluster label or leaf name)</li>
 * <li>{@code left} — left child node (or null for leaves)</li>
 * <li>{@code right} — right child node (or null for leaves)</li>
 * </ul>
 * </p>
 * Immutable by design — tree nodes are created once and never modified.
 */
public final class TreeNode {

    private final double sc;
    private final String name;
    private final TreeNode left;
    private final TreeNode right;

    /**
     * Creates a new {@code TreeNode} with the given data.
     *
     * @param sc
     *            sum-of-costs value at this level
     * @param name
     *            identifier string (cluster label or leaf name)
     * @param left
     *            left child node (null for leaves)
     * @param right
     *            right child node (null for leaves)
     */
    public TreeNode(double sc, String name, TreeNode left, TreeNode right) {
        this.sc = sc;
        this.name = name != null ? name : "";
        this.left = left;
        this.right = right;
    }

    /**
     * Creates a leaf node with no children.
     * <p>
     * Equivalent to C function {@code tree_node_create_leaf()} from
     * {@code binset.h}.
     * </p>
     *
     * @param sc
     *            sum-of-costs value at this level
     * @param name
     *            identifier string (leaf name)
     */
    public TreeNode(double sc, String name) {
        this(sc, name, null, null);
    }

    /**
     * Returns the sum-of-costs value at this level.
     * <p>
     * Equivalent to C function {@code tree_node_get_sc()} from
     * {@code binset.h}.
     * </p>
     *
     * @return SC value
     */
    public double getSC() {
        return sc;
    }

    /**
     * Returns the identifier string for this node.
     * <p>
     * Equivalent to C function {@code tree_node_get_name()} from
     * {@code binset.h}.
     * </p>
     *
     * @return name/identifier string
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the left child node.
     * <p>
     * Equivalent to C function {@code tree_node_get_left()} from
     * {@code binset.h}.
     * </p>
     *
     * @return left child (null for leaves)
     */
    public TreeNode getLeft() {
        return left;
    }

    /**
     * Returns the right child node.
     * <p>
     * Equivalent to C function {@code tree_node_get_right()} from
     * {@code binset.h}.
     * </p>
     *
     * @return right child (null for leaves)
     */
    public TreeNode getRight() {
        return right;
    }

    /**
     * Returns whether this node is a leaf (no children).
     * <p>
     * Equivalent to C function {@code tree_node_is_leaf()} from
     * {@code binset.h}.
     * </p>
     *
     * @return true if both left and right are null
     */
    public boolean isLeaf() {
        return left == null && right == null;
    }

    /**
     * Returns a string representation of this tree node.
     */
    @Override
    public String toString() {
        if (isLeaf()) {
            return "TreeNode{name='" + name + "', sc="
                    + String.format("%.4f", sc) + "}";
        } else {
            return "TreeNode{sc=" + String.format("%.4f", sc) + ", left=" + left
                    + ", right=" + right + "}";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TreeNode other))
            return false;
        return Double.compare(sc, other.sc) == 0 &&
                name.equals(other.name) &&
                Objects.equals(left, other.left) &&
                Objects.equals(right, other.right);
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(sc);
        result = 31 * result + name.hashCode();
        result = 31 * result + (left != null ? left.hashCode() : 0);
        result = 31 * result + (right != null ? right.hashCode() : 0);
        return result;
    }

}
