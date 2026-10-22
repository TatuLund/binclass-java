/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import com.vaadin.flow.signals.local.ValueSignal;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TreeModelBuilder}, the flat presentation records and
 * the {@link Tree} wrapper. The builder walks a {@link TreeNode} hierarchy in
 * post-order, so every node's list index ({@code id}) is assigned after its
 * children: leaves come first, then each merge that references them by index.
 */
class TreeTest {

    /** A minimal in-memory {@link TreeNode} with optional two children. */
    private static final class SimpleNode implements TreeNode {
        private final String name;
        private final double sc;
        private final boolean leaf;
        private final TreeNode left;
        private final TreeNode right;

        SimpleNode(String name, double sc) {
            this(name, sc, null, null);
        }

        SimpleNode(String name, double sc, TreeNode left, TreeNode right) {
            this.name = name;
            this.sc = sc;
            this.left = left;
            this.right = right;
            this.leaf = left == null && right == null;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public double getSC() {
            return sc;
        }

        @Override
        public boolean isLeaf() {
            return leaf;
        }

        @Override
        public TreeNode getLeft() {
            return left;
        }

        @Override
        public TreeNode getRight() {
            return right;
        }
    }

    /** Builds a bare leaf node with the given name and sum-of-costs. */
    private static SimpleNode leaf(String name, double sc) {
        return new SimpleNode(name, sc);
    }

    /** Builds an internal merge node from two children. */
    private static SimpleNode merge(String name, double sc, TreeNode left,
            TreeNode right) {
        return new SimpleNode(name, sc, left, right);
    }

    // --- TreeModel -------------------------------------------------------

    @Test
    void treeModelRejectsEmptyList() {
        assertThrows(IllegalArgumentException.class,
                () -> new TreeModel(List.of()));
    }

    @Test
    void treeModelNodesReturnUnmodifiableCopy() {
        TreeNodeData node = new TreeNodeData(0, "C1", 1.0, 3, "A");
        TreeModel model = new TreeModel(List.of(node));

        List<TreeNodeData> nodes = model.nodes();
        assertThrows(UnsupportedOperationException.class,
                () -> nodes.add(new TreeNodeData(1, "C2", 2.0, 4, "B")));
    }

    // --- TreeNodeData ----------------------------------------------------

    @Test
    void treeNodeDataRejectsNegativeId() {
        assertThrows(IllegalArgumentException.class,
                () -> new TreeNodeData(-1, "C1", 1.0, 1, "A"));
    }

    @Test
    void treeNodeDataDisplayNameFallsBackToNameWhenLabelEmpty() {
        assertEquals("C1", new TreeNodeData(0, "C1", 1.0, 1, "").displayName());
        assertEquals("C1", new TreeNodeData(0, "C1", 1.0, 1, null)
                .displayName());
    }

    @Test
    void treeNodeDataDisplayNameUsesLabelWhenPresent() {
        assertEquals("strainA",
                new TreeNodeData(0, "C1", 1.0, 1, "strainA").displayName());
    }

    // --- Builder: single leaf -------------------------------------------

    @Test
    void buildSingleLeafProducesOneNode() {
        TreeModel model = new TreeModelBuilder(Map.of("C1", "A"),
                Map.of("C1", 5)).build(leaf("C1", 2.5));

        List<TreeNodeData> nodes = model.nodes();
        assertEquals(1, nodes.size());
        TreeNodeData only = nodes.get(0);
        assertEquals(0, only.id());
        assertEquals("C1", only.name());
        assertTrue(only.isLeaf());
        assertEquals(-1, only.leftChild());
        assertEquals(-1, only.rightChild());
        assertEquals(2.5, only.sc(), 0.0);
        assertEquals(5, only.size());
        assertEquals("A", only.label());
        assertEquals(1, only.frequency());
    }

