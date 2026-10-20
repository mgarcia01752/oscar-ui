/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OscarInspectionServiceTest {
    @TempDir Path temporary;
    final OscarInspectionService service = new OscarInspectionService();
    Path input(String text) throws Exception { return Files.writeString(temporary.resolve("configuration.txt"), text); }
    @Test void inspectsRealOscarCatalogWithoutWritingFile() throws Exception {
        Path file = input("Docsis { NetworkAccess 1; MaxCPEAllowed 3; }");
        byte[] before = Files.readAllBytes(file);
        var result = service.inspect(file);
        assertTrue(result.summary().contains("2 top-level"));
        assertTrue(result.content().contains("MaxCPEAllowed 3"));
        assertArrayEquals(before, Files.readAllBytes(file));
        try (var paths = Files.list(temporary)) { assertEquals(1, paths.count()); }
    }
    @Test void acceptsDocsis40Header() throws Exception {
        assertTrue(service.inspect(input("Docsis-4.0 { NetworkAccess 1; }")).summary().contains("type 5"));
    }
    @Test void rejectsInvalidSyntaxUnknownNamesAndTrailingInput() throws Exception {
        for (String invalid : new String[]{"Docsis { NetworkAccess 1 }", "Docsis { NotAnOscarTlv 1; }", "Docsis { NetworkAccess 1; } extra"}) {
            assertThrows(InspectionException.class, () -> service.inspect(input(invalid)));
        }
    }
    @Test void rejectsMissingEmptyBinaryAndOversizedFiles() throws Exception {
        assertThrows(InspectionException.class, () -> service.inspect(temporary.resolve("missing")));
        assertThrows(InspectionException.class, () -> service.inspect(input("")));
        assertThrows(InspectionException.class, () -> service.inspect(input("\0\1\2")));
        assertThrows(InspectionException.class, () -> service.inspect(input("x".repeat(OscarInspectionService.MAX_BYTES + 1))));
    }
    @Test void reportsOptionalSnmpLimitationWithoutInvokingIt() throws Exception {
        var error = assertThrows(InspectionException.class, () -> service.inspect(input("Docsis { Snmp11 sysName.0 OctetString \"public\"; }")));
        assertTrue(error.getMessage().contains("Net-SNMP"));
        assertFalse(Files.exists(temporary.resolve("db")));
    }

    final OscarDocumentService documents = new OscarDocumentService();

    @Test void discoversTypesFromGrammarAndBundledRegistries() throws Exception {
        var types = documents.types();
        assertTrue(types.stream().anyMatch(configurationType -> configurationType.header().equals("Docsis") && configurationType.family().equals("docsis")));
        assertTrue(types.stream().anyMatch(configurationType -> configurationType.header().equals("Docsis-4.0") && configurationType.family().equals("docsis40")));
        assertTrue(types.stream().anyMatch(configurationType -> configurationType.header().equals("DPoE")));
        assertFalse(types.stream().anyMatch(type -> java.util.Set.of("erouter", "opencable").contains(type.family())));
        for (var configurationType : types) {
            var created = documents.create(configurationType, false);
            assertEquals(configurationType, created.configurationType());
            assertTrue(created.root().childTlvs().isEmpty());
            assertNull(created.path());
        }
    }

    @Test void docsisEmbeddedParentsAreAvailableUntilTheirInstanceLimit() throws Exception {
        var type = documents.types().stream().filter(entry -> entry.header().equals("Docsis")).findFirst().orElseThrow();
        var empty = documents.create(type, false);
        var reference = documents.create(type, true);
        for (String name : java.util.List.of("eRouterConfig", "eSTBConfig")) {
            assertTrue(documents.choices(empty, empty.root()).stream().anyMatch(entry -> entry.name().equals(name)), name);
            var parent = reference.root().childTlvs().stream().filter(node -> node.tlvName().equals(name)).findFirst().orElseThrow();
            assertEquals("", documents.editingIssue(type, documents.definition(reference, parent)));
            assertFalse(documents.choices(reference, reference.root()).stream().anyMatch(entry -> entry.name().equals(name)), name);
        }
    }

    @Test void erouterChildrenCanBeAddedAndEditedInBothDocsisFamilies() throws Exception {
        for (String header : java.util.List.of("Docsis", "Docsis-4.0")) {
            var document = documents.open(input(header + " { eRouterConfig; }"));
            var parent = document.root().childTlvs().getFirst();
            var child = documents.choices(document, parent).stream()
                    .filter(entry -> entry.name().equals("InitializationMode")).findFirst().orElseThrow();
            var inserted = documents.insert(document, parent, child, "3");
            var node = inserted.root().childTlvs().getFirst().childTlvs().getFirst();
            assertTrue(documents.canEdit(inserted, node), header);
            var edited = documents.replaceValue(inserted, node, "1");
            assertEquals("1", edited.root().childTlvs().getFirst().childTlvs().getFirst().encodedValueText());
        }
    }

    @Test void resolvesEmbeddedServicesThroughDocsis() throws Exception {
        String text = "Docsis { eRouterConfig { InitializationMode 3; } "
                + "eSTBConfig { VendorSpecificInformation { VendorID 001122; } } "
                + "eMTAConfig 0102aaff; }";
        var document = documents.open(input(text));
        assertEquals(java.util.List.of("eRouterConfig", "eSTBConfig", "eMTAConfig"),
                document.root().childTlvs().stream().map(OscarDocumentService.Node::tlvName).toList());
        assertEquals(text, document.text());
    }

    @Test void preservesSourceOrderCommentsAndRepeatedEntries() throws Exception {
        String text = "/* public */ Docsis { MaxCPEAllowed 3; NetworkAccess 1; MaxCPEAllowed 4; }";
        var document = documents.open(input(text));
        assertEquals(text, document.text());
        assertEquals(java.util.List.of("MaxCPEAllowed", "NetworkAccess", "MaxCPEAllowed"),
                document.root().childTlvs().stream().map(OscarDocumentService.Node::tlvName).toList());
        assertEquals("4", document.root().childTlvs().get(2).encodedValueText());
    }

    @Test void insertsOnlyAvailableEntriesWithoutChangingOriginalText() throws Exception {
        var configurationType = documents.types().stream().filter(entry -> entry.header().equals("Docsis")).findFirst().orElseThrow();
        var original = documents.create(configurationType, false);
        var definition = documents.choices(original, original.root()).stream()
                .filter(entry -> entry.name().equals("NetworkAccess")).findFirst().orElseThrow();
        var changed = documents.insert(original, original.root(), definition, "1");
        assertEquals(1, changed.root().childTlvs().size());
        assertTrue(changed.text().contains("NetworkAccess 1;"));
        assertTrue(original.root().childTlvs().isEmpty());
        assertThrows(InspectionException.class, () -> documents.insert(original, original.root(), definition, "1; NetworkAccess 0"));
        assertThrows(InspectionException.class, () -> documents.insert(original, original.root(), definition, "bad"));
    }

    @Test void savesTextAndRejectsExternalChanges() throws Exception {
        var original = documents.open(input("Docsis { NetworkAccess 1; }"));
        Path destination = temporary.resolve("saved.txt");
        var saved = documents.save(original, destination, null);
        assertEquals(original.text(), Files.readString(destination));
        assertEquals(destination, saved.path());
        Files.writeString(destination, "externally changed");
        assertThrows(InspectionException.class, () -> documents.save(saved, destination, saved.diskHash()));
        assertEquals("externally changed", Files.readString(destination));
    }

    @Test void generatesReferenceTemplateUsingOscarExporter() throws Exception {
        for (var configurationType : documents.types()) {
            var template = documents.create(configurationType, true);
            assertTrue(template.reference());
            String exported = new com.comcast.oscar.configurationfile.ConfigurationFileExport(configurationType.id())
                    .toPrettyPrint(com.comcast.oscar.configurationfile.ConfigurationFileExport.EXPORT_DEFAULT_TLV);
                var lexer = new com.comcast.oscar.parser.tlvLexer(new org.antlr.v4.runtime.ANTLRInputStream(exported));
                var header = lexer.nextToken();
                String normalized = exported.substring(0, header.getStartIndex()) + configurationType.header()
                    + exported.substring(header.getStopIndex() + 1);
                assertTrue(template.text().endsWith(normalized));
            var saved = documents.save(template, temporary.resolve(configurationType.header() + ".txt"), null);
            var reopened = documents.open(saved.path());
            assertEquals(template.text(), reopened.text());
            assertEquals(configurationType, reopened.configurationType());
            assertTrue(reopened.reference());
            assertFalse(reopened.root().childTlvs().isEmpty());
        }
    }

    @Test void insertsNestedChildrenAndRejectsStaleParents() throws Exception {
        var configurationType = documents.types().stream().filter(entry -> entry.header().equals("Docsis-4.0")).findFirst().orElseThrow();
        var empty = documents.create(configurationType, false);
        var container = documents.choices(empty, empty.root()).stream().filter(entry -> entry.path().equals(java.util.List.of(17)))
                .findFirst().orElseThrow();
        var withContainer = documents.open(input(configurationType.header() + " { " + container.name() + "; }"));
        var parent = withContainer.root().childTlvs().get(0);
        var child = documents.choices(withContainer, parent).stream().filter(entry -> entry.path().equals(java.util.List.of(17, 1)))
                .findFirst().orElseThrow();
        var changed = documents.insert(withContainer, parent, child, "2");
        assertEquals("2", changed.root().childTlvs().get(0).childTlvs().get(0).encodedValueText());
        assertEquals(2, OscarDocumentService.count(changed.root()));
        assertTrue(documents.choices(changed, changed.root().childTlvs().get(0)).stream().noneMatch(entry -> entry.equals(child)));
        assertThrows(InspectionException.class, () -> documents.insert(changed, parent, child, "2"));
        assertThrows(InspectionException.class, () -> documents.insert(withContainer, parent, child, "-1"));
        assertThrows(InspectionException.class, () -> documents.insert(withContainer, parent, child, "4294967296"));
    }

    @Test void keepsUnsupportedCodecsAndForeignDefinitionsUnavailable() throws Exception {
        var configurationType = documents.types().stream().filter(entry -> entry.header().equals("Docsis")).findFirst().orElseThrow();
        var empty = documents.create(configurationType, false);
        assertTrue(documents.choices(empty, empty.root()).stream().allMatch(entry -> documents.editingIssue(configurationType, entry).isEmpty()));
        var other = documents.types().stream().filter(entry -> entry.header().equals("Docsis-4.0")).findFirst().orElseThrow();
        var otherDocument = documents.create(other, false);
        var foreign = documents.choices(otherDocument, otherDocument.root()).get(0);
        assertThrows(InspectionException.class, () -> documents.insert(empty, empty.root(), foreign, "1"));
        assertThrows(InspectionException.class, () -> documents.open(input("Docsis { NotAnOscarTlv 1; }")));
    }

    @Test void preservesBomCrLfCommentsAndRejectsUnsafeSaveDestinations() throws Exception {
        String text = "\uFEFF/* public */\r\nDocsis {\r\n}\r\n";
        var original = documents.open(input(text));
        var entry = documents.choices(original, original.root()).stream().filter(definition -> definition.name().equals("NetworkAccess"))
                .findFirst().orElseThrow();
        var changed = documents.insert(original, original.root(), entry, "1");
        assertTrue(changed.text().startsWith(text.substring(0, text.indexOf('}'))));
        assertTrue(changed.text().endsWith("}\r\n"));
        assertFalse(changed.text().replace("\r\n", "").contains("\n"));
        var saved = documents.save(changed, original.path(), original.diskHash());
        assertEquals(changed.text(), documents.open(saved.path()).text());
        assertThrows(InspectionException.class, () -> documents.save(saved, temporary.resolve("absent/failed.txt"), null));
        assertEquals(changed.text(), Files.readString(saved.path()));
        if (Files.getFileStore(temporary).supportsFileAttributeView("posix")) {
            Path link = Files.createSymbolicLink(temporary.resolve("linked.txt"), saved.path());
            assertThrows(InspectionException.class, () -> documents.save(saved, link, saved.diskHash()));
        }
    }
}
