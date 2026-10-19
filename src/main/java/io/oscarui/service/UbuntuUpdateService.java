/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/** Downloads a release unprivileged and hands installation to the packaged Ubuntu helper. */
public final class UbuntuUpdateService {
    private static final Path HELPER = Path.of("/opt/oscar-ui/lib/app/ubuntu-update.py");
    private static final long MAX_PACKAGE_BYTES = 2_000_000_000L;

    /** Immutable release identity; digest is the SHA-256 reported by GitHub for this asset. */
    public record Installer(String version, URI url, long size, String digest) {
        /**
         * Validates metadata before offering installation; rejects non-project URLs and missing hashes.
         *
         * @param version stable numeric version, for example 0.4.0
         * @param url HTTPS download URL for that exact project release
         * @param size positive download size, at most two billion bytes
         * @param digest sha256: followed by 64 hexadecimal digits
         * @throws IllegalArgumentException if any field is invalid
         */
        public Installer {
            if (version == null || !version.matches("[0-9]+\\.[0-9]+\\.[0-9]+") || url == null
                    || !url.toString().startsWith(ReleaseUpdateService.UI_PROJECT + "/releases/download/v" + version + "/")
                    || !url.getPath().endsWith("_amd64.deb") || url.getQuery() != null || url.getFragment() != null
                    || size <= 0 || size > MAX_PACKAGE_BYTES || digest == null
                    || !digest.matches("sha256:[0-9a-fA-F]{64}")) {
                throw new IllegalArgumentException("Invalid Ubuntu installer metadata.");
            }
        }
    }

    /**
     * Checks whether this process runs from the installed Ubuntu x64 package.
     *
     * @return true only for a packaged Ubuntu run with the updater and authentication tools available
     */
    public boolean supported() {
        try {
            String source = UbuntuUpdateService.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            return System.getProperty("os.name", "").equals("Linux")
                    && System.getProperty("os.arch", "").equals("amd64")
                    && Files.readString(Path.of("/etc/os-release")).lines()
                        .anyMatch(line -> line.equals("ID=ubuntu") || line.equals("ID=\"ubuntu\""))
                    && source.startsWith("/opt/oscar-ui/lib/app/") && Files.isRegularFile(HELPER)
                    && Files.isExecutable(Path.of("/usr/bin/pkexec")) && Files.isExecutable(Path.of("/usr/bin/zenity"));
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * Downloads and hashes the candidate without administrator privileges.
     *
     * @param installer validated release identity
     * @param progress receives percentage complete on the calling worker thread
     * @param canceled signals cancellation; partial downloads are removed
     * @return private temporary DEB, to be passed to launch or discard
     * @throws Exception for network, checksum, interruption, or filesystem failures
     */
    public Path download(Installer installer, IntConsumer progress, BooleanSupplier canceled) throws Exception {
        Path directory = Files.createTempDirectory("oscar-ui-update-",
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Path destination = directory.resolve("update.deb");
        boolean complete = false;
        java.net.HttpURLConnection connection = null;
        try {
            URI address = installer.url();
            for (int redirect = 0; redirect <= 5; redirect++) {
                if (canceled.getAsBoolean()) {
                    throw new CancellationException("Download canceled.");
                }
                if (!"https".equals(address.getScheme()) || address.getUserInfo() != null
                        || !java.util.Set.of("github.com", "release-assets.githubusercontent.com",
                            "objects.githubusercontent.com").contains(address.getHost())) {
                    throw new IOException("Untrusted download redirect.");
                }
                connection = (java.net.HttpURLConnection) address.toURL().openConnection();
                connection.setConnectTimeout(15_000);
                connection.setReadTimeout(15_000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "OSCAR-UI");
                int code = connection.getResponseCode();
                if (code == 200) {
                    break;
                }
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (redirect == 5 || !java.util.Set.of(301, 302, 303, 307, 308).contains(code) || location == null) {
                    throw new IOException("Download failed: HTTP " + code);
                }
                address = address.resolve(location);
            }
            try (var input = connection.getInputStream(); var output = Files.newOutputStream(destination)) {
                copyVerified(input, output, installer, progress, canceled);
            }
            complete = true;
            return destination;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (!complete) {
                discard(destination);
            }
        }
    }

    /**
     * Starts a separate updater which waits for this UI process to exit before authenticating.
     *
     * @param installer candidate whose download was verified
     * @param downloaded private downloaded DEB
     * @throws IOException if the updater cannot be started; the UI should remain open
     */
    public void launch(Installer installer, Path downloaded) throws IOException {
        if (!supported()) {
            throw new IOException("In-app updates require an installed Ubuntu package.");
        }
        new ProcessBuilder("/usr/bin/python3", "-I", HELPER.toString(), "--wait",
                Long.toString(ProcessHandle.current().pid()), downloaded.toString(), installer.version(),
                installer.digest().substring(7)).redirectOutput(downloaded.resolveSibling("install.log").toFile())
                .redirectErrorStream(true).start();
    }

    /**
     * Removes a downloaded package when the user cancels before handoff.
     *
     * @param downloaded package returned by download; null is accepted
     */
    public void discard(Path downloaded) {
        if (downloaded != null) {
            try {
                Files.deleteIfExists(downloaded);
                Files.deleteIfExists(downloaded.getParent());
            } catch (IOException ignored) {
                // A failed cleanup must not turn cancellation into an installation.
            }
        }
    }

    static void copyVerified(java.io.InputStream input, java.io.OutputStream output, Installer installer,
                             IntConsumer progress, BooleanSupplier canceled) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[65536];
        long received = 0;
        long deadline = System.nanoTime() + java.time.Duration.ofMinutes(15).toNanos();
        while (true) {
            if (canceled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Download canceled.");
            }
            if (System.nanoTime() > deadline) {
                throw new IOException("Download timed out.");
            }
            int count = input.read(buffer);
            if (count < 0) {
                break;
            }
            received += count;
            if (received > installer.size()) {
                throw new IOException("Download exceeds the expected size.");
            }
            digest.update(buffer, 0, count);
            output.write(buffer, 0, count);
            progress.accept((int) (received * 100 / installer.size()));
        }
        if (received != installer.size() || !HexFormat.of().formatHex(digest.digest())
                .equalsIgnoreCase(installer.digest().substring(7))) {
            throw new IOException("Download size or SHA-256 verification failed.");
        }
    }
}
