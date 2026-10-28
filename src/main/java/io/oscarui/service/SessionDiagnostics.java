/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Properties;

/** Bounded, process-local diagnostics; never retains exception messages or input data. */
public final class SessionDiagnostics {
    private static final int MAX_EVENTS = 200;
    private static final int MAX_CAUSES = 8;
    private static final int MAX_FRAMES = 8;
    private static final ArrayDeque<String> EVENTS = new ArrayDeque<>();

    /** Controlled operation names prevent configuration text from becoming a log label. */
    public enum Stage { SNMP_CACHE, BINARY_FRAMING, BINARY_EXPORT, TEXT_COMPILE, BINARY_ROUND_TRIP, FILE_READ }

    private SessionDiagnostics() {}

    /**
     * Records a failure from any thread, retaining bounded type and stack information only.
     * @param stage non-null controlled operation identifier
     * @param failure exception to describe, or null when OSCAR reported failure without throwing
     */
    public static synchronized void failure(Stage stage, Throwable failure) {
        var event = new StringBuilder(Instant.now().toString()).append(" ").append(stage).append(" failed");
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
        for (int depth = 0; failure != null && depth < MAX_CAUSES && seen.add(failure); depth++) {
            event.append("\n  ").append(failure.getClass().getName());
            var frames = failure.getStackTrace();
            for (int frame = 0; frame < Math.min(frames.length, MAX_FRAMES); frame++) {
                var location = frames[frame];
                event.append("\n    at ").append(location.getClassName()).append('.').append(location.getMethodName())
                        .append(':').append(location.getLineNumber());
            }
            failure = failure.getCause();
        }
        if (EVENTS.size() == MAX_EVENTS) {
            EVENTS.removeFirst();
        }
        EVENTS.addLast(event.toString());
    }

    /**
     * Returns a thread-safe snapshot with packaged versions and platform details.
     * @return plain text suitable for explicit copy or export; no paths or exception messages
     */
    public static synchronized String snapshot() {
        var build = new Properties();
        try (var stream = SessionDiagnostics.class.getResourceAsStream("/build-info.properties")) {
            if (stream != null) {
                build.load(stream);
            }
        } catch (java.io.IOException ignored) {
            // Metadata is optional; do not substitute a guessed version.
        }
        return "OSCAR-UI " + build.getProperty("ui.version", "unknown")
                + "\nOSCAR " + build.getProperty("oscar.version", "unknown")
                + "\nQt " + build.getProperty("qt.version", "unknown")
                + "\nOS " + System.getProperty("os.name") + " " + System.getProperty("os.version")
                + " " + System.getProperty("os.arch") + "\nJava " + System.getProperty("java.version")
                + "\nSession diagnostics (latest " + MAX_EVENTS + " failures). Messages and configuration values omitted.\n\n"
                + (EVENTS.isEmpty() ? "No failures recorded this session." : String.join("\n\n", EVENTS));
    }

    /**
     * Clears captured events from any thread; future failures are still recorded.
     */
    public static synchronized void clear() {
        EVENTS.clear();
    }
}
