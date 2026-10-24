/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.demo;

import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
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
}
