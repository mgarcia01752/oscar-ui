/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui;

import io.oscarui.service.ReleaseUpdateService;
import io.qt.core.QTimer;
import io.qt.core.Qt;
import io.qt.widgets.*;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.*;

/** Application identity, project links, and an explicit asynchronous update check. */
final class AboutDialog extends QDialog {
    private final QLabel updateStatus = new QLabel("Check GitHub for the latest stable OSCAR-UI release.");
    private final QPushButton checkButton = new QPushButton("Check for updates");
    private final QTimer pollTimer = new QTimer(this);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "oscar-ui-release-check");
        thread.setDaemon(true);
        return thread;
    });
    private final String installedVersion;
    private final Callable<String> releaseCheck;
    private Future<String> pendingCheck;

    AboutDialog(QWidget parent) {
        this(parent, null);
    }

    AboutDialog(QWidget parent, Callable<String> releaseCheck) {
        super(parent);
        var build = loadBuildInfo();
        installedVersion = build.getProperty("ui.version", "unknown");
        this.releaseCheck = releaseCheck == null
                ? () -> new ReleaseUpdateService().check(installedVersion) : releaseCheck;
        setObjectName("aboutDialog");
        setWindowTitle("About");
        setMinimumWidth(480);
        var layout = new QVBoxLayout(this);
        layout.setContentsMargins(24, 24, 24, 24);
        layout.setSpacing(16);
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
        addPackageRow(packageGrid, 3, "Qt Jambi", build.getProperty("qt.version", "unknown"),
                "https://github.com/OmixVisualization/qtjambi");
        layout.addWidget(packages);
        var license = link("OSCAR-UI license: Apache License 2.0", ReleaseUpdateService.UI_PROJECT + "/blob/main/LICENSE");
        license.setObjectName("applicationLicenseLink");
        layout.addWidget(license);
        var reportIssue = link("Report an issue with OSCAR-UI", ReleaseUpdateService.UI_PROJECT + "/issues/new/choose");
        reportIssue.setObjectName("reportIssueLink");
        reportIssue.setToolTip("Open the OSCAR-UI issue tracker on GitHub");
        layout.addWidget(reportIssue);
        var updates = new QGroupBox("Release updates");
        var updateLayout = new QVBoxLayout(updates);
        updateStatus.setObjectName("updateStatus");
        updateStatus.setTextFormat(Qt.TextFormat.PlainText);
        updateStatus.setWordWrap(true);
        updateLayout.addWidget(updateStatus);
        checkButton.setObjectName("checkUpdatesButton");
        checkButton.clicked.connect(this::checkUpdates);
        updateLayout.addWidget(checkButton);
        updateLayout.addWidget(link("View releases and downloads", ReleaseUpdateService.UI_PROJECT + "/releases/latest"));
        layout.addWidget(updates);
        var buttons = new QDialogButtonBox();
        buttons.addButton(QDialogButtonBox.StandardButton.Close);
        buttons.rejected.connect(this::reject);
        layout.addWidget(buttons);
        pollTimer.setInterval(100);
        pollTimer.timeout.connect(this::finishCheck);
        finished.connect(ignored -> stopChecking());
    }

    void checkUpdates() {
        if (pendingCheck != null || worker.isShutdown()) {
            return;
        }
        updateStatus.setText("Checking GitHub…");
        checkButton.setEnabled(false);
        pendingCheck = worker.submit(releaseCheck);
        pollTimer.start();
    }

    void stopChecking() {
        pollTimer.stop();
        if (pendingCheck != null) {
            pendingCheck.cancel(true);
        }
        worker.shutdownNow();
    }

    private void finishCheck() {
        if (pendingCheck == null || !pendingCheck.isDone()) {
            return;
        }
        pollTimer.stop();
        try {
            updateStatus.setText(pendingCheck.get());
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
        var label = new QLabel("<a href=\"" + address + "\">" + caption + "</a>");
        label.setOpenExternalLinks(true);
        return label;
    }

    private static Properties loadBuildInfo() {
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
}
