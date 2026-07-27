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
 * Backed by a {@link HashSet} for O(1) lookup performance (unlike the original
 * C implementation which used a linked list). Provides methods to manage
 * collections of binary vectors during clustering operations.
 * </p>
 */
public final class VectorSet implements Iterable<BinaryVector> {

    private final Set<BinaryVector> elements;

    /**
     * Creates an empty {@code VectorSet}.
     */
    public VectorSet() {
        this.elements = new HashSet<>();
    }

    /**
     * Creates a {@code VectorSet} with the given initial capacity.
     *
     * @param initialCapacity
     *            the expected number of elements
     */
    public VectorSet(int initialCapacity) {
        this.elements = new HashSet<>(initialCapacity);
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
        return elements.add(bv);
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
        return elements.remove(bv);
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
        return elements.contains(bv);
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
     * Returns an iterator over the elements in this set.
     * <p>
     * Equivalent to C function {@code st_iterator()} from {@code binset.h}.
     * </p>
     *
     * @return an iterator over the BinaryVector instances
     */
    public Iterator<BinaryVector> iterator() {
        return elements.iterator();
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
    }

    /**
     * Returns a string representation of this VectorSet.
     */
    @Override
    public String toString() {
        return "VectorSet{" + size() + " elements}";
    }

}
