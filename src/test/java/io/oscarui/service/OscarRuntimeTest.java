/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OscarRuntimeTest {
    @TempDir Path temporary;

    @Test void selectsUserCachePreservesOverrideAndRecordsDirectoryFailure() throws Exception {
        String property = "oscar.snmp.cache.path";
        String original = System.getProperty(property);
        try {
            System.clearProperty(property);
            OscarRuntime.configureCache(temporary.resolve("user-cache").toString());
            Path cache = Path.of(System.getProperty(property));
            assertEquals(temporary.resolve("user-cache/snmp/DottedTextualNetSNMPMap.json"), cache);
            assertTrue(Files.isDirectory(cache.getParent()));
            Path override = temporary.resolve("explicit/map.json");
            System.setProperty(property, override.toString());
            OscarRuntime.configureCache(temporary.resolve("unused").toString());
            assertEquals(override.toString(), System.getProperty(property));
            assertFalse(Files.exists(temporary.resolve("unused")));
            Files.writeString(temporary.resolve("blocked"), "file");
            System.setProperty(property, temporary.resolve("blocked/map.json").toString());
            SessionDiagnostics.clear();
            OscarRuntime.configureCache(temporary.toString());
            assertTrue(SessionDiagnostics.snapshot().contains("SNMP_CACHE failed"));
            assertFalse(SessionDiagnostics.snapshot().contains(temporary.toString()));
        } finally {
            if (original == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, original);
            }
            SessionDiagnostics.clear();
        }
    }
}
