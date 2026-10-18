package io.oscarui.service;

import com.comcast.oscar.configurationfile.ConfigurationFile;
import static com.comcast.oscar.cablelabsdefinitions.Constants.PAD;
import static com.comcast.oscar.cablelabsdefinitions.Constants.END_OF_FILE;
import com.comcast.oscar.configurationfile.ConfigurationFileImport;
import com.comcast.oscar.configurationfile.ConfigurationFileExport;
import com.comcast.oscar.dictionary.TlvDictionary;
import com.comcast.oscar.tlv.TlvBuilder;
import com.comcast.oscar.tlv.TlvVariableBinding;
import com.comcast.oscar.utilities.HexDump;
import com.comcast.oscar.utilities.HexString;
import io.oscarui.service.OscarDocumentService.BinaryOrigin;
import io.oscarui.service.OscarDocumentService.FileType;
import io.oscarui.service.OscarDocumentService.Document;
import io.oscarui.service.OscarDocumentService.Node;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

/** Oscar binary adapter with source-file protection and atomic binary writes. */
public final class OscarBinaryService {
    private final OscarDocumentService documents;

    /**
     * Creates a binary adapter using the supplied document service.
     *
     * <p>The adapter shares that service for parsing, origin protection, and atomic writes.
     *
     * @param documents non-null document service shared by the binary adapter
     */
    public OscarBinaryService(OscarDocumentService documents) { this.documents = documents; }

    /**
     * Detects a configuration from its contents and opens a supported document.
     *
     * <p>Tries UTF-8 text before lossless binary decodes. Shared DOCSIS encodings
     * use the legacy-compatible profile; this does not identify a modem version.
     *
     * @param path non-null input file path; the extension does not select a decoder
     * @return parsed document retaining its source identity
     * @throws InspectionException if reading or every supported decode fails
     */
    public Document openAuto(Path path) throws InspectionException {
        byte[] bytes = read(path);
        try {
            String text = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            if (text.codePoints().noneMatch(c -> Character.isISOControl(c)
                    && c != '\n' && c != '\r' && c != '\t')) {
                byte[] encoded = OscarTextCompileCheck.compile(text);
                return documents.openCompiledText(path, text, bytes, encoded);
            }
        } catch (java.nio.charset.CharacterCodingException ignored) {
            // Binary payloads need not be UTF-8.
        }
        var types = documents.types();
        // Try complete, lossless decodes, including nested TLVs, in compatibility order.
        for (String header : List.of("Docsis", "Docsis-4.0", "DPoE",
                "PacketCable-1.0", "PacketCable-1.5", "PacketCable-2.0")) {
            var candidateType = types.stream().filter(entry -> entry.header().equals(header)).findFirst();
            if (candidateType.isEmpty()) {
                continue;
            }
            try { return decode(path, bytes, candidateType.get()); }
            catch (InspectionException ignored) { /* Try the next catalog. */ }
        }
        throw new InspectionException("Cannot identify a supported configuration without losing TLV data. "
                + "The file may be truncated or contain unsupported TLVs.");
    }

    /**
     * Decodes a binary configuration with the explicitly selected Oscar type.
     *
     * <p>Rejects unknown or lossy imports and leaves the input file unchanged.
     *
     * @param path source or destination filesystem path; must not be null
     * @param configurationType non-null supported Oscar configuration type
     * @return editable decoded document with its original binary snapshot
     * @throws InspectionException if the file cannot be read, parsed, or imported without unsupported data
     */
    public Document open(Path path, FileType configurationType) throws InspectionException {
        if (!documents.types().contains(configurationType)) {
            throw new InspectionException("Unsupported configuration type.");
        }
        return decode(path, read(path), configurationType);
    }

    /**
     * Determines whether the configuration family uses the DOCSIS shared secret.
     *
     * @param document non-null document snapshot to inspect or modify
     * @return false for PacketCable; true for other supported families
     */
    public boolean requiresSharedSecret(Document document) {
        return !document.configurationType().family().equals("packetcable");
    }