    @Test
    void buildNullRootThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new TreeModelBuilder(Map.of(), Map.of()).build(null));
    }

    // --- Builder: two leaves --------------------------------------------

    @Test
    void buildTwoLeavesWiresChildrenAndSumsSizes() {
        TreeModel model = new TreeModelBuilder(Map.of("C1", "A", "C2", "B"),
                Map.of("C1", 5, "C2", 7)).build(
                        merge("Merged", 3.0,
                                leaf("C1", 1.0), leaf("C2", 2.0)));

        List<TreeNodeData> nodes = model.nodes();
        assertEquals(3, nodes.size());

        TreeNodeData left = nodes.get(0);
        TreeNodeData right = nodes.get(1);
        TreeNodeData merged = nodes.get(2);

        // Leaves are emitted first in post-order.
        assertEquals("C1", left.name());
        assertEquals("C2", right.name());
        assertTrue(left.isLeaf());
        assertTrue(right.isLeaf());

        // The merge references both leaves by their list indices and sums the
        // subtree sizes.
        assertEquals(merged.id(), 2);
        assertFalse(merged.isLeaf());
        assertEquals(0, merged.leftChild());
        assertEquals(1, merged.rightChild());
        assertEquals(12, merged.size());
        assertEquals(3.0, merged.sc(), 0.0);

        // Tie between A and B is broken by first occurrence (C1's label).
        assertEquals("A", merged.label());
        assertEquals(1, merged.frequency());
    }

    @Test
    void buildPropagatesMostCommonStrainWithCount() {
        TreeModel model = new TreeModelBuilder(Map.of("C1", "A", "C2", "B"),
                Map.of("C1", 5, "C2", 7)).build(
                        merge("Merged", 3.0,
                                leaf("C1", 1.0), leaf("C2", 2.0)));

        TreeNodeData merged = model.nodes().get(2);
        // A and B each appear once; the first-occurrence tie keeps A.
        assertEquals("A", merged.label());
    }

    @Test
    void buildAppliesProvidersWhenLeafUnknown() {
        TreeModel model = new TreeModelBuilder(Map.of(), Map.of()).build(
                leaf("C1", 1.0));

        TreeNodeData only = model.nodes().get(0);
        assertEquals("", only.label());
        assertEquals(0, only.size());
        assertEquals(0, only.frequency());
    }

    // --- Builder: deeper ladder -----------------------------------------

    @Test
    void buildDeeperLadderWiresIndicesInPostOrder() {
        // ((C1 C2) C3): post-order emits C1, C2, Merged_12, C3, Merged_123.
        TreeNode root = merge("Merged_123", 7.0,
                merge("Merged_12", 3.0, leaf("C1", 1.0), leaf("C2", 2.0)),
                leaf("C3", 4.0));

        TreeModel model = new TreeModelBuilder(Map.of("C1", "A", "C2", "B",
                "C3", "C"), Map.of("C1", 5, "C2", 7, "C3", 9)).build(root);

        List<TreeNodeData> nodes = model.nodes();
        assertEquals(5, nodes.size());

        TreeNodeData c1 = nodes.get(0);
        TreeNodeData c2 = nodes.get(1);
        TreeNodeData m12 = nodes.get(2);
        TreeNodeData c3 = nodes.get(3);
        TreeNodeData rootMerge = nodes.get(4);

        assertEquals("C1", c1.name());
        assertTrue(c1.isLeaf());
        assertEquals("C2", c2.name());
        assertTrue(c2.isLeaf());
        assertEquals("C3", c3.name());
        assertTrue(c3.isLeaf());

        // First merge wires its two leaves and sums their sizes.
        assertEquals(0, m12.leftChild());
        assertEquals(1, m12.rightChild());
        assertEquals(12, m12.size());
        // A, B each once -> first occurrence (A) wins the tie.
        assertEquals("A", m12.label());

        // Root wires the previous merge and the third leaf; sizes accumulate.
        assertEquals(2, rootMerge.leftChild());
        assertEquals(3, rootMerge.rightChild());
        assertEquals(21, rootMerge.size());
        // A, B, C each once -> first occurrence (A) wins again.
        assertEquals("A", rootMerge.label());
    }

    // --- Tree wrapper ----------------------------------------------------

    @Test
    void setDataPushesModelProperty() {
        Tree tree = new Tree();
        TreeModel model = new TreeModelBuilder(Map.of(), Map.of()).build(
                leaf("C1", 1.0));

        tree.setData(model);

        assertTrue(tree.getElement().hasProperty(Tree.PROPERTY_MODEL));
    }

    @Test
    void setDataRejectsNull() {
        Tree tree = new Tree();
        assertThrows(IllegalArgumentException.class, () -> tree.setData(null));
    }

    @Test
    void getNodesReturnsFlatListOfNodeMaps() {
        Tree tree = new Tree();
        TreeModel model = new TreeModelBuilder(Map.of("C1", "A", "C2", "B"),
                Map.of("C1", 5, "C2", 7)).build(
                        merge("Merged", 3.0,
                                leaf("C1", 1.0), leaf("C2", 2.0)));

        tree.setData(model);

        List<Map<String, Object>> nodes = tree.getNodes();
        assertEquals(3, nodes.size());

        Map<String, Object> merged = nodes.get(2);
        assertEquals(2, (Integer) merged.get("id"));
        assertEquals("Merged", merged.get("name"));
        assertEquals(12, (Integer) merged.get("size"));
        assertEquals("A", merged.get("label"));
        assertEquals(1, (Integer) merged.get("frequency"));
        assertTrue((Boolean) merged.get("isLeaf") == false);
        assertEquals(0, (Integer) merged.get("leftChild"));
        assertEquals(1, (Integer) merged.get("rightChild"));
    }

    @Test
    void getNodesReturnsEmptyListWhenNothingSet() {
        Tree tree = new Tree();
        assertEquals(0, tree.getNodes().size());
    }

    @Test
    void bindModelThenSetUpdatesBoundSignal() {
        Tree tree = new Tree();
        ValueSignal<TreeModel> signal = new ValueSignal<>(new TreeModelBuilder(
                Map.of(), Map.of()).build(leaf("C1", 1.0)));
        tree.bindModel(signal);

        TreeModel updated = new TreeModelBuilder(Map.of("C1", "A"),
                Map.of("C1", 5)).build(leaf("C1", 2.0));
        tree.setData(updated);

        assertEquals(updated, signal.peek());
    }

    // --- Static helpers --------------------------------------------------

    @Test
    void tooltipTextIncludesNameSizeAndFrequency() {
        TreeNodeData merged = new TreeNodeData(2, "Merged", 3.0, 12, "A", 1,
                false, 0, 1);

        List<String> expected = List.of("Name: A", "Size: 12", "Frequency: 1");
        assertLinesMatch(expected,
                List.of(Tree.tooltipText(merged).split("\n")));
    }

    @Test
    void tooltipTextOmitsFrequencyWhenZero() {
        TreeNodeData merged = new TreeNodeData(0, "Root", 9.0, 4, "", 0, false,
                0, 1);

        String text = Tree.tooltipText(merged);
        assertTrue(text.contains("Size: 4"));
        assertFalse(text.contains("Name:"));
        assertFalse(text.contains("Frequency:"));
    }

    @Test
    void frequencyOfCountsEmptyAndNullAsZero() {
        assertEquals(0, Tree.frequencyOf(null));
        assertEquals(0, Tree.frequencyOf(""));
        assertEquals(1, Tree.frequencyOf("strainA"));
    }

    @Test
    void formatScUsesRootLocaleWithThreeDecimals() {
        assertEquals("1.235", Tree.formatSc(1.23456));
        assertEquals("0.000", Tree.formatSc(0.0));
    }

    @Test
    void sanitizeStripsMarkupAndDecodesEntities() {
        assertEquals("hi&x", Tree.sanitize("<b>hi</b>&amp;x"));
        assertEquals("", Tree.sanitize(null));
        assertEquals("", Tree.sanitize("   "));
    }
}
