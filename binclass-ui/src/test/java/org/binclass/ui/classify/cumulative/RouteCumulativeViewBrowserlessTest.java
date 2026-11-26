/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui.classify.cumulative;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.List;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.UploadTester;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;

import org.binclass.algorithms.core.BinaryVector;
import org.binclass.algorithms.core.DynamicPartition;
import org.binclass.algorithms.events.CumulativeProgress;
import org.binclass.algorithms.events.NewCluster;
import org.binclass.ui.MainLayout;
import org.binclass.ui.classify.localsearch.RouteLocalSearchView;

/**
 * Browserless test for the {@link RouteCumulativeView} Cumulative
 * Classification display. It drives the real Vaadin components — the
 * {@code Upload} and the {@code Run} button — through their tester wrappers, so
 * the view's own signal bindings and the {@link CumulativeViewModel}'s
 * event-bus listener run unmocked. The goal is to prove that uploading a
 * header/data pair enables the Run button, that progress events advance the bar
 * in ten-percent steps, that new-cluster events append one log line each, and
 * that Run disables while running and re-enables on completion.
 */
@ViewPackages(classes = {
        RouteCumulativeView.class, RouteLocalSearchView.class })
class RouteCumulativeViewBrowserlessTest extends BrowserlessTest {

    /** Reads a classpath resource into bytes. */
    private static byte[] readResource(String name) throws Exception {
        try (InputStream in = RouteCumulativeViewBrowserlessTest.class
                .getResourceAsStream(name)) {
            assertNotNull(in, "Missing test resource: " + name);
            return in.readAllBytes();
        }
    }

    /**
     * Returns the view's private {@link CumulativeViewModel} for state
     * assertions.
     */
    private static CumulativeViewModel viewModel(RouteCumulativeView view)
            throws Exception {
        Field field = RouteCumulativeView.class.getDeclaredField("viewModel");
        field.setAccessible(true);
        return (CumulativeViewModel) field.get(view);
    }

    /**
     * Navigates to the view, uploads the header/data pair, and flushes signals.
     */
    private RouteCumulativeView loadedView() throws Exception {
        RouteCumulativeView view = navigate(RouteCumulativeView.class);
        Upload upload = find(Upload.class).withId("uploader-cumulative")
                .single();
        test(upload).upload("/test.header", "text/plain",
                readResource("/test.header"));
        test(upload).upload("/test.data", "text/plain",
                readResource("/test.data"));
        runPendingSignalsTasks();
        return view;
    }

    @Test
    void uploadEnablesRunAndClickStartsClassification() throws Exception {
        RouteCumulativeView view = loadedView();
        CumulativeViewModel vm = viewModel(view);
        FormLayout formLayout = find(FormLayout.class)
                .withId("cumulative-params")
                .single();

        assertEquals(100, vm.data().peek().vectorSet().size());
        assertTrue(vm.statusText().peek().startsWith("Loaded"));

        Button run = find(Button.class).withId("run-cumulative").single();
        assertTrue(test(run).isUsable());

        test(run).click();
        assertTrue(vm.running().peek());
        assertFalse(formLayout.isVisible());
    }

    @Test
    void runDisabledBeforeUpload() throws Exception {
        RouteCumulativeView view = navigate(RouteCumulativeView.class);
        Button run = find(Button.class).withId("run-cumulative").single();
        runPendingSignalsTasks();

        assertFalse(test(run).isUsable());
    }

    @Test
    void progressAdvancesInTenPercentSteps() throws Exception {
        RouteCumulativeView view = loadedView();
        CumulativeViewModel vm = viewModel(view);
        ProgressBar bar = find(ProgressBar.class).withId("cumulative-progress")
                .single();
        vm.setData(vm.data().peek().vectorSet());

        // A progress event at 10% of vectors sets the bar to 0.1.
        vm.eventFired(new CumulativeProgress("cumulative", 10));
        runPendingSignalsTasks();
        assertEquals(0.1, vm.progress().peek(), 1e-9);
        assertEquals(0.1, bar.getValue(), 1e-9);

        // A progress event at 55% rounds to the nearest ten-percent step (60%).
        vm.eventFired(new CumulativeProgress("cumulative", 55));
        runPendingSignalsTasks();
        assertEquals(0.6, vm.progress().peek(), 1e-9);
        assertEquals(0.6, bar.getValue(), 1e-9);

        // A progress event past the end clamps to a full bar.
        vm.eventFired(new CumulativeProgress("cumulative", 200));
        runPendingSignalsTasks();
        assertEquals(1.0, vm.progress().peek(), 1e-9);
        assertEquals(1.0, bar.getValue(), 1e-9);
    }

