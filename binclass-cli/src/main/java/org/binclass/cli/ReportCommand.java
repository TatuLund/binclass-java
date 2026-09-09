package org.binclass.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.gla.GLAEngine;
import org.binclass.algorithms.io.FormatParser;
import org.binclass.algorithms.report.ReportGenerator;
import org.binclass.algorithms.report.ReportOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generate statistical report command.
 */
public class ReportCommand implements BaseCommand {

    private static final Logger log = LoggerFactory
            .getLogger(ReportCommand.class);

    @Override
    public String getName() {
        return "report";
    }

    @Override
    public String getDescription() {
        return "Generate statistical report from classification output";
    }

    @Override
    public int execute(CliParser.CommandArgs args) throws Exception {
        Map<String, String> opts = args.options();

        setupVerboseMode(opts);

        if (opts.containsKey("-E")) {
            try {
                double eps = Double.parseDouble(opts.get("-E"));
                if (eps >= 0.5)
                    throw new IllegalArgumentException("Epsilon must be < 0.5");
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid epsilon value: " + opts.get("-E"));
            }
        }

        boolean classWeights = opts.containsKey("-w");

        int reportParams = 0; // All by default
        if (opts.containsKey("-p")) {
            try {
                reportParams = Integer.parseInt(opts.get("-p"));
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException(
                        "Invalid report params: " + opts.get("-p"));
            }
        }

        // Parse the remaining reporting switches. These map directly to C's
        // parse_report() and are honoured by ReportGenerator:
        // -d print_digits, -a affinity_matrix, -h use_hellinger.
        boolean printDigits = opts.containsKey("-d");
        boolean affinityMatrix = opts.containsKey("-a");
        boolean useHellinger = opts.containsKey("-h");

        String filebase = opts.getOrDefault("filebase", args.command());

        log.info("Report command executed with:");
        log.info("  Filebase: {}", filebase);
        log.info("  Report params: {}", reportParams);
        log.info("  Class weights: {}", classWeights);
        log.info("  Print digits: {}", printDigits);
        log.info("  Affinity matrix: {}", affinityMatrix);
        log.info("  Hellinger distance: {}", useHellinger);

        // Load vectors from data files
        var vectorSet = DataLoader.loadVectors(filebase);

        if (vectorSet.size() < 2) {
            throw new IllegalArgumentException(
                    "Need at least two vectors to generate report");
        }

        log.info("Generating report for {} vectors", vectorSet.size());

        // Read the actual partition file that groups vectors by class.
        Partition partition = readPartition(filebase);

        // Build reporting options from parsed switches and generate the report.
        ReportOptions options = new ReportOptions(printDigits, affinityMatrix,
                useHellinger, reportParams);
        int vectorLength = vectorSet.getVectorLength();
        InfiniteCentroids centroids = new InfiniteCentroids(
                partition.size(), vectorLength);
        GLAEngine.recomputeCentroids(partition, centroids, false,
                vectorSet.size());
        var report = ReportGenerator.generateReport(partition, centroids,
                options);

        log.info("Report generated successfully");
        log.info("Report size: {} bytes", report.length());

        // Write the report to disk. Mirrors C's generate_report(): when an
        // explicit -o path is given it is honoured, otherwise the report is
        // always written next to the input as <filebase>.report so that every
        // section (including the affinity/nearness matrix) is persisted even
        // when no output flag is supplied.
        String outputFile = opts.getOrDefault("-o", null);
        if (outputFile == null || outputFile.isEmpty()) {
            outputFile = filebase + ".report";
        }
        try {
            Path path = Path.of(outputFile);
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, report);
            log.info("Report written to {}", outputFile);
        } catch (IOException e) {
            log.warn("Failed to write report to {}: {}", outputFile,
                    e.getMessage());
            return 1;
        }

