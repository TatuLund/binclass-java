/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.localsearch;

import java.io.InputStream;
import java.lang.reflect.Field;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.UploadTester;
import com.vaadin.flow.component.sidenav.SideNav;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.InfiniteCentroids;
import org.binclass.algorithms.core.Partition;
import org.binclass.algorithms.events.OperatorSuccess;
import org.binclass.algorithms.events.PartitionResult;
import org.binclass.algorithms.events.SearchBestPartition;
import org.binclass.components.tree.TreeModel;
import org.binclass.ui.MainLayout;
import org.binclass.ui.classify.gla.RouteGlaView;

/**
 * Browserless test for the {@link RouteLocalSearchView} Local Search display.
 * It drives the real Vaadin components — the {@code Upload} and the {@code Run}
 * button — through their tester wrappers, so the view's own signal bindings and
 * the {@link LocalSearchViewModel}'s event-bus listener run unmocked. The goal
 * is to prove that uploading a header/data pair enables the Run button and that
 * clicking it actually starts a search (the {@code running} signal flips to
 * {@code true}).
 */
@ViewPackages(classes = {
        RouteGlaView.class, RouteLocalSearchView.class })
class RouteLocalSearchViewBrowserlessTest extends BrowserlessTest {

    /** Reads a classpath resource into bytes. */
    private static byte[] readResource(String name) throws Exception {
        try (InputStream in = RouteLocalSearchViewBrowserlessTest.class
                .getResourceAsStream(name)) {
            assertNotNull(in, "Missing test resource: " + name);
            return in.readAllBytes();
        }
    }

    /** Returns the view's private {@link LocalSearchViewModel} for state. */
    private static LocalSearchViewModel viewModel(RouteLocalSearchView view)
            throws Exception {
        Field field = RouteLocalSearchView.class.getDeclaredField("viewModel");
        field.setAccessible(true);
        return (LocalSearchViewModel) field.get(view);
    }

    /** Selects the Dendrogram tab so its button is visible in MockedUI. */
    private void selectDendrogramTab(RouteLocalSearchView view)
            throws Exception {
        Field tabs = RouteLocalSearchView.class.getDeclaredField("localTabs");
        tabs.setAccessible(true);
        ((com.vaadin.flow.component.tabs.TabSheet) tabs.get(view))
                .setSelectedIndex(3);
    }

    /**
     * Navigates to the view, uploads the header/data pair through the real
     * {@link UploadTester}, and flushes signal effects so the Run button's
     * enabled state reflects the current form.
     */
    private RouteLocalSearchView loadedView() throws Exception {
        RouteLocalSearchView view = navigate(RouteLocalSearchView.class);
        Upload upload = find(Upload.class).withId("uploader-gla").single();
        test(upload).upload("/test.header", "text/plain",
                readResource("/test.header"));
        test(upload).upload("/test.data", "text/plain",
                readResource("/test.data"));
        runPendingSignalsTasks();
        return view;
    }

    @Test
    void uploadEnablesRunAndClickStartsSearch() throws Exception {
        RouteLocalSearchView view = loadedView();
        LocalSearchViewModel vm = viewModel(view);
        FormLayout formLayout = find(FormLayout.class).withId("local-params")
                .single();

        // The upload parsed into the view model and updated the status text.
        assertEquals(100, vm.data().peek().vectorSet().size());
        assertTrue(vm.statusText().peek().startsWith("Loaded"));

        // With data loaded and a valid form, Run is enabled.
        Button run = find(Button.class).withId("run-local").single();
        assertTrue(test(run).isUsable());

        // Clicking Run starts the search: running flips to true synchronously.
        test(run).click();
        assertTrue(vm.running().peek());
        // FormLayout is hidden while running.
        assertFalse(formLayout.isVisible());
    }

    @Test
    void runDisabledBeforeUpload() throws Exception {
        RouteLocalSearchView view = navigate(RouteLocalSearchView.class);
        Button run = find(Button.class).withId("run-local").single();
        runPendingSignalsTasks();

        // No data loaded and the upload set is not validated yet, so Run stays
        // disabled even though the form itself is valid.
        assertFalse(test(run).isUsable());
    }

    @Test
    void epsilonOutOfRangeDisablesRunButDecreasingReenables() throws Exception {
        RouteLocalSearchView view = loadedView();
        Button run = find(Button.class).withId("run-local").single();
        NumberField epsilon = find(NumberField.class).withId("epsilon")
                .single();
        Checkbox decreasing = find(
                Checkbox.class).withId("decreasing-eps").single();

        // An epsilon outside (0, 0.5) invalidates the form and disables Run.
        epsilon.setValue(0.9);
        runPendingSignalsTasks();
        assertFalse(test(run).isUsable());

        // Toggling decreasing-epsilon treats epsilon as a bare value, so the
        // same out-of-range number becomes valid again.
        test(decreasing).check();
        runPendingSignalsTasks();
        assertTrue(test(run).isUsable());

        // Returning to normal mode with an in-range epsilon keeps it enabled.
        test(decreasing).uncheck();
        epsilon.setValue(0.3);
        runPendingSignalsTasks();
        assertTrue(test(run).isUsable());
    }

    @Test
    void heuristicSelectExposesAllEightCodes() throws Exception {
        RouteLocalSearchView view = loadedView();
        find(com.vaadin.flow.component.select.Select.class).withId("heuristic")
                .single();
        // The Local Search form exposes all eight heuristic codes, unlike GLA's
        // narrower strategy select.
    }