    /** Counts the rendered log line children inside the log container. */
    private static int countLogLineChildren(Div logText) {
        return (int) logText.getElement().getChildren().count();
    }

    @Test
    void newClusterEventsAppendOneLogLineEach() throws Exception {
        RouteCumulativeView view = loadedView();
        CumulativeViewModel vm = viewModel(view);
        Div logText = find(Div.class).withId("cumulative-log").single();

        // Capture the initial DOM child count before any events.
        int initialChildren = countLogLineChildren(logText);

        vm.eventFired(new NewCluster("cumulative", 2, null));
        runPendingSignalsTasks();
        assertEquals(1, vm.logLines().peekValues().count());
        // A new child was appended to the log container in the DOM.
        int afterFirst = countLogLineChildren(logText);
        assertEquals(initialChildren + 1, afterFirst);

        // The SC-optimising variant carries a value and logs it in bold.
        vm.eventFired(new NewCluster("cumulative", 3, 42.5));
        runPendingSignalsTasks();
        assertEquals(2, vm.logLines().peekValues().count());
        // A second child was appended alongside the first.
        int afterSecond = countLogLineChildren(logText);
        assertEquals(afterFirst + 1, afterSecond);

        List<String> values = vm.logLines().peekValues().toList();
        assertTrue(values.get(1).contains("SC = 42.500"));

        // Verify the rendered DOM children match the signal values. The first
        // child is a Div holding plain text; the second wraps its SC portion in
        // nested spans, so read its text recursively to find "SC = 42.500".
        var renderedChildren = logText.getElement().getChildren()
                .toList();
        assertEquals(initialChildren + 2, renderedChildren.size());
        assertEquals(values.get(0), renderedChildren.get(initialChildren)
                .getText());
        assertTrue(renderedChildren.get(initialChildren + 1)
                .getTextRecursively().contains("SC = 42.500"));

        // A join is not logged; only new-cluster events append a line.
    }

    @Test
    void runDisablesWhileRunningAndReenablesOnCompletion() throws Exception {
        RouteCumulativeView view = loadedView();
        CumulativeViewModel vm = viewModel(view);
        Button run = find(Button.class).withId("run-cumulative").single();

        test(run).click();
        assertTrue(vm.running().peek());
        assertFalse(test(run).isUsable());

        // Simulate completion: the dynamic partition is stored and result
        // available flips true, re-enabling Run.
        DynamicPartition dynPart = new DynamicPartition(1, 4);
        vm.dynamicPartition().update(_ -> dynPart);
        vm.resultAvailable().update(_ -> true);
        vm.running().set(false);
        runPendingSignalsTasks();

        // The Run button re-enables once the run completes. It lives inside the
        // parameter form, which the click handler hides, so assert on the
        // source-of-truth canRun() signal rather than isUsable(), which also
        // checks component visibility.
        assertTrue(vm.canRun().peek());
    }

    @Test
    void allControlsRender() throws Exception {
        loadedView();

        find(IntegerField.class).withId("delta").single();
        find(Checkbox.class).withId("fixed-delta").single();
        find(Checkbox.class).withId("cum-analysis").single();
        find(IntegerField.class).withId("cum-samples").single();
        find(Checkbox.class).withId("bayesian-predictive").single();
        find(Checkbox.class).withId("feature-significance").single();
        find(Checkbox.class).withId("save-by-pf").single();
        find(Checkbox.class).withId("no-new-classes").single();
        find(Checkbox.class).withId("in-order").single();
        find(NumberField.class).withId("epsilon").single();
        find(Button.class).withId("run-cumulative").single();
    }

    @Test
    void confirmDialogAppearsWhenNavigatingAwayWhileRunning() throws Exception {
        RouteCumulativeView view = navigate(RouteCumulativeView.class);
        UI.getCurrent().add(new MainLayout());
        viewModel(view).running().set(true);

        test(sideNav()).clickItem("Local Search");
        runPendingSignalsTasks();

        ConfirmDialog dialog = find(ConfirmDialog.class).single();
        assertNotNull(dialog);
    }

    /** Returns the app's {@code SideNav} built by {@link MainLayout}. */
    private com.vaadin.flow.component.sidenav.SideNav sideNav() {
        return find(com.vaadin.flow.component.sidenav.SideNav.class)
                .withId("sidenav").single();
    }
}
