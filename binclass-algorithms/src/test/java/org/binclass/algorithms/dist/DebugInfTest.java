package org.binclass.algorithms.dist;

import static org.junit.jupiter.api.Assertions.*;
import org.binclass.algorithms.core.*;
import org.junit.jupiter.api.Test;

public class DebugInfTest {
    @Test
    public void debug() {
        VectorSet vectors = new VectorSet();
        BinaryVector v1 = new BinaryVector(new int[] { 1, 0, 1 });
        BinaryVector v2 = new BinaryVector(new int[] { 0, 1, 0 });
        vectors.addElement(v1);
        vectors.addElement(v2);

        InfiniteCentroids centroids = new InfiniteCentroids(2);
        Centroid c0 = centroids.get(0);
        double[] probs0 = { 0.9, 0.1, 0.9 };
        for (int i = 0; i < 3; i++)
            c0.set(i, probs0[i]);

        Centroid c1 = centroids.get(1);
        double[] probs1 = { 0.1, 0.9, 0.1 };
        for (int i = 0; i < 3; i++)
            c1.set(i, probs1[i]);

        System.out.println("=== Code Lengths ===");
        System.out.printf("v1 vs c0: %.6f%n",
                DistanceCalculator.codeLength(v1, c0));
        System.out.printf("v1 vs c1: %.6f%n",
                DistanceCalculator.codeLength(v1, c1));
        System.out.printf("v2 vs c0: %.6f%n",
                DistanceCalculator.codeLength(v2, c0));
        System.out.printf("v2 vs c1: %.6f%n",
                DistanceCalculator.codeLength(v2, c1));

        Partition partition = new Partition(2);
        NearestNeighbor.infNearestNeighbor(vectors, partition, centroids,
                false);

        System.out.println("\n=== Assignments ===");
        int j = 0;
        for (BinaryVector v : vectors) {
            int cluster = -1;
            for (int c = 1; c <= partition.size(); c++) {
                if (partition.contains(c, v)) {
                    cluster = c;
                    break;
                }
            }
            System.out.println("v" + (j + 1) + " -> cluster " + cluster);
            j++;
        }

        // Check contains results for both clusters
        System.out.println("\n=== Contains checks ===");
        System.out.println(
                "partition.contains(0, v1): " + partition.contains(0, v1));
        System.out.println(
                "partition.contains(1, v1): " + partition.contains(1, v1));
        System.out.println(
                "partition.contains(0, v2): " + partition.contains(0, v2));
        System.out.println(
                "partition.contains(1, v2): " + partition.contains(1, v2));

        // Check cluster sizes
        for (int c = 1; c <= partition.size(); c++) {
            System.out.println("cluster " + c + ": size="
                    + partition.getElements(c).size());
        }

        // Now test with weights
        Partition partitionW = new Partition(2);
        NearestNeighbor.infNearestNeighbor(vectors, partitionW, centroids,
                true);
        System.out.println("\n=== With Weights ===");
        j = 0;
        for (BinaryVector v : vectors) {
            int cluster = -1;
            for (int c = 1; c <= partitionW.size(); c++) {
                if (partitionW.contains(c, v)) {
                    cluster = c;
                    break;
                }
            }
            System.out.println("v" + (j + 1) + " -> cluster " + cluster);
            j++;
        }

        // Check weighted code lengths
        System.out.println("\n=== Weighted Code Lengths ===");
        double w0 = DistanceCalculator.codeLength2(v1, c0);
        double w1 = DistanceCalculator.codeLength2(v1, c1);
        System.out.printf("v1 vs c0 (weighted): %.6f%n", w0);
        System.out.printf("v1 vs c1 (weighted): %.6f%n", w1);

        // Check centroid weights
        System.out.println("\n=== Centroid Weights ===");
        for (int i = 0; i < centroids.size(); i++) {
            System.out.println("centroid " + i + ": weight="
                    + centroids.get(i).getWeight());
        }
    }
}
