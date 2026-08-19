/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.io;

import java.util.Objects;

/**
 * Parses binary vector format strings and header files for data import.
 * <p>
 * Mirrors functions from {@code format.c} in the original C codebase: handles
 * parsing of header file formats, conversion between hex and decimal
 * representations, and string manipulation utilities specific to the BinClass
 * data format.
 * </p>
 * <p>
 * Key operations:
 * <ul>
 * <li>{@link #parseHeader(String)} — parses a header line from a .hdr file</li>
 * <li>{@link #convHex(String, int)} — converts hex string to decimal
 * integer</li>
 * <li>{@link #formatVector(int[])} — formats binary vector as space-separated
 * string</li>
 * </ul>
 * </p>
 */
public final class FormatParser {

    private static final String HEADER_MUST_NOT_BE_NULL = "Header string must not be null";
    private static final String HEX_STRING_MUST_NOT_BE_NULL = "Hex string must not be null";

    /** Default vector separator character */
    private static final char VECTOR_SEPARATOR = ' ';

    private FormatParser() {
        // Utility class — prevent instantiation
    }

    /**
     * Parses a header line from a .hdr file.
     * <p>
     * Supports two formats:
     * <ol>
     * <li><b>Simple format:</b> "n_vectors n_length [strain1 strain2 ...]"</li>
     * <li><b>Key-value format:</b> Multiple lines with key=value pairs
     * (vecoffs, veclen, idlen, etc.)</li>
     * </ol>
     * </p>
     *
     * @param headerContent
     *            the content of a .hdr file (may contain multiple lines)
     * @return a Header object containing parsed metadata fields
     */
    public static Header parseHeader(String headerContent) {
        Objects.requireNonNull(headerContent, HEADER_MUST_NOT_BE_NULL);

        String trimmed = headerContent.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(
                    "Header content cannot be empty");
        }

        // Check if this is key=value format or simple format
        boolean hasEquals = trimmed.contains("=");

