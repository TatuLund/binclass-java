package org.binclass.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-rolled argument parser matching original C behavior. Supports both -flag
 * and --long-flag=value syntax forms.
 */
public class CliParser {

    /** Structured command arguments with name and options map. */
    public interface CommandArgs {
        String command();

        Map<String, String> options();
    }

    /** Default implementation of CommandArgs. */
    public static class CommandArgsImpl implements CommandArgs {
        private final String command;
        private final Map<String, String> options;

        public CommandArgsImpl(String command, Map<String, String> options) {
            this.command = command;
            this.options = options;
        }

        @Override
        public String command() {
            return command;
        }

        @Override
        public Map<String, String> options() {
            return options;
        }
    }

    private static final int MAX_ARGS = 1024;
    private static final String INVALID_EPSILON_VALUE = "Invalid epsilon value: ";

    /**
     * Parse raw args into structured CommandArgs. First non-option token is the
     * subcommand.
     */
    public CommandArgs parse(String[] args) {
        if (args == null || args.length < 2) {
            throw new IllegalArgumentException(
                    "Usage: binclass <command> [options] <filebase>");
        }

        String command = args[1];
        Map<String, String> options = new HashMap<>();

        int i = 2;
        while (i < args.length && i < MAX_ARGS) {
            int next = parseArg(args, i, options);
            if (next > i) {
                // A value-taking option consumed the following token; skip it.
                i = next;
            } else {
                i++;
            }
        }

        return new CommandArgsImpl(command, options);
    }

    /**
     * Parse a single argument and add to options map.
     *
     * @param args
     *            the raw arguments array
     * @param index
     *            current position in args (may be incremented)
     * @param options
     *            map to populate with parsed options
     */
    private int parseArg(String[] args, int index,
            Map<String, String> options) {
        String arg = args[index];
        if (arg.startsWith("--")) {
            return parseLongOption(arg, index, args, options);
        } else if (arg.startsWith("-") && arg.length() > 1) {
            return parseShortOption(arg, index, args, options);
        } else if (arg.equals("help") || arg.equals("--help")
                || arg.equals("-h")) {
            options.put("help", "true");
            return index;
        }
        // Positional argument - treat as filebase
        options.put("filebase", arg);
        return index;
    }

    /**
     * Parse a long option (--flag=value or --flag value).
     *
     * @param arg
     *            the argument starting with --
     * @param index
     *            current position in args (may be incremented)
     * @param args
     *            the raw arguments array
     * @param options
     *            map to populate with parsed options
     * @return the next index to parse after this option and its value
     */
    private int parseLongOption(String arg, int index, String[] args,
            Map<String, String> options) {
        int eqIdx = arg.indexOf('=');
        if (eqIdx > 0) {
            options.put(arg.substring(2, eqIdx), arg.substring(eqIdx + 1));
            return index;
        }
        String flag = arg.substring(2);
        if (index + 1 < args.length && !args[index + 1].startsWith("-")) {
            options.put(flag, args[++index]);
            return index + 1;
        }
        options.put(flag, "true");
        return index;
    }

    /**
     * Parse a short option (-E0.1 or -q).
     *
     * @param arg
     *            the argument starting with -
     * @param index
     *            current position in args (may be incremented)
     * @param args
     *            the raw arguments array
     * @param options
     *            map to populate with parsed options
     * @return the next index to parse after this option and its value
     */
    private int parseShortOption(String arg, int index, String[] args,
            Map<String, String> options) {
        String flag = arg.substring(0, 2);
        String value = arg.substring(2);

        if (value.isEmpty()) {
            if (index + 1 < args.length && !args[index + 1].startsWith("-")) {
                options.put(flag, args[++index]);
                return index + 1;
            }
            options.put(flag, "true");
            return index;
        }
        options.put(flag, value);
        return index;
    }

