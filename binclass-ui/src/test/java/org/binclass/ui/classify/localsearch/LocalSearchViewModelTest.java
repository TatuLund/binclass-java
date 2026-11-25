/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.localsearch;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.core.VectorSet;
import org.binclass.algorithms.events.OperatorSuccess;
import org.binclass.algorithms.events.PartitionResult;
import org.binclass.algorithms.events.SearchBestPartition;
import org.binclass.algorithms.events.SearchCompleted;
import org.binclass.algorithms.events.SearchProgress;
import org.binclass.algorithms.eventbus.EventBus;
import org.binclass.algorithms.gla.AutomaticSearch;
import org.binclass.algorithms.gla.GLAConfig;
import org.binclass.algorithms.gla.RangeSearch;
import org.binclass.components.graph.GraphModel;
import org.binclass.components.graph.GraphPoint;
import org.binclass.components.partition.PartitionModel;

import org.binclass.ui.MultiFileMemoryBuffer;

/**
 * Unit tests for {@link LocalSearchViewModel}, covering signal state, event-
 * driven model updates and the new per-operator {@link OperatorSuccess} line
 * described in {@code docs/WebUI.PRD.md} §1.6 — the UI-free half of the MVVM
 * pattern.
 */
class LocalSearchViewModelTest {

    private final LocalSearchViewModel viewModel = new LocalSearchViewModel();

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
    void eventFiredProgressAppendsGraphPoint() {
        viewModel.setData(vectors(4, 3));
        viewModel.eventFired(new SearchProgress(
                "localsearch", 2, 12.5, false));

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
                "localsearch", 2, Double.NaN, false));

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
                partition, new InfiniteCentroids(2, 3));
        viewModel.eventFired(new SearchBestPartition("localsearch", result));

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
                partition, new InfiniteCentroids(1, 2));
        viewModel.eventFired(new SearchCompleted("localsearch", result));

        assertEquals(30.0,
                viewModel.partitionModel().peek().stochasticComplexity());
    }

    @Test
    void operatorSuccessAppendsKRowToModel() {
        viewModel.setData(vectors(4, 3));
        viewModel.eventFired(new OperatorSuccess("localsearch", 2, 7, 1.5,
                "SPLITJOIN1"));

        OperatorLogModel model = viewModel.operatorLogModel().peek();
        assertEquals(6, model.legend().size(), model.legend().toString());
        List<OperatorLogModel.KRow> rows = model.rows();
        assertEquals(1, rows.size(), rows.toString());
        assertEquals(2, rows.get(0).k(), rows.get(0).k());
        assertEquals(List.of("SPLITJOIN1"), rows.get(0).operators(),
                rows.get(0).operators().toString());
    }

    @Test
    void operatorSuccessAppendsMultipleKRowsToModel() {
        viewModel.setData(vectors(4, 3));
        viewModel.eventFired(new OperatorSuccess("localsearch", 2, 7, 1.5,
                "SPLITJOIN1"));
        viewModel.eventFired(new OperatorSuccess("localsearch", 3, 8, 0.25,
                "REPLACEWORST"));

        OperatorLogModel model = viewModel.operatorLogModel().peek();
        List<OperatorLogModel.KRow> rows = model.rows();
        assertEquals(2, rows.size(), rows.toString());
        assertEquals(2, rows.get(0).k(), rows.get(0).k());
        assertEquals(List.of("SPLITJOIN1"), rows.get(0).operators(),
                rows.get(0).operators().toString());
        assertEquals(3, rows.get(1).k(), rows.get(1).k());
        assertEquals(List.of("REPLACEWORST"), rows.get(1).operators(),
                rows.get(1).operators().toString());
    }

    @Test
    void runClearsOperatorLogModel() {
        viewModel.setData(vectors(4, 3));
        viewModel.eventFired(new OperatorSuccess("localsearch", 2, 7, 1.5,
                "SPLITJOIN1"));
        assertEquals(1, viewModel.operatorLogModel().peek().rows().size());

        viewModel.run();
        assertTrue(viewModel.operatorLogModel().peek().rows().isEmpty(),
                viewModel.operatorLogModel().peek().rows().toString());
    }

    @Test
    void completionPopulatesOperatorStatistics() {
        viewModel.setData(vectors(4, 3));
        PartitionResult result = new PartitionResult(1, 30.0, 1,
                new double[] { Double.NaN, 10.0, 30.0 }, new int[] { 0, 1, 2 },
                new Partition(1, 2), new InfiniteCentroids(1, 2));

        viewModel.eventFired(new OperatorSuccess("localsearch", 2, 7, 1.5,
                "SPLITJOIN1"));
        viewModel.eventFired(new OperatorSuccess("localsearch", 3, 8, 0.25,
                "REPLACEWORST"));
        viewModel.eventFired(new OperatorSuccess("localsearch", 4, 7, 0.10,
                "SPLITJOIN1"));
        viewModel.eventFired(new SearchCompleted("localsearch", result));

        OperatorLogModel model = viewModel.operatorLogModel().peek();
        List<OperatorLogModel.StatBar> stats = model.stats();
        assertEquals(2, stats.size(), stats.toString());
        assertEquals("SPLITJOIN1", stats.get(0).operator(),
                stats.get(0).operator().toString());
        assertEquals(2, stats.get(0).count(), stats.get(0).count());
        assertEquals("REPLACEWORST", stats.get(1).operator(),
                stats.get(1).operator().toString());
        assertEquals(1, stats.get(1).count(), stats.get(1).count());
    }

    @Test
    void completionOmitsStatisticsWhenNoOperatorsImproved() {
        viewModel.setData(vectors(4, 3));
        PartitionResult result = new PartitionResult(1, 30.0, 1,
                new double[] { Double.NaN, 10.0, 30.0 }, new int[] { 0, 1, 2 },
                new Partition(1, 2), new InfiniteCentroids(1, 2));

        viewModel.eventFired(new SearchCompleted("localsearch", result));

        OperatorLogModel model = viewModel.operatorLogModel().peek();
        assertTrue(model.rows().isEmpty(), model.rows().toString());
        assertTrue(model.stats().isEmpty(), model.stats().toString());
    }

    @Test
    void runClearsOperatorStatistics() {
        viewModel.setData(vectors(4, 3));
        PartitionResult result = new PartitionResult(1, 30.0, 1,
                new double[] { Double.NaN, 10.0, 30.0 }, new int[] { 0, 1, 2 },
                new Partition(1, 2), new InfiniteCentroids(1, 2));

        viewModel.eventFired(new OperatorSuccess("localsearch", 2, 7, 1.5,
                "SPLITJOIN1"));
        viewModel.eventFired(new SearchCompleted("localsearch", result));
        assertEquals(1, viewModel.operatorLogModel().peek().stats().size());

        viewModel.run();
        assertTrue(viewModel.operatorLogModel().peek().stats().isEmpty(),
                viewModel.operatorLogModel().peek().stats().toString());
    }

    @Test
    void setFormStoresAndReturnsForm() {
        LocalSearchParametersForm form = LocalSearchParametersForm.defaults();
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
        RangeSearch.Result result = LocalSearchViewModel.runRange(set,
                LocalSearchParametersForm.defaults().toConfig(), 1, 2);
        assertNotNull(result.partition());
    }

    @Test
    void automaticSearchResultBuildsPartitionModel() {
        VectorSet set = vectors(6, 4);
        AutomaticSearch.Result result = LocalSearchViewModel.runAutomatic(set,
                LocalSearchParametersForm.defaults().toConfig());
        assertNotNull(result.partition());
    }

    @Test
    void partitionModelStartsNonEmpty() {
        PartitionModel model = viewModel.partitionModel().peek();
        assertNotNull(model);
        assertEquals(0, model.clusterCount());
    }

    @Test
    void automaticSearchSetsIndeterminateProgress() {
        viewModel.setData(vectors(4, 3));
        viewModel.setForm(LocalSearchParametersForm.defaults());
        viewModel.uploadValid().update(
                _ -> new MultiFileMemoryBuffer.UploadValidation(true, ""));
        viewModel.run();
        // Default heuristic (1) with maxIter 0 resolves to AUTO, so the bar
        // animates.
        assertTrue(viewModel.progressIndeterminate().peek());
    }

    @Test
    void rangeSearchSetsDeterminateProgress() {
        LocalSearchParametersForm form = new LocalSearchParametersForm(
                "test", 1, GLAConfig.DEFAULT.heuristicCount(), 0.1, false,
                GLAConfig.DEFAULT.alternateMode(),
                GLAConfig.DEFAULT.centroidType(),
                GLAConfig.DEFAULT.distanceType(), false);
        viewModel.setData(vectors(6, 3));
        viewModel.setForm(form);
        viewModel.uploadValid().update(
                _ -> new MultiFileMemoryBuffer.UploadValidation(true, ""));
        // A fixed kstop resolves to NAUTO, so the bar reports a fraction.
        viewModel.runRange(vectors(6, 3), form.toConfig(), 1, 5);
    }
}
