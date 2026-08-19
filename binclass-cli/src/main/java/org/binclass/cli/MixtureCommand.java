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

        setupVerboseMode(opts);

        double epsilon = parseOptionDouble(opts, "-E",
                "Invalid epsilon value: " + opts.get("-E"), 0.001);

        int mixtureClasses = parseOptionInt(opts, "-k",
                "Invalid mixture_classes: " + opts.get("-k")) + 1;

        // If user didn't specify -k or specified 0, default to 2 components
        if (mixtureClasses <= 1) {
            mixtureClasses = 2;
        }

        int sampleMixture = parseOptionInt(opts, "-s",
                "Invalid sample_mixture: " + opts.get("-s"));

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

        // Get actual vector length from first vector in set
        int vectorLength = vectorSet.size() > 0
                ? vectorSet.iterator().next().getLength()
                : 16;

        // Create initial centroids for the mixture model
        InfiniteCentroids centroids = new InfiniteCentroids(numComponents,
                vectorLength);

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
