package io.oscarui.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BinaryOpeningTest {
    @TempDir Path temporary;
    private final OscarDocumentService documents = new OscarDocumentService();
    private final OscarBinaryService binary = new OscarBinaryService(documents);

    private OscarDocumentService.FileType type(String header) throws Exception {
        return documents.types().stream().filter(entry -> entry.header().equals(header)).findFirst().orElseThrow();
    }

    @Test void detectsContentAndSelectsCompatibleDocsisCatalogIncludingNestedExtensions() throws Exception {
        Path text = Files.writeString(temporary.resolve("text.bin"),
                "\uFEFF/* source */ Docsis-4.0 { NetworkAccess 1; }");
        assertEquals("Docsis-4.0", binary.openAuto(text).type().header());
        assertEquals(text, binary.openAuto(text).path());
        Path common = Files.write(temporary.resolve("common.txt"), HexFormat.of().parseHex("030101ff00"));
        assertEquals("Docsis", binary.openAuto(common).type().header());
        // Existing Oscar catalog fixtures: top-level 97 and nested 24.41.
        for (String hex : List.of("610101ff00", "1803290101ff00")) {
            Path newer = Files.write(temporary.resolve("newer.txt"), HexFormat.of().parseHex(hex));
            assertEquals("Docsis-4.0", binary.openAuto(newer).type().header());
        }
        for (String hex : List.of("03", "fa0101ff00", "1803290201ff00")) {
            Path broken = Files.write(temporary.resolve("broken.bin"), HexFormat.of().parseHex(hex));
            assertThrows(InspectionException.class, () -> binary.openAuto(broken));
        }
    }

    @Test void savesActiveBinaryAndDetectsExternalChanges() throws Exception {
        Path file = Files.write(temporary.resolve("active.bin"), HexFormat.of().parseHex("030101ff00"));
        var opened = binary.openAuto(file);
        var edited = documents.replaceValue(opened, opened.root().children().getFirst(), "0");
        var saved = binary.saveDocument(edited, file, opened.origin().diskHash(), "public-test-key");
        assertEquals(file, saved.origin().path());
        assertEquals(documents.destinationHash(file), saved.origin().diskHash());
        assertArrayEquals(Files.readAllBytes(file), saved.origin().bytes());
        assertEquals("0", binary.openAuto(file).root().children().getFirst().value());
        Files.writeString(file, "external modification");
        assertThrows(InspectionException.class, () ->
                binary.saveDocument(saved, file, saved.origin().diskHash(), "public-test-key"));
        assertEquals("external modification", Files.readString(file));
    }

    @Test void opensIndependentNetworkAccessBytesForEachDocsisEdition() throws Exception {
        byte[] bytes = HexFormat.of().parseHex("030101ff00");
        Path source = Files.write(temporary.resolve("misleading.txt"), bytes);
        for (String header : List.of("Docsis", "Docsis-4.0")) {
            var document = binary.open(source, type(header));
            assertNull(document.path());
            assertEquals(header, document.type().header());
            assertEquals("NetworkAccess", document.root().children().getFirst().name());
            assertEquals("1", document.root().children().getFirst().value());
            assertArrayEquals(bytes, document.origin().bytes());
            byte[] copy = document.origin().bytes();
            copy[0] = 0;
            assertArrayEquals(bytes, document.origin().bytes());
        }
        assertArrayEquals(bytes, Files.readAllBytes(source));
        assertThrows(InspectionException.class, () -> documents.open(source));
    }

    @Test void preservesOriginAcrossEditingTextSavingAndExportProtection() throws Exception {
        Path source = Files.write(temporary.resolve("source.bin"), HexFormat.of().parseHex("030101ff00"));
        var document = binary.open(source, type("Docsis"));
        var changed = documents.replaceValue(document, document.root().children().getFirst(), "0");
        assertSame(document.origin(), changed.origin());
        assertThrows(InspectionException.class, () -> documents.save(changed, source, documents.destinationHash(source)));
        assertThrows(InspectionException.class, () -> binary.save(changed, source, documents.destinationHash(source), "public-test-key"));
        Path text = temporary.resolve("decoded.txt");
        var saved = documents.save(changed, text, null);
        assertSame(document.origin(), saved.origin());
        assertEquals(changed.text(), documents.open(text).text());
        if (Files.getFileStore(temporary).supportsFileAttributeView("posix")) {
            Path alias = Files.createLink(temporary.resolve("alias.bin"), source);
            assertThrows(InspectionException.class, () -> documents.save(saved, alias, documents.destinationHash(alias)));
        }
    }

    @Test void rejectsMalformedUnknownEmptyOversizedAndTextInputs() throws Exception {
        for (String hex : List.of("", "03", "0302ff", "fa0101ff00", "030101ff01", "000000")) {
            Path source = Files.write(temporary.resolve("invalid.bin"), HexFormat.of().parseHex(hex));
            assertThrows(InspectionException.class, () -> binary.open(source, type("Docsis")));
        }
        Path text = Files.writeString(temporary.resolve("input.bin"), "Docsis { NetworkAccess 1; }");
        assertThrows(InspectionException.class, () -> binary.open(text, type("Docsis")));
        Path large = Files.write(temporary.resolve("large.bin"), new byte[OscarInspectionService.MAX_BYTES + 1]);
        assertThrows(InspectionException.class, () -> binary.open(large, type("Docsis")));
    }

    @Test void opensActualEncoderOutputWithoutASecret() throws Exception {
        var source = documents.open(Files.writeString(temporary.resolve("source.txt"), "Docsis { NetworkAccess 1; }"));
        Path file = Files.write(temporary.resolve("encoded.cfg"), binary.encode(source, "public-test-key"));
        var decoded = binary.open(file, source.type());
        assertEquals("1", decoded.root().children().stream().filter(node -> node.name().equals("NetworkAccess"))
                .findFirst().orElseThrow().value());
    }

    @Test void opensSupportedFamiliesAndReportsUnsupportedGeneratedSnmp() throws Exception {
        for (String header : List.of("Docsis-4.0", "DPoE", "PacketCable-1.0", "PacketCable-1.5", "PacketCable-2.0")) {
            String entries = header.startsWith("PacketCable") ? "TelephonyConfigFileBeginEnd 1;"
                    : "NetworkAccess 1;" + (header.equals("Docsis-4.0") ? """
                      UpstreamServiceFlow { ServiceFlowReference 1; QosParameterSetType 7; }
                      DownstreamServiceFlow { ServiceFlowReference 2; QosParameterSetType 7; }
                      """ : "");
            var source = documents.open(Files.writeString(temporary.resolve("family.txt"), header + " { " + entries + " }"));
            Path file = Files.write(temporary.resolve("family.bin"), binary.encode(source, "public-test-key"));
            if (header.startsWith("PacketCable")) {
                var error = assertThrows(InspectionException.class, () -> binary.open(file, source.type()));
                assertTrue(error.getMessage().contains("SNMP"));
                continue;
            }
            var decoded = binary.open(file, source.type());
            assertEquals(header, decoded.type().header());
            assertFalse(decoded.root().children().isEmpty());
        }
    }

    @Test void keepsRepeatedEntriesInSourceOrderAndProtectsOriginAfterInsertionAndDeletion() throws Exception {
        Path source = Files.write(temporary.resolve("repeated.bin"), HexFormat.of().parseHex("120103030101120104ff00"));
        var document = binary.open(source, type("Docsis"));
        assertEquals(List.of("MaxCPEAllowed", "NetworkAccess", "MaxCPEAllowed"),
                document.root().children().stream().map(OscarDocumentService.Node::name).toList());
        assertEquals(List.of("3", "1", "4"), document.root().children().stream().map(OscarDocumentService.Node::value).toList());
        var removed = documents.delete(document, document.root().children().get(1));
        assertSame(document.origin(), removed.origin());
        var definition = documents.choices(removed, removed.root()).stream()
                .filter(entry -> entry.name().equals("NetworkAccess")).findFirst().orElseThrow();
            var inserted = documents.insert(removed, removed.root(), definition, "0");
        assertSame(document.origin(), inserted.origin());
        assertThrows(InspectionException.class, () -> documents.save(inserted, source, documents.destinationHash(source)));
    }
}