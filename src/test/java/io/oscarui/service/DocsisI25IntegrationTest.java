/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import static org.junit.jupiter.api.Assertions.*;
import com.comcast.oscar.dictionary.TlvRegistry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocsisI25IntegrationTest {
    @TempDir Path temporary;

    @Test void exposesNewRootAndNestedDefinitionsFromOscarWithoutUiCopies() throws Exception {
        var documents = new OscarDocumentService();
        var type = documents.types().stream().filter(entry -> entry.header().equals("Docsis"))
                .findFirst().orElseThrow();
        var document = documents.create(type, false);
        var available = documents.choices(document, document.root()).stream()
                .map(definition -> definition.path()).toList();
        for (int tlvTypeCode : new int[] {45, 76, 79, 81, 82, 83, 84, 88, 91, 92, 93, 94, 101, 102}) {
            assertTrue(available.contains(List.of(tlvTypeCode)), "Missing Add choice " + tlvTypeCode);
        }
        var registry = TlvRegistry.forFamily(type.family());
        for (var path : List.of(List.of(79, 9), List.of(84, 3), List.of(24, 40, 6),
                List.of(70, 42, 10), List.of(93, 47, 1), List.of(94, 47, 5),
                List.of(101, 4), List.of(102, 1), List.of(102, 3))) {
            var definition = registry.find(path);
            assertNotNull(definition, "Missing registry path " + path);
            assertEquals("", documents.editingIssue(type, definition), "Disabled editor " + path);
        }
        assertEquals("byte_array", registry.find(List.of(88)).codec());
    }

    @Test void editsRepeatedAddressContainerWithoutChangingItsSibling() throws Exception {
        // Synthetic encoding fragment, not a complete provisioning profile.
        byte[] original = HexFormat.of().parseHex(
                "351402080106c0000201000002080106c00002020000");
        var documents = new OscarDocumentService();
        var binary = new OscarBinaryService(documents);
        var document = binary.openAuto(Files.write(temporary.resolve("repeated.bin"), original));
        var rows = document.root().childTlvs().getFirst().childTlvs();
        assertEquals(2, rows.size());
        var secondAddress = rows.getLast().childTlvs().getFirst();
        var edited = documents.replaceValue(document, secondAddress, "192.0.2.3(0)");
        assertArrayEquals(HexFormat.of().parseHex(
                "351402080106c0000201000002080106c00002030000"),
                OscarTextCompileCheck.compile(edited.text()));
        assertArrayEquals(original, Files.readAllBytes(document.origin().path()));
    }
}
