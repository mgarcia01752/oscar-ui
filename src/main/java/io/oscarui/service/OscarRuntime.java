/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Establishes writable OSCAR runtime resources before its static SNMP initialization. */
public final class OscarRuntime {
    private static final String CACHE_PROPERTY = "oscar.snmp.cache.path";

    private OscarRuntime() {}

    /**
     * Selects the per-user Qt cache directory before any OSCAR SNMP calls.
     * Preserves an explicit nonblank JVM override. Directory failures are recorded
     * for About diagnostics; the app remains available for troubleshooting.
     * Call once during application startup, before starting document workers.
     * @param cacheDirectory non-null, nonblank OS-provided application cache directory
     */
    public static void configureCache(String cacheDirectory) {
        try {
            String override = System.getProperty(CACHE_PROPERTY);
            Path cache;
            if (override != null && !override.isBlank()) {
                cache = Path.of(override).toAbsolutePath();
            } else {
                if (cacheDirectory == null || cacheDirectory.isBlank()) {
                    throw new IllegalStateException("No application cache directory available");
                }
                cache = Path.of(cacheDirectory).toAbsolutePath().resolve("snmp")
                        .resolve(com.comcast.oscar.netsnmp.Constants.DOTTED_TEXTUAL_NetSNMP_MAP_FILE);
                System.setProperty(CACHE_PROPERTY, cache.toString());
            }
            Files.createDirectories(cache.getParent());
        } catch (IOException | RuntimeException failure) {
            SessionDiagnostics.failure(SessionDiagnostics.Stage.SNMP_CACHE, failure);
        }
    }
}
