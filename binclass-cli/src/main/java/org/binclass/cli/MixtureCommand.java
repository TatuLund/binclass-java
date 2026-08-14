package org.binclass.cli;

import java.util.Map;

import org.binclass.algorithms.classify.MixtureClassifier;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Centroid;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.VectorSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mixture classifier (EM algorithm) command.
 */
public class MixtureCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(MixtureCommand.class);

    @Override
    public String getName() {
        return "mixture";
    }

    @Override
    public String getDescription() {
        return "Apply mixture classifier using EM algorithm";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        boolean verbose = !opts.containsKey("-q");

        double epsilon = 0.001;
        if (opts.containsKey("-E")) {
            try {
                epsilon = Double.parseDouble(opts.get("-E"));
                if (epsilon >= 0.5)
                    throw new IllegalArgumentException("Epsilon must be < 0.5");
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid epsilon value: " + opts.get("-E"));
            }
        }

        int mixtureClasses = 0;
        if (opts.containsKey("-k")) {
            try {
                mixtureClasses = Integer.parseInt(opts.get("-k")) + 1;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid mixture_classes: " + opts.get("-k"));
            }
        }

        int sampleMixture = 0;
        if (opts.containsKey("-s")) {
            try {
                sampleMixture = Integer.parseInt(opts.get("-s")) + 1;
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException(
                        "Invalid sample_mixture: " + opts.get("-s"));
            }
        }

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Mixture command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Mixture classes: {}", mixtureClasses);
        log.info("  Sample mixture: {}", sampleMixture);
        log.info("  Epsilon: {}", epsilon);

        // Load vectors from data files
        VectorSet vectorSet = DataLoader.loadVectors(filebase);

        // Determine number of mixture components (default to 2 if not
        // specified)
        int numComponents = Math.max(1,
                mixtureClasses > 0 ? mixtureClasses : 2);

        log.info("Running EM algorithm with {} vectors and {} components",
                vectorSet.size(), numComponents);

        // Create initial centroids for the mixture model
        InfiniteCentroids centroids = new InfiniteCentroids(numComponents, 16);

        int idx = 0;
        for (BinaryVector bv : vectorSet) {
            if (idx >= numComponents)
                break;
            Centroid centroid = centroids.get(idx);
            centroid.setEl(bv.getEl());
            idx++;
        }

        // Run EM algorithm to fit mixture model
        InfiniteCentroids result = MixtureClassifier.applyMixtureClassifier(
                vectorSet, centroids, sampleMixture > 0 ? sampleMixture : 2);

        log.info("EM algorithm complete with {} components",
                result.size());

        return 0;
    }
}
