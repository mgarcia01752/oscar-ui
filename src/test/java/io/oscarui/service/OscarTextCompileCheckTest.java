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

class OscarTextCompileCheckTest {
    @TempDir Path temporary;
    private final OscarBinaryService binary = new OscarBinaryService(new OscarDocumentService());

    @Test void preservesTextFormattingAndRepeatedTlvOrder() throws Exception {
        String source = "\uFEFF/* retain */ Docsis {\r\n MaxCPEAllowed 3; NetworkAccess 1;"
                + " MaxCPEAllowed 4;\r\n DownstreamServiceFlow { ServiceFlowReference 2; }"
                + " DownstreamServiceFlow { ServiceFlowReference 1; }\r\n}\r\n";
        Path file = Files.writeString(temporary.resolve("ordered.txt"), source);
        var document = binary.openAuto(file);
        assertEquals(source, document.text());
        assertEquals(source, Files.readString(file));
        assertEquals(java.util.List.of("MaxCPEAllowed", "NetworkAccess", "MaxCPEAllowed",
                "DownstreamServiceFlow", "DownstreamServiceFlow"),
                document.root().childTlvs().stream().map(OscarDocumentService.Node::tlvName).toList());
    }

    @Test void reportsOscarSyntaxAndEncodingFailuresAndRestoresConsole() throws Exception {
        var stdout = System.out;
        var stderr = System.err;
        for (String source : new String[] {"Docsis { NetworkAccess ;", "Docsis { NetworkAccess nonsense; }",
                "Docsis { UpstreamServiceFlow { ServiceFlowReference 1; QualityOfServiceParameterSetType 7; } }"}) {
            Path file = Files.writeString(temporary.resolve("invalid.txt"), source);
            var error = assertThrows(InspectionException.class, () -> binary.openAuto(file));
            assertTrue(error.getMessage().startsWith("OSCAR text compilation failed:"));
            assertTrue(error.getMessage().length() > "OSCAR text compilation failed:".length());
            assertSame(stdout, System.out);
            assertSame(stderr, System.err);
            assertEquals(source, Files.readString(file));
        }
    }

    @Test void rejectsEmptySourceAndAcceptsExampleWithSnmpCommunities() throws Exception {
        assertThrows(InspectionException.class, () -> OscarTextCompileCheck.validate("Docsis {}"));
        var example = Path.of("cm-config/examples/docsis/1gbps-ds-100mbps-us.txt");
        assertEquals(Files.readString(example), binary.openAuto(example).text());
    }
}
