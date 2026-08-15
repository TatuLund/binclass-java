package org.binclass.cli;

import java.util.Map;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.generate.DataGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generate synthetic data command.
 */
public class GenerateCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(GenerateCommand.class);

    @Override
    public String getName() {
        return "generate";
    }

    @Override
    public String getDescription() {
        return "Generate synthetic binary vectors (Bernoulli, Markov, random)";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        int vecsToGen = parseOptionInt(opts, "-v",
                "Invalid vecs_to_gen: " + opts.get("-v"));

        setupVerboseMode(opts);
        boolean uniqueVectors = opts.containsKey("-u");

        int dataGenType = parseOptionInt(opts, "-G",
                "Invalid data generator type: " + opts.get("-G"), 1);

        // Validate data generator type (must be 1, 2, or 3)
        if (dataGenType < 1 || dataGenType > 3) {
            throw new IllegalArgumentException(
                    "Unsupported data generator type: " + dataGenType);
        }

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Generate command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Vectors to generate: {}", vecsToGen);
        log.info("  Data generator type: {}", dataGenType);
        log.info("  Unique vectors: {}", uniqueVectors);

        // Load reference vectors from data files (for Markov/random generation)
        VectorSet vectorSet = DataLoader.loadVectors(filebase);

        log.info("Generating {} synthetic vectors using method #{}",
                vecsToGen, dataGenType);

        // Generate synthetic data based on generator type selection
        VectorSet generated;
        switch (dataGenType) {
        case 1:
            // RAND - random binary vectors from reference set
            log.info("Using random vector generation");
            generated = DataGenerator.vectorGen(vectorSet, vecsToGen);
            break;
        case 2:
            // MARKOV - Markov chain sequences
            log.info("Using Markov chain generation");
            generated = DataGenerator.markovGen(vectorSet, vecsToGen);
            break;
        case 3:
            // BERN - Bernoulli model from partition
            log.info("Using Bernoulli model generation");
            Partition partition = new Partition(1);
            for (BinaryVector bv : vectorSet) {
                partition.getElements(1).add(bv);
            }
            generated = DataGenerator.bernoulliGen(partition, vecsToGen);
            break;
        default:
            throw new IllegalArgumentException(
                    "Unsupported data generator type: " + dataGenType);
        }

        log.info("Generated {} vectors", generated.size());

        return 0;
    }
}
