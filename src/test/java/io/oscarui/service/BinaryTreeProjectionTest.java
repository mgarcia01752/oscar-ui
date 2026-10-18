package io.oscarui.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BinaryTreeProjectionTest {
    @TempDir Path temporary;
    private final OscarDocumentService documents = new OscarDocumentService();
    private final OscarBinaryService binary = new OscarBinaryService(documents);

    @Test void communityExampleHasTwelveIndependentSnmpInstancesWithoutRemarks() throws Exception {
        Path example = Path.of("cm-config/examples/docsis/1gbps-ds-100mbps-us.txt");
        String source = Files.readString(example);
        var document = binary.openAuto(example);
        assertEquals(source, document.text());
        var statements = document.root().childTlvs().stream().filter(node -> node.tlvPath().equals(List.of(11))).toList();
        assertEquals(12, statements.size());
        for (var statement : statements) {
            assertFalse(statement.encodedValueText().contains("/*"));
            assertFalse(statement.encodedValueText().contains("Snmp11"));
            String original = source.substring(statement.sourceStartOffset(), statement.sourceEndExclusive());
            assertTrue(original.startsWith("Snmp11 "));
            assertTrue(original.endsWith(";"));
            assertEquals(1, original.chars().filter(character -> character == ';').count());
            assertTrue(new SelectedTlvHexService().inspect(document, statement).hex().startsWith("0B:"));
        }
        assertTrue(statements.get(2).encodedValueText().contains("public"));
        assertTrue(statements.get(8).encodedValueText().contains("private"));
    }

    @Test void rejectsUpstreamLossOfRepeatedNestedInstances() throws Exception {
        Path file = Files.writeString(temporary.resolve("lossy.txt"),
                "Docsis { DownstreamServiceFlow { MaxTrafficBurst 6; MaxTrafficBurst 8; } }");
        assertThrows(InspectionException.class, () -> binary.openAuto(file));
    }

    @Test void binaryValuesAndNestedInstanceOrderRetainOriginalSourceOffsetsAfterEdits() throws Exception {
        String source = "Docsis { DownstreamServiceFlow { MaxTrafficBurst 0006; ServiceFlowReference 2; "
                + "} NetworkAccess 01; "
                + "DownstreamServiceFlow { ServiceFlowReference 1; } }";
        var document = binary.openAuto(Files.writeString(temporary.resolve("ordered.txt"), source));
        assertEquals(source, document.text());
        var flow = document.root().childTlvs().getFirst();
        assertEquals(List.of("ServiceFlowReference", "MaxTrafficBurst"),
                flow.childTlvs().stream().map(OscarDocumentService.Node::tlvName).toList());
        assertEquals(List.of("2", "6"),
                flow.childTlvs().stream().map(OscarDocumentService.Node::encodedValueText).toList());
        assertEquals("1", document.root().childTlvs().get(1).encodedValueText());
        var edited = documents.replaceValue(document, flow.childTlvs().getLast(), "9");
        assertEquals(source.replace("0006", "9"), edited.text());
        assertEquals("9", edited.root().childTlvs().getFirst().childTlvs().getLast().encodedValueText());
        assertTrue(documents.hasBinaryTree(edited));
    }
}
