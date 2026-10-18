package io.oscarui.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OscarBinaryServiceTest {
    private static final String LAB_KEY = "public-test-key";
    @TempDir Path temporary;
    private final OscarDocumentService documents = new OscarDocumentService();
    private final OscarBinaryService binary = new OscarBinaryService(documents);

    private OscarDocumentService.Document open(String text) throws Exception {
        return documents.open(Files.writeString(temporary.resolve("source.txt"), text));
    }

    @Test void binaryImportIncludesOscarsTopLevelAndNestedComments() throws Exception {
        byte[] bytes = HexFormat.of().parseHex("030101190401020001ff00");
        var document = binary.openAuto(Files.write(temporary.resolve("comments.bin"), bytes));
        assertTrue(document.text().contains("/* TLV: [3]"));
        assertTrue(document.text().contains("/* TLV: [25.1]"));
        assertArrayEquals(bytes, document.origin().bytes());
        assertEquals(java.util.List.of(25, 1), document.root().childTlvs().get(1).childTlvs().getFirst().tlvPath());
    }

    @Test void decodesInMemoryWithAnExplicitTypeWithoutCredentials() throws Exception {
        var snapshot = new com.comcast.oscar.tlv.TlvBuilder();
        snapshot.add(new com.comcast.oscar.utilities.HexString(HexFormat.of().parseHex("030101ff00")));
        var exporter = new com.comcast.oscar.configurationfile.ConfigurationFileExport(snapshot, 4);
        String text = exporter.toPrettyPrint(false, true);
        assertEquals(4, exporter.getConfigurationFileType());
        assertTrue(text.contains("NetworkAccess"));
    }

    @Test void writesEncoderBytesWithIndependentlyCalculatedLegacyMics() throws Exception {
        var source = open("\uFEFF/* keep */ Docsis { NetworkAccess 1; }");
        byte[] encoded = binary.encode(source, LAB_KEY);
        assertArrayEquals(expectedDocsis(HexFormat.of().parseHex("030101"), new byte[0]), encoded);
        var destination = temporary.resolve("output.bin");
        binary.save(source, destination, null, LAB_KEY);
        assertArrayEquals(encoded, Files.readAllBytes(destination));
        assertEquals(source.text(), Files.readString(source.path()));
        var changedKey = binary.encode(source, "another-public-test-key");
        assertFalse(Arrays.equals(encoded, changedKey));
    }

    @Test void exportsOriginalSnmpValuesWhileMaskedAndReopensBinary() throws Exception {
        var sourcePath = Path.of("cm-config/examples/docsis/1gbps-ds-100mbps-us.txt");
        var source = binary.openAuto(sourcePath);
        var destination = temporary.resolve("snmp.bin");
        byte[] expected = binary.encode(source, LAB_KEY);
        var masked = new SnmpSecretMask(true, List.of());
        var preview = new ConfigurationTextPreview(source.text(), true, masked.replacements(source));
        assertFalse(preview.text().contains("lab-user"));
        assertArrayEquals(expected, binary.encode(source, LAB_KEY));
        var saved = binary.saveDocument(source, destination, null, LAB_KEY);
        assertArrayEquals(expected, Files.readAllBytes(destination));
        assertEquals(source.text(), Files.readString(sourcePath));
        var reopened = binary.openAuto(destination);
        assertEquals(16, reopened.root().childTlvs().stream()
                .filter(node -> node.tlvName().equals("Snmp11")).count());
        assertTrue(reopened.text().contains("lab-user"));
        assertTrue(reopened.text().contains("lab-group"));
        assertTrue(reopened.text().contains("public"));
        assertTrue(reopened.text().contains("private"));
        assertFalse(reopened.text().contains("********"));
        var invalid = new OscarDocumentService.Document(source.path(),
                source.text().replace("IpAddress \"0.0.0.0\"", "IpAddress \"not-an-address\""),
                source.configurationType(), source.root(), source.diskHash(), false);
        assertThrows(InspectionException.class,
                () -> binary.saveDocument(invalid, destination, saved.origin().diskHash(), LAB_KEY));
        assertArrayEquals(expected, Files.readAllBytes(destination));

        assertArrayEquals(expected, binary.encode(saved, LAB_KEY));
        binary.saveDocument(saved, destination, saved.origin().diskHash(), LAB_KEY);
        assertArrayEquals(expected, Files.readAllBytes(destination));
    }

    @Test void usesDocsis40TypeAndItsEncoderRequirements() throws Exception {
        var source = open("""
                Docsis-4.0 {
                  NetworkAccess 1;
                  UpstreamServiceFlow { ServiceFlowReference 1; QosParameterSetType 7; }
                  DownstreamServiceFlow { ServiceFlowReference 2; QosParameterSetType 7; }
                }
                """);
        byte[] flowBytes = HexFormat.of().parseHex("180701020001060107190701020002060107");
        assertArrayEquals(expectedDocsis(HexFormat.of().parseHex("030101"), flowBytes), binary.encode(source, LAB_KEY));
        var invalid = open("Docsis-4.0 { NetworkAccess 1; }");
        Path existing = Files.writeString(temporary.resolve("existing.bin"), "keep destination");
        assertThrows(InspectionException.class, () -> binary.save(invalid, existing, documents.destinationHash(existing), LAB_KEY));
        assertEquals("keep destination", Files.readString(existing));
    }

    @Test void dispatchesDpoeAndEachPacketCableHeaderThroughOscar() throws Exception {
        var dpoe = open("DPoE { NetworkAccess 1; }");
        assertArrayEquals(expectedDocsis(HexFormat.of().parseHex("030101"), new byte[0]), binary.encode(dpoe, LAB_KEY));
        for (String header : List.of("PacketCable-1.0", "PacketCable-1.5", "PacketCable-2.0")) {
            var source = open(header + " { TelephonyConfigFileBeginEnd 1; }");
            assertFalse(binary.requiresSharedSecret(source));
            byte[] bytes = binary.encode(source, null);
            assertTrue(bytes.length > 3);
            assertEquals(254, Byte.toUnsignedInt(bytes[0]));
            assertFalse(Arrays.equals(source.text().getBytes(StandardCharsets.UTF_8), bytes));
        }
    }

    @Test void preservesFilesOnConflictMissingKeySnmpAndUnsafeDestination() throws Exception {
        var source = open("Docsis { NetworkAccess 1; }");
        Path output = Files.writeString(temporary.resolve("out.bin"), "old output");
        String oldHash = documents.destinationHash(output);
        Files.writeString(output, "external change");
        assertThrows(InspectionException.class, () -> binary.save(source, output, oldHash, LAB_KEY));
        assertEquals("external change", Files.readString(output));
        assertThrows(InspectionException.class, () -> binary.encode(source, ""));
        assertThrows(InspectionException.class, () -> binary.save(source, source.path(), source.diskHash(), LAB_KEY));
        assertThrows(InspectionException.class, () -> binary.save(source, temporary.resolve("missing/out.bin"), null, LAB_KEY));
        if (Files.getFileStore(temporary).supportsFileAttributeView("posix")) {
            Path alias = Files.createLink(temporary.resolve("alias.bin"), source.path());
            assertThrows(InspectionException.class, () -> binary.save(source, alias, source.diskHash(), LAB_KEY));
            Path symlink = Files.createSymbolicLink(temporary.resolve("link.bin"), output);
            assertThrows(InspectionException.class, () -> binary.save(source, symlink, documents.destinationHash(output), LAB_KEY));
        }
        assertEquals(source.text(), Files.readString(source.path()));
        var snmp = open("Docsis { Snmp11 1.3.6.1.2.1.1.5.0 OctetString \"public\"; }");
        byte[] encodedSnmp = binary.encode(snmp, LAB_KEY);
        assertTrue(encodedSnmp.length > 0);
        assertFalse(Files.exists(temporary.resolve("db")));
        try (var files = Files.list(temporary)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().startsWith(".oscar-ui-")));
        }
    }

    @Test void disablesThePinnedCompilersSecretDebugLog() throws Exception {
        var source = open("Docsis { NetworkAccess 1; }");
        String logger = "com.comcast.oscar.compiler.DocsisCompiler";
        org.apache.logging.log4j.core.config.Configurator.setLevel(logger, org.apache.logging.log4j.Level.DEBUG);
        binary.encode(source, LAB_KEY);
        assertFalse(org.apache.logging.log4j.LogManager.getLogger(logger).isDebugEnabled());
    }

    @Test void savesWithoutRequiringOptionalDraftChecks() throws Exception {
        var source = open("Docsis { NetworkAccess 1; }");
        var checks = DraftChecks.inspect(documents, source, true);
        assertFalse(DraftChecks.permitsBinarySave(checks));
        assertFalse(DraftChecks.permitsBinarySave(List.of()));
        Path destination = Files.writeString(temporary.resolve("export.bin"), "replace existing file");
        binary.save(source, destination, documents.destinationHash(destination), LAB_KEY);
        assertArrayEquals(binary.encode(source, LAB_KEY), Files.readAllBytes(destination));
        assertTrue(checks.stream().anyMatch(check -> check.id().equals("minimum-rules") && !check.status().equals("PASS")));
    }

    // MULPI 3.1 I21 D.1.3.1 / D.2.1, pp. 736-738; 4.0 I12 pp. 860-862.
    // This fixture has no E-MIC. CM digest covers parameters in file order;
    // legacy CMTS order is NetworkAccess, CmMic, UpstreamSF, DownstreamSF.
    private byte[] expectedDocsis(byte[] network, byte[] flows) throws Exception {
        var parameters = new ByteArrayOutputStream();
        parameters.writeBytes(network);
        parameters.writeBytes(flows);
        byte[] mic = MessageDigest.getInstance("MD5").digest(parameters.toByteArray());
        var cmMic = new ByteArrayOutputStream();
        cmMic.writeBytes(new byte[]{6, 16});
        cmMic.writeBytes(mic);
        var hmacInput = new ByteArrayOutputStream();
        hmacInput.writeBytes(network);
        hmacInput.writeBytes(cmMic.toByteArray());
        hmacInput.writeBytes(flows);
        var hmac = Mac.getInstance("HmacMD5");
        hmac.init(new SecretKeySpec(LAB_KEY.getBytes(StandardCharsets.UTF_8), "HmacMD5"));
        parameters.writeBytes(cmMic.toByteArray());
        parameters.writeBytes(new byte[]{7, 16});
        parameters.writeBytes(hmac.doFinal(hmacInput.toByteArray()));
        parameters.write(255);
        parameters.write(0);
        while (parameters.size() % 4 != 0) parameters.write(0);
        return parameters.toByteArray();
    }
}
