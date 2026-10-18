package io.oscarui.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnmpSecretMaskTest {
    @TempDir Path temporary;
    private final OscarDocumentService documents = new OscarDocumentService();
    private final OscarBinaryService binary = new OscarBinaryService(documents);
    private final SnmpSecretMask masking = new SnmpSecretMask(true, List.of());

    private OscarDocumentService.Document open(String source) throws Exception {
        return binary.openAuto(Files.writeString(temporary.resolve("source.txt"), source));
    }

    @Test void masksCommunitiesAndAncestorHexWithoutRevealingLengthOrChangingSavedValues() throws Exception {
        String previousHex = null;
        for (String secret : List.of("xyz", "longer-lab-only-secret")) {
            String source = "Docsis { /* " + secret + " */ SNMPv1v2cCoexistenceConfig { "
                    + "SNMPv1v2cCommunityName " + secret + "; } NetworkAccess 1; }";
            var document = open(source);
            var parent = document.root().childTlvs().getFirst();
            var child = parent.childTlvs().getFirst();
            assertEquals("********", masking.value(child));
            var preview = new ConfigurationTextPreview(source, true, masking.replacements(document));
            assertFalse(preview.text().contains(secret));
            assertTrue(preview.text().contains("/* ******** */"));
            assertEquals("SNMPv1v2cCommunityName ********;", preview.text().substring(
                    preview.displayOffset(child.sourceStartOffset()), preview.displayOffset(child.sourceEndExclusive())));
            var hex = masking.hex(parent, new SelectedTlvHexService().inspect(document, parent));
            assertEquals("FF FF FF FF", hex.hex());
            assertEquals(hex.hex(), hex.children().getFirst().hex());
            if (previousHex != null) {
                assertEquals(previousHex, hex.hex());
            }
            previousHex = hex.hex();
            assertEquals(source, document.text());
            var saved = documents.save(document, temporary.resolve("saved-" + secret.length() + ".txt"), null);
            assertEquals(source, Files.readString(saved.path()));
            var disabled = new SnmpSecretMask(false, List.of());
            assertEquals(child.encodedValueText(), disabled.value(child));
            assertTrue(disabled.replacements(document).isEmpty());
        }
    }

    @Test void coversUsmKeyChangesAndIdentityIndexesAndVacmWithNumericOids() throws Exception {
        for (String oid : List.of("1.3.6.1.6.3.15.1.2.2.1.6.3.97.98.99", "1.3.6.1.6.3.15.1.2.2.1.7.1",
                "1.3.6.1.6.3.15.1.2.2.1.9.1", "1.3.6.1.6.3.15.1.2.2.1.10.1",
                "1.3.6.1.6.3.15.1.2.2.1.5.3.97.98.99", "1.3.6.1.6.3.16.1.2.1.3.3.97.98.99",
                "1.3.6.1.6.3.18.1.1.1.2.3.97.98.99", "1.3.6.1.3.101.1.1.2.1.1.3.97.98.99",
                "1.3.6.1.3.101.1.2.1.1.4.1")) {
            var document = open("Docsis { Snmp11 " + oid + " OctetString \"synthetic-key\"; }");
            var node = document.root().childTlvs().getFirst();
            assertTrue(masking.protects(node), oid);
            var preview = new ConfigurationTextPreview(document.text(), true, masking.replacements(document));
            assertFalse(preview.text().contains("synthetic-key"));
            assertFalse(preview.text().contains("97.98.99"));
            assertFalse(masking.value(node).contains(oid));
        }
    }

    @Test void customRulesResolveAliasesMatchOnlyOidComponentsAndRejectInvalidInput() throws Exception {
        var named = SnmpSecretMask.resolveRule("docsDevNmAccessIp");
        assertEquals(SnmpSecretMask.resolveRule(".1.3.6.1.2.1.69.1.2.1.2").oid(), named.oid());
        var custom = new SnmpSecretMask(true, List.of(named, SnmpSecretMask.resolveRule("1.3.6.1.4.1.55555.2")));
        var document = open("Docsis { Snmp11 docsDevNmAccessIp.1 IpAddress \"192.0.2.1\"; "
                + "Snmp11 1.3.6.1.4.1.55555.2.1 OctetString \"vendor-secret\"; "
                + "Snmp11 1.3.6.1.4.1.55555.20.1 OctetString \"visible\"; }");
        assertTrue(custom.protects(document.root().childTlvs().get(0)));
        assertTrue(custom.protects(document.root().childTlvs().get(1)));
        assertFalse(custom.protects(document.root().childTlvs().get(2)));
        for (String invalid : List.of("", "-m ALL", "1.3..6", "1.40.1", "1.3.4294967296", "bad;command")) {
            assertThrows(IllegalArgumentException.class, () -> SnmpSecretMask.resolveRule(invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> SnmpSecretMask.resolveRule("oscarUiUnknownObjectForTest"));
    }

    @Test void selectionMappingSurvivesCrLfCommentsAndMaskLengthChanges() throws Exception {
        String source = "\uFEFFDocsis {\r\n Snmp11 docsDevNmAccessCommunity.1 OctetString \"lab-community\";"
                + " /* TLV: [11]*/\r\n NetworkAccess 1;\r\n}";
        var document = open(source);
        var preview = new ConfigurationTextPreview(source, false, masking.replacements(document));
        var network = document.root().childTlvs().getLast();
        assertEquals("NetworkAccess 1;", preview.text().substring(preview.displayOffset(network.sourceStartOffset()),
                preview.displayOffset(network.sourceEndExclusive())));
        assertFalse(preview.text().contains("lab-community"));
        assertFalse(preview.text().contains("TLV:"));
        assertFalse(preview.text().contains("\r"));
    }

    @Test void inspectionMasksOriginalBinaryEvenAfterSensitiveTlvIsDeletedInEditor() throws Exception {
        var source = open("Docsis { SNMPv1v2cCoexistenceConfig { SNMPv1v2cCommunityName lab-original; } NetworkAccess 1; }");
        byte[] bytes = binary.encode(source, "synthetic-shared-key");
        var document = binary.open(Files.write(temporary.resolve("source.bin"), bytes), source.configurationType());
        var edited = documents.delete(document, document.root().childTlvs().getFirst());
        String listing = binary.maskedInspection(edited, masking);
        assertTrue(listing.contains("SNMPv1v2cCoexistenceConfig | FF FF FF FF"));
        assertTrue(listing.contains("NetworkAccess"));
        assertFalse(listing.contains("6C:61:62"));
        assertArrayEquals(bytes, Files.readAllBytes(document.origin().path()));
    }
}
