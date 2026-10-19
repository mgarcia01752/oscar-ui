/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class ReleaseUpdateServiceTest {
    private final ReleaseUpdateService service = new ReleaseUpdateService();

    @Test void comparesNumericVersionsAndDevelopmentBuilds() throws Exception {
        assertTrue(service.describe("0.9.9", release("v0.10.0")).startsWith("Update available"));
        assertTrue(service.describe("0.10.0", release("v0.10.0")).contains("up to date"));
        assertEquals("Installed: 0.11.0 · Latest release: 0.10.0.",
                service.describe("0.11.0", release("v0.10.0")));
        assertTrue(service.describe("0.10.0-SNAPSHOT", release("v0.10.0")).startsWith("Update available"));
        assertTrue(service.describe("0.10.0+build.1", release("v0.10.0")).contains("up to date"));
    }

    @Test void rejectsInvalidAndNonStableMetadata() {
        for (String payload : new String[] {"null", "{}", "not json", release("latest"),
                release("v1.0.0-beta"), release("v1.0.0").replace("\"draft\":false", "\"draft\":true"),
                release("v1.0.0").replace("\"prerelease\":false", "\"prerelease\":true")}) {
            assertThrows(IOException.class, () -> service.describe("0.1.0", payload));
        }
        assertThrows(IOException.class, () -> service.describe("unknown", release("v1.0.0")));
    }

    @Test void embedsResolvedBuildVersions() throws Exception {
        var metadata = new java.util.Properties();
        try (var resource = getClass().getResourceAsStream("/build-info.properties")) {
            assertNotNull(resource);
            metadata.load(resource);
        }
        for (String key : new String[] {"ui.version", "oscar.version", "qt.version"}) {
            assertNotNull(metadata.getProperty(key));
            assertFalse(metadata.getProperty(key).contains("${"));
        }
    }

    private String release(String version) {
        return "{\"tag_name\":\"" + version + "\",\"draft\":false,\"prerelease\":false}";
    }
}
