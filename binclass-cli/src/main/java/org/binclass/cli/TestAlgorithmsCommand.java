package org.binclass.cli;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test algorithm commands (test1 and test2).
 */
public class TestAlgorithmsCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(TestAlgorithmsCommand.class);

    private final String testName;

    public TestAlgorithmsCommand(String testName) {
        this.testName = testName;
    }

    @Override
    public String getName() {
        return testName;
    }

    @Override
    public String getDescription() {
        if ("test1".equals(testName)) {
            return "Test algorithm 1 (distortion minimizer)";
        } else {
            return "Test algorithm 2 (semi-cumulative)";
        }
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        if ("test1".equals(testName)) {
            return executeTest1(opts, args);
        } else {
            return executeTest2(opts, args);
        }
    }

    private int executeTest1(Map<String, String> opts,
            CliParser.CommandArgs args) throws NumberFormatException {
        int kstart = 0;
        if (opts.containsKey("-k")) {
            try {
                kstart = Integer.parseInt(opts.get("-k")) + 1;
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid kstart: " + opts.get("-k"));
            }
        }

        int t1RsCount = 0;
        if (opts.containsKey("-r")) {
            try {
                t1RsCount = Integer.parseInt(opts.get("-r")) + 1;
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid t1_rs_count: " + opts.get("-r"));
            }
        }

        int t1Trials = 0;
        if (opts.containsKey("-t")) {
            try {
                t1Trials = Integer.parseInt(opts.get("-t")) + 1;
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid t1_trials: " + opts.get("-t"));
            }
        }

        boolean extraIter = opts.containsKey("-e");

        if (kstart == 0) {
            throw new IllegalArgumentException("test1 requires -k option");
        }

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Test1 command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Kstart: {}", kstart);
        log.info("  RS count: {}", t1RsCount);
        log.info("  Trials: {}", t1Trials);
        log.info("  Extra iterations: {}", extraIter);

        return 0;
    }

    private int executeTest2(Map<String, String> opts,
            CliParser.CommandArgs args) throws NumberFormatException {
        int t2Threshold = 0;
        if (opts.containsKey("-t")) {
            try {
                t2Threshold = Integer.parseInt(opts.get("-t"));
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid t2_treshold: " + opts.get("-t"));
            }
        }

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Test2 command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Threshold: {}", t2Threshold);

        return 0;
    }
}
