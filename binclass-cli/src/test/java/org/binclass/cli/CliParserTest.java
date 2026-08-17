package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.binclass.cli.CliParser.CommandArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CliParser}. Covers parsing, validation, and help text.
 */
class CliParserTest {

    private CliParser parser;

    @BeforeEach
    void setUp() {
        parser = new CliParser();
    }

    // ── Parsing ────────────────────────────────────────────────────────

    @Nested
    class ParseTests {

        @Test
        void parseMinimalArgs() {
            // Positional arg "mydata" is ignored by parser (only flags are
            // parsed)
            CommandArgs args = parser
                    .parse(new String[] { "binclass", "classify", "mydata" });
            assertEquals("classify", args.command());
            assertFalse(args.options().containsKey("filebase"));
        }

        @Test
        void parseMinimalArgsWithFilebaseOption() {
            CommandArgs args = parser.parse(
                    new String[] { "binclass", "classify",
                            "--filebase=mydata" });
            assertEquals("classify", args.command());
            assertTrue(args.options().containsKey("filebase"));
            assertEquals("mydata", args.options().get("filebase"));
        }

        @Test
        void parseNullArgs() {
            assertThrows(IllegalArgumentException.class,
                    () -> parser.parse(null));
        }

        @Test
        void parseEmptyArgs() {
            assertThrows(IllegalArgumentException.class,
                    () -> parser.parse(new String[0]));
        }

        @Test
        void parseSingleArg() {
            assertThrows(IllegalArgumentException.class,
                    () -> parser.parse(new String[] { "binclass" }));
        }

        @Test
        void parseShortOptionWithValue() {
            CommandArgs args = parser.parse(
                    new String[] { "binclass", "identify", "-E0.1",
                            "--filebase=mydata" });
            assertEquals("identify", args.command());
            assertEquals("0.1", args.options().get("-E"));
        }

        @Test
        void parseShortOptionWithoutValue() {
            // "-q" followed by "mydata" (non-option) → -q gets value "mydata"
            CommandArgs args = parser.parse(
                    new String[] { "binclass", "classify", "-q", "mydata" });
            assertEquals("mydata", args.options().get("-q"));
        }

        @Test
        void parseLongOptionWithEquals() {
            CommandArgs args = parser.parse(
                    new String[] { "binclass", "identify",
                            "--filebase=mydata" });
            assertEquals("mydata", args.options().get("filebase"));
        }

        @Test
        void parseLongOptionWithValueArg() {
            // "--filebase" followed by "mydata" → filebase gets value "mydata"
            CommandArgs args = parser.parse(
                    new String[] { "binclass", "classify", "--filebase",
                            "mydata" });
            assertEquals("mydata", args.options().get("filebase"));
        }

        @Test
        void parseLongFlagBoolean() {
            // "--help" with no next arg → boolean true
            CommandArgs args = parser.parse(
                    new String[] { "binclass", "identify", "--help" });
            assertEquals("true", args.options().get("help"));
        }

        @Test
        void parseHelpFlag() {
            CommandArgs args = parser.parse(
                    new String[] { "binclass", "classify", "--help" });
            assertEquals("true", args.options().get("help"));
        }

        @Test
        void parseMultipleOptions() {
            // "-t" followed by "mydata" → -t gets value "mydata"
            CommandArgs args = parser.parse(new String[] {
                    "binclass", "identify", "-E0.1", "-q", "-t", "mydata"
            });
            assertEquals("0.1", args.options().get("-E"));
            // -q has no next non-option arg, so it's "true"
            // But wait - after -q comes "-t" which starts with "-", so
            // -q="true"
            // Then -t has no value, but we're at end... actually let me trace:
            // i=2: "-E0.1" → options["-E"]="0.1", i becomes 3
            // i=3: "-q" (value empty), next="-t" starts with "-", so
            // options["-q"]="true", i becomes 4
            // i=4: "-t" (value empty), next="mydata" doesn't start with "-", so
            // options["-t"]="mydata", i becomes 5
            assertEquals("true", args.options().get("-q"));
            assertEquals("mydata", args.options().get("-t"));
        }

        @Test
        void parseOptionWithNextArgValue() {
            // "-f" followed by "1" → -f gets value "1"
            CommandArgs args = parser.parse(new String[] {
                    "binclass", "classify", "-f", "1", "--filebase=mydata"
            });
            assertEquals("1", args.options().get("-f"));
        }

        @Test
        void parseMaxArgsLimit() {
            // MAX_ARGS is 1024; ensure we don't throw on large inputs
            String[] big = new String[50];
            for (int i = 0; i < big.length; i++) {
                big[i] = "-x" + i;
            }
            CommandArgs args = parser.parse(big);
            assertNotNull(args);
        }

