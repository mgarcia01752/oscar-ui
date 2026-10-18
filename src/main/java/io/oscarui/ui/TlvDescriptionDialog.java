/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui;

import io.qt.widgets.*;

/** Scrollable, read-only description supplied by the Oscar registry. */
final class TlvDescriptionDialog extends QDialog {
    TlvDescriptionDialog(QWidget parent, String tlvName, String description) {
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
