/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.inspection;

import io.qt.gui.QFontDatabase;
import io.qt.widgets.*;

/** Metadata-only diagnostics for a failed binary open, independent of the active document. */
public final class BinaryFailureDialog extends QDialog {
    /**
     * Displays a read-only structural report after an unsuccessful binary import.
     *
     * <p>Call on the Qt UI thread.
     * @param parent owning widget, or null for a top-level dialog
     * @param report read-only structural report to display
     */
    public BinaryFailureDialog(QWidget parent, String report) {
        super(parent);
        setObjectName("binaryFailureDialog");
        setWindowTitle("Binary import failed");
        resize(980, 620);
        var layout = new QVBoxLayout(this);
        var output = new QPlainTextEdit();
        output.setObjectName("binaryFailureReport");
        output.setReadOnly(true);
        output.setLineWrapMode(QPlainTextEdit.LineWrapMode.NoWrap);
        output.setFont(QFontDatabase.systemFont(QFontDatabase.SystemFont.FixedFont));
        output.setPlainText(report);
        layout.addWidget(output);
        var buttons = new QDialogButtonBox();
        var copy = buttons.addButton("Copy report", QDialogButtonBox.ButtonRole.ActionRole);
        copy.setObjectName("copyBinaryFailureReport");
        copy.clicked.connect(() -> QApplication.clipboard().setText(report));
        buttons.addButton(QDialogButtonBox.StandardButton.Close);
        buttons.rejected.connect(this::reject);
        layout.addWidget(buttons);
    }
}
