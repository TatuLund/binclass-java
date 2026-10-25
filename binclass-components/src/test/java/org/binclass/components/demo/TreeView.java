/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.demo;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.binclass.components.tree.Tree;
import org.binclass.components.tree.TreeModelBuilder;
import org.binclass.components.tree.TreeNode;

/**
 * Minimal Vaadin view that hosts the {@link Tree} component with representative
 * mock data so Playwright tests can drive it. The data does not need to be
 * mathematically correct, only visually representative: a single-cluster tree,
 * a two-cluster merge and a deeper ladder, plus leaves with long names that get
 * truncated by the client-side renderer.
 */
@Route("tree")
public class TreeView extends VerticalLayout {

    public TreeView() {
        setSpacing(true);
        setPadding(true);

        add(createSingleSection());
        add(createTwoClusterSection());
        add(createLadderSection());
        add(createLargeSection());
    }

    private VerticalLayout createSingleSection() {
        VerticalLayout section = new VerticalLayout();
        section.add("Tree — single cluster (one leaf, no branches)");

        Tree tree = new Tree();
        tree.setId("tree-single");
        tree.setData(new TreeModelBuilder(Map.of("C1", "A"), Map.of("C1", 5))
                .build(leaf("C1", 2.0)));
        section.add(tree);

        return section;
    }

    private VerticalLayout createTwoClusterSection() {
        VerticalLayout section = new VerticalLayout();
        section.add("Tree — two-cluster merge (two leaves, one knob)");

        Tree tree = new Tree();
        tree.setId("tree-two");
        TreeNode root = merge("Merged", 3.0, leaf("C1", 1.0), leaf("C2", 2.0));
        tree.setData(new TreeModelBuilder(Map.of("C1", "A", "C2", "B"),
                Map.of("C1", 5, "C2", 7)).build(root));
        section.add(tree);

        return section;
    }

    private VerticalLayout createLadderSection() {
        VerticalLayout section = new VerticalLayout();
        section.add("Tree — deeper ladder (three leaves, three knobs)");

        Tree tree = new Tree();
        tree.setId("tree-ladder");
        TreeNode root = merge("Merged_123", 7.0,
                merge("Merged_12", 3.0, leaf("C1", 1.0), leaf("C2", 2.0)),
                leaf("C3", 4.0));
        tree.setData(new TreeModelBuilder(Map.of("C1", "A", "C2", "B", "C3",
                "C"), Map.of("C1", 5, "C2", 7, "C3", 9)).build(root));
        section.add(tree);

        return section;
    }

    /** Builds a bare leaf node with the given name and sum-of-costs. */
    private static TreeNode leaf(String name, double sc) {
        return new TreeNode() {
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
                return true;
            }

            @Override
            public TreeNode getLeft() {
                return null;
            }

            @Override
            public TreeNode getRight() {
                return null;
            }
        };
    }

    /** Builds an internal merge node from two children. */
    private static TreeNode merge(String name, double sc, TreeNode left,
            TreeNode right) {
        return new TreeNode() {
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
                return false;
            }

            @Override
            public TreeNode getLeft() {
                return left;
            }

            @Override
            public TreeNode getRight() {
                return right;
            }
        };
    }

    /** Number of leaves in the large demo tree. */
    private static final int LEAF_COUNT = 50;
    /**
     * Leaves grouped into contiguous blocks that share a cluster name so an
     * internal merge can report a high frequency for its assumed name.
     */
    private static final int NAME_BLOCK = 6;

    /** Realistic strain/cluster names, roughly ten characters long. */
    private static final String[] CLUSTER_NAMES = {
            "Aeromonads", "Pseudomona", "Klebsiella", "Salmonella",
            "Escherich", "Staphyloco", "Streptococ", "Neisseria"
    };

    private VerticalLayout createLargeSection() {
        VerticalLayout section = new VerticalLayout();
        section.add("Tree — large cluster (fifty leaves)");

        Tree tree = new Tree();
        tree.setId("tree-large");
        TreeNode root = buildBalancedTree(leafNodes());
        tree.setData(new TreeModelBuilder(leafLabels(), leafSizes(),
                leafFrequencies()).build(root));
        section.add(tree);

        return section;
    }

    /** Builds fifty bare leaves keyed {@code C0}..{@code C49}. */
    private static List<TreeNode> leafNodes() {
        List<TreeNode> leaves = new ArrayList<>();
        for (int i = 0; i < LEAF_COUNT; i++) {
            leaves.add(leaf("C" + String.format("%02d", i), 1.0));
        }
        return leaves;
    }

    /** Maps each leaf key to a realistic cluster name, cycling in blocks. */
    private static Map<String, String> leafLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (int i = 0; i < LEAF_COUNT; i++) {
            String key = "C" + String.format("%02d", i);
            int nameIndex = (i / NAME_BLOCK) % CLUSTER_NAMES.length;
            labels.put(key, CLUSTER_NAMES[nameIndex]);
        }
        return labels;
    }

    /** Maps each leaf key to a cluster size between ten and twenty. */
    private static Map<String, Integer> leafSizes() {
        Map<String, Integer> sizes = new LinkedHashMap<>();
        for (int i = 0; i < LEAF_COUNT; i++) {
            String key = "C" + String.format("%02d", i);
            sizes.put(key, (i * 7 % 11) + 10);
        }
        return sizes;
    }

    /**
     * Maps each leaf key to a frequency between five and twenty, capped at its
     * size so an internal node never reports more vectors than it contains.
     */
    private static Map<String, Integer> leafFrequencies() {
        Map<String, Integer> frequencies = new LinkedHashMap<>();
        for (int i = 0; i < LEAF_COUNT; i++) {
            String key = "C" + String.format("%02d", i);
            int frequency = (i * 3 % 16) + 5;
            frequencies.put(key, Math.min(frequency, sizesFor(key)));
        }
        return frequencies;
    }

    /** Returns the deterministic cluster size for a leaf key. */
    private static int sizesFor(String key) {
        int i = Integer.parseInt(key.substring(1));
        return (i * 7 % 11) + 10;
    }

    /**
     * Combines leaves into a balanced binary tree, naming merges sequentially.
     * The merge cost grows with depth so inner nodes carry larger sum-of-costs
     * than the leaves they descend from.
     */
    private static TreeNode buildBalancedTree(List<TreeNode> leaves) {
        int[] counter = new int[] { 0 };
        return buildBalanced(leaves, counter);
    }

    private static TreeNode buildBalanced(List<TreeNode> leaves,
            int[] counter) {
        if (leaves.size() == 1) {
            return leaves.get(0);
        }
        int mid = leaves.size() / 2;
        TreeNode left = buildBalanced(leaves.subList(0, mid), counter);
        TreeNode right = buildBalanced(leaves.subList(mid, leaves.size()),
                counter);
        double sc = left.getSC() + right.getSC() + 10.0;
        return merge("Merge_" + counter[0]++, sc, left, right);
    }
}
