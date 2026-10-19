/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui;

import io.oscarui.service.ReleaseUpdateService.ReleaseCheck;
import io.qt.core.QTimer;
import io.qt.widgets.QPushButton;
import java.util.concurrent.*;

/** Quiet startup release lookup; all widget changes remain on the Qt thread. */
final class UpgradeChip extends QPushButton {
    private final QTimer poller = new QTimer(this);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "oscar-upgrade-chip");
        thread.setDaemon(true);
        return thread;
    });
    private Future<ReleaseCheck> pending;

    UpgradeChip(Runnable openUpdates) {
        super("Upgrade");
        setObjectName("upgradeChip");
        setAccessibleName("Upgrade OSCAR-UI");
        setToolTip("A newer OSCAR-UI release is available. View upgrade options.");
        setStyleSheet("QPushButton { background: #238636; color: white; border: 1px solid #2ea043;"
                + " border-radius: 10px; padding: 3px 12px; font-weight: bold; }"
                + " QPushButton:hover { background: #2ea043; }"
                + " QPushButton:focus { border: 2px solid white; }");
        hide();
        clicked.connect(openUpdates::run);
        poller.setInterval(100);
        poller.timeout.connect(this::finishCheck);
    }

    void check(Callable<ReleaseCheck> lookup) {
        if (pending == null && !worker.isShutdown()) {
            pending = worker.submit(lookup);
            poller.start();
        }
    }

    void setAvailable(boolean available) {
        // A newer manual check supersedes an outstanding startup lookup.
        if (pending != null) {
            pending.cancel(true);
            pending = null;
        }
        poller.stop();
        setVisible(available);
    }

    void stop() {
        poller.stop();
        if (pending != null) {
            pending.cancel(true);
            pending = null;
        }
        worker.shutdownNow();
    }

    private void finishCheck() {
        if (pending == null || !pending.isDone()) {
            return;
        }
        poller.stop();
        try {
            setVisible(pending.get().updateAvailable());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | CancellationException error) {
            // Offline startup leaves the chip hidden; About offers an explicit retry.
        } finally {
            pending = null;
        }
    }
}