    /**
     * Compiles the document through Oscar without writing a destination file.
     *
     * <p>Disables the upstream compiler logger before supplying session credentials.
     *
     * @param document non-null document snapshot to inspect or modify
     * @param sharedSecret session secret; required for families that use it
     * @return nonempty compiled configuration bytes
     * @throws InspectionException if credentials, supported values, or Oscar compilation are insufficient
     */
    public byte[] encode(Document document, String sharedSecret) throws InspectionException {
        if (requiresSharedSecret(document) && (sharedSecret == null || sharedSecret.isEmpty())) {
            throw new InspectionException("Set the shared secret in Settings > Secrets before saving binary.");
        }
        if (containsSnmp(document.root())) {
            throw new InspectionException("Binary export of SNMP statements is not supported yet.");
        }
        try {
            // The pinned legacy compiler logs its key at DEBUG. Disable that
            // specific logger before passing session credentials to Oscar.
            Configurator.setLevel("com.comcast.oscar.compiler.DocsisCompiler", Level.OFF);
            String text = document.text();
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            var imported = new ConfigurationFileImport(new StringBuilder(text));
            if (imported.getConfigurationFileType() != document.configurationType().id()) {
                throw new InspectionException("Oscar resolved a different configuration type.");
            }
            var compiled = new ConfigurationFile(document.configurationType().id(), imported.getTlvBuilder(),
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

    /**
     * Exports compiled binary bytes with destination and conflict protection.
     *
     * <p>Preserves the editable source and imported binary origin.
     *
     * @param document non-null document snapshot to inspect or modify
     * @param destination non-null destination file path
     * @param expectedHash expected destination SHA-256, or null when no file should exist
     * @param sharedSecret session secret; required for families that use it
     * @return normalized destination path after a successful atomic write
     * @throws InspectionException if encoding, destination protection, conflict checks, or the atomic write fail
     */
    public Path save(Document document, Path destination, String expectedHash, String sharedSecret)
            throws InspectionException {
        Path target = destination.toAbsolutePath().normalize();
        rejectTextDestination(document, target);
        byte[] bytes = encode(document, sharedSecret);
        rejectTextDestination(document, target);
        documents.saveBytes(target, bytes, expectedHash);
        return target;
    }

    /**
     * Saves the active binary and returns a freshly decoded snapshot.
     *
     * <p>Checks round-trip support before writing and protects the text source.
     * The imported binary may be replaced only after destination conflict checks.
     *
     * @param document non-null configuration snapshot to encode
     * @param destination non-null binary destination path
     * @param expectedHash expected SHA-256, or null for a new destination
     * @param sharedSecret session secret; required for families that use it
     * @return decoded snapshot associated with the successfully saved binary
     * @throws InspectionException if encoding, decoding, protection, or writing fails
     */
    public Document saveDocument(Document document, Path destination, String expectedHash, String sharedSecret)
            throws InspectionException {
        Path target = destination.toAbsolutePath().normalize();
        rejectTextFile(document, target);
        byte[] bytes = encode(document, sharedSecret);
        Document saved = decode(target, bytes, document.configurationType());
        rejectTextFile(document, target);
        documents.saveBytes(target, bytes, expectedHash);
        return saved;
    }

    /**
     * Formats the active binary snapshot using Oscar's hex/ASCII formatter.
     *
     * <p>Does not read the file again or encode unsaved edits.
     *
     * @param document active binary document, with no active text path
     * @return Oscar's offset, hexadecimal, and ASCII listing
     * @throws InspectionException if the document is not an active binary
     */
    public String hexDump(Document document) throws InspectionException {
        return HexDump.dumpHexString(inspectionBytes(document));
    }

    /**
     * Lists top-level TLVs from the active binary snapshot in source order.
     *
     * <p>Uses Oscar's dictionary length widths, TLV boundaries, and hex formatter.
     * Padding and the end marker are omitted; nested TLVs remain in parent bytes.
     * Unsaved edits are not encoded and the source file is not read again.
     *
     * @param document active binary document, with no active text path
     * @return one type-labelled hexadecimal row per top-level TLV instance
     * @throws InspectionException if no binary is active or its TLVs cannot be listed
     */
    public String tlvBreakdown(Document document) throws InspectionException {
        byte[] bytes = inspectionBytes(document);
        try {
            var listing = new StringBuilder();
            for (String payload : payloads(bytes, dictionary(document.configurationType()).getTopLevelByteLength())) {
                int tlvTypeCode = Integer.parseInt(payload.substring(0, 2), 16);
                listing.append("[Type:").append(tlvTypeCode).append("] ")
                        .append(new HexString(HexFormat.of().parseHex(payload)).toString(":")).append('\n');
            }
            return listing.toString();
        } catch (com.comcast.oscar.tlv.TlvException | RuntimeException error) {
            throw new InspectionException("Oscar could not produce the binary TLV breakdown.");
        }
    }

    private byte[] inspectionBytes(Document document) throws InspectionException {
        if (document == null || document.path() != null || document.origin() == null) {
            throw new InspectionException("Open a binary configuration to inspect its bytes.");
        }
        return document.origin().bytes();
    }

    private TlvDictionary dictionary(FileType configurationType) {
        String table = switch (configurationType.family()) {
            case "docsis40" -> TlvDictionary.DOCSIS_40_DICTIONARY_TABLE_NAME;
            case "packetcable" -> TlvDictionary.PACKET_CABLE_DICTIONARY_TABLE_NAME;
            case "dpoe" -> TlvDictionary.DPOE_DICTIONARY_TABLE_NAME;
            default -> TlvDictionary.DOCSIS_DICTIONARY_TABLE_NAME;
        };
        return new TlvDictionary(table);
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

    private Document decode(Path path, byte[] bytes, FileType configurationType) throws InspectionException {
        try {
            Configurator.setLevel("com.comcast.oscar", Level.OFF);
            var dictionary = dictionary(configurationType);
            var lengthFieldBytesByTlvType = dictionary.getTopLevelByteLength();
            List<String> original = payloads(bytes, lengthFieldBytesByTlvType);
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
            snapshot.add(new TlvVariableBinding(bytes, lengthFieldBytesByTlvType));
            var exporter = new ConfigurationFileExport(snapshot, configurationType.id());
            exporter.setDotTextOIDOutputFormat(false);
            String text = exporter.toPrettyPrint(ConfigurationFileExport.EXPORT_FOUND_TLV, false);
            if (text.length() > OscarInspectionService.MAX_BYTES) {
                throw new InspectionException("The decoded configuration exceeds the text size limit.");
            }
            Path origin = path.toAbsolutePath().normalize();
            var document = documents.decoded(text, configurationType, new BinaryOrigin(origin,
                    java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)), bytes));
            if (containsSnmp(document.root())) {
                throw new InspectionException("Editable binary import of SNMP statements is not supported yet.");
            }
            var imported = new ConfigurationFileImport(new StringBuilder(document.text()));
            if (!original.equals(payloads(imported.toByteArray(), lengthFieldBytesByTlvType))) {
                throw new InspectionException("Oscar cannot preserve this binary configuration as editable text; import cancelled.");
            }
            return document;
        } catch (RuntimeException | LinkageError | com.comcast.oscar.tlv.TlvException error) {
            throw new InspectionException("Oscar could not decode this binary configuration. Check its type and whether it is truncated or unsupported.");
        } catch (java.security.NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private List<String> payloads(byte[] bytes, Map<Integer, Integer> lengthFieldBytesByTlvType)
            throws com.comcast.oscar.tlv.TlvException, InspectionException {
        var entries = new ArrayList<String>();
        for (int tlvOffsetBytes = 0; tlvOffsetBytes < bytes.length;) {
            int tlvTypeCode = Byte.toUnsignedInt(bytes[tlvOffsetBytes]);
            if (tlvTypeCode == PAD) {
                tlvOffsetBytes++;
                continue;
            }
            if (tlvTypeCode == END_OF_FILE) {
                for (int trailingByteOffset = tlvOffsetBytes + 1; trailingByteOffset < bytes.length; trailingByteOffset++) {
                    if (Byte.toUnsignedInt(bytes[trailingByteOffset]) != PAD) {
                        throw new InspectionException("Unexpected data after the binary end marker.");
                    }
                }
                break;
            }
            int nextTlvOffsetBytes = TlvBuilder.nextTLVIndex(bytes, tlvOffsetBytes, lengthFieldBytesByTlvType.getOrDefault(tlvTypeCode, (int) TlvBuilder.TLV_ONE_BYTE_LENGTH));
            if (nextTlvOffsetBytes <= tlvOffsetBytes || nextTlvOffsetBytes > bytes.length) {
                throw new InspectionException("Truncated binary TLV.");
            }
            entries.add(HexFormat.of().formatHex(Arrays.copyOfRange(bytes, tlvOffsetBytes, nextTlvOffsetBytes)));
            tlvOffsetBytes = nextTlvOffsetBytes;
        }
        if (entries.isEmpty()) {
            throw new InspectionException("No supported TLV entries in the binary configuration.");
        }
        return entries;
    }

    private void rejectTextDestination(Document document, Path target) throws InspectionException {
        documents.rejectBinaryOrigin(document, target);
        rejectTextFile(document, target);
    }

    private void rejectTextFile(Document document, Path target) throws InspectionException {
        if (document.path() == null) {
            return;
        }
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
        return node.tlvName().equals("Snmp11") || node.tlvName().equals("Snmp64")
                || node.childTlvs().stream().anyMatch(this::containsSnmp);
    }
}