        if (hasEquals) {
            return parseKeyValueFormat(trimmed);
        } else {
            return parseSimpleFormat(trimmed);
        }
    }

    /**
     * Parses header in key=value format (e.g., "vecoffs=23\nveclen=47").
     */
    private static Header parseKeyValueFormat(String content) {
        int nVectors = -1;
        int length = -1;
        int vecOffs = 0;
        int idOffs = 15; // Default ID offset

        String[] lines = content.split("\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty())
                continue;

            String[] parts = line.split("=", 2);
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();

                switch (key) {
                case "n_vectors" -> nVectors = Integer.parseInt(value);
                case "veclen", "length" -> {
                    int parsedLen = Integer.parseInt(value);
                    length = parsedLen;
                }
                case "vecoffs" -> {
                    int parsedVecOffs = Integer.parseInt(value);
                    vecOffs = parsedVecOffs;
                }
                case "idoffs" -> {
                    idOffs = Integer.parseInt(value);
                }
                default -> {
                    /* ignore other keys */ }
                }
            }
        }

        if (length < 0) {
            throw new IllegalArgumentException(
                    "Key-value header must contain veclen/length field");
        }

        // If n_vectors not in header, use -1 to indicate it should be
        // determined from data file
        return new Header(nVectors, length, null, vecOffs, idOffs);
    }

    /**
     * Parses header in simple format (e.g., "100 50 strain1 strain2").
     */
    private static Header parseSimpleFormat(String headerLine) {
        String[] parts = headerLine.split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException(
                    "Header must contain at least vector count and length, got: '"
                            + headerLine + "'");
        }

        int nVectors;
        try {
            nVectors = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid vector count in header: " + parts[0], e);
        }

        int length;
        try {
            length = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid length in header: " + parts[1], e);
        }

        // Extract optional strain identifiers
        String[] strains = null;
        if (parts.length > 2) {
            strains = new String[parts.length - 2];
            System.arraycopy(parts, 2, strains, 0, parts.length - 2);
        }

        return new Header(nVectors, length, strains);
    }

    /**
     * Converts a hexadecimal string to decimal integer.
     * <p>
     * Equivalent to C function {@code conv_hex()} from {@code format.c}. Parses
     * a hex string representation and returns its decimal equivalent. Handles
     * both uppercase and lowercase hex digits (0-9, A-F/a-f).
     * </p>
     * <p>
     * Algorithm:
     * <ol>
     * <li>For each character in the hex string</li>
     * <li>Convert to numeric value (0-15)</li>
     * <li>Add to running total multiplied by appropriate power of 16</li>
     * </ol>
     * </p>
     *
     * @param hexString
     *            the hexadecimal string to convert (e.g., "FF", "1a3")
     * @return the decimal integer value represented by the hex string
     */
    public static int convHex(String hexString) {
        Objects.requireNonNull(hexString, HEX_STRING_MUST_NOT_BE_NULL);

        if (hexString.isEmpty()) {
            return 0;
        }

        // Remove optional "0x" or "0X" prefix
        String clean = hexString;
        if (clean.startsWith("0x") || clean.startsWith("0X")) {
            clean = clean.substring(2);
        }

        int result = 0;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            int digitValue;

            if (c >= '0' && c <= '9') {
                digitValue = c - '0';
            } else if (c >= 'a' && c <= 'f') {
                digitValue = c - 'a' + 10;
            } else if (c >= 'A' && c <= 'F') {
                digitValue = c - 'A' + 10;
            } else {
                throw new IllegalArgumentException("Invalid hex character: '"
                        + c + "' in string: " + hexString);
            }

            result = result * 16 + digitValue;
        }

        return result;
    }

    /**
     * Formats a binary vector as a space-separated string.
     * <p>
     * Equivalent to C function {@code format_vector()} from {@code format.c}.
     * Converts an integer array of 0s and 1s into a human-readable string
     * representation suitable for display or logging.
     * </p>
     * <p>
     * Example: {0, 1, 1, 0} → "0 1 1 0"
     * </p>
     *
     * @param vector
     *            the binary vector to format (array of 0s and 1s)
     * @return a space-separated string representation of the vector
     */
    public static String formatVector(int[] vector) {
        Objects.requireNonNull(vector, "Vector must not be null");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(VECTOR_SEPARATOR);
            }
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    /**
     * Parses a space-separated string of binary values into an integer array.
     * <p>
     * Helper method that converts formatted vector strings back to arrays. Used
     * in conjunction with {@link #formatVector(int[])} for round-trip
     * conversion.
     * </p>
     *
     * @param formatted
     *            the space-separated string (e.g., "0 1 1 0")
     * @return an integer array containing the parsed binary values
     */
    public static int[] parseVector(String formatted) {
        Objects.requireNonNull(formatted, "Formatted vector must not be null");

        String trimmed = formatted.trim();
        if (trimmed.isEmpty()) {
            return new int[0];
        }

        // Strip all whitespace characters (newlines, tabs, etc.) that aren't
        // separators
        String cleaned = trimmed.replaceAll("\\s+", "");

        if (cleaned.isEmpty()) {
            throw new IllegalStateException(
                    "Cleand vector should not be empty!");
        }

        // Check if it's a continuous binary string or space-separated values
        // Use cleaned (whitespace-stripped) to determine continuity - if only
        // 0s and 1s remain, treat as continuous
        boolean hasSpace = formatted.contains(" ");
        boolean isContinuous = !hasSpace || cleaned.matches("[01]+");

        int[] result;
        if (isContinuous) {
            // Parse continuous binary string like "11000" → [1, 1, 0, 0, 0]
            result = new int[cleaned.length()];
            for (int i = 0; i < cleaned.length(); i++) {
                char c = cleaned.charAt(i);
                if (c != '0' && c != '1') {
                    throw new IllegalArgumentException(
                            "Invalid binary value in formatted vector: '" + c
                                    + "' at position " + i,
                            null);
                }
                result[i] = c - '0';
            }
        } else {
            // Parse space-separated values like "0 1 1 0"
            String[] parts = trimmed.split("\\s+");
            result = new int[parts.length];

            for (int i = 0; i < parts.length; i++) {
                try {
                    result[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            "Invalid binary value in formatted vector: "
                                    + parts[i],
                            e);
                }
            }
        }

        return result;
    }

    /**
     * Represents parsed header metadata from a .hdr file.
     * <p>
     * Mirrors the C {@code Header} struct from {@code format.c}. Contains
     * essential metadata fields extracted during data file parsing: vector
     * count, length, and optional strain identifiers.
     * </p>
     */
    public static final class Header {

        private final int nVectors; // Total number of vectors in the dataset
        private final int length; // Length of each binary vector (number of
                                  // bits)
        private final String[] strains; // Optional strain identifiers
        private final int vecOffs; // Offset to start of binary portion in data
                                   // lines
        private final int idOffs; // Offset to start of ID string

        /**
         * Creates a new Header with the given metadata.
         *
         * @param nVectors
         *            total number of vectors in the dataset
         * @param length
         *            length of each binary vector (number of bits)
         * @param strains
         *            optional strain identifiers, or null if not present
         */
        public Header(int nVectors, int length, String[] strains) {
            this(nVectors, length, strains, 0);
        }

        /**
         * Creates a new Header with the given metadata including vecOffs.
         *
         * @param nVectors
         *            total number of vectors in the dataset
         * @param length
         *            length of each binary vector (number of bits)
         * @param strains
         *            optional strain identifiers, or null if not present
         * @param vecOffs
         *            offset to start of binary portion in data lines
         */
        public Header(int nVectors, int length, String[] strains, int vecOffs) {
            this(nVectors, length, strains, vecOffs, 15); // Default idOffs=15
        }

        /**
         * Creates a new Header with the given metadata including vecOffs and
         * idOffs.
         *
         * @param nVectors
         *            total number of vectors in the dataset
         * @param length
         *            length of each binary vector (number of bits)
         * @param strains
         *            optional strain identifiers, or null if not present
         * @param vecOffs
         *            offset to start of binary portion in data lines
         * @param idOffs
         *            offset to start of ID string
         */
        public Header(int nVectors, int length, String[] strains, int vecOffs,
                int idOffs) {
            this.nVectors = nVectors;
            this.length = length;
            this.strains = strains != null ? strains.clone() : null;
            this.vecOffs = vecOffs;
            this.idOffs = idOffs;
        }

        /**
         * Returns the total number of vectors in the dataset.
         *
         * @return vector count from header metadata
         */
        public int getNVectors() {
            return nVectors;
        }

        /**
         * Returns the length of each binary vector (number of bits).
         *
         * @return vector length from header metadata
         */
        public int getLength() {
            return length;
        }

        /**
         * Returns the offset to start of binary portion in data lines.
         *
         * @return vecOffs value from header metadata
         */
        public int getVecOffs() {
            return vecOffs;
        }

        /**
         * Returns the offset to start of ID string.
         *
         * @return idOffs value from header metadata
         */
        public int getIdOffs() {
            return idOffs;
        }

        /**
         * Returns the strain identifiers, if present.
         *
         * @return array of strain names, or null if not specified in header
         */
        public String[] getStrains() {
            return strains != null ? strains.clone() : null;
        }

        /**
         * Returns whether this header includes strain identifiers.
         *
         * @return true if strains were present in the original header line
         */
        public boolean hasStrains() {
            return strains != null && strains.length > 0;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Header{");
            sb.append("nVectors=").append(nVectors);
            sb.append(", length=").append(length);
            if (hasStrains()) {
                sb.append(", strains=[");
                for (int i = 0; i < strains.length; i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(strains[i]);
                }
                sb.append("]");
            }
            sb.append("}");
            return sb.toString();
        }
    }
}
