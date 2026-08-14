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
        Path hdrFile = Path.of(filebase + ".hdr");
        Path datFile = Path.of(filebase + ".dat");

        // Read header to get metadata
        String headerLine = Files.readString(hdrFile).lines().findFirst()
                .orElseThrow(() -> new IOException("Empty header file: "
                        + hdrFile));

        FormatParser.Header header = FormatParser.parseHeader(headerLine);
        int nVectors = header.getNVectors();
        int length = header.getLength();

        // Read vectors from data file
        List<String> lines = Files.readAllLines(datFile);
        VectorSet vectorSet = new VectorSet(nVectors);

        for (int i = 0; i < Math.min(lines.size(), nVectors); i++) {
            String line = lines.get(i).trim();
            if (!line.isEmpty()) {
                int[] values = FormatParser.parseVector(line);
                BinaryVector bv = new BinaryVector(values, 0, length, 0, null);
                vectorSet.addElement(bv);
            }
        }

        return vectorSet;
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
        Path hdrFile = Path.of(filebase + ".hdr");
        Path datFile = Path.of(filebase + ".dat");

        // Read header to get metadata and strains
        String headerLine = Files.readString(hdrFile).lines().findFirst()
                .orElseThrow(() -> new IOException("Empty header file: "
                        + hdrFile));

        FormatParser.Header header = FormatParser.parseHeader(headerLine);
        int nVectors = header.getNVectors();
        int length = header.getLength();
        String[] strains = header.getStrains();

        // Read vectors from data file
        List<String> lines = Files.readAllLines(datFile);
        VectorSet vectorSet = new VectorSet(nVectors);

        for (int i = 0; i < Math.min(lines.size(), nVectors); i++) {
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
}