        @Test
        void parseCommandArgsImpl() {
            Map<String, String> opts = new java.util.HashMap<>();
            opts.put("key", "val");
            CliParser.CommandArgsImpl impl = new CliParser.CommandArgsImpl(
                    "cmd", opts);
            assertEquals("cmd", impl.command());
            assertEquals("val", impl.options().get("key"));
        }

        @Test
        void parseShortOptionWithAdjacentValue() {
            CommandArgs args = parser.parse(
                    new String[] { "binclass", "identify", "-E0.5",
                            "--filebase=mydata" });
            assertEquals("0.5", args.options().get("-E"));
        }

        @Test
        void parseLongOptionWithAdjacentValue() {
            CommandArgs args = parser.parse(
                    new String[] { "binclass", "identify",
                            "--filebase=mydata" });
            assertEquals("mydata", args.options().get("filebase"));
        }
    }

    // ── Validation ─────────────────────────────────────────────────────

    @Nested
    class ValidateTests {

        @Test
        void validateMissingFilebase() {
            // No filebase key → validation error
            CommandArgs args = new CliParser.CommandArgsImpl("classify",
                    Map.of());
            List<String> errors = parser.validate(args);
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e -> e.contains("filebase")));
        }

        @Test
        void validateIdentifyInvalidEpsilon() {
            CommandArgs args = buildArgs("identify", Map.of("-E", "abc"));
            List<String> errors = parser.validate(args);
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e -> e.contains("epsilon")));
        }

        @Test
        void validateIdentifyValidEpsilon() {
            CommandArgs args = buildArgs("identify", Map.of("-E", "0.3"));
            List<String> errors = parser.validate(args);
            assertTrue(errors.isEmpty());
        }

        @Test
        void validateClassifyInvalidKstart() {
            CommandArgs args = buildArgs("classify", Map.of("-b", "-1"));
            List<String> errors = parser.validate(args);
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e -> e.contains("kstart")));
        }

        @Test
        void validateClassifyValidKstart() {
            CommandArgs args = buildArgs("classify", Map.of("-b", "2"));
            List<String> errors = parser.validate(args);
            assertTrue(errors.isEmpty());
        }

        @Test
        void validateClassifyInvalidDistanceType() {
            CommandArgs args = buildArgs("classify", Map.of("-f", "abc"));
            List<String> errors = parser.validate(args);
            assertFalse(errors.isEmpty());
            assertTrue(
                    errors.stream().anyMatch(e -> e.contains("distance type")));
        }

        @Test
        void validateCompareInvalidPrintMode() {
            CommandArgs args = buildArgs("compare", Map.of("-V", "xyz"));
            List<String> errors = parser.validate(args);
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e -> e.contains("print mode")));
        }

        @Test
        void validateReportInvalidParams() {
            CommandArgs args = buildArgs("report", Map.of("-p", "abc"));
            List<String> errors = parser.validate(args);
            assertFalse(errors.isEmpty());
            assertTrue(
                    errors.stream().anyMatch(e -> e.contains("report params")));
        }

        @Test
        void validateGenerateInvalidVecs() {
            CommandArgs args = buildArgs("generate", Map.of("-v", "abc"));
            List<String> errors = parser.validate(args);
            assertFalse(errors.isEmpty());
            assertTrue(
                    errors.stream().anyMatch(e -> e.contains("vecs_to_gen")));
        }

        @Test
        void validateBootstrapInvalidK() {
            CommandArgs args = buildArgs("bootstrap", Map.of("-K", "abc"));
            List<String> errors = parser.validate(args);
            assertFalse(errors.isEmpty());
            assertTrue(
                    errors.stream().anyMatch(e -> e.contains("bootstrap_k")));
        }

        @Test
        void validateFClassifyInvalidKstop() {
            CommandArgs args = buildArgs("fclassify", Map.of("-S", "abc"));
            List<String> errors = parser.validate(args);
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e -> e.contains("kstopwhen")));
        }

        @Test
        void validateCumulativeInvalidN() {
            CommandArgs args = buildArgs("cumulative", Map.of("-N", "abc"));
            List<String> errors = parser.validate(args);
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream()
                    .anyMatch(e -> e.contains("cumulative_analysis")));
        }

        @Test
        void validateSClassifyInvalidJoinTarget() {
            CommandArgs args = buildArgs("sclassify", Map.of("-j", "abc"));
            List<String> errors = parser.validate(args);
            assertFalse(errors.isEmpty());
            assertTrue(
                    errors.stream().anyMatch(e -> e.contains("join_target")));
        }

        @Test
        void validateTreeInvalidHellinger() {
            CommandArgs args = buildArgs("tree", Map.of("-H", "abc"));
            List<String> errors = parser.validate(args);
            assertFalse(errors.isEmpty());
            assertTrue(
                    errors.stream().anyMatch(e -> e.contains("use_hellinger")));
        }

        @Test
        void validateCentroidsNoErrors() {
            CommandArgs args = buildArgs("centroids",
                    Map.of("filebase", "data"));
            List<String> errors = parser.validate(args);
            assertTrue(errors.isEmpty());
        }

        @Test
        void validateSortPartNoErrors() {
            CommandArgs args = buildArgs("sortpart",
                    Map.of("filebase", "data"));
            List<String> errors = parser.validate(args);
            assertTrue(errors.isEmpty());
        }

        @Test
        void validateMixtureInvalidK() {
            CommandArgs args = buildArgs("mixture", Map.of("-k", "abc"));
            List<String> errors = parser.validate(args);
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream()
                    .anyMatch(e -> e.contains("mixture_classes")));
        }

        @Test
        void validateCutInvalidA() {
            CommandArgs args = buildArgs("cut", Map.of("-a", "abc"));
            List<String> errors = parser.validate(args);
            assertFalse(errors.isEmpty());
            assertTrue(
                    errors.stream().anyMatch(e -> e.contains("analyse_int")));
        }

        @Test
        void validateFunctionInvalidDistanceType() {
            CommandArgs args = buildArgs("function", Map.of("-f", "abc"));
            List<String> errors = parser.validate(args);
            assertFalse(errors.isEmpty());
            assertTrue(
                    errors.stream().anyMatch(e -> e.contains("distance type")));
        }

        @Test
        void validateTest1InvalidK() {
            CommandArgs args = buildArgs("test1", Map.of("-k", "abc"));
            List<String> errors = parser.validate(args);
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream().anyMatch(e -> e.contains("kstart")));
        }

        @Test
        void validateTest2InvalidThreshold() {
            CommandArgs args = buildArgs("test2", Map.of("-t", "abc"));
            List<String> errors = parser.validate(args);
            assertFalse(errors.isEmpty());
            assertTrue(
                    errors.stream().anyMatch(e -> e.contains("t2_treshold")));
        }

        @Test
        void validateUnknownCommand() {
            CommandArgs args = buildArgs("unknown_cmd",
                    Map.of("filebase", "data"));
            List<String> errors = parser.validate(args);
            assertFalse(errors.isEmpty());
            assertTrue(errors.stream()
                    .anyMatch(e -> e.contains("Unknown command")));
        }

        @Test
        void validateAllCommandsHaveFilebase() {
            String[] commands = { "identify", "classify", "compare", "report",
                    "generate",
                    "bootstrap", "fclassify", "cumulative", "sclassify", "tree",
                    "centroids", "sortpart", "mixture", "cut", "function",
                    "test1", "test2" };
            for (String cmd : commands) {
                // All commands should fail validation when no filebase is
                // provided
                CommandArgs args = new CliParser.CommandArgsImpl(cmd, Map.of());
                List<String> errors = parser.validate(args);
                assertTrue(
                        errors.stream().anyMatch(e -> e.contains("filebase")),
                        () -> cmd + " should require filebase");
            }
        }

    } // end ValidateTests

    // ── Help Text ──────────────────────────────────────────────────────

    @Nested
    class HelpTextTests {

        @Test
        void helpTextNotEmpty() {
            String help = parser.helpText();
            assertNotNull(help);
            assertFalse(help.isEmpty());
            assertTrue(help.contains("binclass"));
        }

        @Test
        void helpTextForCommandReturnsContent() {
            String help = parser.helpTextForCommand("classify");
            assertNotNull(help);
            assertFalse(help.isEmpty());
            assertTrue(help.contains("classify"));
        }

        @Test
        void helpTextForNullCommand() {
            String help = parser.helpTextForCommand(null);
            assertEquals(parser.helpText(), help);
        }

        @Test
        void helpTextForEmptyCommand() {
            String help = parser.helpTextForCommand("");
            assertEquals(parser.helpText(), help);
        }

        @Test
        void helpTextForUnknownCommand() {
            String help = parser.helpTextForCommand("nonexistent");
            assertTrue(help.contains("Unknown command"));
        }

        @Test
        void helpTextContainsAllCommands() {
            String help = parser.helpText();
            String[] commands = { "identify", "classify", "compare", "report",
                    "generate",
                    "bootstrap", "fclassify", "cumulative", "sclassify", "tree",
                    "centroids", "sortpart", "mixture", "cut", "function",
                    "test1", "test2" };
            for (String cmd : commands) {
                assertTrue(help.contains(cmd),
                        () -> "Help should mention command: " + cmd);
            }
        }

        @Test
        void helpTextForIdentifyContainsOptions() {
            String help = parser.helpTextForCommand("identify");
            assertTrue(help.contains("-E"));
            assertTrue(help.contains("-q"));
            assertTrue(help.contains("-t"));
        }

        @Test
        void helpTextForClassifyContainsOptions() {
            String help = parser.helpTextForCommand("classify");
            assertTrue(help.contains("-b"));
            assertTrue(help.contains("-s"));
            assertTrue(help.contains("-E"));
            assertTrue(help.contains("-L"));
        }

        @Test
        void helpTextForCompareContainsOptions() {
            String help = parser.helpTextForCommand("compare");
            assertTrue(help.contains("-V"));
            assertTrue(help.contains("-M"));
        }

        @Test
        void helpTextForCentroidsContainsOptions() {
            String help = parser.helpTextForCommand("centroids");
            assertTrue(help.contains("-q"));
        }

        @Test
        void helpTextForCutContainsOptions() {
            String help = parser.helpTextForCommand("cut");
            assertTrue(help.contains("-a"));
            assertTrue(help.contains("-A"));
            assertTrue(help.contains("-d"));
            assertTrue(help.contains("-D"));
        }
    }

    // ── Edge Cases ─────────────────────────────────────────────────────

    private CommandArgs buildArgs(String command, Map<String, String> opts) {
        var merged = new java.util.HashMap<>(opts);
        merged.put("filebase", "testdata");
        return new CliParser.CommandArgsImpl(command, merged);
    }

    @Test
    void parseEmptyStringValue() {
        // "-q" followed by "mydata" (non-option) → -q gets value "mydata", not
        // "true"
        CommandArgs args = parser
                .parse(new String[] { "binclass", "classify", "-q", "mydata" });
        assertEquals("mydata", args.options().get("-q"));
    }

    @Test
    void parseOptionWithDashValue() {
        // -f-1 should be parsed as flag=-f, value="-1"
        CommandArgs args = parser.parse(new String[] { "binclass", "classify",
                "-f-1", "--filebase=mydata" });
        assertEquals("-1", args.options().get("-f"));
    }

    @Test
    void parseLongOptionWithDashValue() {
        CommandArgs args = parser.parse(
                new String[] { "binclass", "identify", "--filebase=-data" });
        assertEquals("-data", args.options().get("filebase"));
    }

    @Test
    void validateIdentifyEmptyEpsilonIsError() {
        // Empty string → isDouble returns false, so validation produces an
        // error
        CommandArgs args = buildArgs("identify", Map.of("-E", ""));
        List<String> errors = parser.validate(args);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("epsilon")));
    }

    @Test
    void validateClassifyEmptyKstartIsError() {
        // Empty string → isInt returns false, so validation produces an error
        CommandArgs args = buildArgs("classify", Map.of("-b", ""));
        List<String> errors = parser.validate(args);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.contains("kstart")));
    }

    @Test
    void parseHelpShortFlag() {
        // "-h" is parsed as short option with key="-h", value="true" (no next
        // arg)
        CommandArgs args = parser
                .parse(new String[] { "binclass", "classify", "-h" });
        assertEquals("true", args.options().get("-h"));
    }

    @Test
    void parseHelpWord() {
        // "help" as positional arg → matches help word check
        CommandArgs args = parser.parse(
                new String[] { "binclass", "classify", "help" });
        assertEquals("true", args.options().get("help"));
    }

    @Test
    void parseHelpLongFlagStandalone() {
        // "--help" standalone → help=true (not "-h")
        CommandArgs args = parser.parse(
                new String[] { "binclass", "classify", "--help" });
        assertEquals("true", args.options().get("help"));
    }

    @Test
    void parseShortOptionWithAnotherOptionNext() {
        // -q followed by another option → -q=true (no value consumed)
        CommandArgs args = parser.parse(
                new String[] { "binclass", "classify", "-q", "-t" });
        assertEquals("true", args.options().get("-q"));
    }

    @Test
    void parseLongOptionWithAnotherOptionNext() {
        // --verbose followed by another option → verbose=true (no value
        // consumed)
        CommandArgs args = parser.parse(
                new String[] { "binclass", "classify", "--verbose", "-t" });
        assertEquals("true", args.options().get("verbose"));
    }

    @Test
    void parseMixedShortAndLongOptions() {
        CommandArgs args = parser.parse(new String[] {
                "binclass", "identify", "-E0.1", "--filebase=mydata", "-q"
        });
        assertEquals("0.1", args.options().get("-E"));
        assertEquals("mydata", args.options().get("filebase"));
        assertEquals("true", args.options().get("-q"));
    }

    @Test
    void parseOptionWithEmptyAdjacentValue() {
        // -q (empty adjacent value) at end → true
        CommandArgs args = parser.parse(
                new String[] { "binclass", "classify", "-q" });
        assertEquals("true", args.options().get("-q"));
    }

    @Test
    void parseCommandIsSecondArg() {
        // First arg is ignored, second is command
        CommandArgs args = parser.parse(
                new String[] { "ignored", "classify", "--filebase=mydata" });
        assertEquals("classify", args.command());
    }
}
