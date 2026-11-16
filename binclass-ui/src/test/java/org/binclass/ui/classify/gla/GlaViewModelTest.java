/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.gla;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.events.PartitionResult;
import org.binclass.algorithms.events.SearchBestPartition;
import org.binclass.algorithms.events.SearchCompleted;
import org.binclass.algorithms.events.SearchProgress;
import org.binclass.algorithms.eventbus.EventBus;
import org.binclass.algorithms.gla.AutomaticSearch;
import org.binclass.algorithms.gla.RangeSearch;
import org.binclass.components.graph.GraphModel;
import org.binclass.components.graph.GraphPoint;
import org.binclass.components.partition.PartitionModel;

import org.binclass.ui.MultiFileMemoryBuffer;

/**
 * Unit tests for {@link GlaViewModel}, covering signal state, search-type
 * resolution and event-driven model updates described in
 * {@code docs/WebUI.PRD.md} §1.6 — the UI-free half of the MVVM pattern.
 */
class GlaViewModelTest {

    private final GlaViewModel viewModel = new GlaViewModel();

    @BeforeEach
    void setUp() {
        EventBus.get().registerEventBusListener(viewModel);
    }

    @AfterEach
    void tearDown() {
        viewModel.unregister();
    }

    /** Builds a small vector set of the given length. */
    private static VectorSet vectors(int count, int length) {
        VectorSet set = new VectorSet(count);
        for (int i = 0; i < count; i++) {
            int[] bits = new int[length];
            bits[i % length] = 1;
            BinaryVector bv = new BinaryVector(bits, length);
            bv.setStrain("s" + i);
            set.addElement(bv);
        }
        return set;
    }

    @Test
    void initialSignalsHaveExpectedValues() {
        assertEquals(false, viewModel.running().peek());
        assertNotNull(viewModel.graphPoints().peek());
        assertEquals("", viewModel.statusText().peek());
        assertFalse(viewModel.canRun().map(s -> s).peek());
    }

    @Test
    void setDataPopulatesDataSignalAndStatus() {
        viewModel.setData(vectors(4, 3));

        assertTrue(viewModel.data().peek() != null);
        assertEquals(4, viewModel.data().peek().vectorSet().size());
        assertEquals("Loaded 4 vectors.", viewModel.statusText().peek());
    }

    @Test
    void canRunTrueAfterDataLoaded() {
        viewModel.setData(vectors(3, 2));
        viewModel.uploadValid().update(
                _v -> new MultiFileMemoryBuffer.UploadValidation(true, ""));

        assertTrue(viewModel.canRun().map(s -> s).peek());
    }

    @Test
    void canRunFalseWhenUploadInvalid() {
        viewModel.setData(vectors(3, 2));

        assertFalse(viewModel.canRun().map(s -> s).peek());
    }

    @Test
    void canRunFalseWhileRunning() {
        viewModel.setData(vectors(3, 2));
        viewModel.uploadValid().update(
                _v -> new MultiFileMemoryBuffer.UploadValidation(true, ""));
        viewModel.running().set(true);

        assertFalse(viewModel.canRun().map(s -> s).peek());
    }

    @Test
    void eventFiredProgressAppendsGraphPoint() {
        viewModel.setData(vectors(4, 3));
        viewModel.eventFired(new SearchProgress(
                "gla", 2, 12.5, false));

        GraphModel model = viewModel.graphPoints().peek();
        List<GraphPoint> points = model.points();
        assertEquals(1, points.size()); // single appended point (empty start)
        assertEquals(2, points.get(0).x());
        assertEquals(12.5, points.get(0).y());
    }

    @Test
    void eventFiredProgressDropsNonFiniteSc() {
        viewModel.setData(vectors(4, 3));
        viewModel.eventFired(new SearchProgress(
                "gla", 2, Double.NaN, false));

        assertEquals(0, viewModel.graphPoints().peek().points().size());
    }

    @Test
    void eventFiredBestPartitionUpdatesModel() {
        viewModel.setData(vectors(4, 3));
        Partition partition = new Partition(2, 4);
        partition.addElement(1, new BinaryVector(new int[] { 0, 1, 1 }, 3));
        partition.addElement(1, new BinaryVector(new int[] { 1, 1, 0 }, 3));
        partition.addElement(2, new BinaryVector(new int[] { 1, 0, 0 }, 3));

        PartitionResult result = new PartitionResult(1, 42.5, 2,
                new double[] { Double.NaN, 50.0, 42.5 }, new int[] { 0, 1, 2 },
                partition);
        viewModel.eventFired(new SearchBestPartition("gla", result));

        PartitionModel model = viewModel.partitionModel().peek();
        assertNotNull(model);
        assertEquals(2, model.clusterCount());
    }
    
    @Test
    void eventFiredCompletedUpdatesGraphAndPartition() {
        viewModel.setData(vectors(4, 3));
        Partition partition = new Partition(1, 2);
        partition.addElement(1, new BinaryVector(new int[] { 0, 1 }, 2));
        partition.addElement(1, new BinaryVector(new int[] { 1, 0 }, 2));

        PartitionResult result = new PartitionResult(1, 30.0, 1,
                new double[] { Double.NaN, 10.0, 30.0 }, new int[] { 0, 1, 2 },
                partition);
        viewModel.eventFired(new SearchCompleted("gla", result));

        assertEquals(30.0,
                viewModel.partitionModel().peek().stochasticComplexity());
        assertEquals(2, viewModel.graphPoints().peek().points().size());
    }

    @Test
    void setFormStoresAndReturnsForm() {
        GlaParametersForm form = GlaParametersForm.defaults();
        viewModel.setForm(form);

        assertEquals(form, viewModel.getForm());
    }

    @Test
    void runWithoutDataIsNoOp() {
        viewModel.run();
        assertFalse(viewModel.running().peek());
    }

    @Test
    void rangeSearchResultBuildsPartitionModel() {
        VectorSet set = vectors(6, 4);
        RangeSearch.Result result = GlaViewModel.runRange(set,
                GlaParametersForm.defaults().toConfig(), 1, 2);
        assertNotNull(result.partition());
    }

    @Test
    void automaticSearchResultBuildsPartitionModel() {
        VectorSet set = vectors(6, 4);
        AutomaticSearch.Result result = GlaViewModel.runAutomatic(set,
                GlaParametersForm.defaults().toConfig());
        assertNotNull(result.partition());
    }
}
