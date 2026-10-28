/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.maintenance;

import io.oscarui.service.ReleaseUpdateService;
import io.oscarui.service.UbuntuUpdateService;
import io.qt.core.QTimer;
import io.qt.core.Qt;
import io.qt.widgets.*;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.*;

/** Application identity, project links, and an explicit asynchronous update check. */
public final class AboutDialog extends QDialog {
    private final QLabel updateStatus = new QLabel("Check GitHub for the latest stable OSCAR-UI release.");
    private final QPushButton checkButton = new QPushButton("Check for updates");
    private final QPushButton updateButton = new QPushButton("Update on Ubuntu");
    private volatile UbuntuUpdateService.Installer availableInstaller;
    private UbuntuUpdateService.Installer requestedInstaller;
    private final QPushButton windowsUpdateButton = new QPushButton("Update on Windows");
    private volatile io.oscarui.service.WindowsUpdateService.Installer availableWindowsInstaller;
    private io.oscarui.service.WindowsUpdateService.Installer requestedWindowsInstaller;
    private boolean uninstallRequested;
    private final QLabel releaseNotes = link("View releases and downloads", ReleaseUpdateService.UI_PROJECT + "/releases/latest");
    private final QTimer pollTimer = new QTimer(this);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "oscar-ui-release-check");
        thread.setDaemon(true);
        return thread;
    });
    private final String installedVersion;
    private final Callable<String> releaseCheck;
    private Future<String> pendingCheck;
    private volatile Boolean updateAvailable;
    private java.util.function.Consumer<Boolean> updateListener = available -> {};

    /**
     * Creates the About panel and its asynchronous release-check controls.
     *
     * <p>Call on the Qt UI thread.
     * @param parent owning widget, or null for a top-level dialog
     */
    public AboutDialog(QWidget parent) {
        this(parent, null);
    }

    /**
     * Creates the About panel and its asynchronous release-check controls.
     *
     * <p>Call on the Qt UI thread.
     * @param parent owning widget, or null for a top-level dialog
     * @param releaseCheck background release lookup override, or null to use the production service
     */
    public AboutDialog(QWidget parent, Callable<String> releaseCheck) {
        super(parent);
        var build = loadBuildInfo();
        installedVersion = build.getProperty("ui.version", "unknown");
        this.releaseCheck = releaseCheck == null
                ? () -> {
                    var result = new ReleaseUpdateService().checkRelease(installedVersion);
                    availableInstaller = result.installer();
                    availableWindowsInstaller = result.windowsInstaller();
                    updateAvailable = result.updateAvailable();
                    return result.status();
                } : releaseCheck;
        setObjectName("aboutDialog");
        setWindowTitle("About");
        setMinimumWidth(760);
        var layout = new QVBoxLayout(this);
        layout.setContentsMargins(20, 16, 20, 16);
        layout.setSpacing(12);
        var heading = new QHBoxLayout();
        var icon = new QLabel();
        icon.setPixmap(QApplication.windowIcon().pixmap(72, 72));
        heading.addWidget(icon);
        var title = new QLabel("<h1>OSCAR-UI</h1><p>Version " + installedVersion + "</p>");
        heading.addWidget(title, 1);
        layout.addLayout(heading);
        var description = new QLabel("View, edit, and inspect cable modem configurations with OSCAR.");
        description.setWordWrap(true);
        layout.addWidget(description);
        var packages = new QGroupBox();
        var packageGrid = new QGridLayout(packages);
        packageGrid.setHorizontalSpacing(24);
        packageGrid.setVerticalSpacing(12);
        packageGrid.setColumnStretch(0, 2);
        packageGrid.setColumnStretch(1, 1);
        packageGrid.setColumnStretch(2, 1);
        packageGrid.addWidget(new QLabel("<b>Package</b>"), 0, 0);
        packageGrid.addWidget(new QLabel("<b>Version</b>"), 0, 1);
        packageGrid.addWidget(new QLabel("<b>Link</b>"), 0, 2);
        addPackageRow(packageGrid, 1, "OSCAR-UI", installedVersion, ReleaseUpdateService.UI_PROJECT);
        addPackageRow(packageGrid, 2, "OSCAR", build.getProperty("oscar.version", "unknown"),
                ReleaseUpdateService.OSCAR_PROJECT);
        var columns = new QHBoxLayout();
        columns.setSpacing(16);
        var applicationColumn = new QVBoxLayout();
        applicationColumn.addWidget(packages);
        columns.addLayout(applicationColumn, 1);
        columns.addWidget(registrySpecifications(), 1);
        layout.addLayout(columns);
        var links = new QHBoxLayout();
        var license = link("OSCAR-UI license: Apache License 2.0", ReleaseUpdateService.UI_PROJECT + "/blob/main/LICENSE");
        license.setObjectName("applicationLicenseLink");
        links.addWidget(license);
        links.addStretch();
        var reportIssue = link("Report an issue with OSCAR-UI", io.oscarui.service.IssueReportService.issueUrl(build, System.getProperties()));
        reportIssue.setObjectName("reportIssueLink");
        reportIssue.setToolTip("Open a GitHub issue draft with application and platform versions; review before submitting");
        links.addWidget(reportIssue);
        var updates = new QGroupBox("Release updates");
        var updateLayout = new QVBoxLayout(updates);
        updateStatus.setObjectName("updateStatus");
        updateStatus.setTextFormat(Qt.TextFormat.PlainText);
        updateStatus.setWordWrap(true);
        updateLayout.addWidget(updateStatus);
        checkButton.setObjectName("checkUpdatesButton");
        checkButton.clicked.connect(this::checkUpdates);
        updateLayout.addWidget(checkButton);
        updateButton.setObjectName("ubuntuUpdateButton");
        updateButton.setVisible(new UbuntuUpdateService().supported());
        updateButton.setEnabled(false);
        updateButton.clicked.connect(() -> {
            requestedInstaller = availableInstaller;
            accept();
        });
        updateLayout.addWidget(updateButton);
        windowsUpdateButton.setObjectName("windowsUpdateButton");
        windowsUpdateButton.setVisible(new io.oscarui.service.WindowsUpdateService().supported());
        windowsUpdateButton.setEnabled(false);
        windowsUpdateButton.clicked.connect(this::requestWindowsUpdate);
        updateLayout.addWidget(windowsUpdateButton);
        updateLayout.addWidget(releaseNotes);
        String operatingSystem = System.getProperty("os.name", "");
        String installGuide;
        if (operatingSystem.startsWith("Windows")) {
            installGuide = "windows";
        } else if (operatingSystem.startsWith("Mac")) {
            installGuide = "macos";
        } else {
            installGuide = "ubuntu";
        }
        var updateInstructions = link("How to update or uninstall OSCAR-UI",
                ReleaseUpdateService.UI_PROJECT + "/blob/main/docs/install/" + installGuide + ".md#update");
        updateInstructions.setObjectName("updateUninstallLink");
        updateLayout.addWidget(updateInstructions);
        applicationColumn.addWidget(updates);
        applicationColumn.addStretch();
        var uninstall = new QPushButton("Uninstall OSCAR-UI…");
        uninstall.setObjectName("uninstallButton");
        uninstall.setVisible(new io.oscarui.service.UninstallService().supported());
        uninstall.clicked.connect(() -> {
            uninstallRequested = true;
            accept();
        });
        applicationColumn.addWidget(uninstall);
        layout.addLayout(links);
        var buttons = new QDialogButtonBox();
        buttons.addButton(QDialogButtonBox.StandardButton.Close);
        buttons.rejected.connect(this::reject);
        layout.addWidget(buttons);
        pollTimer.setInterval(100);
        pollTimer.timeout.connect(this::finishCheck);
        finished.connect(ignored -> stopChecking());
    }

    /**
     * Registers the UI-thread listener notified after a successful release check.
     *
     * <p>Call on the Qt UI thread.
     * @param listener non-null listener invoked on the Qt UI thread
     */
    public void onUpdateAvailability(java.util.function.Consumer<Boolean> listener) {
        updateListener = listener;
    }

    /**
     * Starts a release check unless one is active or the dialog worker has stopped.
     *
     * <p>Call on the Qt UI thread.
     */
    public void checkUpdates() {
        if (pendingCheck != null || worker.isShutdown()) {
            return;
        }
        availableInstaller = null;
        availableWindowsInstaller = null;
        windowsUpdateButton.setEnabled(false);
        updateButton.setEnabled(false);
        updateStatus.setText("Checking GitHub…");
        checkButton.setEnabled(false);
        pendingCheck = worker.submit(releaseCheck);
        pollTimer.start();
    }

    /**
     * Reports whether the user requested uninstall; call on the Qt UI thread after exec().
     * @return true when the dialog was accepted through Uninstall
     */
    public boolean uninstallRequested() {
        return uninstallRequested;
    }

    /**
     * Stops polling, cancels any outstanding check, and shuts down the release worker.
     *
     * <p>Call on the Qt UI thread.
     */
    public void stopChecking() {
        pollTimer.stop();
        if (pendingCheck != null) {
            pendingCheck.cancel(true);
        }
        worker.shutdownNow();
    }

    /**
     * Reads packaged build metadata without guessing missing version values.
     * @return build properties, possibly empty when metadata is unavailable
     */
    public static Properties loadBuildInfo() {
        var build = new Properties();
        try (var resource = AboutDialog.class.getResourceAsStream("/build-info.properties")) {
            if (resource != null) {
                build.load(resource);
            }
        } catch (IOException exception) {
            // Missing metadata is displayed as unknown, never as a guessed release.
        }
        return build;
    }

    UbuntuUpdateService.Installer requestedInstaller() {
        return requestedInstaller;
    }

    io.oscarui.service.WindowsUpdateService.Installer requestedWindowsInstaller() {
        return requestedWindowsInstaller;
    }

    private QWidget registrySpecifications() {
        var panel = new QGroupBox();
        panel.setObjectName("registrySpecifications");
        var grid = new QGridLayout(panel);
        grid.setHorizontalSpacing(24);
        grid.setVerticalSpacing(12);
        grid.setColumnStretch(1, 1);
        grid.addWidget(new QLabel("<b>TLV Registry</b>"), 0, 0);
        grid.addWidget(new QLabel("<b>Specification</b>"), 0, 1);
        panel.setToolTip("File types and source editions from the bundled OSCAR registry. "
                + "Source provenance does not imply complete specification compliance.");
        try {
            var types = new io.oscarui.service.OscarDocumentService().types();
            for (int row = 0; row < types.size(); row++) {
                var type = types.get(row);
                var fileType = new QLabel(type.header());
                fileType.setObjectName("registryFileType" + row);
                fileType.setTextFormat(Qt.TextFormat.PlainText);
                grid.addWidget(fileType, row + 1, 0);
                var edition = new QLabel(type.edition().replace(" + ", "\n"));
                edition.setObjectName("registryEdition" + row);
                edition.setTextFormat(Qt.TextFormat.PlainText);
                edition.setToolTip(type.edition());
                grid.addWidget(edition, row + 1, 1);
            }
        } catch (io.oscarui.service.InspectionException exception) {
            grid.addWidget(new QLabel("Unavailable"), 1, 0);
            grid.addWidget(new QLabel("Cannot read bundled registry metadata"), 1, 1);
        }
        return panel;
    }

    private void requestWindowsUpdate() {
        requestedWindowsInstaller = availableWindowsInstaller;
        accept();
    }

    private void finishCheck() {
        if (pendingCheck == null || !pendingCheck.isDone()) {
            return;
        }
        pollTimer.stop();
        try {
            updateStatus.setText(pendingCheck.get());
            if (updateAvailable != null) {
                updateListener.accept(updateAvailable);
            }
            updateButton.setEnabled(availableInstaller != null);
            windowsUpdateButton.setEnabled(availableWindowsInstaller != null);
            if (availableWindowsInstaller != null) {
                updateStatus.setText(updateStatus.text() + " Windows patch: "
                        + String.format(java.util.Locale.ROOT, "%.1f MiB", availableWindowsInstaller.size() / 1048576.0));
            } else if (new io.oscarui.service.WindowsUpdateService().supported() && Boolean.TRUE.equals(updateAvailable)) {
                updateStatus.setText(updateStatus.text() + " Full MSI installation required; see the update instructions.");
            }
            if (availableInstaller != null && new UbuntuUpdateService().supported()) {
                releaseNotes.setText("<a href=\"" + ReleaseUpdateService.UI_PROJECT + "/releases/tag/v"
                        + availableInstaller.version() + "\">View release notes and downloads</a>");
                updateStatus.setText(updateStatus.text() + " Ubuntu download: "
                        + String.format(java.util.Locale.ROOT, "%.1f MiB", availableInstaller.size() / 1048576.0));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            updateStatus.setText("Update check interrupted. Please try again.");
        } catch (ExecutionException | CancellationException exception) {
            updateStatus.setText("Unable to check for updates. Check your connection or try again later."
                    + " You can also open the releases link below.");
        }
        pendingCheck = null;
        checkButton.setEnabled(true);
    }

    private static void addPackageRow(QGridLayout grid, int row, String packageName,
                                      String packageVersion, String projectUrl) {
        grid.addWidget(new QLabel(packageName), row, 0);
        grid.addWidget(new QLabel(packageVersion), row, 1);
        var projectLink = link("GitHub", projectUrl);
        projectLink.setAccessibleName(packageName + " on GitHub");
        projectLink.setToolTip(projectUrl);
        grid.addWidget(projectLink, row, 2);
    }

    private static QLabel link(String caption, String address) {
        var label = new QLabel("<a href=\"" + address.replace("&", "&amp;") + "\">" + caption + "</a>");
        label.setOpenExternalLinks(true);
        return label;
    }
}