    /**
     * Validate required options per command. Returns list of error messages.
     */
    public List<String> validate(CommandArgs args) {
        List<String> errors = new ArrayList<>();
        String cmd = args.command().toLowerCase();

        // All commands require at least a filebase (last positional arg)
        if (!args.options().containsKey("filebase")) {
            errors.add("Missing required argument: <filebase>");
        }

        switch (cmd) {
        case "identify" -> validateIdentify(args, errors);
        case "classify" -> validateClassify(args, errors);
        case "compare" -> validateCompare(args, errors);
        case "report" -> validateReport(args, errors);
        case "generate" -> validateGenerate(args, errors);
        case "bootstrap" -> validateBootstrap(args, errors);
        case "fclassify", "splitgla" -> validateFClassify(args, errors);
        case "cumulative" -> validateCumulative(args, errors);
        case "sclassify", "joingla" -> validateSClassify(args, errors);
        case "tree" -> validateTree(args, errors);
        case "centroids" -> validateCentroids(args, errors);
        case "sortpart" -> validateSortPart(args, errors);
        case "mixture" -> validateMixture(args, errors);
        case "cut" -> validateCut(args, errors);
        case "function" -> validateFunction(args, errors);
        case "test1" -> validateTest1(args, errors);
        case "test2" -> validateTest2(args, errors);
        default -> errors.add("Unknown command: " + cmd);
        }

        return errors;
    }

    private void validateIdentify(CommandArgs args, List<String> errors) {
        String e = args.options().get("-E");
        if (e != null && !isDouble(e)) {
            try {
                Double.parseDouble(e);
            } catch (NumberFormatException _) {
                errors.add(INVALID_EPSILON_VALUE + e);
            }
        }
    }

    private void validateClassify(CommandArgs args, List<String> errors) {
        String kstart = args.options().get("-b");
        if (kstart != null) {
            try {
                int val = Integer.parseInt(kstart);
                if (val < 1)
                    errors.add("kstart must be >= 1");
            } catch (NumberFormatException _) {
                errors.add("Invalid kstart value: " + kstart);
            }
        }

        String kstop = args.options().get("-s");
        if (kstop != null && !isDouble(kstop)) {
            try {
                Integer.parseInt(kstop);
            } catch (NumberFormatException _) {
                errors.add("Invalid kstop value: " + kstop);
            }
        }

        String f = args.options().get("-f");
        if (f != null && !isInt(f)) {
            try {
                Integer.parseInt(f);
            } catch (NumberFormatException _) {
                errors.add("Invalid distance type: " + f);
            }
        }
    }

    private void validateCompare(CommandArgs args, List<String> errors) {
        String v = args.options().get("-V");
        if (v != null && !isInt(v)) {
            try {
                Integer.parseInt(v);
            } catch (NumberFormatException _) {
                errors.add("Invalid print mode: " + v);
            }
        }
    }

    private void validateReport(CommandArgs args, List<String> errors) {
        String p = args.options().get("-p");
        if (p != null && !isInt(p)) {
            try {
                Integer.parseInt(p);
            } catch (NumberFormatException _) {
                errors.add("Invalid report params: " + p);
            }
        }

        String e = args.options().get("-E");
        if (e != null && !isDouble(e)) {
            try {
                Double.parseDouble(e);
            } catch (NumberFormatException _) {
                errors.add(INVALID_EPSILON_VALUE + e);
            }
        }
    }

    private void validateGenerate(CommandArgs args, List<String> errors) {
        String v = args.options().get("-v");
        if (v != null && !isInt(v)) {
            try {
                Integer.parseInt(v);
            } catch (NumberFormatException _) {
                errors.add("Invalid vecs_to_gen: " + v);
            }
        }

        String g = args.options().get("-G");
        if (g != null && !isInt(g)) {
            try {
                Integer.parseInt(g);
            } catch (NumberFormatException _) {
                errors.add("Invalid data generator type: " + g);
            }
        }
    }

