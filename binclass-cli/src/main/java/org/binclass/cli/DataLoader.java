package org.binclass.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.io.FormatParser;

/**
 * Utility class for loading binary vector data from files.
 */
public class DataLoader {

    public DataLoader() {
    }

    /**
     * Loads vectors from .hdr and .dat files.
     * 
     * @param filebase
     *            the base name of the data files (without extension)
     * @return a VectorSet containing all loaded binary vectors
     * @throws IOException
     *             if there's an error reading the files
     */
    public static VectorSet loadVectors(String filebase) throws IOException {
        Path hdrFile = findHeaderFile(filebase);
        Path datFile = findDataFile(filebase);

        // Read entire header content (supports both simple and key=value
        // formats)
        String headerContent = Files.readString(hdrFile);
        if (headerContent.isEmpty()) {
            throw new IOException("Empty header file: " + hdrFile);
        }

        FormatParser.Header header = FormatParser.parseHeader(headerContent);
        int nVectors = header.getNVectors();
        int length = header.getLength();

        // Read vectors from data file
        List<String> lines = Files.readAllLines(datFile);

        VectorSet vectorSet = new VectorSet(lines.size());

        int loadedCount = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isEmpty()) {
                // If n_vectors is specified and we've reached the limit, stop
                if (nVectors > 0 && loadedCount >= nVectors) {
                    break;
                }
                // Extract binary portion starting at vecoffs offset
                int startOffset = header.getVecOffs() > 0 ? header.getVecOffs()
                        : 0;
                String binaryStr = (startOffset < line.length())
                        ? line.substring(startOffset)
                        : "";

                if (!binaryStr.isEmpty()) {
                    // Strip trailing whitespace and non-binary characters
                    // (including newlines)
                    int endIdx = binaryStr.length();
                    while (endIdx > 0 && !Character
                            .isDigit(binaryStr.charAt(endIdx - 1))) {
                        endIdx--;
                    }
                    binaryStr = binaryStr.substring(0, endIdx);

                    // Pad with zeros if shorter than expected length
                    if (binaryStr.length() < length) {
                        StringBuilder padded = new StringBuilder();
                        for (int p = 0; p < length - binaryStr.length(); p++) {
                            padded.append('0');
                        }
                        padded.append(binaryStr);
                        binaryStr = padded.toString();
                    } else if (binaryStr.length() > length) {
                        binaryStr = binaryStr.substring(0, length);
                    }

                    int[] values = FormatParser.parseVector(binaryStr);

                    // Extract strain identifier from position idoffs to vecoffs
                    String strain = extractStrain(line, header);

                    BinaryVector bv = new BinaryVector(values, 0, length, 0,
                            strain != null ? strain : "");
                    vectorSet.addElement(bv);
                    loadedCount++;
                }
            }
        }

        return vectorSet;
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
     * Loads vectors from .hdr and .dat files with strain identifiers.
     * 
     * @param filebase
     *            the base name of the data files (without extension)
     * @return a VectorSet containing all loaded binary vectors with strains
     * @throws IOException
     *             if there's an error reading the files
     */
    public static VectorSet loadVectorsWithStrains(String filebase)
            throws IOException {
        Path hdrFile = findHeaderFile(filebase);
        Path datFile = findDataFile(filebase);

        // Read entire header content (supports both simple and key=value
        // formats)
        String headerContent = Files.readString(hdrFile);
        if (headerContent.isEmpty()) {
            throw new IOException("Empty header file: " + hdrFile);
        }

        FormatParser.Header header = FormatParser.parseHeader(headerContent);
        int nVectors = header.getNVectors();
        int length = header.getLength();
        String[] strains = header.getStrains();

        // Read vectors from data file
        List<String> lines = Files.readAllLines(datFile);

        // If n_vectors is -1, use all non-empty lines; otherwise limit to
        // n_vectors
        int count = (nVectors > 0) ? Math.min(lines.size(), nVectors)
                : lines.size();
        VectorSet vectorSet = new VectorSet(count);

        for (int i = 0; i < count; i++) {
            String line = lines.get(i).trim();
            if (!line.isEmpty()) {
                int[] values = FormatParser.parseVector(line);
                String strain = strains != null && i < strains.length
                        ? strains[i]
                        : null;
                BinaryVector bv = new BinaryVector(values, 0, length, 0,
                        strain);
                vectorSet.addElement(bv);
            }
        }

        return vectorSet;
    }

    private static Path findHeaderFile(String filebase) throws IOException {
        // Try .header first, then fall back to .hdr
        Path headerFile = Path.of(filebase + ".header");
        if (Files.exists(headerFile)) {
            return headerFile;
        }

        Path hdrFile = Path.of(filebase + ".hdr");
        if (Files.exists(hdrFile)) {
            return hdrFile;
        }

        throw new IOException(
                "Header file not found: " + filebase + ".header or "
                        + filebase + ".hdr");
    }

    private static Path findDataFile(String filebase) throws IOException {
        // Try .data first, then fall back to .dat
        Path dataFile = Path.of(filebase + ".data");
        if (Files.exists(dataFile)) {
            return dataFile;
        }

        Path datFile = Path.of(filebase + ".dat");
        if (Files.exists(datFile)) {
            return datFile;
        }

        throw new IOException("Data file not found: " + filebase + ".data or "
                + filebase + ".dat");
    }
}
