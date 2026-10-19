/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Hands removal to the operating system without running the UI as administrator. */
public final class UninstallService {
    private static final Path HELPER = Path.of("/opt/oscar-ui/lib/app/ubuntu-uninstall.py");

    /**
     * Detects a packaged installation with available removal tools.
     *
     * @return false for development checkouts and unsupported platforms
     */
    public boolean supported() {
        if (new UbuntuUpdateService().supported() && Files.isRegularFile(HELPER)) {
            return true;
        }
        try {
            Path source = Path.of(UninstallService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return System.getProperty("os.name", "").startsWith("Windows")
                    && source.toString().endsWith(".jar")
                    && source.getParent().getFileName().toString().equals("app")
                    && Files.isRegularFile(source.getParent().getParent().resolve("OSCAR-UI.exe"));
        } catch (Exception exception) {
            return false;
        }
    }

    /**
     * Starts an external removal handoff after confirmation and document saving.
     *
     * <p>Ubuntu waits for this process to exit before requesting authorization.
     * Windows opens Installed apps for the user to select OSCAR-UI.
     * The caller closes the UI only after this method succeeds.
     *
     * @throws IOException if this installation is unsupported or the helper cannot start
     */
    public void launch() throws IOException {
        if (!supported()) {
            throw new IOException("Uninstall requires an installed OSCAR-UI package.");
        }
        if (System.getProperty("os.name", "").startsWith("Windows")) {
            if (!io.qt.gui.QDesktopServices.openUrl(new io.qt.core.QUrl("ms-settings:appsfeatures"))) {
                throw new IOException("Could not open Windows Installed apps.");
            }
        } else {
            Path directory = Files.createTempDirectory("oscar-ui-uninstall-");
            new ProcessBuilder("/usr/bin/python3", "-I", HELPER.toString(),
                    Long.toString(ProcessHandle.current().pid()), directory.resolve("uninstall.log").toString())
                    .redirectErrorStream(true).redirectOutput(directory.resolve("uninstall.log").toFile()).start();
        }
    }
}
