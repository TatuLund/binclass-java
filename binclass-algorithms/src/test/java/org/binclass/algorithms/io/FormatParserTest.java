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
}
