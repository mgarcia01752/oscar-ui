/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.HttpURLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Exact-baseline MSP discovery and normal-user handoff to the installed Windows helper. */
public final class WindowsUpdateService {
    /**
     * Describes a candidate patch; the helper independently rechecks it before installation.
     * @param version target release version
     * @param fromVersion exact supported installed version
     * @param productCode Windows Installer baseline product identity
     * @param fingerprint dependency and installer compatibility hash
     * @param size patch download size in bytes
     */
    public record Installer(String version, String fromVersion, String productCode,
                            String fingerprint, long size) {}

    /**
     * Detects a protected Windows installation with the patch baseline and helper.
     * @return true for a packaged x64 Windows installation under Program Files
     */
    public boolean supported() {
        try {
            Path application = applicationDirectory();
            String programFiles = System.getenv("ProgramFiles");
            return System.getProperty("os.name", "").startsWith("Windows")
                    && System.getProperty("os.arch", "").equals("amd64") && programFiles != null
                    && application.startsWith(Path.of(programFiles))
                    && Files.isRegularFile(application.resolve("windows-baseline.json"))
                    && Files.isRegularFile(application.resolve("windows-update.ps1"));
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * Reads the hash-verified release manifest on the calling worker thread.
     * @param installedVersion current application version
     * @param release parsed published GitHub release
     * @return compatible patch or null when a full MSI is required
     * @throws IOException when metadata cannot be downloaded or verified
     */
    public Installer findInstaller(String installedVersion, JsonNode release) throws IOException {
        if (!supported()) {
            return null;
        }
        JsonNode asset = null;
        for (JsonNode candidate : release.path("assets")) {
            if (candidate.path("name").asText().equals("windows-update.json")) {
                if (asset != null) {
                    throw new IOException("Ambiguous Windows update metadata.");
                }
                asset = candidate;
            }
        }
        if (asset == null) {
            return null;
        }
        String version = release.path("tag_name").asText().replaceFirst("^v", "");
        byte[] bytes = downloadMetadata(version, asset);
        var mapper = new ObjectMapper();
        var baseline = mapper.readTree(Files.readString(applicationDirectory().resolve("windows-baseline.json")));
        return compatible(installedVersion, version, baseline, mapper.readTree(bytes));
    }

    /**
     * Starts a normal-user helper which verifies, downloads, elevates and restarts.
     * @param installer candidate selected after a successful release check
     * @throws IOException when not installed or the helper process cannot start
     */
    public void launch(Installer installer) throws IOException {
        if (!supported() || installer == null || !installer.version().matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
            throw new IOException("Windows patch updating requires a supported installed baseline.");
        }
        Path shell = Path.of(System.getenv("SystemRoot"), "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
        new ProcessBuilder(shell.toString(), "-NoProfile", "-WindowStyle", "Hidden", "-ExecutionPolicy", "Bypass",
                "-File", applicationDirectory().resolve("windows-update.ps1").toString(), "-Version", installer.version(),
                "-WaitPid", Long.toString(ProcessHandle.current().pid()))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    }

    static Installer compatible(String installedVersion, String targetVersion, JsonNode baseline, JsonNode update)
            throws IOException {
        if (baseline == null || update == null || !targetVersion.matches("[0-9]+\\.[0-9]+\\.[0-9]+")) {
            throw new IOException("Invalid Windows update metadata.");
        }
        if (baseline.path("layout").asInt() != 1 || update.path("layout").asInt() != 1
                || !update.path("version").asText().equals(targetVersion)
                || !update.path("fromVersion").asText().equals(installedVersion)
                || !baseline.path("productCode").asText().equals(update.path("productCode").asText())
                || !baseline.path("fingerprint").asText().equals(update.path("fingerprint").asText())
                || update.path("patch").isNull() || update.path("patch").isMissingNode()) {
            return null;
        }
        String productCode = update.path("productCode").asText();
        String fingerprint = update.path("fingerprint").asText();
        JsonNode patch = update.path("patch");
        if (!productCode.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}") || !fingerprint.matches("[a-fA-F0-9]{64}")
                || !patch.path("url").asText().equals(ReleaseUpdateService.UI_PROJECT + "/releases/download/v"
                    + targetVersion + "/OSCAR-UI-" + targetVersion + "-windows-x64.msp")
                || !patch.path("digest").asText().matches("sha256:[a-fA-F0-9]{64}")
                || patch.path("size").asLong() <= 0 || patch.path("size").asLong() > 100 * 1024 * 1024) {
            throw new IOException("Invalid Windows patch identity.");
        }
        return new Installer(targetVersion, installedVersion, productCode, fingerprint, patch.path("size").asLong());
    }

    private static Path applicationDirectory() throws IOException {
        try {
            return Path.of(WindowsUpdateService.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent();
        } catch (Exception exception) {
            throw new IOException("Cannot locate installed Windows application.", exception);
        }
    }

    private static byte[] downloadMetadata(String version, JsonNode asset) throws IOException {
        String url = asset.path("browser_download_url").asText();
        String digest = asset.path("digest").asText();
        long size = asset.path("size").asLong();
        if (!url.equals(ReleaseUpdateService.UI_PROJECT + "/releases/download/v" + version + "/windows-update.json")
                || !digest.matches("sha256:[a-fA-F0-9]{64}") || size <= 0 || size > 1_048_576) {
            throw new IOException("Invalid Windows manifest asset.");
        }
        URI address = URI.create(url);
        for (int redirect = 0; redirect <= 5; redirect++) {
            if (!"https".equals(address.getScheme()) || address.getUserInfo() != null
                    || !java.util.Set.of("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
                        .contains(address.getHost())) {
                throw new IOException("Untrusted Windows update redirect.");
            }
            var connection = (HttpURLConnection) address.toURL().openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(15_000);
            connection.setInstanceFollowRedirects(false);
            try {
                int code = connection.getResponseCode();
                if (code == 200) {
                    byte[] bytes;
                    try (var input = connection.getInputStream()) {
                        bytes = input.readNBytes(1_048_577);
                    }
                    try {
                        if (bytes.length != size || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
                                .equalsIgnoreCase(digest.substring(7))) {
                            throw new IOException("Windows manifest verification failed.");
                        }
                    } catch (java.security.NoSuchAlgorithmException exception) {
                        throw new IOException(exception);
                    }
                    return bytes;
                }
                String location = connection.getHeaderField("Location");
                if (!java.util.Set.of(301, 302, 303, 307, 308).contains(code) || location == null) {
                    throw new IOException("Windows metadata download failed.");
                }
                address = address.resolve(location);
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Too many Windows metadata redirects.");
    }
}
