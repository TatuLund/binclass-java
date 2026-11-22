/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.report.ReportModel.BitFrequency;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ReportModel}, focused on the per-bit frequency output.
 */
class ReportModelTest {

    /**
     * Regression test: a length-47 vector must yield 47 frequency entries, one
     * per bit position including index 0. Previously the count/percentage loops
     * started at {@code p = 1}, dropping position 0 and rendering only 46 rows.
     */
    @Test
    void totalFrequenciesIncludeBitPositionZero() {
        int length = 47;

        Partition partition = new Partition(1);
        // A vector whose every bit is set so counts are non-trivial at all
        // positions, including index 0.
        int[] el = new int[length];
        for (int p = 0; p < length; p++) {
            el[p] = 1;
        }
        BinaryVector v = new BinaryVector(el, length);
        partition.addElement(1, v);

        double[][] centroidsData = new double[1][length];
        for (int p = 0; p < length; p++) {
            centroidsData[0][p] = 1.0;
        }
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 1);

        ReportModel model = ReportModel.of(partition, centroids,
                ReportOptions.DEFAULT);

        List<BitFrequency> frequencies = model.totalFrequencies();
        assertEquals(length, frequencies.size(),
                "one entry per bit position, including index 0");

        // Positions must be contiguous starting at 0.
        for (int p = 0; p < length; p++) {
            BitFrequency f = frequencies.get(p);
            assertEquals(p, f.bit(), "bit position should match list index");
            assertEquals(1, f.count(), "single vector sets each bit once");
            assertEquals(100.0, f.percentage(), 1e-9);
        }
    }

    /**
     * Regression test for a shorter vector: length 3 must produce exactly 3
     * entries covering positions 0, 1 and 2 (not just 1 and 2).
     */
    @Test
    void totalFrequenciesCoverAllPositionsShortVector() {
        Partition partition = new Partition(1);
        int[] el = { 0, 1, 1 };
        BinaryVector v = new BinaryVector(el, 3);
        partition.addElement(1, v);

        double[][] centroidsData = { { 0.5, 0.5, 0.5 } };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 1);

        ReportModel model = ReportModel.of(partition, centroids,
                ReportOptions.DEFAULT);

        List<BitFrequency> frequencies = model.totalFrequencies();
        assertEquals(3, frequencies.size());
        assertEquals(0, frequencies.get(0).bit());
        assertEquals(1, frequencies.get(1).bit());
        assertEquals(2, frequencies.get(2).bit());
        // Position 0 is unset in this vector; positions 1 and 2 are set.
        assertEquals(0, frequencies.get(0).count());
        assertEquals(1, frequencies.get(1).count());
        assertEquals(1, frequencies.get(2).count());
        assertTrue(frequencies.get(1).percentage() > 0.0);
    }
}
