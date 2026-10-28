/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VendorExtensionIntegrationTest {
    @TempDir Path temporary;

    @Test void opensAndEditsOpaqueVendorChildrenWithoutRegistryDefinitions() throws Exception {
        var documents = new OscarDocumentService();
        var binary = new OscarBinaryService(documents);
        for (String family : List.of("docsis", "docsis40")) {
            var type = documents.types().stream().filter(entry -> entry.family().equals(family)).findFirst().orElseThrow();
            byte[] original = HexFormat.of().parseHex("2b0e08030005ca0101abf201cd0101ef");
            Path file = Files.write(temporary.resolve(family + ".bin"), original);
            var document = binary.open(file, type);
            var children = document.root().childTlvs().getFirst().childTlvs();
            assertEquals(List.of("VendorId", "UnknownTLV1", "UnknownTLV242", "UnknownTLV1"),
                    children.stream().map(OscarDocumentService.Node::tlvName).toList());
            var raw = children.get(2);
            assertEquals(List.of(43, 242), raw.tlvPath());
            assertTrue(documents.canEdit(document, raw));
            assertEquals("byte_array", documents.definition(document, children.get(1)).codec());
            var changed = documents.replaceValue(document, raw, "\"AA\"");
            assertArrayEquals(HexFormat.of().parseHex("2b0e08030005ca0101abf201aa0101ef"),
                    OscarTextCompileCheck.compile(changed.text()));
            assertArrayEquals(original, Files.readAllBytes(file));
        }
    }

    @Test void opaqueChildrenNeverAcquireStandardContainerStructure() throws Exception {
        var documents = new OscarDocumentService();
        var binary = new OscarBinaryService(documents);
        var document = binary.openAuto(Files.write(temporary.resolve("opaque.bin"),
                HexFormat.of().parseHex("2b0a08030005ca0501abf200")));
        var children = document.root().childTlvs().getFirst().childTlvs();
        assertFalse(children.get(1).isContainer());
        assertTrue(documents.canEdit(document, children.get(1)));
        assertEquals("\"\"", children.getLast().encodedValueText());
        assertArrayEquals(HexFormat.of().parseHex("2b0a08030005ca0501abf200"),
                OscarTextCompileCheck.compile(document.text()));
    }
}
