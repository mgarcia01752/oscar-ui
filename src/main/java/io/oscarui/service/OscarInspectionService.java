/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import com.comcast.oscar.configurationfile.ConfigurationFileImport;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/** Read-only text inspection using the unmodified Oscar API; no signing or encoding. */
public final class OscarInspectionService {
    public static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_UNSIGNED_BYTE_VALUE = (1 << Byte.SIZE) - 1;

    private static final Pattern SNMP = Pattern.compile("\\bSnmp(?:11|64)\\b");

    /**
     * Inspects a bounded UTF-8 file using Oscar without signing or encoding.
     *
     * <p>Rejects SNMP statements to avoid upstream cache writes.
     *
     * @param path source or destination filesystem path; must not be null
     * @return source text, normalized path, and syntax-inspection summary
     * @throws InspectionException if the input cannot be read or supported text cannot be inspected
     */
    public Inspection inspect(Path path) throws InspectionException {
        if (path == null || !Files.isRegularFile(path)) {
            throw new InspectionException("Select an existing configuration text file.");
        }
        byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_BYTES + 1);
        } catch (IOException e) {
            throw new InspectionException("Cannot read the selected file. Check its permissions.", e);
        }
        if (bytes.length == 0) {
            throw new InspectionException("The selected file is empty.");
        }
        if (bytes.length > MAX_BYTES) {
            throw new InspectionException("The starter accepts files up to 1 MiB.");
        }
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new InspectionException("Expected UTF-8 configuration text, not a binary configuration.", e);
        }
        if (text.indexOf('\0') >= 0) {
            throw new InspectionException("Binary configuration files are not supported yet.");
        }
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        String withoutComments = text.replaceAll("(?s)/\\*.*?\\*/", " ");
        if (SNMP.matcher(withoutComments).find()) {
            throw new InspectionException("SNMP statements are not supported in this read-only starter. "
                    + "Oscar's optional Net-SNMP integration writes a working-directory cache; "
                    + "it is intentionally not invoked. Other TLV configuration text can be inspected.");
        }
        for (String family : new String[]{"docsis", "docsis40", "packetcable", "dpoe"}) {
            if (ConfigurationFileImport.class.getResource("/tlv-registry/" + family + ".json") == null) {
                throw new InspectionException("Oscar's bundled TLV catalogs are missing. Rebuild with the pinned thin artifact.");
            }
        }
        try {
            var parsed = new ConfigurationFileImport(new StringBuilder(text));
            int count = 0;
            for (int tlvTypeCode = 0; tlvTypeCode <= MAX_UNSIGNED_BYTE_VALUE; tlvTypeCode++) count += parsed.getTopLevelTLVJSON(tlvTypeCode).length();
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return new Inspection(path.toAbsolutePath().normalize(), text,
                    "Oscar parsed " + count + " top-level TLV entries (configuration type "
                    + parsed.getConfigurationFileType() + ").\nSHA-256: " + hash
                    + "\nRead-only syntax/catalog inspection; not full value validation or MIC verification.");
        } catch (LinkageError e) {
            throw new InspectionException("Oscar or a required dependency could not initialize. Check the installed artifacts and catalogs.", e);
        } catch (RuntimeException e) {
            // Parser messages can contain input values; do not log configuration contents.
            throw new InspectionException("Oscar rejected the configuration syntax or TLV names. Check the header and nesting.", e);
        } catch (NoSuchAlgorithmException e) {
            throw new InspectionException("The Java runtime does not provide SHA-256.", e);
        }
    }
}
