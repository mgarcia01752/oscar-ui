/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReleaseConfigurationTest {
    @Test void packagedSettingsMatchCanonicalSource() throws Exception {
        try (var input = ReleaseConfiguration.class.getResourceAsStream("/release.properties")) {
            assertNotNull(input);
            assertArrayEquals(Files.readAllBytes(Path.of("src/main/resources/release.properties")), input.readAllBytes());
        }
        assertEquals("https://github.com/mgarcia01752/oscar-ui", ReleaseConfiguration.project("ui", "web"));
        assertEquals("https://api.github.com/repos/Comcast/Oscar", ReleaseConfiguration.project("oscar", "api"));
        for (String version : new String[]{"1.2.3", "2.10.7"}) {
            assertEquals("oscar-ui-" + version + "-macos-x64.dmg",
                    ReleaseConfiguration.installer(version, "macos", "x64", "dmg"));
            assertEquals("OSCAR-UI-" + version + "-windows-x64.msp", ReleaseConfiguration.patch(version));
        }
        assertThrows(IllegalStateException.class, () -> ReleaseConfiguration.project("missing", "web"));
    }
}
