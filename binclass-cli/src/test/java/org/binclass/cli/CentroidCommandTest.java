package org.binclass.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for CentroidCommand to verify centroids are computed from a
 * partition file as the per-bit frequency average of each class's member
 * vectors.
 */
class CentroidCommandTest {

    private CentroidCommand command;
    private TestCommandArgs args;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        command = new CentroidCommand();
        args = TestUtils.createTestArgs("data");
    }

    @AfterEach
    void tearDown() {
        // No static mocks are used now that real files are read.
    }

    /**
     * Writes a header file and a two-cluster partition for the given filebase.
     * Cluster 1 holds vectors {@code 1000} and {@code 1100}; cluster 2 holds
     * {@code 0011}. Returns the filebase path (without extension).
     */
    private Path writePartitionFiles() throws IOException {
        String header = "vecoffs=23\nveclen=4\nidoffs=15\nnamelen=9\n";
        Files.writeString(tempDir.resolve("data.header"), header);

        int vecOffs = 23;
        String[] strains = { "strain0", "strain1", "strain2" };
        String[] bits = { "1000", "1100", "0011" };
        int[] classes = { 1, 1, 2 };

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < strains.length; i++) {
            if (i == 0 || classes[i] != classes[i - 1]) {
                sb.append("Class ").append(classes[i]).append('\n');
            }
            // Name field starts at column 0 (non-space) so PartitionReader
            // treats the line as a vector; bits land exactly at vecOffs.
            StringBuilder line = new StringBuilder();
            while (line.length() < vecOffs)
                line.append(' ');
            line.setCharAt(0, 'X');
            line.append(bits[i]);
            sb.append(line).append('\n');
        }
        Files.writeString(tempDir.resolve("data.partition"), sb.toString());

        return tempDir.resolve("data");
    }

    /**
     * Reads the centroid values written by {@link CentroidWriter} into an array
     * of rows. The first two non-comment lines hold the number of centroids and
     * the vector length; each following line holds one centroid's
     * space-separated values followed by a trailing weight, which is discarded.
     */
    private static double[][] parseCentroidFile(Path file) throws IOException {
        List<String> lines = Files.readAllLines(file);
        int pos = 0;
        while (pos < lines.size()) {
            String trimmed = lines.get(pos).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                pos++;
                continue;
            }
            break;
        }
        // First value is the number of centroids, second is the vector length.
        int d = Integer.parseInt(lines.get(pos + 1).trim());
        pos += 2;

        List<double[]> centroids = new ArrayList<>();
        for (int i = pos; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] tokens = line.split("\\s+");
            double[] values = new double[d];
            for (int j = 0; j < d; j++) {
                values[j] = Double.parseDouble(tokens[j]);
            }
            centroids.add(values);
        }
        return centroids.toArray(new double[0][]);
    }

    @Test
    void testExecuteComputesCentroidsFromPartition() throws Exception {
        Path filebase = writePartitionFiles();
        args.setOptions(Map.of("filebase", filebase.toString()));

        int result = command.execute(args);

        assertEquals(0, result);

        double[][] centroids = parseCentroidFile(filebase
                .resolveSibling("data.centroids"));
        assertEquals(2, centroids.length);
        // Cluster 1: average of "1000" and "1100" -> [1.0, 0.5, 0.0, 0.0]
        assertArrayEquals(new double[] { 1.0, 0.5, 0.0, 0.0 }, centroids[0],
                1e-9);
        // Cluster 2: single vector "0011" -> [0.0, 0.0, 1.0, 1.0]
        assertArrayEquals(new double[] { 0.0, 0.0, 1.0, 1.0 }, centroids[1],
                1e-9);
    }

    @Test
    void testExecuteWithRoundedFlag() throws Exception {
        Path filebase = writePartitionFiles();
        args.setOptions(Map.of("filebase", filebase.toString(), "-R", ""));

        int result = command.execute(args);

        assertEquals(0, result);

        double[][] centroids = parseCentroidFile(filebase
                .resolveSibling("data.centroids"));
        // 0.5 rounds up to 1.0 (>= 0.5) when rounding is enabled
        assertArrayEquals(new double[] { 1.0, 1.0, 0.0, 0.0 }, centroids[0],
                1e-9);
        assertArrayEquals(new double[] { 0.0, 0.0, 1.0, 1.0 }, centroids[1],
                1e-9);
    }

    @Test
    void testExecuteWithQuietMode() throws Exception {
        Path filebase = writePartitionFiles();
        args.setOptions(Map.of("filebase", filebase.toString(), "-q", ""));

        int result = command.execute(args);

        assertEquals(0, result);
        assertTrue(Files.exists(filebase.resolveSibling("data.centroids")));
    }

    @Test
    void testExecuteWithDefaultFilebase() throws Exception {
        // args.command() is used as the filebase when no -f option is given;
        // point it at the absolute path inside tempDir so files are found.
        args = new TestCommandArgs(tempDir.resolve("data").toString());
        writePartitionFiles();
        args.setOptions(Map.of());

        int result = command.execute(args);

        assertEquals(0, result);
        double[][] centroids = parseCentroidFile(
                tempDir.resolve("data.centroids"));
        assertEquals(2, centroids.length);
    }

    @Test
    void testExecuteWithMissingPartition() throws Exception {
        args.setOptions(Map.of("filebase", tempDir.resolve("missing")
                .toString()));

        assertThrows(Exception.class, () -> command.execute(args));
    }

    @Test
    void testGetName() {
        assertEquals("centroids", command.getName());
    }

    @Test
    void testGetDescription() {
        String desc = command.getDescription();
        assertNotNull(desc);
        assertTrue(!desc.isEmpty());
    }
}
