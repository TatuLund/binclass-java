/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.download;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Anchor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DownloadAnchor}, the reusable styled download link used
 * by the GLA and local-search views. Verifies the DRY extraction preserved the
 * {@link Anchor} type, carries its CSS hook class, and exposes the label and
 * file-name surface those views rely on.
 */
class DownloadAnchorTest {

    @BeforeEach
    void setUp() {
        // The constructor eagerly calls setHref(DownloadHandler,...), which
        // needs a current UI to resolve the stream resource URI.
        UI.setCurrent(new UI());
    }

    @Test
    void extendsAnchor() {
        DownloadAnchor anchor = new DownloadAnchor("Download partition",
                () -> new byte[0]);
        assertTrue(anchor instanceof Anchor);
    }

    @Test
    void carriesCssHookClass() {
        DownloadAnchor anchor = new DownloadAnchor("Download partition",
                () -> new byte[0]);
        assertTrue(anchor.hasClassName(DownloadAnchor.CLASS_NAME));
    }

    @Test
    void storesLabelFromConstructor() {
        DownloadAnchor anchor = new DownloadAnchor("Download centroids",
                () -> new byte[0]);
        assertEquals("Download centroids", anchor.getText());
    }

    @Test
    void setDownloadFileNameStoresValue() {
        DownloadAnchor anchor = new DownloadAnchor("Download partition",
                () -> new byte[0]);
        anchor.setDownloadFileName("entero.partition");
        assertEquals("entero.partition", anchor.fileName());
    }

    @Test
    void fileNameDefaultsToEmptyWhenUnset() {
        DownloadAnchor anchor = new DownloadAnchor("Download partition",
                () -> new byte[0]);
        assertEquals("", anchor.fileName());
    }
}
