package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for TestAlgorithmsCommand.
 */
class AlgorithmsCommandTest {

    @Test
    void testGetName() {
        BaseCommand cmd1 = new TestAlgorithmsCommand("test1");
        assertEquals("test1", cmd1.getName());

        BaseCommand cmd2 = new TestAlgorithmsCommand("test2");
        assertEquals("test2", cmd2.getName());
    }

    @Test
    void testGetDescription() {
        BaseCommand cmd1 = new TestAlgorithmsCommand("test1");
        assertTrue(cmd1.getDescription().contains("distortion minimizer"));

        BaseCommand cmd2 = new TestAlgorithmsCommand("test2");
        assertTrue(cmd2.getDescription().contains("semi-cumulative"));
    }

    @Test
    void testExecuteTest1WithValidOptions() throws Exception {
        CliParser.CommandArgs args = createArgs("test1", Map.of("-k", "5"));

        TestAlgorithmsCommand cmd = new TestAlgorithmsCommand("test1");
        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode);
    }

    @Test
    void testExecuteTest2WithValidOptions() throws Exception {
        CliParser.CommandArgs args = createArgs("test2", Map.of("-t", "10"));

        TestAlgorithmsCommand cmd = new TestAlgorithmsCommand("test2");
        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode);
    }

    @Test
    void testExecuteTest1RequiresKOption() {
        CliParser.CommandArgs args = createArgs("test1", Map.of());

        TestAlgorithmsCommand cmd = new TestAlgorithmsCommand("test1");
        assertThrows(IllegalArgumentException.class, () -> cmd.execute(args));
    }

    @Test
    void testExecuteTest1WithInvalidK() {
        CliParser.CommandArgs args = createArgs("test1", Map.of("-k", "abc"));

        TestAlgorithmsCommand cmd = new TestAlgorithmsCommand("test1");
        assertThrows(IllegalArgumentException.class, () -> cmd.execute(args));
    }

    @Test
    void testExecuteTest2WithInvalidThreshold() {
        CliParser.CommandArgs args = createArgs("test2", Map.of("-t", "xyz"));

        TestAlgorithmsCommand cmd = new TestAlgorithmsCommand("test2");
        assertThrows(IllegalArgumentException.class, () -> cmd.execute(args));
    }

    @Test
    void testExecuteTest1WithAllOptions() throws Exception {
        CliParser.CommandArgs args = createArgs("test1", Map.of(
                "-k", "3",
                "-r", "2",
                "-t", "4",
                "-e", ""));

        TestAlgorithmsCommand cmd = new TestAlgorithmsCommand("test1");
        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode);
    }

    @Test
    void testExecuteTest2WithVerbose() throws Exception {
        CliParser.CommandArgs args = createArgs("test2", Map.of("-t", "5"));

        TestAlgorithmsCommand cmd = new TestAlgorithmsCommand("test2");
        int exitCode = cmd.execute(args);

        assertEquals(0, exitCode);
    }

    private static CliParser.CommandArgs createArgs(String command,
            Map<String, String> options) {
        return new CliParser.CommandArgsImpl(command, new HashMap<>(options));
    }
}