    /**
     * Navigates to the Local Search view, adds the app layout so its {@code
     * SideNav} is present in the component tree for menu-item clicks
     * (browserless navigation does not apply the router layout automatically),
     * and forces a search into the {@code running} state without waiting for
     * the background task, so {@code beforeLeave} deterministically postpones.
     */
    private RouteLocalSearchView lsWithRunningSearch() throws Exception {
        RouteLocalSearchView view = navigate(RouteLocalSearchView.class);
        UI.getCurrent().add(new MainLayout());
        viewModel(view).running().set(true);
        return view;
    }

    /** Returns the app's {@code SideNav} built by {@link MainLayout}. */
    private SideNav sideNav() {
        return find(SideNav.class).withId("sidenav").single();
    }

    @Test
    void confirmDialogAppearsWhenNavigatingAwayWhileRunning() throws Exception {
        lsWithRunningSearch();

        // Click another menu item; beforeLeave postpones and opens the dialog.
        test(sideNav()).clickItem("GLA Search");
        runPendingSignalsTasks();

        ConfirmDialog dialog = find(ConfirmDialog.class).single();
        assertNotNull(dialog);
    }

    @Test
    void confirmResumesNavigationAndCancelsSearch() throws Exception {
        RouteLocalSearchView view = lsWithRunningSearch();
        LocalSearchViewModel vm = viewModel(view);

        test(sideNav()).clickItem("GLA Search");
        runPendingSignalsTasks();

        ConfirmDialog dialog = find(ConfirmDialog.class).single();
        assertNotNull(dialog);

        // Selecting confirm cancels the search and proceeds to the target view.
        test(dialog).confirm();
        runPendingSignalsTasks();

        assertEquals(false, vm.running().peek());
        assertTrue(RouteGlaView.class.isAssignableFrom(
                getCurrentView().getClass()));
    }

    @Test
    void cancelKeepsSearchRunningAndStaysInView() throws Exception {
        RouteLocalSearchView view = lsWithRunningSearch();
        LocalSearchViewModel vm = viewModel(view);

        test(sideNav()).clickItem("GLA Search");
        runPendingSignalsTasks();

        ConfirmDialog dialog = find(ConfirmDialog.class).single();
        assertNotNull(dialog);

        // Selecting cancel keeps the search running and stays on the LS view.
        test(dialog).cancel();
        runPendingSignalsTasks();

        assertEquals(true, vm.running().peek());
        assertTrue(RouteLocalSearchView.class.isAssignableFrom(
                getCurrentView().getClass()));
    }

    @Test
    void generateDendrogramButtonDisabledUntilCompleteEnabledOnCompletion()
            throws Exception {
        RouteLocalSearchView view = navigate(RouteLocalSearchView.class);
        selectDendrogramTab(view);
        Button generateTree = find(Button.class)
                .withId("generate-tree-local").single();
        runPendingSignalsTasks();

        // Before a result exists, the button is disabled.
        assertFalse(test(generateTree).isUsable());

        LocalSearchViewModel vm = viewModel(view);
        Partition partition = new Partition(2, 4);
        partition.addElement(1, new BinaryVector(new int[] { 0, 1, 1 }, 3));
        partition.addElement(1, new BinaryVector(new int[] { 1, 1, 0 }, 3));
        partition.addElement(2, new BinaryVector(new int[] { 1, 0, 0 }, 3));

        PartitionResult result = new PartitionResult(1, 42.5, 2,
                new double[] { Double.NaN, 50.0, 42.5 }, new int[] { 0, 1, 2 },
                partition, new InfiniteCentroids(2, 3));
        vm.eventFired(new SearchBestPartition("local-search", result));
        runPendingSignalsTasks();

        // After a result exists, the button becomes enabled.
        assertTrue(test(generateTree).isUsable());

        test(generateTree).click();
        // Poll until the tree model is populated on the bound signal.
        TreeModel model = null;
        for (int i = 0; i < 100 && model == null; i++) {
            Thread.sleep(10);
            model = vm.treeModel().peek();
        }

        assertNotNull(model, "treeModel should be populated after clicking");
        assertEquals(3, model.nodes().size());
    }

    /**
     * Navigates to the Local Search view and adds the app layout so its
     * {@code SideNav} is present in the component tree (browserless navigation
     * does not apply the router layout automatically).
     */
    private RouteLocalSearchView lsWithAppLayout() throws Exception {
        RouteLocalSearchView view = navigate(RouteLocalSearchView.class);
        UI.getCurrent().add(new MainLayout());
        return view;
    }

    @Test
    void operatorSuccessRendersVisualLogLegendAndRow() throws Exception {
        lsWithAppLayout();
        LocalSearchViewModel vm = viewModel(lsWithAppLayout());

        // Post an operator success so the model gains its fixed legend and one
        // per-k row; runPendingSignalsTasks flushes the bindModel effect.
        vm.eventFired(new OperatorSuccess("localsearch", 2, 7, 1.5,
                "SPLITJOIN1"));
        runPendingSignalsTasks();

        // The fixed legend renders all six operators with their labels.
        Span legend = find(Span.class).withId("operator-log-legend")
                .single();
        assertTrue(legend.getElement().getTextRecursively()
                .contains("Split-Join 1"),
                legend.getElement().getTextRecursively());

        // The per-k row for k=2 holds SPLITJOIN1's success square.
        Span row = find(Span.class).withId("op-log-row-2").single();
        assertTrue(row.isVisible());
    }
}
