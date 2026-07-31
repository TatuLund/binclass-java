package org.binclass.algorithms.dist;

import java.util.Arrays;
import org.binclass.algorithms.core.*;

public class DebugPartitionSize {
    public static void main(String[] args) {
        // Test 1: identical vectors - all should go to cluster 0 (all-ones
        // centroid)
        VectorSet v1 = new VectorSet();
        for (int i = 0; i < 3; i++) {
            v1.addElement(new BinaryVector(new int[] { 1, 1, 1 }, 3));
        }

        InfiniteCentroids c1 = new InfiniteCentroids(2, 3);
        Centroid centroid0 = c1.get(0);
        for (int i = 0; i < 3; i++)
            centroid0.set(i, 1.0);
        Centroid centroid1 = c1.get(1);
        for (int i = 0; i < 3; i++)
            centroid1.set(i, 0.0);

        Partition p1 = new Partition(2);
        NearestNeighbor.fastNearestNeighbor(v1, p1, c1);

        System.out.println("Test 1 - Identical vectors:");
        System.out.println("  partition.size() (cluster count): " + p1.size());
        for (int i = 1; i <= p1.size(); i++) {
            VectorSet cluster = p1.getElements(i);
            int totalElements = 0;
            for (BinaryVector bv : cluster)
                totalElements++;
            System.out.println(
                    "  Cluster " + i + ": " + cluster.size() + " elements");
        }

        // Test 2: different vectors - should go to separate clusters
        VectorSet v2 = new VectorSet();
        v2.addElement(new BinaryVector(new int[] { 1, 0, 1 }));
        v2.addElement(new BinaryVector(new int[] { 0, 1, 0 }));

        InfiniteCentroids c2 = new InfiniteCentroids(2);
        Centroid centroidA = c2.get(0);
        double[] probsA = { 0.9, 0.1, 0.9 };
        for (int i = 0; i < 3; i++)
            centroidA.set(i, probsA[i]);
        Centroid centroidB = c2.get(1);
        double[] probsB = { 0.1, 0.9, 0.1 };
        for (int i = 0; i < 3; i++)
            centroidB.set(i, probsB[i]);

        Partition p2 = new Partition(2);
        NearestNeighbor.fastNearestNeighbor(v2, p2, c2);

        System.out.println("\nTest 2 - Different vectors:");
        System.out.println("  partition.size() (cluster count): " + p2.size());
        for (int i = 1; i <= p2.size(); i++) {
            VectorSet cluster = p2.getElements(i);
            int totalElements = 0;
            for (BinaryVector bv : cluster)
                totalElements++;
            System.out.println(
                    "  Cluster " + i + ": " + cluster.size() + " elements");
        }

        // Test 3: single centroid - all vectors go to same cluster
        VectorSet v3 = new VectorSet();
        v3.addElement(new BinaryVector(new int[] { 1, 0 }));
        v3.addElement(new BinaryVector(new int[] { 0, 1 }));

        InfiniteCentroids c3 = new InfiniteCentroids(1);
        Centroid centroidC = c3.get(0);
        for (int i = 0; i < 2; i++)
            centroidC.set(i, 0.5);

        Partition p3 = new Partition(1);
        NearestNeighbor.fastNearestNeighbor(v3, p3, c3);

        System.out.println("\nTest 3 - Single centroid:");
        System.out.println("  partition.size() (cluster count): " + p3.size());
        for (int i = 1; i <= p3.size(); i++) {
            VectorSet cluster = p3.getElements(i);
            int totalElements = 0;
            for (BinaryVector bv : cluster)
                totalElements++;
            System.out.println(
                    "  Cluster " + i + ": " + cluster.size() + " elements");
        }

        // Test 4: check if partition has a method to get total element count
        System.out.println("\nPartition methods available:");
        for (java.lang.reflect.Method m : Partition.class
                .getDeclaredMethods()) {
            System.out.println("  " + m.getName() + "("
                    + java.util.Arrays.toString(m.getParameterTypes()) + ") -> "
                    + m.getReturnType().getSimpleName());
        }
    }
}
