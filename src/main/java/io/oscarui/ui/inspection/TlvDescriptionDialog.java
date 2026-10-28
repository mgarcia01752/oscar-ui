/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.inspection;

import io.oscarui.ui.formatting.TlvDescriptionFormatter;

import io.qt.widgets.*;

/** Scrollable, read-only description supplied by the Oscar registry. */
public final class TlvDescriptionDialog extends QDialog {
    /**
     * Displays the registry description without modifying its source text.
     *
     * <p>Call on the Qt UI thread.
     * @param parent owning widget, or null for a top-level dialog
     * @param tlvName human-readable TLV name for the window title
     * @param description registry description, or null/blank for empty output
     */
    public TlvDescriptionDialog(QWidget parent, String tlvName, String description) {
        super(parent);
        setObjectName("tlvDescriptionDialog");
        setWindowTitle("Description - " + tlvName);
        resize(650, 400);
        var layout = new QVBoxLayout(this);
        var text = new QPlainTextEdit();
        text.setObjectName("tlvDescriptionText");
        text.setReadOnly(true);
        String formatted = TlvDescriptionFormatter.format(description);
        text.setPlainText(formatted.isBlank() ? "No description is available for this TLV." : formatted);
        layout.addWidget(text);
        var buttons = new QDialogButtonBox();
        buttons.addButton(QDialogButtonBox.StandardButton.Close);
        buttons.rejected.connect(this::reject);
        layout.addWidget(buttons);
    }
}
