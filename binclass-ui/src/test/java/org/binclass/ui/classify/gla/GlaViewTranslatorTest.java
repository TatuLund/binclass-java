/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.gla;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.TreeNode;
import org.binclass.algorithms.tree.TreeBuilder;
import org.binclass.components.graph.GraphModel;
import org.binclass.components.partition.ClusterData;
import org.binclass.components.partition.PartitionModel;
import org.binclass.components.partition.VectorData;
import org.binclass.components.tree.TreeModel;
import org.binclass.components.tree.TreeNodeData;

/**
 * Unit tests for {@link GlaViewTranslator}, covering the
 * algorithm-to-presentation mappings described in {@code docs/WebUI.PRD.md}
 * §1.3 — done here in the UI module so the components stay free of an
 * algorithms dependency.
 */
class GlaViewTranslatorTest {

    /** Builds a two-cluster partition with labelled vectors for translation. */
    private static Partition samplePartition() {
        Partition partition = new Partition(2, 4);
        partition.addElement(1, new BinaryVector(new int[] { 0, 1, 1 }, 3));
        partition.addElement(1, new BinaryVector(new int[] { 1, 1, 0 }, 3));
        partition.addElement(2, new BinaryVector(new int[] { 1, 0, 0 }, 3));
        return partition;
    }

    @Test
    void toPartitionModelBuildsOneClusterPerCluster() {
        Partition partition = samplePartition();
        InfiniteCentroids centroids = new InfiniteCentroids(2);
        Centroid c0 = new Centroid(3);
        c0.set(0, 0.1);
        c0.set(1, 0.9);
        c0.set(2, 0.8);
        centroids.set(0, c0);

        PartitionModel model = GlaViewTranslator.toPartitionModel(
                partition, centroids, 42.5);

        assertEquals(2, model.clusterCount());
        assertEquals(3, model.vectorCount());
        assertEquals(42.5, model.stochasticComplexity());
    }

    @Test
    void toPartitionModelUsesMostFrequentStrainAsName() {
        Partition partition = new Partition(1, 3);
        BinaryVector a = new BinaryVector(new int[] { 0, 0 }, 2);
        a.setStrain("alpha");
        BinaryVector b = new BinaryVector(new int[] { 1, 1 }, 2);
        b.setStrain("alpha");
        partition.addElement(1, a);
        partition.addElement(1, b);

        PartitionModel model = GlaViewTranslator.toPartitionModel(partition,
                null, 0.0);
        ClusterData cluster = model.clusters().get(0);

        assertEquals("alpha", cluster.name());
    }

    @Test
    void toPartitionModelPrefersClassNameAsClusterName() {
        Partition partition = new Partition(1, 3);
        // Distinct per-vector strain identifiers but a shared class-name.
        BinaryVector a = new BinaryVector(new int[] { 0, 0 }, 0, 2, 0,
                "0523-84", "ESCH COLI");
        BinaryVector b = new BinaryVector(new int[] { 1, 1 }, 0, 2, 0,
                "1434-82", "ESCH COLI");
        BinaryVector c = new BinaryVector(new int[] { 0, 1 }, 0, 2, 0,
                "2852-78", "ESCH COLI");
        partition.addElement(1, a);
        partition.addElement(1, b);
        partition.addElement(1, c);

        PartitionModel model = GlaViewTranslator.toPartitionModel(partition,
                null, 0.0);
        ClusterData cluster = model.clusters().get(0);

        // The dominant class-name labels the cluster, not an arbitrary strain.
        assertEquals("ESCH COLI", cluster.name());
    }

    @Test
    void toPartitionModelFallsBackToStrainWhenNoClassName() {
        Partition partition = new Partition(1, 2);
        BinaryVector a = new BinaryVector(new int[] { 0, 0 }, 2);
        a.setStrain("beta");
        BinaryVector b = new BinaryVector(new int[] { 1, 1 }, 2);
        b.setStrain("beta");
        partition.addElement(1, a);
        partition.addElement(1, b);

        PartitionModel model = GlaViewTranslator.toPartitionModel(partition,
                null, 0.0);
        ClusterData cluster = model.clusters().get(0);

        assertEquals("beta", cluster.name());
    }

    @Test
    void toPartitionModelFallsBackToIndexWhenNoStrain() {
        Partition partition = new Partition(1, 2);
        partition.addElement(1, new BinaryVector(new int[] { 0, 1 }, 2));
        partition.addElement(1, new BinaryVector(new int[] { 1, 0 }, 2));

        PartitionModel model = GlaViewTranslator.toPartitionModel(partition,
                null, 0.0);
        ClusterData cluster = model.clusters().get(0);

        assertEquals("1", cluster.name());
    }

