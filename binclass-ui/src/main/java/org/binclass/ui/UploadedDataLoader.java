/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui;

import java.util.List;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.io.FormatParser;
import org.binclass.algorithms.util.MathUtils;

/**
 * Reusable in-memory loader that turns uploaded {@code .hdr} / {@code .dat}
 * content into a {@link VectorSet}, reusing the format readers already shipped
 * in {@code binclass-algorithms}. The UI uses an {@code Upload} component
 * rather than CLI's filebase-on-disk model, so vectors are parsed straight from
 * the buffered upload bytes instead of being written to temp files first.
 * <p>
 * This mirrors the logic of the CLI {@code DataLoader} but operates on
 * in-memory strings, keeping it reusable across every GLA-style display (GLA
 * Search, Local Search, Bootstrap) without depending on {@code binclass-cli}.
 * </p>
 */
@NullMarked
public final class UploadedDataLoader {

    private UploadedDataLoader() {
        // Utility class — prevent instantiation.
    }

    /**
     * Builds a {@link VectorSet} from uploaded header and data content.
     * <p>
     * Mirrors {@code DataLoader.loadVectors}: parses the header with
     * {@link FormatParser}, reads each non-empty data line into a
     * {@link BinaryVector}, and pre-builds the log2-factorial lookup table once
     * so stochastic-complexity math is ready for the search.
     * </p>
     *
     * @param headerContent
     *            the raw content of the uploaded {@code .hdr} file
     * @param dataLines
     *            the raw lines of the uploaded {@code .dat} file; may be empty
     *            but must not be {@code null}
     * @return a {@link VectorSet} containing every parsed vector, or an empty
     *         set when no usable line exists
     */
    public static VectorSet load(String headerContent, List<String> dataLines) {
        // A blank header (e.g. a bare .dat upload without a matching .hdr) is
        // tolerated by falling back to the common default offsets so the loader
        // still parses the binary payload from the data lines.
        FormatParser.Header header = headerContent == null
                || headerContent.isBlank()
                        ? new FormatParser.Header(-1, -1, null, 23, 15, 0)
                        : FormatParser.parseHeader(headerContent);
        int length = header.getLength();

        VectorSet vectorSet = new VectorSet(Math.max(1, dataLines.size()));
        for (String line : dataLines) {
            if (!line.isEmpty()) {
                BinaryVector bv = parseDataLine(line, header, length);
                if (bv != null) {
                    vectorSet.addElement(bv);
                }
            }
        }

        // Pre-build the log2-factorial lookup table once, sized to cover every
        // stochastic-complexity index (cluster sizes / bit frequencies up to
        // ~n).
        // Mirrors C read_set(): prepare_log2_factorials((n+n)) in binset.c.
        int n = vectorSet.size();
        if (n > 0) {
            MathUtils.prepareLog2Factorials(2 * n);
        }

        return vectorSet;
    }

    /**
     * Parses a single non-empty data line into a {@link BinaryVector}.
     * <p>
     * The binary portion is extracted starting at {@code vecoffs}, trailing
     * non-binary characters are stripped, and the result is padded or truncated
     * to the expected {@code length}. Returns {@code null} when no usable
     * binary string can be recovered from the line.
     * </p>
     *
     * @param line
     *            the complete line from the data file
     * @param header
     *            parsed format header with idoffs/nameLen/vecoffs positions
     * @param length
     *            expected binary vector length
     * @return a {@link BinaryVector}, or {@code null} if the line has no data
     */
    static BinaryVector parseDataLine(String line, FormatParser.Header header,
            int length) {
        int startOffset = header.getVecOffs() > 0 ? header.getVecOffs() : 0;
        String binaryStr = (startOffset < line.length())
                ? line.substring(startOffset)
                : "";

        if (binaryStr.isEmpty()) {
            return null;
        }

        int endIdx = binaryStr.length();
        while (endIdx > 0 && !Character.isDigit(binaryStr.charAt(endIdx - 1))) {
            endIdx--;
        }
        binaryStr = binaryStr.substring(0, endIdx);

        // Only pad or truncate when the header declared a real vector length.
        // A blank header leaves {@code length} at its default (-1), meaning
        // "unknown", so the parsed payload is used as-is.
        if (length > 0) {
            if (binaryStr.length() < length) {
                StringBuilder padded = new StringBuilder(length);
                for (int p = 0; p < length - binaryStr.length(); p++) {
                    padded.append('0');
                }
                padded.append(binaryStr);
                binaryStr = padded.toString();
            } else if (binaryStr.length() > length) {
                binaryStr = binaryStr.substring(0, length);
            }
        }

        int[] values = FormatParser.parseVector(binaryStr);
        String strain = extractStrain(line, header);
        String className = extractClassName(line, header);

        return new BinaryVector(values, 0, length, 0,
                strain != null ? strain : "", className);
    }

    /**
     * Extracts the strain identifier from a data line.
     *
     * @param line
     *            the complete line from the data file
     * @param header
     *            parsed format header with idoffs and vecoffs positions
     * @return the strain identifier string, or empty string if not found
     */
    private static String extractStrain(String line,
            FormatParser.Header header) {
        int idoffs = header.getIdOffs() > 0 ? header.getIdOffs() : 15;
        int vecoffs = header.getVecOffs() > 0 ? header.getVecOffs() : 23;

        if (idoffs < line.length() && vecoffs <= line.length()) {
            return line.substring(idoffs, Math.min(vecoffs, line.length()))
                    .trim();
        }
        return "";
    }

    /**
     * Extracts the class-name string from a data line.
     * <p>
     * Mirrors C {@code pic_write_bv()} / {@code bv_set_name()}, which stores
     * the first {@code name_len} characters of each input line as the class
     * name (e.g. {@code "BUDV AQUA"}). When {@code nameLen} is unset, falls
     * back to trimming everything before the ID offset.
     * </p>
     *
     * @param line
     *            the complete line from the data file
     * @param header
     *            parsed format header with idoffs and nameLen positions
     * @return the class-name string, or empty string if not found
     */
    private static String extractClassName(String line,
            FormatParser.Header header) {
        int nameLen = header.getNameLen() > 0 ? header.getNameLen() : 0;
        int idOffs = header.getIdOffs() > 0 ? header.getIdOffs() : 15;

        if (nameLen > 0 && line.length() >= nameLen) {
            return line.substring(0, nameLen);
        }
        if (idOffs <= line.length()) {
            return line.substring(0, idOffs).trim();
        }
        return "";
    }

    /**
     * Convenience overload that parses a header plus space-or-continuous binary
     * data lines and returns the resulting set. Kept small so callers can build
     * it inline from an upload buffer without threading a {@code Header}
     * around.
     *
     * @param headerContent
     *            the raw content of the uploaded {@code .hdr} file
     * @param dataLines
     *            the raw lines of the uploaded {@code .dat} file
     * @return the parsed {@link VectorSet}, never {@code null}
     */
    public static VectorSet loadVectors(@Nullable String headerContent,
            List<String> dataLines) {
        return load(headerContent, dataLines);
    }
}
