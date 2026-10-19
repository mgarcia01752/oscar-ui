/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CertificateInputTest {
    @Test void hexAndPemPreserveEveryByteAcrossFragmentBoundaries() {
        byte[] payload = new byte[1025];
        for (int index = 0; index < payload.length; index++) {
            payload[index] = (byte) index;
        }
        String hex = CertificateInput.toOscarHex(payload);
        assertArrayEquals(payload, CertificateInput.parse(hex));
        assertArrayEquals(payload, CertificateInput.parse("[" + hex.replace(':', ' ') + "]"));
        String pem = "-----BEGIN CERTIFICATE-----\n"
                + Base64.getMimeEncoder().encodeToString(payload) + "\n-----END CERTIFICATE-----";
        assertArrayEquals(payload, CertificateInput.parse(pem));
        assertArrayEquals(payload, CertificateInput.fromFile(pem.getBytes(StandardCharsets.US_ASCII), "lab.crt"));
        assertArrayEquals(payload, CertificateInput.fromFile(hex.getBytes(StandardCharsets.US_ASCII), "lab.hex"));
        byte[] decoded = CertificateInput.fromFile(payload, "lab.der");
        assertArrayEquals(payload, decoded);
        assertNotSame(payload, decoded);
    }

    @Test void rejectsMalformedOrNonCertificateText() {
        for (String invalid : new String[] {"", "1", "0G", "30:1", "hello", "30; NetworkAccess 1;",
                "-----BEGIN CERTIFICATE-----\n%%%\n-----END CERTIFICATE-----",
                "-----BEGIN PRIVATE KEY-----\nMAE=\n-----END PRIVATE KEY-----",
                "-----BEGIN CERTIFICATE-----\nMAE="}) {
            assertThrows(IllegalArgumentException.class, () -> CertificateInput.parse(invalid), invalid);
        }
        assertThrows(IllegalArgumentException.class, () -> CertificateInput.fromFile(new byte[0], "empty.der"));
        assertThrows(IllegalArgumentException.class,
                () -> CertificateInput.fromFile("garbage".getBytes(StandardCharsets.US_ASCII), "invalid.pem"));
    }
}
