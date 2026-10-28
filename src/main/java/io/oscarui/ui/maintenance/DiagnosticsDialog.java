/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.maintenance;

import io.oscarui.service.SessionDiagnostics;
import io.qt.widgets.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Read-only session diagnostics with explicit export, never automatic uploading. */
public final class DiagnosticsDialog extends QDialog {
    private final QPlainTextEdit output = new QPlainTextEdit();

    /**
     * Creates a diagnostic snapshot viewer on the Qt UI thread.
     * @param parent owning widget, or null for a top-level dialog
     */
    public DiagnosticsDialog(QWidget parent) {
        super(parent);
        setObjectName("diagnosticsDialog");
        setWindowTitle("Session diagnostics");
        resize(780, 460);
        var layout = new QVBoxLayout(this);
        var find = new QLineEdit();
        find.setPlaceholderText("Find text — press Enter for next match");
        find.returnPressed.connect(() -> {
            if (!output.find(find.text())) {
                var cursor = output.textCursor();
                cursor.movePosition(io.qt.gui.QTextCursor.MoveOperation.Start);
                output.setTextCursor(cursor);
                output.find(find.text());
            }
        });
        layout.addWidget(find);
        output.setObjectName("diagnosticsText");
        output.setReadOnly(true);
        output.setPlainText(SessionDiagnostics.snapshot());
        layout.addWidget(output);
        var buttons = new QDialogButtonBox();
        buttons.addButton("Refresh", QDialogButtonBox.ButtonRole.ActionRole).clicked.connect(
                () -> output.setPlainText(SessionDiagnostics.snapshot()));
        buttons.addButton("Copy", QDialogButtonBox.ButtonRole.ActionRole).clicked.connect(
                () -> QApplication.clipboard().setText(output.toPlainText()));
        buttons.addButton("Save…", QDialogButtonBox.ButtonRole.ActionRole).clicked.connect(() -> saveLog(this));
        buttons.addButton("Clear", QDialogButtonBox.ButtonRole.ActionRole).clicked.connect(() -> {
            SessionDiagnostics.clear();
            output.setPlainText(SessionDiagnostics.snapshot());
        });
        buttons.addButton(QDialogButtonBox.StandardButton.Close);
        buttons.rejected.connect(this::reject);
        layout.addWidget(buttons);
    }

    /**
     * Offers an explicit UTF-8 export of the current session log; cancellation writes nothing.
     * Call on the Qt UI thread. Write failures are reported without paths or file contents.
     * @param parent owning widget, or null for a top-level file chooser
     */
    public static void saveLog(QWidget parent) {
        var selected = QFileDialog.getSaveFileName(parent, "Save diagnostics", "oscar-ui-diagnostics.txt",
                "Text files (*.txt)");
        if (selected == null || selected.result == null || selected.result.isBlank()) {
            return;
        }
        try {
            Files.writeString(Path.of(selected.result), SessionDiagnostics.snapshot(), StandardCharsets.UTF_8);
        } catch (java.io.IOException | RuntimeException failure) {
            QMessageBox.warning(parent, "Save diagnostics", "Could not save diagnostics. Check destination permissions.");
        }
    }
}
