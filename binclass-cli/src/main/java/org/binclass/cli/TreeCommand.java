package org.binclass.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.binclass.algorithms.classify.Classifier;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.TreeNode;
import org.binclass.algorithms.tree.TreeBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build hierarchical tree command.
 */
public class TreeCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(TreeCommand.class);

    @Override
    public String getName() {
        return "tree";
    }

    @Override
    public String getDescription() {
        return "Build hierarchical dendrogram from partition data";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        int useHellinger = parseOptionInt(opts, "-H",
                "Invalid use_hellinger value", 0);
        if (opts.containsKey("-H") && (useHellinger < 1 || useHellinger > 4)) {
            throw new IllegalArgumentException(
                    "Use hellinger must be 1, 2, or 4");
        }

        setupVerboseMode(opts);
        boolean jeffreysPrior = opts.containsKey("-J");

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Tree command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Use hellinger: {}", useHellinger);
        log.info("  Jeffreys prior: {}", jeffreysPrior);

        // Load vectors from data files
        var vectorSet = DataLoader.loadVectors(filebase);

        // Get actual vector length from first vector in set
        int vectorLength = vectorSet.size() > 0
                ? vectorSet.iterator().next().getLength()
                : 16;

        int numClusters = Math.min(3, vectorSet.size());

        // Create centroids and partition using classifier
        InfiniteCentroids centroids = new InfiniteCentroids(numClusters,
                vectorLength);
        Partition partition = new Partition(numClusters);

        Classifier.identifyVectors(vectorSet, partition, centroids, 0.001);

        log.info("Built tree from {} vectors in {} clusters",
                vectorSet.size(), numClusters);

        // Build dendrogram using appropriate algorithm variant
        TreeNode root;
        if (useHellinger > 0) {
            root = TreeBuilder.makeTreePnn(partition, centroids);
            log.info("Built Hellinger distance tree");
        } else {
            root = TreeBuilder.makeTreePnn2(partition, centroids);
            log.info("Built class nearness tree");
        }

        if (root != null) {
            log.info("Tree built successfully with {} levels",
                    countLevels(root));
        } else {
            log.warn("No tree generated - partition may be empty");
        }

        // Persist the resulting dendrogram (default <filebase>.tree, override
        // with -o). Mirrors C traverse_tree(): in-order traversal with
        // depth-based indentation.
        String outputFile = opts.getOrDefault("-o", null);
        if (outputFile == null || outputFile.isEmpty()) {
            outputFile = filebase + ".tree";
        }
        int code = writeTree(root, outputFile);
        if (code != 0) {
            return code;
        }

        return 0;
    }

    /**
     * Writes a dendrogram to the given path, creating parent directories as
     * needed. The tree is serialized with an in-order traversal matching the C
     * traverse_tree() output: leaves print their name and internal nodes print
     * their merge cost (SC) alongside a label.
     *
     * @param root
     *            the root node of the dendrogram (may be null)
     * @param outputFile
     *            destination file path
     * @return 0 on success, 1 if writing failed
     */
    private int writeTree(TreeNode root, String outputFile) {
        try {
            Path path = Path.of(outputFile);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, serializeTree(root));
            log.info("Tree written to {}", outputFile);
            return 0;
        } catch (IOException e) {
            log.warn("Failed to write tree to {}: {}", outputFile,
                    e.getMessage());
            return 1;
        }
    }

    /**
     * Serializes a dendrogram using an in-order traversal matching the C
     * traverse_tree() format. Leaves print their name prefixed with a marker;
     * internal nodes print their merge cost (SC) and label. Each line is
     * indented by its depth to convey hierarchy.
     *
     * @param root
     *            the root node of the dendrogram (may be null)
     * @return the serialized tree text
     */
    private String serializeTree(TreeNode root) {
        StringBuilder sb = new StringBuilder();
        serializeNode(root, 1, sb);
        return sb.toString();
    }

    /**
     * Recursively appends a node and its subtree to the builder using an
     * in-order traversal (left child, node, right child).
     *
     * @param node
     *            the current tree node
     * @param depth
     *            the 1-based depth of the node within the tree
     * @param sb
     *            the builder to append to
     */
    private void serializeNode(TreeNode node, int depth, StringBuilder sb) {
        if (node == null) {
            return;
        }

        String indent = " ".repeat(Math.max(0, depth - 1));

        // Traverse left subtree first
        if (node.getLeft() != null) {
            serializeNode(node.getLeft(), depth + 1, sb);
        }

        // Emit the current node. Leaves are marked with '*' and print their
        // name; internal nodes print their merge cost (SC) and label.
        if (node.isLeaf()) {
            String prefix = (depth == 1) ? "*[" : " [";
            sb.append(indent).append(prefix)
                    .append(String.format("%.4f, %s]%n", node.getSC(),
                            node.getName()));
        } else {
            sb.append(indent).append(node.getName())
                    .append(String.format(" [%.4f]%n", node.getSC()));
        }

        // Traverse right subtree last
        if (node.getRight() != null) {
            serializeNode(node.getRight(), depth + 1, sb);
        }
    }

    /**
     * Count the number of levels in a binary tree.
     */
    private int countLevels(TreeNode node) {
        if (node == null
                || (node.getLeft() == null && node.getRight() == null)) {
            return 1;
        }
        return 1 + Math.max(countLevels(node.getLeft()),
                countLevels(node.getRight()));
    }
}
