package org.binclass.algorithms.dist;

import java.util.*;
import org.binclass.algorithms.core.*;

public class DebugVectorSetTest {
    public static void main(String[] args) {
        // Test 1: identical vectors - should all be added (IdentityHashMap uses
        // reference equality)
        VectorSet vs = new VectorSet();
        BinaryVector v1 = new BinaryVector(new int[] { 1, 1, 1 }, 3);
        BinaryVector v2 = new BinaryVector(new int[] { 1, 1, 1 }, 3);
        BinaryVector v3 = new BinaryVector(new int[] { 1, 1, 1 }, 3);

        System.out.println("v1 == v2: " + (v1 == v2)); // false - different
                                                       // objects
        vs.addElement(v1);
        vs.addElement(v2);
        vs.addElement(v3);
        System.out.println("VectorSet size after adding 3 identical vectors: "
                + vs.size());

        int count = 0;
        for (BinaryVector bv : vs) {
            count++;
            System.out.println(
                    "  Vector " + count + ": " + Arrays.toString(bv.getEl()));
        }
        System.out.println("Iteration count: " + count);

        // Test 2: different vectors - should all be added
        VectorSet vs2 = new VectorSet();
        BinaryVector d1 = new BinaryVector(new int[] { 1, 0, 1 });
        BinaryVector d2 = new BinaryVector(new int[] { 0, 1, 0 });
        vs2.addElement(d1);
        vs2.addElement(d2);
        System.out.println("\nDifferent vectors size: " + vs2.size());

        // Test 3: same object added twice - should be 1 (IdentityHashMap)
        VectorSet vs3 = new VectorSet();
        BinaryVector v4 = new BinaryVector(new int[] { 1, 0 });
        vs3.addElement(v4);
        vs3.addElement(v4); // same reference
        System.out.println("Same object twice size: " + vs3.size());
    }
}
