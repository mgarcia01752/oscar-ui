/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui;

import io.oscarui.service.UbuntuUpdateService;
import io.qt.core.QTimer;
import io.qt.core.Qt;
import io.qt.widgets.*;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Owns the cancelable download; installation begins only after an explicit final confirmation. */
final class UbuntuUpdateDialog extends QDialog {
    private final UbuntuUpdateService service;
    private final UbuntuUpdateService.Installer installer;
    private final QLabel status = new QLabel();
    private final QProgressBar progress = new QProgressBar();
    private final QPushButton install = new QPushButton("Close and install");
    private final QPushButton cancel = new QPushButton("Cancel");
    private final QTimer timer = new QTimer(this);
    private final AtomicBoolean canceled = new AtomicBoolean();
    private final AtomicInteger percentage = new AtomicInteger();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "oscar-ui-update-download");
        thread.setDaemon(true);
        return thread;
    });
    private Future<Path> pending;
    private Path downloaded;

    UbuntuUpdateDialog(QWidget parent, UbuntuUpdateService.Installer installer, UbuntuUpdateService service) {
        this(parent, installer, service, null);
    }

    UbuntuUpdateDialog(QWidget parent, UbuntuUpdateService.Installer installer, UbuntuUpdateService service,
                       Callable<Path> downloadTask) {
        super(parent);
        this.installer = installer;
        this.service = service;
        setObjectName("ubuntuUpdateDialog");
        setWindowTitle("Update OSCAR-UI");
        setMinimumWidth(480);
        var layout = new QVBoxLayout(this);
        status.setTextFormat(Qt.TextFormat.PlainText);
        status.setWordWrap(true);
        status.setText("Downloading OSCAR-UI " + installer.version() + "…");
        layout.addWidget(status);
        layout.addWidget(progress);
        var buttons = new QHBoxLayout();
        install.setEnabled(false);
        install.setObjectName("installUbuntuUpdate");
        install.clicked.connect(this::accept);
        cancel.clicked.connect(this::reject);
        buttons.addWidget(install);
        buttons.addWidget(cancel);
        layout.addLayout(buttons);
        pending = worker.submit(downloadTask == null
                ? () -> service.download(installer, percentage::set, canceled::get) : downloadTask);
        timer.setInterval(100);
        timer.timeout.connect(this::poll);
        timer.start();
    }

    /**
     * Cancels a pending download or discards the verified package before closing.
     * Cancellation waits for the bounded network read to return; no installer is started.
     */
    @Override public void reject() {
        canceled.set(true);
        if (pending != null) {
            status.setText("Canceling download…");
            cancel.setEnabled(false);
            return;
        }
        service.discard(downloaded);
        downloaded = null;
        super.reject();
    }

    Path downloadedPackage() {
        return downloaded;
    }

    private void poll() {
        progress.setValue(percentage.get());
        if (pending == null || !pending.isDone()) {
            return;
        }
        timer.stop();
        try {
            downloaded = pending.get();
            status.setText("Download verified. Close OSCAR-UI to install version " + installer.version()
                    + ". Ubuntu will ask for administrator approval. The installer reports the result and offers to reopen the app.");
            install.setEnabled(true);
        } catch (Exception exception) {
            status.setText("Download failed or was canceled. Nothing was installed. Retry from About or use the releases link.");
        } finally {
            pending = null;
            worker.shutdown();
        }
        if (canceled.get()) {
            reject();
        }
    }
}
