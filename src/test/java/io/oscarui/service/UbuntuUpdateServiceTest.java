/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

class UbuntuUpdateServiceTest {
    private UbuntuUpdateService.Installer installer(byte[] bytes) throws Exception {
        return new UbuntuUpdateService.Installer("0.4.0", URI.create(ReleaseUpdateService.UI_PROJECT
                + "/releases/download/v0.4.0/oscar-ui_amd64.deb"), bytes.length,
                "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }

    @Test void verifiesCompleteDownloadAndRejectsCorruptionTruncationAndCancellation() throws Exception {
        byte[] bytes = new byte[]{1, 2, 3};
        var release = installer(bytes);
        var output = new ByteArrayOutputStream();
        var progress = new java.util.concurrent.atomic.AtomicInteger();
        UbuntuUpdateService.copyVerified(new ByteArrayInputStream(bytes), output, release, progress::set, () -> false);
        assertArrayEquals(bytes, output.toByteArray());
        assertEquals(100, progress.get());
        for (byte[] bad : new byte[][]{new byte[]{1}, new byte[]{1, 2, 4}, new byte[]{1, 2, 3, 4}}) {
            assertThrows(IOException.class, () -> UbuntuUpdateService.copyVerified(new ByteArrayInputStream(bad),
                    new ByteArrayOutputStream(), release, ignored -> {}, () -> false));
        }
        assertThrows(CancellationException.class, () -> UbuntuUpdateService.copyVerified(new ByteArrayInputStream(bytes),
                output, release, ignored -> {}, () -> true));
    }

    @Test void refusesMissingHashesForeignUrlsAndWrongReleasePaths() throws Exception {
        var valid = installer(new byte[]{1});
        for (String url : new String[]{"http://github.com/package.deb", "https://evil.example/oscar-ui_amd64.deb",
                ReleaseUpdateService.UI_PROJECT + "/releases/download/v0.3.0/oscar-ui_amd64.deb"}) {
            assertThrows(IllegalArgumentException.class, () -> new UbuntuUpdateService.Installer(
                    valid.version(), URI.create(url), 1, valid.digest()));
        }
        assertThrows(IllegalArgumentException.class, () -> new UbuntuUpdateService.Installer(
                valid.version(), valid.url(), 1, ""));
        assertFalse(new UbuntuUpdateService().supported(), "Development runs must not offer installation");
    }

    @Test void releaseCheckRequiresNewerVersionAndVerifiedAsset() throws Exception {
        var valid = installer(new byte[]{1});
        String json = "{\"tag_name\":\"v0.4.0\",\"draft\":false,\"prerelease\":false,\"assets\":[{"
                + "\"name\":\"oscar-ui_amd64.deb\",\"browser_download_url\":\"" + valid.url()
                + "\",\"size\":1,\"digest\":\"" + valid.digest() + "\"}]}";
        var service = new ReleaseUpdateService();
        assertEquals(valid, service.parseRelease("0.3.0", json).installer());
        assertNull(service.parseRelease("0.4.0", json).installer());
        assertNull(service.parseRelease("0.3.0", json.replace(valid.digest(), "")).installer());
    }
}
