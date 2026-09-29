/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Partition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads partition files into {@link Partition} instances.
 * <p>
 * Mirrors C's {@code read_partition()} from {@code binset.c}: the first pass
 * counts {@code Class N} headers to determine the cluster count (a
 * {@code Class (Trash)} header resets the counter), then each subsequent vector
 * line is parsed at the offsets taken from the dataset's header file and added
 * to the current cluster. The partition uses 1-based cluster indices, matching
 * the C {@code Partition} struct.
 * </p>
 */
public final class PartitionReader {

    private static final Logger logger = LoggerFactory
            .getLogger(PartitionReader.class);

    /** Default offset to the start of the strain identifier. */
    private static final int DEFAULT_ID_OFFS = 15;
    /** Default offset to the start of the binary portion. */
    private static final int DEFAULT_VEC_OFFS = 23;
    /** Default length of the class-name field. */
    private static final int DEFAULT_NAME_LEN = 9;

    private PartitionReader() {
        // Utility class — prevent instantiation
    }

    /**
     * Reads a partition file that groups vectors by class.
     * <p>
     * Combines {@code read_partition()} from {@code binset.c} with the vector
     * parsing of {@code pic_read_bv()} from {@code binstuff.c}. The first pass
     * counts class headers to determine the cluster count; each subsequent line
     * is parsed at the offsets taken from the dataset's header file.
     * </p>
     *
     * @param filebase
     *            the base name of the data files (without extension)
     * @return a populated partition with one cluster per class header
     * @throws IOException
     *             if an I/O error occurs or no classes are found
     */
    public static Partition readPartition(String filebase) throws IOException {
        return readPartition(filebase, ".partition");
    }

    /**
     * Reads a partition file with an explicit suffix.
     * <p>
     * Used by the compare command to read {@code .partition1} /
     * {@code .partition2} files derived from a shared filebase. The header is
     * always taken from {@code <filebase>.header}.
     * </p>
     *
     * @param filebase
     *            the base name of the data files (without extension)
     * @param partitionSuffix
     *            the suffix appended to the filebase to locate the partition
     *            file (e.g. {@code ".partition1"})
     * @return a populated partition with one cluster per class header
     * @throws IOException
     *             if an I/O error occurs or no classes are found
     */
    public static Partition readPartition(String filebase,
            String partitionSuffix) throws IOException {
        FormatParser.Header header = readHeader(filebase);
        Path partitionFile = Path.of(filebase + partitionSuffix);
        if (!Files.exists(partitionFile)) {
            throw new IOException("Partition file not found: " + partitionFile);
        }

        List<String> lines = Files.readAllLines(partitionFile);
        return buildPartition(lines, header);
    }

    /**
     * Builds a partition from already-read lines and parsed header.
     * <p>
     * The first pass counts class headers to determine the cluster count; each
     * subsequent line is parsed at the offsets taken from the header file and
     * added to the current cluster.
     * </p>
     *
     * @param lines
     *            the partition file lines
     * @param header
     *            parsed format header with the vector offsets
     * @return a populated partition with one cluster per class header
     */
    private static Partition buildPartition(List<String> lines,
            FormatParser.Header header) {
        int vecOffs = header.getVecOffs() > 0 ? header.getVecOffs()
                : DEFAULT_VEC_OFFS;
        int idOffs = header.getIdOffs() > 0 ? header.getIdOffs()
                : DEFAULT_ID_OFFS;
        int nameLen = header.getNameLen() > 0 ? header.getNameLen()
                : DEFAULT_NAME_LEN;
        int length = header.getLength();

        // First pass: count class headers to determine the cluster count.
        int k = 0;
        for (String line : lines) {
            if (line.startsWith("Class ")) {
                k++;
            }
        }
        if (k == 0) {
            throw new IllegalArgumentException(
                    "No classes in partition content");
        }

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

        logger.debug("Read partition with {} clusters", k);
        return partition;
    }

    /**
     * Reads a partition directly from file content. Useful for tests and
     * callers that already hold the text.
     *
     * @param content
     *            the full contents of a partition file
     * @param vecOffs
     *            offset to the start of the binary portion
     * @param idOffs
     *            offset to the start of the strain identifier
     * @param nameLen
     *            length of the class-name field
     * @param length
     *            number of bits in each vector
     * @return a populated partition with one cluster per class header
     */
    public static Partition readPartition(String content, int vecOffs,
            int idOffs, int nameLen, int length) {
        List<String> lines = new ArrayList<>(List.of(content.split("\\R")));

        FormatParser.Header header = new FormatParser.Header(0, length, null,
                vecOffs, idOffs, nameLen);
        return buildPartition(lines, header);
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
     *            number of bits in each vector
     * @return {@code true} when the line holds a parseable vector
     */
    public static boolean isVectorLine(String line, int vecOffs, int length) {
        return line.length() >= vecOffs + length - 1
                && (line.isEmpty() || line.charAt(0) != ' ');
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
     *            number of bits in each vector
     * @return the parsed {@link BinaryVector}
     */
    public static BinaryVector parsePartitionLine(String line, int nameLen,
            int idOffs, int vecOffs, int length) {
        String strain = extractStrain(line, idOffs, vecOffs);
        String className = extractClassName(line, nameLen, idOffs);

        int[] values = new int[length];
        int end = Math.min(vecOffs + length, line.length());
        for (int pos = vecOffs; pos < end; pos++) {
            char c = line.charAt(pos);
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

    private static String extractStrain(String line, int idOffs, int vecOffs) {
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