    private void validateBootstrap(CommandArgs args, List<String> errors) {
        String k = args.options().get("-K");
        if (k != null && !isInt(k)) {
            try {
                Integer.parseInt(k);
            } catch (NumberFormatException _) {
                errors.add("Invalid bootstrap_k: " + k);
            }
        }

        String n = args.options().get("-N");
        if (n != null && !isInt(n)) {
            try {
                Integer.parseInt(n);
            } catch (NumberFormatException _) {
                errors.add("Invalid bootstrap_size: " + n);
            }
        }

        String c = args.options().get("-c");
        if (c != null && !isInt(c)) {
            try {
                Integer.parseInt(c);
            } catch (NumberFormatException _) {
                errors.add("Invalid centroid_type: " + c);
            }
        }
    }

    private void validateFClassify(CommandArgs args, List<String> errors) {
        String s = args.options().get("-S");
        if (s != null && !isInt(s)) {
            try {
                Integer.parseInt(s);
            } catch (NumberFormatException _) {
                errors.add("Invalid kstopwhen: " + s);
            }
        }

        String e = args.options().get("-E");
        if (e != null && !isDouble(e)) {
            try {
                Double.parseDouble(e);
            } catch (NumberFormatException _) {
                errors.add(INVALID_EPSILON_VALUE + e);
            }
        }
    }

    private void validateCumulative(CommandArgs args, List<String> errors) {
        String n = args.options().get("-N");
        if (n != null && !isInt(n)) {
            try {
                Integer.parseInt(n);
            } catch (NumberFormatException _) {
                errors.add("Invalid cumulative_analysis: " + n);
            }
        }

        String s = args.options().get("-s");
        if (s != null && !isInt(s)) {
            try {
                Integer.parseInt(s);
            } catch (NumberFormatException _) {
                errors.add("Invalid cumulative_samples: " + s);
            }
        }

        String e = args.options().get("-E");
        if (e != null && !isDouble(e)) {
            try {
                Double.parseDouble(e);
            } catch (NumberFormatException _) {
                errors.add(INVALID_EPSILON_VALUE + e);
            }
        }
    }

    private void validateSClassify(CommandArgs args, List<String> errors) {
        String j = args.options().get("-j");
        if (j != null && !isInt(j)) {
            try {
                Integer.parseInt(j);
            } catch (NumberFormatException _) {
                errors.add("Invalid join_target: " + j);
            }
        }

        String t = args.options().get("-T");
        if (t != null && !isDouble(t)) {
            try {
                Double.parseDouble(t);
            } catch (NumberFormatException _) {
                errors.add("Invalid gla_treshold: " + t);
            }
        }

        String e = args.options().get("-E");
        if (e != null && !isDouble(e)) {
            try {
                Double.parseDouble(e);
            } catch (NumberFormatException _) {
                errors.add(INVALID_EPSILON_VALUE + e);
            }
        }
    }

    private void validateTree(CommandArgs args, List<String> errors) {
        String h = args.options().get("-H");
        if (h != null && !isInt(h)) {
            try {
                Integer.parseInt(h);
            } catch (NumberFormatException _) {
                errors.add("Invalid use_hellinger: " + h);
            }
        }
    }

    /** No additional validation for centroids command. */
    private void validateCentroids(CommandArgs args, List<String> errors) {
        // intentionally empty - no extra options to validate
    }

    /** No additional validation for sortpart command. */
    private void validateSortPart(CommandArgs args, List<String> errors) {
        // intentionally empty - no extra options to validate
    }

    private void validateMixture(CommandArgs args, List<String> errors) {
        String k = args.options().get("-k");
        if (k != null && !isInt(k)) {
            try {
                Integer.parseInt(k);
            } catch (NumberFormatException _) {
                errors.add("Invalid mixture_classes: " + k);
            }
        }

        String s = args.options().get("-s");
        if (s != null && !isInt(s)) {
            try {
                Integer.parseInt(s);
            } catch (NumberFormatException _) {
                errors.add("Invalid sample_mixture: " + s);
            }
        }

        String e = args.options().get("-E");
        if (e != null && !isDouble(e)) {
            try {
                Double.parseDouble(e);
            } catch (NumberFormatException _) {
                errors.add(INVALID_EPSILON_VALUE + e);
            }
        }
    }