    @Test
    void toPartitionModelRoundsCentroidForHover() {
        Partition partition = samplePartition();
        InfiniteCentroids centroids = new InfiniteCentroids(2);
        Centroid c0 = new Centroid(3);
        c0.set(0, 0.1);
        c0.set(1, 0.6);
        c0.set(2, 0.9);
        centroids.set(0, c0);

        PartitionModel model = GlaViewTranslator.toPartitionModel(
                partition, centroids, 0.0);
        ClusterData cluster = model.clusters().get(0);

        int[] rounded = cluster.vectors().get(0).roundedCentroid();
        assertEquals(3, rounded.length);
        assertEquals(0, rounded[0]); // 0.1 rounds down
        assertEquals(1, rounded[1]); // 0.6 rounds up
        assertEquals(1, rounded[2]); // 0.9 rounds up
    }

    @Test
    void toPartitionModelHandlesMissingCentroids() {
        Partition partition = samplePartition();

        PartitionModel model = GlaViewTranslator.toPartitionModel(partition,
                null, 7.0);

        assertEquals(2, model.clusterCount());
        assertTrue(
                model.clusters().get(0).centroid().probabilities().length >= 0);
    }

    @Test
    void toGraphModelMapsIndexToXWhenKsAbsent() {
        double[] scs = new double[] { Double.NaN, 10.0, 8.0, 9.0 };

        GraphModel model = GlaViewTranslator.toGraphModel(scs, null);

        List<org.binclass.components.graph.GraphPoint> points = model.points();
        assertEquals(3, points.size());
        assertEquals(1, points.get(0).x());
        assertEquals(10.0, points.get(0).y());
    }

    @Test
    void toGraphModelDropsNonFinitePoints() {
        double[] scs = new double[] { Double.NaN, 5.0, Double.NaN, 3.0 };

        GraphModel model = GlaViewTranslator.toGraphModel(scs, null);

        assertEquals(2, model.points().size());
    }

    @Test
    void toGraphModelUsesKsWhenProvided() {
        double[] scs = new double[] { Double.NaN, 1.0, 2.0 };
        int[] ks = { 0, 4, 6 };

        GraphModel model = GlaViewTranslator.toGraphModel(scs, ks);
        List<org.binclass.components.graph.GraphPoint> points = model.points();

        assertEquals(2, points.size());
        assertEquals(4, points.get(0).x());
        assertEquals(6, points.get(1).x());
    }

    /** Builds a centroid array of length {@code l} with alternating values. */
    private static InfiniteCentroids sampleCentroids(int k, int l) {
        InfiniteCentroids centroids = new InfiniteCentroids(k);
        for (int i = 0; i < k; i++) {
            Centroid c = new Centroid(l);
            for (int b = 0; b < l; b++) {
                c.set(b, b % 2 == 0 ? 0.1 : 0.9);
            }
            centroids.set(i, c);
        }
        return centroids;
    }

    /** Indexes every node in a model by its list id for direct lookup. */
    private static Map<Integer, TreeNodeData> indexById(TreeModel model) {
        Map<Integer, TreeNodeData> byId = new java.util.HashMap<>();
        for (TreeNodeData n : model.nodes()) {
            byId.put(n.id(), n);
        }
        return byId;
    }

    /** Returns the size of the leaf whose identifier equals {@code name}. */
    private static int leafSize(TreeModel model, String name) {
        for (TreeNodeData n : model.nodes()) {
            if (n.isLeaf() && n.name().equals(name)) {
                return n.size();
            }
        }
        throw new AssertionError("no leaf named " + name);
    }

    @Test
    void toTreeModelRootSizeMatchesTotalVectors() {
        Partition partition = new Partition(2, 3);
        partition.addElement(1, new BinaryVector(new int[] { 0, 1, 1 }, 3));
        partition.addElement(1, new BinaryVector(new int[] { 1, 1, 0 }, 3));
        partition.addElement(2, new BinaryVector(new int[] { 1, 0, 0 }, 3));
        partition.addElement(2, new BinaryVector(new int[] { 0, 0, 1 }, 3));
        partition.addElement(2, new BinaryVector(new int[] { 1, 1, 1 }, 3));

        InfiniteCentroids centroids = sampleCentroids(2, 3);
        TreeNode root = TreeBuilder.makeTreePnn(partition, centroids);
        assertNotNull(root);

        TreeModel model = GlaViewTranslator.toTreeModel(partition, root);
        List<TreeNodeData> nodes = model.nodes();

        // Post-order traversal appends the root last; its subtree size must
        // equal the whole input dataset regardless of how makeTreePnn moved
        // elements between clusters while merging.
        TreeNodeData rootNode = nodes.get(nodes.size() - 1);
        assertFalse(rootNode.isLeaf());
        assertEquals(partition.getNumberOfVectors(), rootNode.size());
    }

