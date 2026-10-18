/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.partition;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.Tag;

/**
 * Vaadin wrapper for the {@code binclass-vector} web component.
 * <p>
 * Renders a binary vector as a grid of black/white cells (one cell per bit). On
 * hover, cells whose bit differs from the rounded centroid value are
 * highlighted red. The name is shown inline when the grid is larger than
 * {@code 8x8}, otherwise it lives in a tooltip.
 * </p>
 */
@Tag("binclass-vector")
@JsModule("./src/vector.ts")
public class Vector extends Component {

    /**
     * DOM event fired when a cell is selected, carrying the clicked index and
     * vector name.
     */
    public static final String EVENT_SELECTED = "vector-selected";

    /**
     * Sets all presentation data for this vector from a {@link VectorData}
     * record.
     *
     * @param data
     *            the immutable presentation data; must not be null
     */
    public void setData(VectorData data) {
        if (data == null) {
            throw new IllegalArgumentException("VectorData must not be null");
        }
        setBits(data.bits());
        setName(data.name());
        setIdentifier(data.identifier());
        setDistanceToCentroid(data.distanceToCentroid());
        setRoundedCentroid(data.roundedCentroid());
    }

    /**
     * Sets the bit values rendered by this vector.
     *
     * @param bits
     *            the bit values ({@code 0} or {@code 1})
     */
    public void setBits(int[] bits) {
        List<Integer> values = bits == null
                ? List.of()
                : IntStream.of(bits).boxed().collect(Collectors.toList());
        getElement().setPropertyList("bits", values);
    }

    /**
     * Returns the bit values currently pushed to the client.
     *
     * @return the bit array, or an empty array when none have been set
     */
    public int[] getBits() {
        List<?> values = getElement().getPropertyBean("bits", List.class);
        if (values == null) {
            return new int[0];
        }
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = ((Number) values.get(i)).intValue();
        }
        return result;
    }

    /**
     * Sets the display name shown as a prefix (large grids) or in the tooltip.
     *
     * @param name
     *            the vector name; must not be null
     */
    public void setName(String name) {
        getElement().setProperty("name", name == null ? "" : name);
    }

    /**
     * Sets the optional identifier shown alongside the name in tooltips.
     *
     * @param identifier
     *            an identifier, or {@code null} to clear it
     */
    public void setIdentifier(String identifier) {
        getElement().setProperty("identifier",
                identifier == null ? "" : identifier);
    }

    /**
     * Sets the rounded centroid values used for hover highlight comparison.
     *
     * @param roundedCentroid
     *            per-bit rounded centroid values ({@code 0} or {@code 1})
     */
    public void setRoundedCentroid(int[] roundedCentroid) {
        List<Integer> values = roundedCentroid == null
                ? List.of()
                : IntStream.of(roundedCentroid).boxed()
                        .collect(Collectors.toList());
        getElement().setPropertyList("roundedCentroid", values);
    }

    /**
     * Sets the pre-computed Hamming distance from this vector to its centroid.
     * Shown in tooltips.
     *
     * @param distanceToCentroid
     *            the Hamming distance
     */
    public void setDistanceToCentroid(int distanceToCentroid) {
        getElement().setProperty("distanceToCentroid", distanceToCentroid);
    }

}
