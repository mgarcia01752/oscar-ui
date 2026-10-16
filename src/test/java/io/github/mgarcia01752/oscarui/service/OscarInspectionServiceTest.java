package io.github.mgarcia01752.oscarui.service;

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
        assertTrue(types.stream().anyMatch(type -> type.header().equals("Docsis") && type.family().equals("docsis")));
        assertTrue(types.stream().anyMatch(type -> type.header().equals("Docsis-4.0") && type.family().equals("docsis40")));
        assertTrue(types.stream().anyMatch(type -> type.header().equals("DPoE")));
        for (var type : types) {
            var created = documents.create(type, false);
            assertEquals(type, created.type());
            assertTrue(created.root().children().isEmpty());
            assertNull(created.path());
        }
    }

    @Test void preservesSourceOrderCommentsAndRepeatedEntries() throws Exception {
        String text = "/* public */ Docsis { MaxCPEAllowed 3; NetworkAccess 1; MaxCPEAllowed 4; }";
        var document = documents.open(input(text));
        assertEquals(text, document.text());
        assertEquals(java.util.List.of("MaxCPEAllowed", "NetworkAccess", "MaxCPEAllowed"),
                document.root().children().stream().map(OscarDocumentService.Node::name).toList());
        assertEquals("4", document.root().children().get(2).value());
    }

    @Test void insertsOnlyAvailableEntriesWithoutChangingOriginalText() throws Exception {
        var type = documents.types().stream().filter(entry -> entry.header().equals("Docsis")).findFirst().orElseThrow();
        var original = documents.create(type, false);
        var definition = documents.choices(original, original.root()).stream()
                .filter(entry -> entry.name().equals("NetworkAccess")).findFirst().orElseThrow();
        var changed = documents.insert(original, original.root(), definition, "1");
        assertEquals(1, changed.root().children().size());
        assertTrue(changed.text().contains("NetworkAccess 1;"));
        assertTrue(original.root().children().isEmpty());
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
        for (var type : documents.types()) {
            var template = documents.create(type, true);
            assertTrue(template.reference());
            String exported = new com.comcast.oscar.configurationfile.ConfigurationFileExport(type.id())
                    .toPrettyPrint(com.comcast.oscar.configurationfile.ConfigurationFileExport.EXPORT_DEFAULT_TLV);
                var lexer = new com.comcast.oscar.parser.tlvLexer(new org.antlr.v4.runtime.ANTLRInputStream(exported));
                var header = lexer.nextToken();
                String normalized = exported.substring(0, header.getStartIndex()) + type.header()
                    + exported.substring(header.getStopIndex() + 1);
                assertTrue(template.text().endsWith(normalized));
            var saved = documents.save(template, temporary.resolve(type.header() + ".txt"), null);
            var reopened = documents.open(saved.path());
            assertEquals(template.text(), reopened.text());
            assertEquals(type, reopened.type());
            assertTrue(reopened.reference());
            assertFalse(reopened.root().children().isEmpty());
        }
    }

    @Test void insertsNestedChildrenAndRejectsStaleParents() throws Exception {
        var type = documents.types().stream().filter(entry -> entry.header().equals("Docsis-4.0")).findFirst().orElseThrow();
        var empty = documents.create(type, false);
        var container = documents.choices(empty, empty.root()).stream().filter(entry -> entry.path().equals(java.util.List.of(17)))
                .findFirst().orElseThrow();
        var withContainer = documents.insert(empty, empty.root(), container, "");
        var parent = withContainer.root().children().get(0);
        var child = documents.choices(withContainer, parent).stream().filter(entry -> entry.path().equals(java.util.List.of(17, 1)))
                .findFirst().orElseThrow();
        var changed = documents.insert(withContainer, parent, child, "2");
        assertEquals("2", changed.root().children().get(0).children().get(0).value());
        assertEquals(2, OscarDocumentService.count(changed.root()));
        assertTrue(documents.choices(changed, changed.root().children().get(0)).stream().noneMatch(entry -> entry.equals(child)));
        assertThrows(InspectionException.class, () -> documents.insert(changed, parent, child, "2"));
        assertThrows(InspectionException.class, () -> documents.insert(withContainer, parent, child, "-1"));
        assertThrows(InspectionException.class, () -> documents.insert(withContainer, parent, child, "4294967296"));
    }

    @Test void keepsUnsupportedCodecsAndForeignDefinitionsUnavailable() throws Exception {
        var type = documents.types().stream().filter(entry -> entry.header().equals("Docsis")).findFirst().orElseThrow();
        var empty = documents.create(type, false);
        assertTrue(documents.choices(empty, empty.root()).stream().allMatch(entry -> documents.editingIssue(type, entry).isEmpty()));
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
