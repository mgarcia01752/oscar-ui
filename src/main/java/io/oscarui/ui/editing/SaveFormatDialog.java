/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.editing;

import io.qt.widgets.*;

/** Chooses an export format without starting a save operation. */
public final class SaveFormatDialog extends QDialog {
    public enum SaveFormat { TEXT, BINARY }

    private SaveFormat selectedFormat = SaveFormat.TEXT;

    /**
     * Creates Text/Binary format choices without writing a file.
     *
     * <p>Call on the Qt UI thread.
     * @param parent owning widget, or null for a top-level dialog
     */
    public SaveFormatDialog(QWidget parent) {
        super(parent);
        setObjectName("saveFormatDialog");
        setWindowTitle("Save configuration");
        var layout = new QVBoxLayout(this);
        layout.addWidget(new QLabel("Choose a save format. Binary exports through Oscar; Text preserves editable source."));
        var buttons = new QDialogButtonBox();
        var textButton = buttons.addButton("Text", QDialogButtonBox.ButtonRole.ActionRole);
        textButton.setObjectName("chooseTextSave");
        var binaryButton = buttons.addButton("Binary", QDialogButtonBox.ButtonRole.ActionRole);
        binaryButton.setObjectName("chooseBinarySave");
        textButton.clicked.connect(this::accept);
        binaryButton.clicked.connect(() -> {
            selectedFormat = SaveFormat.BINARY;
            accept();
        });
        buttons.addButton(QDialogButtonBox.StandardButton.Cancel);
        buttons.rejected.connect(this::reject);
        layout.addWidget(buttons);
    }

    /**
     * Returns the selected save format; no file is written.
     *
     * <p>Call on the Qt UI thread.
     * @return Text or Binary as selected in the dialog
     */
    public SaveFormat selectedFormat() {
        return selectedFormat;
    }
}
