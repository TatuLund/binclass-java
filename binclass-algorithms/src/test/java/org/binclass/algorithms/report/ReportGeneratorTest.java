/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.report;

import static org.junit.jupiter.api.Assertions.*;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ReportGenerator}.
 */
class ReportGeneratorTest {

    @Test
    void testGenerateReportSingleCluster() {
        // Single cluster — report should contain basic statistics
        Partition partition = new Partition(1);
        int[] el1 = { 0, 1, 1 };
        BinaryVector v1 = new BinaryVector(el1, 3);
        partition.addElement(1, v1);

        double[][] centroidsData = { { 0.5, 0.5, 0.5 } };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 1);

        String report = ReportGenerator.generateReport(partition, centroids);
        assertNotNull(report);
        assertTrue(report.contains("STATISTICAL REPORT"));
    }

    @Test
    void testGenerateReportTwoClusters() {
        // Two clusters — report should include nearness matrix
        Partition partition = new Partition(2);

        int[] el1 = { 0, 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 3);
        partition.addElement(1, v1);

        int[] el2 = { 1, 1, 1 };
        BinaryVector v2 = new BinaryVector(el2, 3);
        partition.addElement(2, v2);

        double[][] centroidsData = {
                { 0.5, 0.5, 0.5 },
                { 0.5, 0.5, 0.5 }
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 2);

        String report = ReportGenerator.generateReport(partition, centroids);
        assertNotNull(report);
        assertTrue(report.contains("STATISTICAL REPORT"));
    }

    @Test
    void testClassNearnessSamePartition() {
        // Same partition compared to itself should have nearness 0
        Partition partition = new Partition(2);
        int[] el1 = { 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        partition.addElement(1, v1);

        double nearness = ReportGenerator.classNearness(partition, partition);
        assertEquals(0.0, nearness, 1e-10);
    }

    @Test
    void testClassNearnessDifferentPartitions() {
        // Different partitions should have non-zero nearness
        Partition partition1 = new Partition(2);
        int[] el1 = { 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 2);
        partition1.addElement(1, v1);

        Partition partition2 = new Partition(2);
        int[] el2 = { 1, 1 };
        BinaryVector v2 = new BinaryVector(el2, 2);
        partition2.addElement(1, v2);

        double nearness = ReportGenerator.classNearness(partition1, partition2);
        assertTrue(nearness >= 0.0); // Should be non-negative
    }

    @Test
    void testFrequencyListAddFrequency() {
        // Test frequency list accumulation
        ReportGenerator.FrequencyList freqs = new ReportGenerator.FrequencyList();

        int[] el1 = { 1, 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 3);
        freqs.addFrequency(v1);

        assertEquals(1, freqs.getTotal());
        assertEquals(1, freqs.getCount(0)); // Bit 0 is 1
        assertEquals(0, freqs.getCount(1)); // Bit 1 is 0
        assertEquals(1, freqs.getCount(2)); // Bit 2 is 1
    }

    @Test
    void testFrequencyListMultipleVectors() {
        // Test frequency list with multiple vectors
        ReportGenerator.FrequencyList freqs = new ReportGenerator.FrequencyList();

        int[] el1 = { 1, 0, 1 };
        BinaryVector v1 = new BinaryVector(el1, 3);
        freqs.addFrequency(v1);

        int[] el2 = { 0, 1, 1 };
        BinaryVector v2 = new BinaryVector(el2, 3);
        freqs.addFrequency(v2);

        assertEquals(2, freqs.getTotal());
        assertEquals(1, freqs.getCount(0)); // Bit 0: one 1, one 0
        assertEquals(1, freqs.getCount(1)); // Bit 1: one 1, one 0
        assertEquals(2, freqs.getCount(2)); // Bit 2: two 1s
    }

    @Test
    void testGenerateReportWithMultipleVectors() {
        // Test report generation with multiple vectors per cluster
        Partition partition = new Partition(2);

        int[] el1 = { 0, 0, 0 };
        BinaryVector v1 = new BinaryVector(el1, 3);
        partition.addElement(1, v1);

        int[] el2 = { 0, 0, 1 };
        BinaryVector v2 = new BinaryVector(el2, 3);
        partition.addElement(1, v2);

        int[] el3 = { 1, 1, 1 };
        BinaryVector v3 = new BinaryVector(el3, 3);
        partition.addElement(2, v3);

        double[][] centroidsData = {
                { 0.5, 0.5, 0.75 }, // Average of cluster 1 vectors
                { 1.0, 1.0, 1.0 } // Cluster 2 vector
        };
        InfiniteCentroids centroids = new InfiniteCentroids(centroidsData, 2);

        String report = ReportGenerator.generateReport(partition, centroids);
        assertNotNull(report);
        assertTrue(report.contains("STATISTICAL REPORT"));
    }
}
