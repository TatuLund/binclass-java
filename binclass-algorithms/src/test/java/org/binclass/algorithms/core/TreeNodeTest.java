/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link TreeNode}.
 */
class TreeNodeTest {

    @Test
    void testCreation() {
        TreeNode node = new TreeNode(1.5, "cluster_A");

        assertEquals(1.5, node.getSC(), 1e-10);
        assertEquals("cluster_A", node.getName());
    }

    @Test
    void testLeafNode() {
        TreeNode leaf = new TreeNode(0.5, "leaf_1");

        assertTrue(leaf.isLeaf());
        assertNull(leaf.getLeft());
        assertNull(leaf.getRight());
    }

    @Test
    void testInternalNode() {
        TreeNode left = new TreeNode(0.3, "left_child");
        TreeNode right = new TreeNode(0.7, "right_child");

        TreeNode internal = new TreeNode(1.2, "internal", left, right);

        assertFalse(internal.isLeaf());
        assertNotNull(internal.getLeft());
        assertNotNull(internal.getRight());
        assertEquals(left, internal.getLeft());
        assertEquals(right, internal.getRight());
    }

    @Test
    void testNullName() {
        TreeNode node = new TreeNode(1.0, null);

        assertEquals("", node.getName()); // Null becomes empty string
    }

    @Test
    void testToString_Leaf() {
        TreeNode leaf = new TreeNode(0.5, "leaf");

        String str = leaf.toString();
        assertTrue(str.contains("TreeNode{"));
        assertTrue(str.contains("name='leaf'"));
    }

    @Test
    void testToString_Internal() {
        TreeNode left = new TreeNode(0.3, "left");
        TreeNode right = new TreeNode(0.7, "right");

        TreeNode internal = new TreeNode(1.2, "internal", left, right);

        String str = internal.toString();
        assertTrue(str.contains("TreeNode{"));
        assertTrue(str.contains("sc="));
    }

    @Test
    void testEquals() {
        TreeNode node1 = new TreeNode(1.0, "test");
        TreeNode node2 = new TreeNode(1.0, "test");

        assertEquals(node1, node2);
        assertEquals(node1.hashCode(), node2.hashCode());
    }

    @Test
    void testNotEquals() {
        TreeNode node1 = new TreeNode(1.0, "test");
        TreeNode node2 = new TreeNode(2.0, "test"); // Different SC

        assertNotEquals(node1, node2);
    }

    @Test
    void testTreeNavigation() {
        // Create a simple binary tree: root -> [left, right]
        TreeNode leaf1 = new TreeNode(0.3, "A");
        TreeNode leaf2 = new TreeNode(0.5, "B");
        TreeNode internal = new TreeNode(1.0, "AB", leaf1, leaf2);
        TreeNode root = new TreeNode(2.0, "Root", internal, null);

        // Navigate the tree
        assertEquals("A", root.getLeft().getLeft().getName());
        assertEquals("B", root.getLeft().getRight().getName());
        assertEquals("AB", root.getLeft().getName());
        assertEquals("Root", root.getName());
    }

    @Test
    void testSCValue() {
        TreeNode node = new TreeNode(3.14159, "test");

        assertEquals(3.14159, node.getSC(), 1e-10);
    }

    @Test
    void testComplexTreeStructure() {
        // Create a more complex tree structure
        TreeNode leaf1 = new TreeNode(0.1, "L1");
        TreeNode leaf2 = new TreeNode(0.2, "L2");
        TreeNode leaf3 = new TreeNode(0.3, "L3");

        TreeNode internal1 = new TreeNode(0.5, "AB", leaf1, leaf2);
        TreeNode internal2 = new TreeNode(0.8, "ABC", internal1, leaf3);

        assertEquals("L1", internal2.getLeft().getLeft().getName());
        assertEquals("L2", internal2.getLeft().getRight().getName());
        assertEquals("L3", internal2.getRight().getName());
    }

    @Test
    void testHashCodeConsistency() {
        TreeNode node = new TreeNode(1.0, "test");

        int hash1 = node.hashCode();
        int hash2 = node.hashCode(); // Should be consistent

        assertEquals(hash1, hash2);
    }

    @Test
    void testNullChildren() {
        TreeNode internal = new TreeNode(1.0, "internal", null, null);

        assertTrue(internal.isLeaf()); // Both children null means leaf
    }
}
