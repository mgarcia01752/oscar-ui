package io.github.mgarcia01752.oscarui.service;

import com.comcast.oscar.configurationfile.ConfigurationFile;
import com.comcast.oscar.configurationfile.ConfigurationFileImport;
import com.comcast.oscar.configurationfile.ConfigurationFileExport;
import com.comcast.oscar.dictionary.TlvDictionary;
import com.comcast.oscar.tlv.TlvBuilder;
import com.comcast.oscar.tlv.TlvVariableBinding;
import io.github.mgarcia01752.oscarui.service.OscarDocumentService.BinaryOrigin;
import io.github.mgarcia01752.oscarui.service.OscarDocumentService.FileType;
import io.github.mgarcia01752.oscarui.service.OscarDocumentService.Document;
import io.github.mgarcia01752.oscarui.service.OscarDocumentService.Node;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

/** Oscar binary adapter with source-file protection and atomic binary writes. */
public final class OscarBinaryService {
    private final OscarDocumentService documents;

    public OscarBinaryService(OscarDocumentService documents) { this.documents = documents; }

    /** Detect from content, never from the filename extension. Shared DOCSIS
     * encodings use the legacy-compatible profile; this is not a modem-version claim. */
    public Document openAuto(Path path) throws InspectionException {
        byte[] bytes = read(path);
        try {
            String text = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            if (text.codePoints().noneMatch(c -> Character.isISOControl(c)
                    && c != '\n' && c != '\r' && c != '\t')) {
                try { return documents.openText(path, bytes); }
                catch (InspectionException ignored) { /* Printable binary can also be valid UTF-8. */ }
            }
        } catch (java.nio.charset.CharacterCodingException ignored) {
            // Binary payloads need not be UTF-8.
        }
        var types = documents.types();
        // Try complete, lossless decodes, including nested TLVs, in compatibility order.
        for (String header : List.of("Docsis", "Docsis-4.0", "DPoE",
                "PacketCable-1.0", "PacketCable-1.5", "PacketCable-2.0")) {
            var type = types.stream().filter(entry -> entry.header().equals(header)).findFirst();
            if (type.isEmpty()) continue;
            try { return decode(path, bytes, type.get()); }
            catch (InspectionException ignored) { /* Try the next catalog. */ }
        }
        throw new InspectionException("Cannot identify a supported configuration without losing TLV data. "
                + "The file may be truncated or contain unsupported TLVs.");
    }