    private void validateCut(CommandArgs args, List<String> errors) {
        String a = args.options().get("-a");
        if (a != null && !isInt(a)) {
            try {
                Integer.parseInt(a);
            } catch (NumberFormatException _) {
                errors.add("Invalid analyse_int: " + a);
            }
        }

        String analyzeStab = args.options().get("-A");
        if (analyzeStab != null && !isInt(analyzeStab)) {
            try {
                Integer.parseInt(analyzeStab);
            } catch (NumberFormatException _) {
                errors.add("Invalid analyse_int_stab: " + analyzeStab);
            }
        }

        String d = args.options().get("-d");
        if (d != null && !isInt(d)) {
            try {
                Integer.parseInt(d);
            } catch (NumberFormatException _) {
                errors.add("Invalid fixed_delta: " + d);
            }
        }

        String realDelta = args.options().get("-D");
        if (realDelta != null && !isInt(realDelta)) {
            try {
                Integer.parseInt(realDelta);
            } catch (NumberFormatException _) {
                errors.add("Invalid real_delta_value: " + realDelta);
            }
        }
    }

    private void validateFunction(CommandArgs args, List<String> errors) {
        String f = args.options().get("-f");
        if (f != null && !isInt(f)) {
            try {
                Integer.parseInt(f);
            } catch (NumberFormatException _) {
                errors.add("Invalid distance type: " + f);
            }
        }
    }

    private void validateTest1(CommandArgs args, List<String> errors) {
        String k = args.options().get("-k");
        if (k != null && !isInt(k)) {
            try {
                Integer.parseInt(k);
            } catch (NumberFormatException _) {
                errors.add("Invalid kstart: " + k);
            }
        }

        String r = args.options().get("-r");
        if (r != null && !isInt(r)) {
            try {
                Integer.parseInt(r);
            } catch (NumberFormatException _) {
                errors.add("Invalid t1_rs_count: " + r);
            }
        }

        String t = args.options().get("-t");
        if (t != null && !isInt(t)) {
            try {
                Integer.parseInt(t);
            } catch (NumberFormatException _) {
                errors.add("Invalid t1_trials: " + t);
            }
        }
    }

    private void validateTest2(CommandArgs args, List<String> errors) {
        String t = args.options().get("-t");
        if (t != null && !isInt(t)) {
            try {
                Integer.parseInt(t);
            } catch (NumberFormatException _) {
                errors.add("Invalid t2_treshold: " + t);
            }
        }
    }

