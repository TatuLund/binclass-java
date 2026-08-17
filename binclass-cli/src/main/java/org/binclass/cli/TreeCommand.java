package org.binclass.cli;

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
        int numClusters = Math.min(3, vectorSet.size());

        // Create centroids and partition using classifier
        InfiniteCentroids centroids = new InfiniteCentroids(numClusters, 16);
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

        return 0;
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
