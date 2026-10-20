/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import static org.junit.jupiter.api.Assertions.*;
import com.comcast.oscar.dictionary.TlvRegistry;
import java.nio.file.*;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommonTlvPreferencesTest {
    @TempDir Path directory;

    @Test void defaultsAndSavedOverridesSurviveReloadAndRegistryEditionChanges() throws Exception {
        var service = new OscarDocumentService();
        var type = service.types().stream().filter(t -> t.header().equals("Docsis")).findFirst().orElseThrow();
        var other = service.types().stream().filter(t -> t.header().equals("Docsis-4.0")).findFirst().orElseThrow();
        var registry = TlvRegistry.forFamily(type.family());
        var network = registry.find(List.of(3));
        var frequency = registry.find(List.of(1));
        var store = new CommonTlvPreferences();
        Path path = directory.resolve("preferences/common-tlvs.json");
        store.load(path);
        assertTrue(store.isCommon(type, network));
        assertFalse(store.isCommon(type, frequency));
        var draft = store.copy();
        draft.setCommon(type, frequency, true);
        draft.setCommon(type, network, false);
        assertFalse(store.isCommon(type, frequency));
        store.save(draft);
        var reloaded = new CommonTlvPreferences();
        reloaded.load(path);
        assertTrue(reloaded.isCommon(type, network));
        assertTrue(reloaded.isCommon(type, frequency));
        assertFalse(reloaded.isCommon(other, frequency));
        var updatedEdition = new OscarDocumentService.FileType(type.id(), type.header(), type.family(), "new-registry-edition");
        assertTrue(reloaded.isCommon(updatedEdition, frequency));
        assertTrue(Files.readString(path).contains("common_ui_tlv"));
        draft = reloaded.copy();
        draft.setCommon(type, frequency, false);
        reloaded.save(draft);
        store.load(path);
        assertFalse(store.isCommon(type, frequency));
    }

    @Test void failedWriteDoesNotApplyDraft() throws Exception {
        var service = new OscarDocumentService();
        var type = service.types().stream().filter(t -> t.header().equals("Docsis")).findFirst().orElseThrow();
        var frequency = TlvRegistry.forFamily(type.family()).find(List.of(1));
        var store = new CommonTlvPreferences();
        Path parent = directory.resolve("blocked");
        store.load(parent.resolve("common-tlvs.json"));
        Files.writeString(parent, "not a directory");
        var draft = store.copy();
        draft.setCommon(type, frequency, true);
        assertThrows(IOException.class, () -> store.save(draft));
        assertFalse(store.isCommon(type, frequency));
        assertEquals("not a directory", Files.readString(parent));
    }

    @Test void preservesUnknownEntriesAndRejectsCorruptOrFutureSettings() throws Exception {
        Path path = directory.resolve("common-tlvs.json");
        String futureEntry = "{\"version\":1,\"profiles\":{\"future-family\":{\"250\":{\"common_ui_tlv\":true}}}}";
        Files.writeString(path, futureEntry);
        var store = new CommonTlvPreferences();
        store.load(path);
        store.save(store.copy());
        assertTrue(Files.readString(path).contains("future-family"));
        for (String invalid : List.of("broken", "{\"version\":2,\"profiles\":{}}",
                "{\"version\":1,\"profiles\":{\"Docsis\":{\"1\":{\"common_ui_tlv\":\"yes\"}}}}")) {
            Files.writeString(path, invalid);
            assertThrows(IOException.class, () -> store.load(path));
            assertThrows(IOException.class, () -> store.save(store.copy()));
            assertEquals(invalid, Files.readString(path));
        }
    }
}
