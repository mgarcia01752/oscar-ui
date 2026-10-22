/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AppearanceSettingsStoreTest {
    @TempDir Path temporary;

    @Test void templateBannerPreferenceDefaultsAndSurvivesOtherSettingsChanges() throws Exception {
        var path = temporary.resolve("banner.json");
        var store = new AppearanceSettingsStore(path);
        assertFalse(store.loadDisableTemplateBanner());
        Files.writeString(path, "{\"version\":1,\"appearance\":\"system\"}");
        assertFalse(store.loadDisableTemplateBanner());
        store.save(Appearance.DARK, false, true);
        new AppearanceSettingsStore(path).save(Appearance.LIGHT, true);
        assertTrue(store.loadDisableTemplateBanner());
        store.save(Appearance.SYSTEM, false, false);
        assertFalse(store.loadDisableTemplateBanner());
    }

    @Test void referenceTemplatesUseSelectedBannerWithoutChangingTlvs() throws Exception {
        var service = new OscarDocumentService();
        for (var type : service.types()) {
            var full = service.create(type, true).text();
            var compact = service.create(type, true,
                    com.comcast.oscar.configurationfile.TemplateBannerLevel.SPDX_AUTHOR).text();
            assertTrue(full.contains("Snmp11 OID DataType Value"));
            assertFalse(compact.contains("Snmp11 OID DataType Value"));
            assertTrue(compact.contains("SPDX-License-Identifier: Apache-2.0"));
            assertTrue(compact.contains("Author: Maurice Garcia"));
            assertTrue(compact.contains("Author: Allen Flickinger"));
            assertEquals(full.substring(full.indexOf(type.header() + " {")),
                    compact.substring(compact.indexOf(type.header() + " {")));
        }
    }

    @Test void missingSettingsDefaultToSystemAndChoicesSurviveReload() throws Exception {
        Path settingsPath = temporary.resolve("preferences/settings.json");
        var store = new AppearanceSettingsStore(settingsPath);
        assertEquals(Appearance.SYSTEM, store.load());
        assertFalse(Files.exists(settingsPath));
        for (Appearance appearance : Appearance.values()) {
            store.save(appearance);
            assertEquals(appearance, new AppearanceSettingsStore(settingsPath).load());
            var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(settingsPath.toFile());
            assertEquals(4, json.size());
            assertEquals(1, json.get("version").intValue());
        }
        try (var files = Files.list(settingsPath.getParent())) {
            assertEquals(1, files.count());
        }
    }

    @Test void maskingSurvivesReloadAndAppearanceChanges() throws Exception {
        Path settingsPath = temporary.resolve("settings.json");
        Files.writeString(settingsPath, "{\"version\":1,\"appearance\":\"system\"}");
        var store = new AppearanceSettingsStore(settingsPath);
        assertFalse(store.loadObfuscateSnmpSecrets());
        store.save(Appearance.DARK, true);
        var reloaded = new AppearanceSettingsStore(settingsPath);
        assertTrue(reloaded.loadObfuscateSnmpSecrets());
        reloaded.save(Appearance.LIGHT);
        assertTrue(store.loadObfuscateSnmpSecrets());
        store.save(Appearance.SYSTEM, false);
        assertFalse(reloaded.loadObfuscateSnmpSecrets());
        Files.writeString(settingsPath,
                "{\"version\":1,\"appearance\":\"system\",\"obfuscateSnmpSecrets\":\"yes\"}");
        assertThrows(IOException.class, store::loadObfuscateSnmpSecrets);
    }

    @Test void invalidSettingsAreReportedWithoutRewritingThem() throws Exception {
        Path settingsPath = temporary.resolve("settings.json");
        for (String invalid : new String[]{"broken", "{}", "null",
                "{\"version\":2,\"appearance\":\"dark\"}",
                "{\"version\":1,\"appearance\":\"invalid\"}"}) {
            Files.writeString(settingsPath, invalid);
            assertThrows(IOException.class, () -> new AppearanceSettingsStore(settingsPath).load());
            assertEquals(invalid, Files.readString(settingsPath));
        }
    }

    @Test void writeFailureDoesNotReplaceExistingContent() throws Exception {
        Path blockedParent = Files.writeString(temporary.resolve("blocked"), "keep");
        var store = new AppearanceSettingsStore(blockedParent.resolve("settings.json"));
        assertThrows(IOException.class, () -> store.save(Appearance.DARK));
        assertEquals("keep", Files.readString(blockedParent));
    }
}
