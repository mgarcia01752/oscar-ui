/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import com.comcast.oscar.cablelabsdefinitions.Constants;
import com.comcast.oscar.datatype.DataTypeDictionaryReference;
import com.comcast.oscar.dictionary.TlvDefinition;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/** Converts certificate input to bytes; OSCAR owns TLV encoding and fragmentation. */
public final class CertificateInput {
    private static final Pattern PEM = Pattern.compile(
            "\\A\\s*-----BEGIN CERTIFICATE-----\\s*([A-Za-z0-9+/=\\s]+)"
            + "-----END CERTIFICATE-----\\s*\\z");

    private CertificateInput() {}

    /**
     * Identifies the two supported legacy CVC definitions using OSCAR constants.
     * @param definition catalog entry, or null
     * @return true for manufacturer or co-signer CVC
     */
    public static boolean supports(TlvDefinition definition) {
        return definition != null && definition.path().size() == 1
                && (definition.path().getFirst().equals(Constants.MANUFACTURER_CVC)
                    || definition.path().getFirst().equals(Constants.CO_SIGNER_CVC))
                && definition.codec().equals(DataTypeDictionaryReference.DATA_TYPE_MULTI_TLV_BYTE_ARRAY);
    }

    /**
     * Decodes pasted HEX or one PEM CERTIFICATE block without checking certificate trust.
     * @param input non-null pasted text; HEX permits colons, whitespace and enclosing brackets
     * @return nonempty original binary bytes
     * @throws IllegalArgumentException for empty, incomplete or malformed input
     */
    public static byte[] parse(String input) {
        String text = input.strip();
        if (text.contains("-----")) {
            var match = PEM.matcher(text);
            if (!match.matches()) {
                throw new IllegalArgumentException("Paste one complete PEM CERTIFICATE block.");
            }
            try {
                return nonempty(Base64.getDecoder().decode(match.group(1).replaceAll("\\s", "")));
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException("Invalid PEM certificate Base64.");
            }
        }
        if (text.startsWith("[") && text.endsWith("]")) {
            text = text.substring(1, text.length() - 1);
        }
        for (String byteGroup : text.split("[:\\s]+")) {
            if ((byteGroup.length() & 1) != 0 || !byteGroup.matches("[0-9a-fA-F]*")) {
                throw new IllegalArgumentException("Enter complete HEX byte pairs, for example 30:82:01:FF.");
            }
        }
        return nonempty(HexFormat.of().parseHex(text.replaceAll("[:\\s]", "")));
    }

    /**
     * Reads file content as PEM, a HEX text file, or opaque DER bytes.
     * @param contents file bytes; never modified
     * @param filename selected filename; .hex and .txt designate HEX text
     * @return nonempty certificate bytes, without signature or ASN.1 validation
     * @throws IllegalArgumentException for invalid textual or empty content
     */
    public static byte[] fromFile(byte[] contents, String filename) {
        String text = new String(contents, StandardCharsets.US_ASCII);
        String lowerName = filename.toLowerCase(java.util.Locale.ROOT);
        if (text.stripLeading().startsWith("-----") || lowerName.endsWith(".pem")
                || lowerName.endsWith(".hex") || lowerName.endsWith(".txt")) {
            return parse(text);
        }
        return nonempty(contents.clone());
    }

    /**
     * Formats certificate bytes as OSCAR's colon-separated scalar HEX syntax.
     * @param certificateBytes nonempty payload
     * @return uppercase HEX without a TLV header
     * @throws IllegalArgumentException for empty input
     */
    public static String toOscarHex(byte[] certificateBytes) {
        return HexFormat.ofDelimiter(":").withUpperCase().formatHex(nonempty(certificateBytes));
    }

    static int encodedEnd(byte[] encoded, int byteOffset, TlvDefinition definition)
            throws com.comcast.oscar.tlv.TlvException, InspectionException {
        int endOffset = byteOffset;
        do {
            int nextOffset = com.comcast.oscar.tlv.TlvBuilder.nextTLVIndex(encoded, endOffset, definition.lengthBytes());
            if (nextOffset <= endOffset || nextOffset > encoded.length) {
                throw new InspectionException("OSCAR returned truncated certificate/TLV bytes.");
            }
            endOffset = nextOffset;
        } while (supports(definition) && endOffset < encoded.length
                && Byte.toUnsignedInt(encoded[endOffset]) == definition.type());
        return endOffset;
    }

    private static byte[] nonempty(byte[] bytes) {
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Select a certificate or paste HEX / PEM text.");
        }
        return bytes;
    }
}
