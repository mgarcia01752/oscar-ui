/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SelectedTlvHexServiceTest {
    @TempDir Path temporary;
    private final OscarDocumentService documents = new OscarDocumentService();
    private final SelectedTlvHexService hex = new SelectedTlvHexService();

    @Test void preservesSelectedRepeatedInstanceAndDescendants() throws Exception {
        var document = documents.open(Files.writeString(temporary.resolve("flows.txt"), """
                Docsis {
                  DownstreamServiceFlow { ServiceFlowReference 1; }
                  DownstreamServiceFlow { ServiceFlowReference 2; ServiceFlowError { ErrorCode 3; } }
                }
                """));
        var selected = document.root().childTlvs().get(1);
        var result = hex.inspect(document, selected);
        assertEquals("19:09:01:02:00:02:05:03:02:01:03", result.hex());
        assertEquals(2, result.children().size());
        assertEquals("01:02:00:02", result.children().getFirst().hex());
        assertEquals("05:03:02:01:03", result.children().get(1).hex());
        assertEquals("02:01:03", result.children().get(1).children().getFirst().hex());
        assertEquals(result.children().get(1), hex.inspect(document, selected.childTlvs().get(1)));
        assertEquals(result.children().getFirst(), hex.inspect(document, selected.childTlvs().getFirst()));
        assertThrows(InspectionException.class, () -> hex.inspect(document, document.root()));
    }

    @Test void handlesScalarBinaryAndCurrentEditsWithoutSecret() throws Exception {
        var binary = new OscarBinaryService(documents);
        var document = binary.openAuto(Files.write(temporary.resolve("network.bin"),
                java.util.HexFormat.of().parseHex("030101ff00")));
        var result = hex.inspect(document, document.root().childTlvs().getFirst());
        assertEquals("03:01:01", result.hex());
        assertTrue(result.children().isEmpty());
        var edited = documents.replaceValue(document, document.root().childTlvs().getFirst(), "0");
        assertEquals("03:01:00", hex.inspect(edited, edited.root().childTlvs().getFirst()).hex());
        assertThrows(InspectionException.class, () -> hex.inspect(edited, document.root().childTlvs().getFirst()));
    }

    @Test void usesOscarLengthWidthsForDocsis40() throws Exception {
        var document = documents.open(Files.writeString(temporary.resolve("d40.txt"),
                "Docsis-4.0 { CmSshServerConfiguration { CommonSshConfiguration { SshEnabledInterfaces 1; } } }"));
        assertEquals("67:00:05:01:03:03:01:01", hex.inspect(document, document.root().childTlvs().getFirst()).hex());
    }
}
