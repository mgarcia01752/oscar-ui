/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DocumentEditingTest {
    @TempDir Path temporary;
    private final OscarDocumentService service = new OscarDocumentService();

    private OscarDocumentService.Document open(String text) throws Exception {
        return service.open(Files.writeString(temporary.resolve("input.txt"), text));
    }

    @Test void addsAndEditsAdjacentCertificatesThroughOscar() throws Exception {
        var document = open(DocsisTestFixtures.MINIMUM_DOCUMENT);
        byte[] payload = new byte[1025];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) index;
        }
        String hex = CertificateInput.toOscarHex(payload);
        for (String name : java.util.List.of("ManufacturerCVC", "Co-signerCVC",
                "ManufacturerCVCChain", "Co-signerCVCChain")) {
            var definition = service.choices(document, document.root()).stream()
                    .filter(entry -> entry.name().equals(name)).findFirst().orElseThrow();
            assertTrue(CertificateInput.supports(definition));
            assertEquals("", service.valueIssue(document.configurationType(), definition, hex));
            assertFalse(service.valueIssue(document.configurationType(), definition, "GG").isEmpty());
            document = service.insert(document, document.root(), definition, hex);
        }
        byte[] encoded = OscarTextCompileCheck.compile(document.text());
        var projected = service.openCompiledText(document.path(), document.text(),
                document.text().getBytes(java.nio.charset.StandardCharsets.UTF_8), encoded);
        for (String name : java.util.List.of("ManufacturerCVC", "Co-signerCVC",
                "ManufacturerCVCChain", "Co-signerCVCChain")) {
            var node = projected.root().childTlvs().stream().filter(entry -> entry.tlvName().equals(name))
                    .findFirst().orElseThrow();
            assertArrayEquals(payload, CertificateInput.parse(node.encodedValueText()));
            var inspected = new SelectedTlvHexService().inspect(projected, node);
            byte[] fragments = java.util.HexFormat.ofDelimiter(":").parseHex(inspected.hex());
            assertEquals(payload.length + 2 * ((payload.length + 253) / 254), fragments.length);
        }
        var binaries = new OscarBinaryService(service);
        byte[] finalized = binaries.encode(projected, "synthetic-lab-key");
        var reopened = binaries.openAuto(Files.write(temporary.resolve("certificates.bin"), finalized));
        assertArrayEquals(finalized, binaries.encode(reopened, "synthetic-lab-key"));
        var manufacturer = projected.root().childTlvs().stream()
                .filter(node -> node.tlvName().equals("ManufacturerCVC")).findFirst().orElseThrow();
        var edited = service.replaceValue(projected, manufacturer, "30:01:FF");
        assertTrue(edited.text().contains("ManufacturerCVC 30:01:FF;"));
        var cosigner = edited.root().childTlvs().stream()
                .filter(node -> node.tlvName().equals("Co-signerCVC")).findFirst().orElseThrow();
        assertArrayEquals(payload, CertificateInput.parse(cosigner.encodedValueText()));
        var chain = edited.root().childTlvs().stream()
                .filter(node -> node.tlvName().equals("ManufacturerCVCChain")).findFirst().orElseThrow();
        var changedChain = service.replaceValue(edited, chain, "30:02:01:FF");
        assertTrue(changedChain.text().contains("ManufacturerCVCChain 30:02:01:FF;"));
        var untouchedChain = changedChain.root().childTlvs().stream()
                .filter(node -> node.tlvName().equals("Co-signerCVCChain")).findFirst().orElseThrow();
        assertArrayEquals(payload, CertificateInput.parse(untouchedChain.encodedValueText()));
    }

    @Test void duplicatesServiceFlowWithoutChangingOriginalOrSourceFile() throws Exception {
        String flow = "DownstreamServiceFlow { /* keep */ ServiceFlowReference 1; QualityOfServiceParameterSetType 07; }";
        String text = "Docsis {\r\n    " + flow + "\r\n    NetworkAccess 1;\r\n}";
        var original = open(text);
        var selected = original.root().childTlvs().getFirst();
        assertTrue(service.canDuplicate(original, selected));
        var changed = service.duplicate(original, selected);
        assertEquals(text.replace(flow, flow + "\r\n    " + flow), changed.text());
        assertEquals(3, changed.root().childTlvs().size());
        var copy = changed.root().childTlvs().get(1);
        assertEquals(2, copy.childTlvs().size());
        var edited = service.replaceValue(changed, copy.childTlvs().getFirst(), "2");
        assertEquals("1", edited.root().childTlvs().getFirst().childTlvs().getFirst().encodedValueText());
        assertEquals("2", edited.root().childTlvs().get(1).childTlvs().getFirst().encodedValueText());
        assertEquals(text, Files.readString(original.path()));
        assertEquals(original.diskHash(), changed.diskHash());
        assertFalse(service.canDuplicate(original, original.root()));
        assertFalse(service.canDuplicate(original, selected.childTlvs().getFirst()));
        assertFalse(service.canDuplicate(original, original.root().childTlvs().get(1)));
        assertThrows(InspectionException.class, () -> service.duplicate(changed, selected));
        var saved = service.save(edited, original.path(), original.diskHash());
        assertEquals(edited.text(), service.open(saved.path()).text());
    }

    @Test void editsExactRepeatedInstanceAndPreservesCommentsBomAndCrLf() throws Exception {
        String text = "\uFEFFDocsis {\r\n MaxCPEAllowed 3;\r\n MaxCPEAllowed /* before */ 004 /* after */;\r\n}";
        var original = open(text);
        var selected = original.root().childTlvs().get(1);
        assertEquals("4", selected.encodedValueText());
        assertSame(original, service.replaceValue(original, selected, "004"));
        var changed = service.replaceValue(original, selected, "5");
        assertEquals(text.replace("004", "5"), changed.text());
        assertThrows(InspectionException.class, () -> service.replaceValue(changed, selected, "6"));
        assertThrows(InspectionException.class, () -> service.replaceValue(original, selected, "5; NetworkAccess 1"));
        var saved = service.save(changed, original.path(), original.diskHash());
        assertEquals(changed.text(), service.open(saved.path()).text());
    }

    @Test void deletesOnlySelectedRepeatedInstanceAndNeverWritesSource() throws Exception {
        String text = "Docsis { /* keep */ MaxCPEAllowed 3; MaxCPEAllowed 4; /* tail */ }";
        var original = open(text);
        var changed = service.delete(original, original.root().childTlvs().get(1));
        assertEquals(text.replace("MaxCPEAllowed 4;", ""), changed.text());
        assertEquals(text, Files.readString(original.path()));
        assertEquals(1, changed.root().childTlvs().size());
        assertThrows(InspectionException.class, () -> service.delete(original, original.root()));
        assertThrows(InspectionException.class, () -> service.delete(changed, original.root().childTlvs().get(0)));
    }

    @Test void deletesLastNestedChildAndAllowsInsertionAgain() throws Exception {
        var configurationType = service.types().stream().filter(t -> t.header().equals("Docsis-4.0")).findFirst().orElseThrow();
        var empty = service.create(configurationType, false);
        var parentDefinition = service.choices(empty, empty.root()).stream()
                .filter(d -> d.path().equals(java.util.List.of(17))).findFirst().orElseThrow();
        var withParent = open(configurationType.header() + " { " + parentDefinition.name() + "; }");
        var parent = withParent.root().childTlvs().getFirst();
        var childDefinition = service.choices(withParent, parent).stream()
                .filter(d -> d.path().equals(java.util.List.of(17, 1))).findFirst().orElseThrow();
        var withChild = service.insert(withParent, parent, childDefinition, "2");
        var edited = service.replaceValue(withChild, withChild.root().childTlvs().getFirst().childTlvs().getFirst(), "3");
        assertEquals("3", edited.root().childTlvs().getFirst().childTlvs().getFirst().encodedValueText());
        var deleted = service.delete(edited, edited.root().childTlvs().getFirst().childTlvs().getFirst());
        assertTrue(deleted.root().childTlvs().getFirst().childTlvs().isEmpty());
        assertTrue(service.choices(deleted, deleted.root().childTlvs().getFirst()).contains(childDefinition));
        var restored = service.insert(deleted, deleted.root().childTlvs().getFirst(), childDefinition, "2");
        assertEquals(2, OscarDocumentService.count(restored.root()));
        var removedParent = service.delete(restored, restored.root().childTlvs().getFirst());
        assertTrue(removedParent.root().childTlvs().isEmpty());
    }

    @Test void keepsUnmappedReferencesProtectedAndChecksExplicitlyIncomplete() throws Exception {
        var document = open("/* Oscar Desktop reference template: not deployment defaults. */\nDocsis { UnknownPlaceholder 1; }");
        var node = document.root().childTlvs().getFirst();
        assertFalse(service.canDelete(document, node));
        assertFalse(service.canEdit(document, node));
        var checks = DraftChecks.inspect(service, document, false);
        assertTrue(checks.stream().anyMatch(c -> c.id().equals("minimum-rules") && c.status().equals("NOT IMPLEMENTED")));
        assertTrue(checks.stream().anyMatch(c -> c.id().equals("shared-secret") && c.status().equals("MISSING")));
    }

    @Test void scaffoldsImmediateRegistryChildrenAndPopulatesNestedContainersExplicitly() throws Exception {
        for (String header : java.util.List.of("Docsis", "Docsis-4.0")) {
            var configurationType = service.types().stream().filter(entry -> entry.header().equals(header)).findFirst().orElseThrow();
            var registry = com.comcast.oscar.dictionary.TlvRegistry.forFamily(configurationType.family());
            String text = "\uFEFF/* keep */\r\n" + header + " {\r\n}\r\n";
            var original = open(text);
            var classifier = registry.find(java.util.List.of(22));
                if (classifier == null) {
                classifier = service.choices(original, original.root()).stream()
                    .filter(entry -> registry.children(entry.path()).stream()
                        .anyMatch(child -> service.isContainer(configurationType, child)
                            && service.editingIssue(configurationType, child).isEmpty()))
                    .findFirst().orElseThrow();
                }
            var changed = service.insert(original, original.root(), classifier, "");
            var parent = changed.root().childTlvs().getFirst();
            var expected = registry.children(classifier.path()).stream()
                    .filter(child -> !service.editingIssue(configurationType, child).equals("Deferred editing scope"))
                    .filter(child -> child.maxInstances() != 0).map(com.comcast.oscar.dictionary.TlvDefinition::path).toList();
            assertEquals(expected, parent.childTlvs().stream().map(OscarDocumentService.Node::tlvPath).toList());
            assertTrue(parent.childTlvs().stream().allMatch(child -> child.childTlvs().isEmpty() && child.encodedValueText().isEmpty()));
            assertTrue(parent.childTlvs().stream().allMatch(child -> service.canDelete(changed, child)));
            assertTrue(changed.text().startsWith("\uFEFF/* keep */\r\n"));
            assertFalse(changed.text().replace("\r\n", "").contains("\n"));
            assertEquals(text, Files.readString(original.path()));
                var nested = parent.childTlvs().stream().filter(OscarDocumentService.Node::isContainer)
                    .findFirst().orElseThrow();
            assertTrue(service.canPopulate(changed, nested));
            var populated = service.populate(changed, nested);
            var updatedNested = populated.root().childTlvs().getFirst().childTlvs().stream()
                    .filter(child -> child.tlvPath().equals(nested.tlvPath())).findFirst().orElseThrow();
            assertEquals(registry.children(nested.tlvPath()).stream()
                            .filter(child -> !service.editingIssue(configurationType, child).equals("Deferred editing scope"))
                            .filter(child -> child.maxInstances() != 0)
                            .map(com.comcast.oscar.dictionary.TlvDefinition::path).toList(),
                    updatedNested.childTlvs().stream().map(OscarDocumentService.Node::tlvPath).toList());
            assertFalse(service.canPopulate(populated, updatedNested));
            assertThrows(InspectionException.class, () -> service.populate(populated, nested));
            assertThrows(InspectionException.class, () -> service.populate(populated, updatedNested));
            var saved = service.save(populated, temporary.resolve(header + "-scaffold.txt"), null);
            assertEquals(populated.text(), service.open(saved.path()).text());
        }
    }
}