    private boolean isDouble(String s) {
        if (s == null || s.isEmpty())
            return false;
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException _) {
            return false;
        }
    }

    private boolean isInt(String s) {
        if (s == null || s.isEmpty())
            return false;
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException _) {
            return false;
        }
    }

    /** Generate help text for a specific command or all commands. */
    public String helpText() {
        StringBuilder sb = new StringBuilder();
        sb.append(
                "BinClass - Software Package For Classifying Binary Vectors\n");
        sb.append("\nUsage: binclass <command> [options] <filebase>\n\n");
        sb.append("Commands:\n");
        sb.append("  identify      Identify vectors by classification\n");
        sb.append("  classify      Classify vectors with GLA\n");
        sb.append("  compare       Compare two partitions\n");
        sb.append("  report        Generate statistical report\n");
        sb.append("  generate      Generate synthetic data\n");
        sb.append("  bootstrap     Bootstrap GLA trials\n");
        sb.append("  fclassify     Fast classify with Split-GLA\n");
        sb.append("  cumulative    Cumulative classification\n");
        sb.append("  sclassify     Semi-cumulative classification\n");
        sb.append("  tree          Build hierarchical tree\n");
        sb.append("  centroids     Save/load centroids\n");
        sb.append("  sortpart      Sort/partition operations\n");
        sb.append("  mixture       Mixture classifier (EM)\n");
        sb.append("  cut           Cut/trim partitions\n");
        sb.append("  function      Render SC/Shannon functions vs k\n");
        sb.append("  test1         Test algorithm 1 (distortion minimizer)\n");
        sb.append("  test2         Test algorithm 2 (semi-cumulative)\n");
        return sb.toString();
    }

    /** Generate help text for a specific command. */
    public String helpTextForCommand(String command) {
        if (command == null || command.isEmpty()) {
            return helpText();
        }

        return switch (command.toLowerCase()) {
        case "identify" ->
            """
                    identify - Identify vectors by classification
                    Usage: binclass identify [options] <filebase>

                    Options:
                      -E<epsilon>   Set epsilon threshold (< 0.5)
                      -q            Quiet mode (no verbose output)
                      -t            Enable trashcan mode
                      -f<type>      Distance type (1=HAM,2=L1,3=L2,4=CL,5=L1_CL,6=L2_CL,7=SR,8=SA)
                      -J            Use Jeffreys prior
                      -w            Use class weights
                      -M            Exact matches only
                      -P<parfile>   Store partition to file
                    """;

        case "classify" ->
            """
                    classify - Classify vectors with GLA
                    Usage: binclass classify [options] <filebase>

                    Options:
                      -b<kstart>    Start k value (>= 1)
                      -s<kstop>     Stop k value
                      -S<kswhn>     Stop when k reaches this value
                      -E<epsilon>   Set epsilon threshold (< 0.5)
                      -q            Quiet mode
                      -r<heuristic> Heuristic type (1-8)
                      -t            Enable trashcan mode
                      -e<mode>      Alternate matching mode (1-4)
                      -m            Analyse missing values
                      -c<type>      Centroid type (1=CLASSIC,2=SRAND,3=SEMI,4=RAND,5=PNN)
                      -l            Log centroids
                      -J            Use Jeffreys prior
                      -w            Use class weights
                      -B<first_d>   Require better first distance
                      -C            Best code length mode
                      -R            Rounded centroids
                      -f<type>      Distance type (1-6)
                      -n<max_iter>  Max iterations (0=auto)
                      -j<count>     Heuristic count (+1)
                      -F<safety>    Safety limit (> max_iter, < 10000)
                      -a<iter_base> Iteration base
                      -d<dumpfile>  Dump file path
                      -L<ctrfile>   Centroid file for loading
                    """;

        case "compare" -> """
                compare - Compare two partitions
                Usage: binclass compare [options] <filebase>

                Options:
                  -M            Exact matches only
                  -q            Quiet mode
                  -V<mode>      Print mode (1=values,2=percentages,3=matches)
                """;

        case "report" -> """
                report - Generate statistical report
                Usage: binclass report [options] <filebase>

                Options:
                  -E<epsilon>   Set epsilon threshold (< 0.5)
                  -q            Quiet mode
                  -d            Print digits
                  -a            Affinity matrix
                  -w            Use class weights
                  -h            Use Hellinger distance
                  -p<params>    Report parameters (0=all,1-7=specific)
                  -l            Log centroids
                """;

        case "generate" ->
            """
                    generate - Generate synthetic data
                    Usage: binclass generate [options] <filebase>

                    Options:
                      -v<amount>    Number of vectors to generate
                      -q            Quiet mode
                      -u            Unique vectors only
                      -G<type>      Data generator type (1=RAND,2=BERNOULLI,3=MARKOV,4=RVECTOR)
                    """;

        case "bootstrap" -> """
                bootstrap - Bootstrap GLA trials
                Usage: binclass bootstrap [options] <filebase>

                Options:
                  -v<amount>    Vectors to generate
                  -P            Save best bootstraps
                  -J            Use Jeffreys prior
                  -r<heuristic> Heuristic type (1-6)
                  -N<size>      Bootstrap size
                  -K<k>         Bootstrap k value (+1)
                  -E<epsilon>   Set epsilon threshold (< 0.5)
                  -I<i>         Bootstrap iteration count
                  -q            Quiet mode
                  -w            Use class weights
                  -c<type>      Centroid type (1=CLASSIC,2=SRAND,3=SEMI,5=PNN)
                """;

        case "fclassify", "splitgla" -> """
                fclassify/splitgla - Fast classify with Split-GLA
                Usage: binclass fclassify [options] <filebase>

                Options:
                  -q            Quiet mode
                  -A            Use absolute match
                  -S<kstopwhn>  Stop when k reaches this value
                  -J            Use Jeffreys prior
                  -E<epsilon>   Set epsilon threshold (< 0.5)
                """;

        case "cumulative" -> """
                cumulative - Cumulative classification
                Usage: binclass cumulative [options] <filebase>

                Options:
                  -q            Quiet mode
                  -O            Cumulative input order
                  -I            Input order analysis
                  -S            Disable Bayesian predictive
                  -F            Test feature significance
                  -c            Save by partition file
                  -n            No new classes
                  -E<epsilon>   Set epsilon threshold (< 0.5)
                  -N<analysis>  Cumulative analysis count
                  -s<samples>   Cumulative samples
                  -D<delta>     Fixed delta value (>= 0)
                  -d<delta>     Delta value (>= 0)
                """;

        case "sclassify", "joingla" -> """
                sclassify/joingla - Semi-cumulative classification
                Usage: binclass sclassify [options] <filebase>

                Options:
                  -q            Quiet mode
                  -A            Use absolute match
                  -E<epsilon>   Set epsilon threshold (< 0.5)
                  -J            Use Jeffreys prior
                  -j<target>    Join target (>= 2)
                  -T<threshold> GLA threshold (> 1.0)
                """;

        case "tree" ->
            """
                    tree - Build hierarchical tree
                    Usage: binclass tree [options] <filebase>

                    Options:
                      -H<type>      Use distance type (1=HELLINGER,2=CUSTOM,4=PARSIMONY)
                      -J            Use Jeffreys prior
                      -q            Quiet mode
                    """;

        case "centroids" -> """
                centroids - Save/load centroids
                Usage: binclass centroids [options] <filebase>

                Options:
                  -q            Quiet mode
                """;

        case "sortpart" -> """
                sortpart - Sort/partition operations
                Usage: binclass sortpart [options] <filebase>

                Options:
                  -q            Quiet mode
                """;

        case "mixture" -> """
                mixture - Mixture classifier (EM algorithm)
                Usage: binclass mixture [options] <filebase>

                Options:
                  -q            Quiet mode
                  -E<epsilon>   Set epsilon threshold (< 0.5)
                  -k<classes>   Number of mixture classes (+1)
                  -s<samples>   Sample mixture count (+1)
                """;

        case "cut" -> """
                cut - Cut/trim partitions
                Usage: binclass cut [options] <filebase>

                Options:
                  -r            Relative interval
                  -s            Minimal interval
                  -m            Maximal interval
                  -q            Quiet mode
                  -A<kstart>    Analyse interval stability (+1)
                  -a<kstart>    Analyse interval (+1)
                  -d<delta>     Delta value (>= 0)
                  -D<delta>     Fixed delta value (>= 0)
                """;

        case "function" ->
            """
                    function - Render SC/Shannon functions vs k
                    Usage: binclass function [options] <filebase>

                    Options:
                      -q            Quiet mode
                      -w            Use class weights
                      -f<type>      Distance type (1=HAM,2=L1,3=L2,4=CL,5=L1_CL,6=L2_CL)
                    """;

        case "test1" -> """
                test1 - Test algorithm 1 (distortion minimizer)
                Usage: binclass test1 [options] <filebase>

                Options:
                  -q            Quiet mode
                  -k<kstart>    Start k value (+1)
                  -r<count>     RS count (+1)
                  -t<trials>   Trials count (+1)
                  -e            Extra iterations
                """;

        case "test2" -> """
                test2 - Test algorithm 2 (semi-cumulative)
                Usage: binclass test2 [options] <filebase>

                Options:
                  -q            Quiet mode
                  -t<threshold> Threshold value
                """;

        default -> "Unknown command: " + command;
        };
    }
}
