/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.io;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FormatParser} covering header parsing and format conversion.
 */
final class FormatParserTest {

    @Test
    void parseHeaderWithValidInput() {
        String header = "10 5 strain1 strain2";

        FormatParser.Header result = FormatParser.parseHeader(header);

        assertNotNull(result);
        assertEquals(10, result.getNVectors());
        assertEquals(5, result.getLength());
        assertNotNull(result.getStrains());
        assertEquals(2, result.getStrains().length);
    }

    @Test
    void parseHeaderWithSingleStrain() {
        String header = "5 3 mystrain";

        FormatParser.Header result = FormatParser.parseHeader(header);

        assertNotNull(result);
        assertEquals(5, result.getNVectors());
        assertEquals(3, result.getLength());
        assertNotNull(result.getStrains());
        assertEquals(1, result.getStrains().length);
    }

    @Test
    void parseHeaderWithNoStrain() {
        String header = "8 4";

        FormatParser.Header result = FormatParser.parseHeader(header);

        assertNotNull(result);
        assertEquals(8, result.getNVectors());
        assertEquals(4, result.getLength());
        assertNull(result.getStrains());
    }

    @Test
    void parseHeaderWithNullInput() {
        assertThrows(NullPointerException.class,
                () -> FormatParser.parseHeader(null));
    }

    @Test
    void convHexValidInput() {
        int result = FormatParser.convHex("1A");

        assertEquals(26, result); // 0x1A = 26 decimal
    }

    @Test
    void convHexZero() {
        int result = FormatParser.convHex("0");

        assertEquals(0, result);
    }

    @Test
    void convHexMaxValue() {
        int result = FormatParser.convHex("FF");

        assertEquals(255, result); // 0xFF = 255 decimal
    }

    @Test
    void formatVectorWithBinaryValues() {
        int[] el = { 1, 0, 1, 1 };

        String result = FormatParser.formatVector(el);

        assertEquals("1 0 1 1", result);
    }

    @Test
    void formatVectorWithAllZeros() {
        int[] el = { 0, 0, 0 };

        String result = FormatParser.formatVector(el);

        assertEquals("0 0 0", result);
    }

    @Test
    void parseVectorRoundTrip() {
        int[] original = { 1, 0, 1, 1, 0 };

        String formatted = FormatParser.formatVector(original);
        int[] parsed = FormatParser.parseVector(formatted);

        assertArrayEquals(original, parsed);
    }

    @Test
    void headerGetters() {
        FormatParser.Header header = new FormatParser.Header(10, 5,
                new String[] { "s1", "s2" });

        assertEquals(10, header.getNVectors());
        assertEquals(5, header.getLength());
        assertArrayEquals(new String[] { "s1", "s2" }, header.getStrains());
    }

    @Test
    void headerToString() {
        FormatParser.Header header = new FormatParser.Header(10, 5,
                new String[] { "s1" });

        String str = header.toString();

        assertTrue(str.contains("10"));
        assertTrue(str.contains("5"));
        assertTrue(str.contains("s1"));
    }

    // --- parseVector tests for continuous binary strings with whitespace ---

    @Test
    void parseVectorContinuousWithTrailingNewline() {
        // Input has 48 binary chars + trailing newline
        String input = "011000000110000110010000110111100000010110010011\n";

        int[] result = FormatParser.parseVector(input);

        assertEquals(48, result.length);
        assertEquals(0, result[0]);
        assertEquals(1, result[1]);
        assertEquals(1, result[result.length - 1]);
    }

    @Test
    void parseVectorContinuousWithTrailingSpace() {
        String input = "11000   ";

        int[] result = FormatParser.parseVector(input);

        assertArrayEquals(new int[] { 1, 1, 0, 0, 0 }, result);
    }

    @Test
    void parseVectorContinuousWithTrailingCarriageReturn() {
        String input = "1010\r";

        int[] result = FormatParser.parseVector(input);

        assertArrayEquals(new int[] { 1, 0, 1, 0 }, result);
    }