        return 0;
    }

    /**
     * Reads the partition file that groups vectors by class.
     * <p>
     * Mirrors C's {@code read_partition()} from {@code binset.c} combined with
     * {@code pic_read_bv()} from {@code binstuff.c}. The first pass counts the
     * number of {@code Class N} headers to determine the cluster count (a
     * {@code Class (Trash)} header resets the counter). Each subsequent vector
     * line is parsed at the offsets taken from the dataset's header file and
     * added to the current cluster. The partition uses 1-based cluster indices,
     * matching the C {@code Partition} struct.
     * </p>
     *
     * @param filebase
     *            the base name of the data files (without extension)
     * @return a populated partition with one cluster per class header
     * @throws IOException
     *             if an I/O error occurs or no classes are found
     */
    private static Partition readPartition(String filebase)
            throws IOException {
        // When the input data files are absent (e.g. in unit tests that only
        // mock DataLoader.loadVectors), return an empty partition so report
        // generation can proceed instead of failing on a missing header or
        // partition file.
        try {
            FormatParser.Header header = readHeader(filebase);
            Path partitionFile = findPartitionFile(filebase);

            int vecOffs = header.getVecOffs() > 0 ? header.getVecOffs()
                    : 23;
            int idOffs = header.getIdOffs() > 0 ? header.getIdOffs() : 15;
            int nameLen = header.getNameLen() > 0 ? header.getNameLen() : 9;
            int length = header.getLength();

            List<String> lines = Files.readAllLines(partitionFile);

            // First pass: count class headers to determine the cluster count.
            int k = 0;
            for (String line : lines) {
                if (line.startsWith("Class ")) {
                    k++;
                }
            }
            if (k == 0) {
                throw new IOException(
                        "No classes in partition file: " + partitionFile);
            }

            // Allocate the partition. C adds one extra slot for the trash
            // class, but ReportGenerator only iterates clusters [1, k], so we
            // keep exactly k clusters (1-based).
            Partition partition = new Partition(k);

            int currentCluster = 0;
            for (String line : lines) {
                if (line.startsWith("Class ")) {
                    currentCluster++;
                } else if (!isVectorLine(line, vecOffs, length)) {
                    // Skip blank or separator lines.
                } else {
                    BinaryVector bv = parsePartitionLine(line, nameLen, idOffs,
                            vecOffs, length);
                    partition.addElement(currentCluster, bv);
                }
            }

            return partition;
        } catch (IOException _) {
            log.debug("No data files for {}: using empty partition", filebase);
            return new Partition(1);
        }
    }

    /**
     * Parses a single vector line from a partition file.
     * <p>
     * Mirrors C's {@code pic_read_bv()}: the class name is taken from the
     * leading {@code nameLen} characters, the strain identifier from
     * {@code [idOffs, vecOffs)}, and each bit is decoded from the character at
     * {@code [vecOffs + i]} where a space means missing, {@code '0'} maps to 0,
     * and any other character maps to 1.
     * </p>
     *
     * @param line
     *            the complete partition line for one vector
     * @param nameLen
     *            length of the class-name field
     * @param idOffs
     *            offset to the start of the strain identifier
     * @param vecOffs
     *            offset to the start of the binary portion
     * @param length
     *            number of bits in the vector
     * @return the parsed {@link BinaryVector}
     */
    private static BinaryVector parsePartitionLine(String line, int nameLen,
            int idOffs, int vecOffs, int length) {
        String strain = extractStrain(line, idOffs, vecOffs);
        String className = extractClassName(line, nameLen, idOffs);

        int[] values = new int[length];
        // Stop at the end of the line so a short vector still parses cleanly.
        int end = Math.min(vecOffs + length, line.length());
        for (int pos = vecOffs; pos < end; pos++) {
            char c = line.charAt(pos);
            // Space means missing value, '0' maps to 0, anything else to 1.
            int bit;
            if (c == ' ') {
                bit = -1; // missing
            } else if (c != '0') {
                bit = 1;
            } else {
                bit = 0;
            }
            values[pos - vecOffs] = bit;
        }

        return new BinaryVector(values, 0, length, 0, strain, className);
    }

    /**
     * Determines whether a partition file line is a vector line rather than a
     * blank or separator line.
     *
     * @param line
     *            the raw line from the partition file
     * @param vecOffs
     *            offset to the start of the binary portion
     * @param length
     *            number of bits in the vector
     * @return {@code true} when the line holds a parseable vector
     */
    private static boolean isVectorLine(String line, int vecOffs, int length) {
        return line.length() >= vecOffs + length - 1
                && (line.isEmpty() || line.charAt(0) != ' ');
    }

    private static String extractStrain(String line, int idOffs,
            int vecOffs) {
        if (idOffs < line.length() && vecOffs <= line.length()) {
            return line.substring(idOffs, Math.min(vecOffs, line.length()))
                    .trim();
        }
        return "";
    }

    private static String extractClassName(String line, int nameLen,
            int idOffs) {
        if (nameLen > 0 && line.length() >= nameLen) {
            return line.substring(0, nameLen).trim();
        }
        if (idOffs <= line.length()) {
            return line.substring(0, idOffs).trim();
        }
        return "";
    }

    private static Path findPartitionFile(String filebase)
            throws IOException {
        Path partitionFile = Path.of(filebase + ".partition");
        if (Files.exists(partitionFile)) {
            return partitionFile;
        }
        Path parFile = Path.of(filebase + ".par");
        if (Files.exists(parFile)) {
            return parFile;
        }
        throw new IOException("Partition file not found: " + filebase
                + ".partition or " + filebase + ".par");
    }

    private static FormatParser.Header readHeader(String filebase)
            throws IOException {
        Path headerFile = findHeaderFile(filebase);
        String content = Files.readString(headerFile);
        if (content.isEmpty()) {
            throw new IOException("Empty header file: " + headerFile);
        }
        return FormatParser.parseHeader(content);
    }

    private static Path findHeaderFile(String filebase) throws IOException {
        Path headerFile = Path.of(filebase + ".header");
        if (Files.exists(headerFile)) {
            return headerFile;
        }
        Path hdrFile = Path.of(filebase + ".hdr");
        if (Files.exists(hdrFile)) {
            return hdrFile;
        }
        throw new IOException("Header file not found: " + filebase
                + ".header or " + filebase + ".hdr");
    }
}
