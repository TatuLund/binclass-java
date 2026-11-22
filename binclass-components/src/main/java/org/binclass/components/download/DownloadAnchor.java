/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.components.download;

import java.io.ByteArrayInputStream;

import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.AttachmentType;
import com.vaadin.flow.server.streams.DownloadHandler;
import com.vaadin.flow.server.streams.DownloadResponse;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * An {@link Anchor} pre-wired for an in-memory server-to-client download,
 * styled to look like a button through the Aura theme tokens.
 * <p>
 * The GLA and local-search views each expose two near-identical download links
 * (best partition and logged centroids). This component centralises that logic:
 * it binds a {@link DownloadHandler} that streams bytes produced by a caller
 * supplied {@link ByteProducer}, fixes the {@code text/plain; charset=UTF-8}
 * content type, and carries the {@link #CLASS_NAME} hook so all styling lives
 * in {@code downloadanchor.css} (imported via {@code @StyleSheet}). Callers
 * only supply a label, a file name and a byte supplier.
 * </p>
 */
@NullMarked
@StyleSheet("/downloadanchor.css")
public class DownloadAnchor extends Anchor {

    /** CSS class applied to every instance for styling and test selection. */
    public static final String CLASS_NAME = "download-anchor";

    /**
     * Content type shared by every download produced through this component.
     */
    private static final String CONTENT_TYPE = "text/plain; charset=UTF-8";

    @Nullable
    private String downloadFileName;

    /**
     * Supplies the bytes to stream when the anchor is clicked. The producer
     * runs on the server at click time, so it may read live view-model state.
     */
    @FunctionalInterface
    public interface ByteProducer {

        /**
         * Produces the raw bytes to download.
         *
         * @return the bytes to stream to the client; never {@code null}
         */
        byte[] produce();
    }

    /**
     * Builds a styled download anchor.
     *
     * @param label
     *            the link text shown to the user (for example
     *            {@code "Download partition"})
     * @param producer
     *            a supplier of the bytes to stream on click; runs server-side
     *            at click time
     */
    public DownloadAnchor(String label, ByteProducer producer) {
        super();
        setHref(DownloadHandler.fromInputStream(event -> {
            event.setContentType(CONTENT_TYPE);
            byte[] bytes = producer.produce();
            return new DownloadResponse(new ByteArrayInputStream(bytes),
                    fileName(), CONTENT_TYPE, bytes.length);
        }), AttachmentType.DOWNLOAD);
        addClassName(CLASS_NAME);
        setText(label);
    }

    /**
     * Sets the file name offered to the client on download. Applied to both the
     * {@code setFileName} call and the returned response so the two stay in
     * sync.
     *
     * @param fileName
     *            the full file name, including extension (for example
     *            {@code "entero.partition"})
     */
    public void setDownloadFileName(String fileName) {
        this.downloadFileName = fileName;
    }

    /**
     * Returns the file name previously set with {@link #setDownloadFileName},
     * or an empty string when none has been set yet.
     *
     * @return the configured download file name, never {@code null}
     */
    String fileName() {
        return downloadFileName == null ? "" : downloadFileName;
    }
}
