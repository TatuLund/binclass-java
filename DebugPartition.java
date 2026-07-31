import org.binclass.algorithms.core.*;
import org.binclass.algorithms.dist.NearestNeighbor;
import java.util.Arrays;

public class DebugPartition {
    public static void main(String[] args) {
        // Create a VectorSet with identical vectors [1, 1, 1]
        VectorSet vectors = new VectorSet();
        for (int i = 0; i < 3; i++) {
            BinaryVector vector = new BinaryVector(new int[] { 1, 1, 1 }, 3);
            System.out.println("Added vector " + i + ": " + Arrays.toString(vector.getEl()));
            vectors.addElement(vector);
        }

        System.out.println("\nVectorSet size: " + vectors.size());
        
        // Check iteration
        int count = 0;
        for (BinaryVector v : vectors) {
            count++;
            System.out.println("Iterated vector " + count + ": " + Arrays.toString(v.getEl()));
        }

        // Create centroids: one with all 1s, one with all 0s
        InfiniteCentroids centroids = new InfiniteCentroids(2, 3);
        Centroid centroid0 = centroids.get(0);
        for (int i = 0; i < 3; i++) {
            centroid0.set(i, 1.0);
        }

        Centroid centroid1 = centroids.get(1);
        for (int i = 0; i < 3; i++) {
            centroid1.set(i, 0.0);
        }

        Partition partition = new Partition(2);
        System.out.println("\nPartition size before: " + partition.size());
        
        NearestNeighbor.fastNearestNeighbor(vectors, partition, centroids);

        System.out.println("Partition size after (number of clusters): " + partition.size());
        
        // Check what's in each cluster
        int totalElements = 0;
        for (int i = 1; i <= partition.size(); i++) {
            VectorSet cluster = partition.getElements(i);
            System.out.println("Cluster " + i + " has " + cluster.size() + " elements");
            
            int j = 0;
            for (BinaryVector v : cluster) {
                j++;
                totalElements++;
                System.out.println("  Element " + j + ": " + Arrays.toString(v.getEl()));
            }
        }
        System.out.println("\nTotal elements across all clusters: " + totalElements);
    }
}