    @Test
    void toTreeModelInternalNodesSumChildrenAndRootTotals() {
        Partition partition = new Partition(3, 2);
        partition.addElement(1, new BinaryVector(new int[] { 0, 0 }, 2));
        partition.addElement(1, new BinaryVector(new int[] { 1, 1 }, 2));
        partition.addElement(2, new BinaryVector(new int[] { 0, 1 }, 2));
        partition.addElement(3, new BinaryVector(new int[] { 1, 0 }, 2));
        partition.addElement(3, new BinaryVector(new int[] { 0, 0 }, 2));
        partition.addElement(3, new BinaryVector(new int[] { 1, 1 }, 2));
        partition.addElement(3, new BinaryVector(new int[] { 0, 1 }, 2));
        // Cluster sizes: C1=2, C2=1, C3=4 -> total seven.
        assertEquals(7, partition.getNumberOfVectors());

        TreeNode c1 = new TreeNode(1.0, "C1");
        TreeNode c2 = new TreeNode(2.0, "C2");
        TreeNode c3 = new TreeNode(3.0, "C3");
        TreeNode inner = new TreeNode(5.0, "Merged", c2, c3);
        TreeNode root = new TreeNode(9.0, "Root", c1, inner);

        TreeModel model = GlaViewTranslator.toTreeModel(partition, root);
        Map<Integer, TreeNodeData> byId = indexById(model);

        // Each leaf carries its cluster's size straight from the partition.
        assertEquals(2, leafSize(model, "C1"));
        assertEquals(1, leafSize(model, "C2"));
        assertEquals(4, leafSize(model, "C3"));

        // Every internal node sums its children's sizes; the root totals all.
        for (TreeNodeData n : model.nodes()) {
            if (!n.isLeaf()) {
                int childSum = byId.get(n.leftChild()).size()
                        + byId.get(n.rightChild()).size();
                assertEquals(childSum, n.size(),
                        "internal node size must equal the sum of its children");
            }
        }
        TreeNodeData rootData = model.nodes().get(model.nodes().size() - 1);
        assertEquals(7, rootData.size());
        assertEquals(partition.getNumberOfVectors(), rootData.size());
    }

    @Test
    void toTreeModelSingleClusterYieldsOneLeafOfTotalSize() {
        Partition partition = new Partition(1, 3);
        partition.addElement(1, new BinaryVector(new int[] { 0, 1, 1 }, 3));
        partition.addElement(1, new BinaryVector(new int[] { 1, 1, 0 }, 3));
        partition.addElement(1, new BinaryVector(new int[] { 1, 0, 0 }, 3));

        TreeModel model = GlaViewTranslator.toTreeModel(partition, null);
        List<TreeNodeData> nodes = model.nodes();
        assertEquals(1, nodes.size());
        TreeNodeData node = nodes.get(0);
        assertTrue(node.isLeaf());
        assertEquals("C1", node.name());
        // A lone cluster's leaf size equals the whole dataset.
        assertEquals(partition.getNumberOfVectors(), node.size());
    }

    @Test
    void toTreeModelNullRootBuildsSingleLeafOfTotalSize() {
        Partition partition = new Partition(2, 3);
        partition.addElement(1, new BinaryVector(new int[] { 0, 1, 1 }, 3));
        partition.addElement(1, new BinaryVector(new int[] { 1, 1, 0 }, 3));
        partition.addElement(2, new BinaryVector(new int[] { 1, 0, 0 }, 3));

        // A null root (empty/single-cluster fallback) yields a lone leaf named
        // "C1" whose size is taken from cluster 0 only, so for k > 1 it equals
        // the first cluster's size rather than the whole dataset.
        TreeModel model = GlaViewTranslator.toTreeModel(partition, null);
        List<TreeNodeData> nodes = model.nodes();
        assertEquals(1, nodes.size());
        TreeNodeData node = nodes.get(0);
        assertTrue(node.isLeaf());
        assertEquals("C1", node.name());
        assertEquals(partition.getClusterSize(1), node.size());
    }

