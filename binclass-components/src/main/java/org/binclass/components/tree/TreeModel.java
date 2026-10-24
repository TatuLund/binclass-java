/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.tree;

import java.util.List;

import org.jspecify.annotations.NullMarked;

/**
 * Immutable presentation data for a whole dendrogram rendered by the
 * {@link Tree} component.
 * <p>
 * Carries a flat, index-addressed list of {@link TreeNodeData}. The list index
 * is equal to each node's id, so children are referenced by plain int indices
 * and no per-node coordinates need to be pushed from Java. Independent of
 * {@code binclass-algorithms}; the translation layer in {@code binclass-ui}
 * maps an algorithm {@code TreeNode} into this record.
 * </p>
 */
@NullMarked
public record TreeModel(List<TreeNodeData> nodes) {

    /**
     * Creates a tree model from a flat list of nodes.
     *
     * @param nodes
     *            the ordered node list; index equals each node's id
     */
    public TreeModel(List<TreeNodeData> nodes) {
        this.nodes = List.copyOf(nodes);
        if (this.nodes.isEmpty()) {
            throw new IllegalArgumentException("nodes must not be empty");
        }
    }

    /**
     * Returns an unmodifiable copy of the node list so callers cannot mutate
     * internal state.
     *
     * @return an unmodifiable copy of {@link #nodes()}
     */
    public List<TreeNodeData> nodes() {
        return List.copyOf(nodes);
    }
}