    @Test
    void parseVectorContinuousWithTrailingTab() {
        String input = "1100\t";

        int[] result = FormatParser.parseVector(input);

        assertArrayEquals(new int[] { 1, 1, 0, 0 }, result);
    }

    @Test
    void parseVectorContinuousWithMixedWhitespace() {
        String input = "1010 \t\r\n";

        int[] result = FormatParser.parseVector(input);

        assertArrayEquals(new int[] { 1, 0, 1, 0 }, result);
    }

    @Test
    void parseVectorContinuousWithInternalSpaces() {
        // Continuous string with internal spaces should still be treated as
        // continuous
        String input = "1010 1100";

        int[] result = FormatParser.parseVector(input);

        assertArrayEquals(new int[] { 1, 0, 1, 0, 1, 1, 0, 0 }, result);
    }

    @Test
    void parseVectorContinuousWithLeadingWhitespace() {
        String input = "   1100";

        int[] result = FormatParser.parseVector(input);

        assertArrayEquals(new int[] { 1, 1, 0, 0 }, result);
    }

    @Test
    void parseVectorContinuousEmpty() {
        String input = "   ";

        int[] result = FormatParser.parseVector(input);

        assertEquals(0, result.length);
    }

    @Test
    void parseVectorSpaceSeparatedWithTrailingNewline() {
        String input = "1 0 1 0\n";

        int[] result = FormatParser.parseVector(input);

        assertArrayEquals(new int[] { 1, 0, 1, 0 }, result);
    }

    @Test
    void parseVectorInvalidCharacterThrows() {
        String input = "10a0";

        assertThrows(IllegalArgumentException.class,
                () -> FormatParser.parseVector(input));
    }

    // --- parseHeader tests for key=value format ---

    @Test
    void parseHeaderKeyValueFormat() {
        String header = "vecoffs=23\nveclen=47\nidlen=7\nidoffs=15";

        FormatParser.Header result = FormatParser.parseHeader(header);

        assertEquals(-1, result.getNVectors());
        assertEquals(47, result.getLength());
        assertEquals(23, result.getVecOffs());
    }

    @Test
    void parseHeaderKeyValueFormatWithNVectors() {
        String header = "n_vectors=50\nveclen=100\nvecoffs=0";

        FormatParser.Header result = FormatParser.parseHeader(header);

        assertEquals(50, result.getNVectors());
        assertEquals(100, result.getLength());
        assertEquals(0, result.getVecOffs());
    }

    @Test
    void parseHeaderKeyValueFormatWithStrains() {
        String header = "vecoffs=23\nveclen=47\nidlen=7\nidoffs=15\nnamelen=9";

        FormatParser.Header result = FormatParser.parseHeader(header);

        assertEquals(-1, result.getNVectors());
        assertEquals(47, result.getLength());
    }

    @Test
    void parseHeaderMixedFormats() {
        // Header with both simple and key=value fields - currently not
        // supported
        String header = "50 47\nvecoffs=23";

        assertThrows(IllegalArgumentException.class,
                () -> FormatParser.parseHeader(header));
    }

    @Test
    void parseHeaderKeyValueMissingVeclenThrows() {
        String header = "vecoffs=23\nidlen=7";

        assertThrows(IllegalArgumentException.class,
                () -> FormatParser.parseHeader(header));
    }

    // --- Header constructor tests ---

    @Test
    void headerConstructorWithVecOffs() {
        FormatParser.Header header = new FormatParser.Header(10, 5,
                null, 23);

        assertEquals(10, header.getNVectors());
        assertEquals(5, header.getLength());
        assertNull(header.getStrains());
        assertEquals(23, header.getVecOffs());
    }

    @Test
    void headerConstructorDefaultVecOffs() {
        FormatParser.Header header = new FormatParser.Header(10, 5,
                null);

        assertEquals(0, header.getVecOffs());
    }
}
