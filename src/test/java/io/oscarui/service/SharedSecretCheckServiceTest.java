/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import static org.junit.jupiter.api.Assertions.*;
import com.comcast.oscar.configurationfile.ConfigurationFile;
import com.comcast.oscar.configurationfile.ConfigurationFileImport;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SharedSecretCheckServiceTest {
    @TempDir Path temporary;
    private final SharedSecretCheckService checker = new SharedSecretCheckService();
    private final byte[] key = "public-test-key".getBytes(StandardCharsets.UTF_8);

    @Test void checksBothProfilesAndExtendedModesWithoutChangingFileOrKey() throws Exception {
        for (boolean docsis40 : new boolean[]{false, true}) {
            for (int algorithm : new int[]{1, 2}) {
                for (boolean explicit : new boolean[]{false, true}) {
                    String digest = "00:".repeat(algorithm == 2 ? 7 : 15) + "00";
                    String extension = docsis40
                            ? "DocsisExtension { VendorId [FF:FF:FF]; ExtendedCmtsMic { HmacType " + algorithm
                                + "; MicBitmap [10:00:00:00:00:10]; "
                                + (explicit ? "ExplicitDigest [" + digest + "];" : "") + "} }"
                            : "VendorSpecific { VendorId [FF:FF:FF]; ExtCmtsMicConf { ExtCmtsMicHMACType " + algorithm
                                + "; ExtCmtsMicBitmap 000100000000000000000000000000000000000000010000; "
                                + (explicit ? "ExpExtCmtsMicDigest [" + digest + "];" : "") + "} }";
                    byte[] binary = compile(docsis40, extension);
                    Path path = Files.write(temporary.resolve("configuration.bin"), binary);
                    byte[] originalKey = key.clone();
                    assertEquals(SharedSecretCheckService.Outcome.CORRECT, checker.check(path, key));
                    assertEquals(SharedSecretCheckService.Outcome.INCORRECT,
                            checker.check(path, "wrong-key".getBytes(StandardCharsets.UTF_8)));
                    assertArrayEquals(binary, Files.readAllBytes(path));
                    assertArrayEquals(originalKey, key);
                }
            }
        }
    }

    @Test void checksStandardMicAndRejectsTextMissingMicAndDamagedFiles() throws Exception {
        Path path = temporary.resolve("file.txt");
        assertEquals(SharedSecretCheckService.Outcome.CORRECT,
                checker.check(Files.write(path, compile(false, "")), key));
        assertThrows(InspectionException.class,
                () -> checker.check(Files.writeString(path, "Docsis { NetworkAccess 1; }"), key));
        assertThrows(InspectionException.class,
                () -> checker.check(Files.write(path, new byte[]{3, 1, 1, (byte) 255}), key));
        byte[] corrupt = compile(false, "");
        corrupt[2] ^= 1;
        assertThrows(InspectionException.class, () -> checker.check(Files.write(path, corrupt), key));
        assertThrows(InspectionException.class, () -> checker.check(path, new byte[0]));
        assertThrows(InspectionException.class, () -> checker.check(temporary.resolve("missing"), key));
    }

    private byte[] compile(boolean docsis40, String extension) {
        String flows = docsis40
                ? "UpstreamServiceFlow { ServiceFlowReference 1; QosParameterSetType 7; } "
                    + "DownstreamServiceFlow { ServiceFlowReference 2; QosParameterSetType 7; } " : "";
        var imported = new ConfigurationFileImport(new StringBuilder(
                (docsis40 ? "Docsis-4.0" : "Docsis") + " { NetworkAccess 1; " + flows + extension + " }"));
        var configuration = new ConfigurationFile(docsis40 ? ConfigurationFile.DOCSIS_VER_40
                : ConfigurationFile.DOCSIS_VER_31, imported.getTlvBuilder(), "public-test-key");
        configuration.commit();
        return configuration.toByteArray();
    }
}