    private byte[] read(Path path) throws InspectionException {
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(OscarInspectionService.MAX_BYTES + 1);
            if (bytes.length == 0 || bytes.length > OscarInspectionService.MAX_BYTES) {
                throw new InspectionException("Select a nonempty configuration up to 1 MiB.");
            }
            return bytes;
        } catch (IOException error) {
            throw new InspectionException("Cannot read the configuration. Check the file and permissions.");
        }
    }

    public Document open(Path path, FileType type) throws InspectionException {
        if (!documents.types().contains(type)) throw new InspectionException("Unsupported configuration type.");
        return decode(path, read(path), type);
    }

    private Document decode(Path path, byte[] bytes, FileType type) throws InspectionException {
        try {
            Configurator.setLevel("com.comcast.oscar", Level.OFF);
            String table = switch (type.family()) {
                case "docsis40" -> TlvDictionary.DOCSIS_40_DICTIONARY_TABLE_NAME;
                case "packetcable" -> TlvDictionary.PACKET_CABLE_DICTIONARY_TABLE_NAME;
                case "dpoe" -> TlvDictionary.DPOE_DICTIONARY_TABLE_NAME;
                default -> TlvDictionary.DOCSIS_DICTIONARY_TABLE_NAME;
            };
            var dictionary = new TlvDictionary(table);
            var lengths = dictionary.getTopLevelByteLength();
            List<String> original = payloads(bytes, lengths);
            for (String entry : original) {
                int identifier = Integer.parseInt(entry.substring(0, 2), 16);
                if (!dictionary.getTopLevelTLV().contains(identifier)) {
                    throw new InspectionException("Unknown binary TLV; editable import would lose data.");
                }
                String name = dictionary.getTlvDefinition(identifier).getString("TlvName");
                if (name.equals("Snmp11") || name.equals("Snmp64")) {
                    throw new InspectionException("Editable binary import of SNMP statements is not supported yet.");
                }
            }
            var snapshot = new TlvBuilder();
            snapshot.add(new TlvVariableBinding(bytes, lengths));
            var exporter = new ConfigurationFileExport(snapshot, type.id());
            exporter.setDotTextOIDOutputFormat(false);
            String text = exporter.toPrettyPrint(false, true);
            if (text.length() > OscarInspectionService.MAX_BYTES) {
                throw new InspectionException("The decoded configuration exceeds the text size limit.");
            }
            Path origin = path.toAbsolutePath().normalize();
            var document = documents.decoded(text, type, new BinaryOrigin(origin,
                    java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)), bytes));
            if (containsSnmp(document.root())) throw new InspectionException("Editable binary import of SNMP statements is not supported yet.");
            var imported = new ConfigurationFileImport(new StringBuilder(document.text()));
            if (!original.equals(payloads(imported.toByteArray(), lengths))) {
                throw new InspectionException("Oscar cannot preserve this binary configuration as editable text; import cancelled.");
            }
            return document;
        } catch (RuntimeException | LinkageError | com.comcast.oscar.tlv.TlvException error) {
            throw new InspectionException("Oscar could not decode this binary configuration. Check its type and whether it is truncated or unsupported.");
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private List<String> payloads(byte[] bytes, Map<Integer, Integer> lengths)
            throws com.comcast.oscar.tlv.TlvException, InspectionException {
        var entries = new ArrayList<String>();
        for (int offset = 0; offset < bytes.length;) {
            int type = Byte.toUnsignedInt(bytes[offset]);
            if (type == 0) { offset++; continue; }
            if (type == 255) {
                for (int remaining = offset + 1; remaining < bytes.length; remaining++) {
                    if (bytes[remaining] != 0) throw new InspectionException("Unexpected data after the binary end marker.");
                }
                break;
            }
            int next = TlvBuilder.nextTLVIndex(bytes, offset, lengths.getOrDefault(type, 1));
            if (next <= offset || next > bytes.length) throw new InspectionException("Truncated binary TLV.");
            entries.add(HexFormat.of().formatHex(Arrays.copyOfRange(bytes, offset, next)));
            offset = next;
        }
        if (entries.isEmpty()) throw new InspectionException("No supported TLV entries in the binary configuration.");
        return entries;
    }

    public boolean requiresSharedSecret(Document document) {
        return !document.type().family().equals("packetcable");
    }

    public byte[] encode(Document document, String sharedSecret) throws InspectionException {
        if (requiresSharedSecret(document) && (sharedSecret == null || sharedSecret.isEmpty())) {
            throw new InspectionException("Set the shared secret in Settings > Global > Secrets before saving binary.");
        }
        if (containsSnmp(document.root())) {
            throw new InspectionException("Binary export of SNMP statements is not supported yet.");
        }
        try {
            // The pinned legacy compiler logs its key at DEBUG. Disable that
            // specific logger before passing session credentials to Oscar.
            Configurator.setLevel("com.comcast.oscar.compiler.DocsisCompiler", Level.OFF);
            String text = document.text();
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            var imported = new ConfigurationFileImport(new StringBuilder(text));
            if (imported.getConfigurationFileType() != document.type().id()) {
                throw new InspectionException("Oscar resolved a different configuration type.");
            }
            var compiled = new ConfigurationFile(document.type().id(), imported.getTlvBuilder(),
                    sharedSecret == null ? "" : sharedSecret);
            compiled.commit();
            byte[] bytes = compiled.toByteArray();
            if (bytes == null || bytes.length == 0) {
                throw new InspectionException("Oscar produced no binary output.");
            }
            return bytes;
        } catch (RuntimeException | LinkageError error) {
            // Upstream exception messages may contain input values; keep UI
            // diagnostics generic and never print the exception or credentials.
            throw new InspectionException("Oscar could not encode this configuration. Check its values, required TLVs, "
                    + "and the selected type. The destination was not changed.");
        }
    }

    public Path save(Document document, Path destination, String expectedHash, String sharedSecret)
            throws InspectionException {
        Path target = destination.toAbsolutePath().normalize();
        rejectTextDestination(document, target);
        byte[] bytes = encode(document, sharedSecret);
        rejectTextDestination(document, target);
        documents.saveBytes(target, bytes, expectedHash);
        return target;
    }

    /** Save the active binary, refreshing its snapshot only after an atomic write. */
    public Document saveDocument(Document document, Path destination, String expectedHash, String sharedSecret)
            throws InspectionException {
        Path target = destination.toAbsolutePath().normalize();
        rejectTextFile(document, target);
        byte[] bytes = encode(document, sharedSecret);
        Document saved = decode(target, bytes, document.type());
        rejectTextFile(document, target);
        documents.saveBytes(target, bytes, expectedHash);
        return saved;
    }

    private void rejectTextDestination(Document document, Path target) throws InspectionException {
        documents.rejectBinaryOrigin(document, target);
        rejectTextFile(document, target);
    }

    private void rejectTextFile(Document document, Path target) throws InspectionException {
        if (document.path() == null) return;
        try {
            if (target.equals(document.path()) || (Files.exists(target) && Files.exists(document.path())
                    && Files.isSameFile(target, document.path()))) {
                throw new InspectionException("Choose a different destination for binary output; keep the editable text file.");
            }
        } catch (IOException error) {
            throw new InspectionException("Cannot check the binary destination.", error);
        }
    }

    private boolean containsSnmp(Node node) {
        return node.name().equals("Snmp11") || node.name().equals("Snmp64")
                || node.children().stream().anyMatch(this::containsSnmp);
    }
}
