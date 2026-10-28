/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.maintenance;

import io.oscarui.service.ReleaseUpdateService.ReleaseCheck;
import io.qt.core.QTimer;
import io.qt.widgets.QPushButton;
import java.util.concurrent.*;

/** Quiet startup release lookup; all widget changes remain on the Qt thread. */
public final class UpgradeChip extends QPushButton {
    private final Runnable openUpdates;
    private final QTimer poller = new QTimer(this);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "oscar-upgrade-chip");
        thread.setDaemon(true);
        return thread;
    });
    private Future<ReleaseCheck> pending;

    /**
     * Creates a hidden update indicator that delegates clicks to the update flow.
     *
     * <p>Call on the Qt UI thread.
     * @param openUpdates UI-thread callback invoked when the indicator is clicked
     */
    public UpgradeChip(Runnable openUpdates) {
        super("Update");
        this.openUpdates = openUpdates;
        setObjectName("upgradeChip");
        setAccessibleName("Update OSCAR-UI");
        setToolTip("A newer OSCAR-UI release is available. View update options.");
        setStyleSheet("QPushButton { background: #238636; color: white; border: 1px solid #2ea043;"
                + " border-radius: 4px; padding: 3px 12px; font-weight: bold; }"
                + " QPushButton:hover { background: #2ea043; }"
                + " QPushButton:focus { border: 2px solid white; }");
        hide();
        clicked.connect(this::openUpdateOptions);
        poller.setInterval(100);
        poller.timeout.connect(this::finishCheck);
    }

    /**
     * Starts the asynchronous release lookup for the update indicator.
     *
     * <p>Call on the Qt UI thread.
     * @param lookup non-null background release lookup; must not access Qt widgets
     */
    public void check(Callable<ReleaseCheck> lookup) {
        if (pending == null && !worker.isShutdown()) {
            pending = worker.submit(lookup);
            poller.start();
        }
    }

    /**
     * Shows or hides the update indicator according to release availability.
     *
     * <p>Call on the Qt UI thread.
     * @param available true when a newer release is available
     */
    public void setAvailable(boolean available) {
        // A newer manual check supersedes an outstanding startup lookup.
        if (pending != null) {
            pending.cancel(true);
            pending = null;
        }
        poller.stop();
        setVisible(available);
    }

    /**
     * Stops polling and cancels the update lookup worker.
     *
     * <p>Call on the Qt UI thread.
     */
    public void stop() {
        poller.stop();
        if (pending != null) {
            pending.cancel(true);
            pending = null;
        }
        worker.shutdownNow();
    }

    private void openUpdateOptions() {
        openUpdates.run();
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