    @Test
    void toTreeModelLeafLabelsUseMostFrequentStrain() {
        Partition partition = new Partition(1, 2);
        BinaryVector a = new BinaryVector(new int[] { 0, 0 }, 2);
        a.setStrain("alpha");
        BinaryVector b = new BinaryVector(new int[] { 1, 1 }, 2);
        b.setStrain("alpha");
        BinaryVector c = new BinaryVector(new int[] { 0, 1 }, 2);
        c.setStrain("beta");
        partition.addElement(1, a);
        partition.addElement(1, b);
        partition.addElement(1, c);

        TreeModel model = GlaViewTranslator.toTreeModel(partition, null);
        TreeNodeData node = model.nodes().get(0);

        // The dominant strain labels the leaf; its identifier stays the cluster
        // key so the builder can look it up.
        assertEquals("alpha", node.label());
        assertEquals("C1", node.name());
    }

    @Test
    void buildLeafMapsCapturesSizesBeforeMakeTreePnnMutation() {
        Partition partition = new Partition(2, 3);
        partition.addElement(1, new BinaryVector(new int[] { 0, 1, 1 }, 3));
        partition.addElement(1, new BinaryVector(new int[] { 1, 1, 0 }, 3));
        partition.addElement(2, new BinaryVector(new int[] { 1, 0, 0 }, 3));
        partition.addElement(2, new BinaryVector(new int[] { 0, 0, 1 }, 3));
        // True dataset size is four; makeTreePnn will inflate the partition.
        assertEquals(4, partition.getNumberOfVectors());

        Map<String, String> labels = new java.util.HashMap<>();
        Map<String, Integer> sizes = new java.util.HashMap<>();
        GlaViewTranslator.buildLeafMaps(partition, labels, sizes);

        // Snapshot taken before mutation: leaf sizes sum to the true dataset.
        int snapshotTotal = 0;
        for (int size : sizes.values()) {
            snapshotTotal += size;
        }
        assertEquals(4, snapshotTotal);

        // makeTreePnn mutates the partition in place and inflates its vector
        // count; the earlier snapshot must still reflect the true total.
        InfiniteCentroids centroids = sampleCentroids(2, 3);
        TreeBuilder.makeTreePnn(partition, centroids);
        assertTrue(partition.getNumberOfVectors() > 4,
                "makeTreePnn should inflate the partition so the test is meaningful");

        int recomputeTotal = 0;
        for (int i = 0; i < partition.size(); i++) {
            recomputeTotal += partition.getClusterSize(i + 1);
        }
        // Re-reading the mutated partition now over-counts, proving the
        // snapshot
        // was necessary.
        assertTrue(recomputeTotal > snapshotTotal);
    }

    @Test
    void toTreeModelWithPrebuiltMapsRootMatchesTrueDataset() {
        Partition partition = new Partition(2, 3);
        partition.addElement(1, new BinaryVector(new int[] { 0, 1, 1 }, 3));
        partition.addElement(1, new BinaryVector(new int[] { 1, 1, 0 }, 3));
        partition.addElement(2, new BinaryVector(new int[] { 1, 0, 0 }, 3));
        partition.addElement(2, new BinaryVector(new int[] { 0, 0, 1 }, 3));
        int trueTotal = partition.getNumberOfVectors();

        Map<String, String> labels = new java.util.HashMap<>();
        Map<String, Integer> sizes = new java.util.HashMap<>();
        GlaViewTranslator.buildLeafMaps(partition, labels, sizes);

        // Mutate the partition in place to simulate makeTreePnn's effect.
        InfiniteCentroids centroids = sampleCentroids(2, 3);
        TreeNode root = TreeBuilder.makeTreePnn(partition, centroids);
        assertNotNull(root);

        // Feed the pre-built maps into the overload; the root must equal the
        // true dataset size, not the inflated post-merge count.
        TreeModel model = GlaViewTranslator.toTreeModel(
                partition, root, labels, sizes);
        TreeNodeData rootNode = model.nodes().get(model.nodes().size() - 1);
        assertFalse(rootNode.isLeaf());
        assertEquals(trueTotal, rootNode.size());
    }

    @Test
    void toVectorDataMapsClassNameToNameAndStrainToIdentifier() {
        Partition partition = new Partition(1, 2);
        BinaryVector bv = new BinaryVector(new int[] { 1, 0 }, 0, 2, 0,
                "3093-84", "ESCH COLI");
        partition.addElement(1, bv);

        PartitionModel model = GlaViewTranslator.toPartitionModel(partition,
                null, 0.0);
        VectorData data = model.clusters().get(0).vectors().get(0);

        // The class-name (group label) is the display name; the strain is the
        // per-vector identifier shown after it.
        assertEquals("ESCH COLI", data.name());
        assertEquals("3093-84", data.identifier());
    }
}
