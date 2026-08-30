/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.algorithms.core;

import java.util.*;

/**
 * A set of {@link BinaryVector} instances, mirroring the C {@code ST} struct
 * from {@code binset.h}.
 * <p>
 * Backed by an {@link IdentityHashMap} for identity-based comparison (using
 * reference equality) rather than content-based equality. This ensures each
 * vector instance is treated as unique regardless of its bit pattern, which
 * matches the behavior expected in clustering operations where different
 * instances may have identical content but represent distinct vectors.
 * </p>
 */
public final class VectorSet implements Iterable<BinaryVector> {

    private final IdentityHashMap<BinaryVector, Boolean> elements;
    private final java.util.ArrayList<BinaryVector> insertionOrder = new java.util.ArrayList<>();

    /**
     * Creates an empty {@code VectorSet}.
     */
    public VectorSet() {
        this.elements = new IdentityHashMap<>();
    }

    /**
     * Creates a {@code VectorSet} with the given initial capacity.
     *
     * @param initialCapacity
     *            the expected number of elements
     */
    public VectorSet(int initialCapacity) {
        this.elements = new IdentityHashMap<>(initialCapacity);
    }

    /**
     * Adds an element to this set.
     * <p>
     * Equivalent to C function {@code st_add_element()} from {@code binset.h}.
     * </p>
     *
     * @param bv
     *            the BinaryVector to add
     * @return true if the set changed as a result of the call (element was not
     *         already present)
     */
    public boolean addElement(BinaryVector bv) {
        boolean added = elements.put(bv, Boolean.TRUE) == null;
        if (added) {
            insertionOrder.add(bv);
        }
        return added;
    }

    /**
     * Adds an element to this set (convenience method).
     * <p>
     * Equivalent to {@link #addElement(BinaryVector)}.
     * </p>
     *
     * @param bv
     *            the BinaryVector to add
     * @return true if the set changed as a result of the call
     */
    public boolean add(BinaryVector bv) {
        return addElement(bv);
    }

    /**
     * Removes an element from this set.
     * <p>
     * Equivalent to C function {@code st_remove_element()} from
     * {@code binset.h}.
     * </p>
     *
     * @param bv
     *            the BinaryVector to remove
     * @return true if the set contained the specified element
     */
    public boolean removeElement(BinaryVector bv) {
        boolean removed = elements.remove(bv) != null;
        if (removed) {
            for (int i = 0; i < insertionOrder.size(); i++) {
                if (insertionOrder.get(i) == bv) {
                    insertionOrder.remove(i);
                    break;
                }
            }
        }
        return removed;
    }

    /**
     * Checks whether this set contains the given element.
     * <p>
     * Equivalent to C function {@code st_contains()} from {@code binset.h}.
     * </p>
     *
     * @param bv
     *            the BinaryVector to check for
     * @return true if the set contains the specified element
     */
    public boolean contains(BinaryVector bv) {
        return elements.containsKey(bv);
    }

    /**
     * Returns the number of elements in this set.
     * <p>
     * Equivalent to C function {@code st_size()} from {@code binset.h}.
     * </p>
     *
     * @return size of the set
     */
    public int size() {
        return elements.size();
    }

    /**
     * Returns the length (number of bits) of vectors in this set.
     * <p>
     * Assumes all vectors in the set have the same length. Iterates through the
     * set to find any vector and returns its length.
     * </p>
     *
     * @return the bit-length of vectors in this set, or 0 if empty
     */
    public int getVectorLength() {
        BinaryVector bv = elements.keySet().stream().findFirst().orElse(null);
        if (bv != null) {
            return bv.getLength();
        }
        return 0; // Empty set
    }

    /**
     * Returns an iterator over the elements in this set.
     * <p>
     * Equivalent to C function {@code st_iterator()} from {@code binset.h}.
     * </p>
     *
     * @return an iterator over the BinaryVector instances
     */
    public Iterator<BinaryVector> iterator() {
        return new ArrayList<>(insertionOrder).iterator();
    }

    /**
     * Returns a new array containing all vectors in this set.
     * <p>
     * Equivalent to C function {@code st_to_array()} from {@code binset.h}.
     * </p>
     *
     * @param type
     *            the component type of the returned array (unused, for API
     *            compatibility)
     * @return a new array containing all BinaryVector instances in this set
     */
    public <T> T[] toArray(T[] type) {
        return (T[]) insertionOrder.toArray(type);
    }

    /**
     * Returns the underlying collection of elements.
     *
     * @return an unmodifiable view of the elements
     */
    public Collection<BinaryVector> getElements() {
        return Collections.unmodifiableCollection(elements.keySet());
    }

    /**
     * Returns whether this set is empty.
     *
     * @return true if the set contains no elements
     */
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    /**
     * Removes all elements from this set.
     * <p>
     * Equivalent to C function {@code st_clear()} from {@code binset.h}.
     * </p>
     */
    public void clear() {
        elements.clear();
        insertionOrder.clear();
    }

    /**
     * Copies all vectors from this set into the target set.
     * <p>
     * Equivalent to C function {@code st_copy()} from {@code binset.h}.
     * </p>
     *
     * @param target
     *            the destination VectorSet to copy elements into
     */
    public void copyTo(VectorSet target) {
        for (BinaryVector bv : this) {
            target.addElement(bv);
        }
    }

    /**
     * Returns a string representation of this VectorSet.
     */
    @Override
    public String toString() {
        return "VectorSet{" + size() + " elements}";
    }

}
