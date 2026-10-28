/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui;

import io.qt.widgets.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Owns application maintenance dialogs; the workspace owns save approval and shutdown. */
final class ApplicationMaintenanceController {
    private final QWidget parent;
    private final BooleanSupplier idle;
    private final Consumer<Runnable> reviewDocuments;
    private final Runnable closeApproved;
    private final Consumer<Boolean> updateAvailability;

    ApplicationMaintenanceController(QWidget parent, BooleanSupplier idle,
            Consumer<Runnable> reviewDocuments, Runnable closeApproved,
            Consumer<Boolean> updateAvailability) {
        this.parent = parent;
        this.idle = idle;
        this.reviewDocuments = reviewDocuments;
        this.closeApproved = closeApproved;
        this.updateAvailability = updateAvailability;
    }

    void showAbout(boolean checkUpdates) {
        var dialog = new AboutDialog(parent);
        dialog.onUpdateAvailability(updateAvailability);
        io.oscarui.service.UbuntuUpdateService.Installer installer;
        io.oscarui.service.WindowsUpdateService.Installer windowsInstaller;
        boolean uninstallRequested;
        try {
            if (checkUpdates) {
                dialog.checkUpdates();
            }
            dialog.exec();
            installer = dialog.requestedInstaller();
            windowsInstaller = dialog.requestedWindowsInstaller();
            uninstallRequested = dialog.uninstallRequested();
        } finally {
            dialog.stopChecking();
            dialog.dispose();
        }
        if (uninstallRequested && idle.getAsBoolean()) {
            confirmUninstall();
        } else if (windowsInstaller != null && idle.getAsBoolean()) {
            reviewDocuments.accept(() -> installWindowsUpdate(windowsInstaller));
        } else if (installer != null && idle.getAsBoolean()) {
            reviewDocuments.accept(() -> installUbuntuUpdate(installer));
        }
    }

    private void confirmUninstall() {
        String action = System.getProperty("os.name", "").startsWith("Windows")
                ? "Close OSCAR-UI and open Windows Installed apps? Select OSCAR-UI there to uninstall."
                : "Close OSCAR-UI and request administrator approval to uninstall?";
        if (QMessageBox.question(parent, "Uninstall OSCAR-UI", action
                + "\nConfiguration files outside the installation directory will be kept.",
                new QMessageBox.StandardButtons(QMessageBox.StandardButton.Yes, QMessageBox.StandardButton.Cancel),
                QMessageBox.StandardButton.Cancel) != QMessageBox.StandardButton.Yes) {
            return;
        }
        reviewDocuments.accept(() -> {
            try {
                new io.oscarui.service.UninstallService().launch();
                closeApproved.run();
            } catch (java.io.IOException exception) {
                QMessageBox.warning(parent, "Uninstall could not start", exception.getMessage());
            }
        });
    }

    private void installWindowsUpdate(io.oscarui.service.WindowsUpdateService.Installer installer) {
        if (QMessageBox.question(parent, "Update OSCAR-UI",
                "Close OSCAR-UI to download and install version " + installer.version()
                        + "? Windows will request administrator approval. The app reopens after a successful update.",
                new QMessageBox.StandardButtons(QMessageBox.StandardButton.Yes, QMessageBox.StandardButton.Cancel),
                QMessageBox.StandardButton.Cancel) != QMessageBox.StandardButton.Yes) {
            return;
        }
        try {
            new io.oscarui.service.WindowsUpdateService().launch(installer);
            closeApproved.run();
        } catch (java.io.IOException exception) {
            QMessageBox.warning(parent, "Update could not start", exception.getMessage());
        }
    }

    private void installUbuntuUpdate(io.oscarui.service.UbuntuUpdateService.Installer installer) {
        var service = new io.oscarui.service.UbuntuUpdateService();
        var download = new UbuntuUpdateDialog(parent, installer, service);
        try {
            if (download.exec() == QDialog.DialogCode.Accepted.value()) {
                try {
                    service.launch(installer, download.downloadedPackage());
                    closeApproved.run();
                } catch (java.io.IOException exception) {
                    service.discard(download.downloadedPackage());
                    QMessageBox.warning(parent, "Update could not start", exception.getMessage());
                }
            }
        } finally {
            download.dispose();
        }
    }

}
