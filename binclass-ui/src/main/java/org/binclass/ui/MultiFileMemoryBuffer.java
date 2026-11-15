/*
 * Copyright (c) 2024 BinClass Contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.binclass.ui;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.vaadin.flow.component.upload.MultiFileReceiver;
import org.jspecify.annotations.NullMarked;

/**
 * In-memory {@link MultiFileReceiver} that buffers every uploaded file by name
 * so a single upload action can supply both the {@code .hdr}/{@code .header}
 * header and the {@code .dat}/{@code .data} data. Unlike Vaadin's built-in
 * {@code MemoryBuffer}, which retains only one file, this keeps each file
 * separately so the view can pair them before parsing a
 * {@link org.binclass.algorithms.core.VectorSet}.
 * <p>
 * Implementing {@link MultiFileReceiver} (rather than just {@code Receiver}) is
 * what tells Vaadin's {@code Upload} component that more than one file may be
 * selected at once; without it the component forces a single-file limit and the
 * native picker only allows picking one file.
 * <p>
 * The view wires {@link #onFileFinished(String)} to re-parse after every file
 * finishes and sets an optional {@link #setOnReady(Runnable)} callback that
 * runs whenever the buffered set changes.
 */
@NullMarked
public final class MultiFileMemoryBuffer implements MultiFileReceiver {

    private static final Logger log = LoggerFactory
            .getLogger(MultiFileMemoryBuffer.class);

    private final Map<String, ByteArrayOutputStream> buffers = new LinkedHashMap<>();
    private Runnable onReady;

    /**
     * Registers a callback invoked after each file finishes uploading so the
     * view can rebuild its vector set from whatever is currently buffered.
     *
     * @param onReady
     *            the runnable to run when the buffer changes, or {@code null}
     */
    public void setOnReady(Runnable onReady) {
        this.onReady = onReady;
    }

    @Override
    public OutputStream receiveUpload(String fileName, String fileType) {
        return buffers.computeIfAbsent(fileName,
                key -> new ByteArrayOutputStream());
    }

    /**
     * Stores the bytes of a single uploaded file under {@code fileName} and
     * fires the optional ready callback so the latest buffer is parsed. Used by
     * the view's {@link com.vaadin.flow.server.streams.UploadHandler}, which
     * reads each file's stream directly rather than through
     * {@link #receiveUpload}.
     *
     * @param fileName
     *            the name of the uploaded file
     * @param data
     *            the raw bytes that were uploaded
     */
    public void receiveBytes(String fileName, byte[] data) {
        ByteArrayOutputStream out = buffers.computeIfAbsent(
                fileName, key -> new ByteArrayOutputStream());
        try {
            out.write(data);
        } catch (IOException e) {
            log.warn("Failed to buffer uploaded file {}", fileName, e);
        }
        if (onReady != null) {
            onReady.run();
        }
    }

    /**
     * Returns the content of the first uploaded header file, or an empty string
     * when no {@code .hdr}/{@code .header} was supplied.
     *
     * @return the buffered header content (never {@code null})
     */
    public String getHeader() {
        for (Map.Entry<String, ByteArrayOutputStream> entry : buffers
                .entrySet()) {
            if (isHeader(entry.getKey())) {
                return toText(entry.getValue());
            }
        }
        return "";
    }

    /**
     * Returns the lines of the first uploaded data file, or an empty list when
     * none was supplied.
     *
     * @return the buffered data lines (never {@code null})
     */
    public List<String> getDataLines() {
        for (Map.Entry<String, ByteArrayOutputStream> entry : buffers
                .entrySet()) {
            if (isData(entry.getKey())) {
                return List.of(toText(entry.getValue()).split("\\n"));
            }
        }
        return List.of();
    }

    /**
     * Outcome of validating the currently buffered upload set. Carries whether
     * exactly one header and one data file were supplied, plus a human-readable
     * reason when they were not so the view can show it next to the Run button.
     *
     * @param valid
     *            {@code true} only when one header and one data file are
     *            present
     * @param reason
     *            an empty string when {@link #valid()}, otherwise a hint for
     *            the user
     */
    public record UploadValidation(boolean valid, String reason) {
    }

    /**
     * Validates that exactly two files were uploaded: one header file and one
     * data file. An empty buffer or any other combination is reported as
     * invalid with a short reason the view can display next to the Run button.
     *
     * @return the validation result describing the current upload set
     */
    public UploadValidation validate() {
        java.util.Set<String> names = new java.util.LinkedHashSet<>(
                buffers.keySet());
        if (names.isEmpty()) {
            return new UploadValidation(false,
                    "Upload your .data and .header files.");
        }
        long headers = names.stream().filter(MultiFileMemoryBuffer::isHeader)
                .count();
        long datas = names.stream().filter(MultiFileMemoryBuffer::isData)
                .count();
        if (names.size() != 2 || headers != 1 || datas != 1) {
            return new UploadValidation(false,
                    "Upload exactly one .data file and one .header file.");
        }
        return new UploadValidation(true, "");
    }

    /**
     * Clears every buffered file so a fresh upload starts from an empty buffer.
     */
    public void clear() {
        buffers.clear();
    }

    private static String toText(ByteArrayOutputStream out) {
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static boolean isHeader(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".hdr") || lower.endsWith(".header");
    }

    private static boolean isData(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".dat") || lower.endsWith(".data");
    }
}
