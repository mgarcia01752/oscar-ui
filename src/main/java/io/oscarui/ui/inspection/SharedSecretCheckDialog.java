/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.inspection;

import io.oscarui.ui.settings.SharedSecretField;

import io.oscarui.service.SharedSecretCheckService;
import io.oscarui.service.InspectionException;
import io.qt.core.QTimer;
import io.qt.widgets.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/** Isolated binary checker; credentials never enter saved settings or the editor. */
public final class SharedSecretCheckDialog extends QDialog {
    private final QLineEdit secret = new QLineEdit();
    private final QLineEdit file = new QLineEdit();
    private final QLabel result = new QLabel();
    private final QPushButton check = new QPushButton("Check");
    private final QPushButton browse = new QPushButton("Browse...");
    private final QTimer timer = new QTimer(this);
    private CompletableFuture<String> pending;

    /**
     * Creates an independent, asynchronous shared-secret verification dialog.
     *
     * <p>Call on the Qt UI thread.
     * @param parent owning widget, or null for a top-level dialog
     */
    public SharedSecretCheckDialog(QWidget parent) {
        super(parent);
        setObjectName("sharedSecretCheckDialog");
        setWindowTitle("Check Shared Secret");
        setMinimumWidth(520);
        var layout = new QVBoxLayout(this);
        var form = new QFormLayout();
        secret.setObjectName("checkSharedSecret");
        secret.setEchoMode(QLineEdit.EchoMode.Password);
        form.addRow("Shared secret", new SharedSecretField(this, secret));
        file.setObjectName("checkBinaryPath");
        form.addRow("Binary file", file);
        layout.addLayout(form);
        browse.setObjectName("browseCheckBinary");
        browse.clicked.connect(() -> {
            var selection = QFileDialog.getOpenFileName(this, "Select DOCSIS binary", "",
                    "Binary configurations (*.bin *.cm *.cfg);;All files (*)");
            if (selection != null && selection.result != null && !selection.result.isEmpty()) {
                file.setText(selection.result);
            }
        });
        layout.addWidget(browse);
        var note = new QLabel("Binary files only. DOCSIS compatibility is detected automatically. "
                + "The file and editor remain unchanged. A different extended-MIC key can also cause a mismatch.");
        note.setWordWrap(true);
        layout.addWidget(note);
        result.setObjectName("sharedSecretCheckResult");
        result.setWordWrap(true);
        layout.addWidget(result);
        check.setObjectName("runSharedSecretCheck");
        check.setEnabled(false);
        secret.textChanged.connect(this::updateCheck);
        file.textChanged.connect(this::updateCheck);
        check.clicked.connect(this::startCheck);
        layout.addWidget(check);
        var buttons = new QDialogButtonBox();
        buttons.addButton(QDialogButtonBox.StandardButton.Close);
        buttons.rejected.connect(this::reject);
        layout.addWidget(buttons);
        timer.setInterval(50);
        timer.timeout.connect(this::receiveResult);
        finished.connect(code -> {
            timer.stop();
            secret.clear();
        });
    }

    private void updateCheck() {
        if (pending == null) {
            result.clear();
        }
        check.setEnabled(pending == null && !secret.text().isEmpty() && !file.text().isBlank());
    }

    private void startCheck() {
        byte[] secretBytes = secret.text().getBytes(StandardCharsets.UTF_8);
        String binaryPath = file.text();
        result.setText("Checking...");
        browse.setEnabled(false);
        secret.setEnabled(false);
        file.setEnabled(false);
        check.setEnabled(false);
        pending = CompletableFuture.supplyAsync(() -> {
            try {
                var outcome = new SharedSecretCheckService().check(Path.of(binaryPath), secretBytes);
                return outcome == SharedSecretCheckService.Outcome.CORRECT
                        ? "Correct Shared Secret" : "Incorrect Shared Secret";
            } catch (InspectionException failure) {
                return "Unable to Check: " + failure.getMessage();
            } catch (RuntimeException failure) {
                return "Unable to Check: Select a readable DOCSIS binary file.";
            } finally {
                Arrays.fill(secretBytes, (byte) 0);
            }
        });
        timer.start();
    }

    private void receiveResult() {
        if (pending == null || !pending.isDone()) {
            return;
        }
        timer.stop();
        String message = pending.join();
        pending = null;
        secret.clear();
        secret.setEnabled(true);
        file.setEnabled(true);
        browse.setEnabled(true);
        updateCheck();
        result.setText(message);
    }
}
