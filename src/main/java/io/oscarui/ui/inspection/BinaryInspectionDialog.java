/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.inspection;

import io.qt.gui.QFontDatabase;
import io.qt.widgets.*;

/** Read-only Oscar binary inspection listing, separate from the editable document. */
public final class BinaryInspectionDialog extends QDialog {
    public enum View { HEX_DUMP, TLV_BREAKDOWN }

    /**
     * Displays a read-only binary listing supplied by the inspection service.
     *
     * <p>Call on the Qt UI thread.
     * @param parent owning widget, or null for a top-level dialog
     * @param view requested binary listing view
     * @param listing precomputed listing text; does not trigger compilation
     * @param masked true when values are hidden by display masking
     */
    public BinaryInspectionDialog(QWidget parent, View view, String listing, boolean masked) {
        super(parent);
        setObjectName("binaryInspectionDialog");
        setWindowTitle(view == View.HEX_DUMP ? "HexDump" : "TLV breakdown");
        resize(900, 600);
        var layout = new QVBoxLayout(this);
        var note = new QLabel(masked ? "Masked binary snapshot: sensitive top-level records are replaced by FF FF FF FF. "
                + "Original offsets, lengths, and ASCII columns are omitted. Unsaved edits are not included." : "Binary snapshot from the last open or save. Unsaved edits are not included."
                + (view == View.TLV_BREAKDOWN
                ? " Top-level TLVs in file order; nested bytes stay within each parent. Padding and end markers are omitted."
                : " All bytes, including padding and end markers."));
        note.setWordWrap(true);
        layout.addWidget(note);
        var output = new QPlainTextEdit();
        output.setObjectName("binaryInspectionText");
        output.setReadOnly(true);
        output.setLineWrapMode(QPlainTextEdit.LineWrapMode.NoWrap);
        output.setFont(QFontDatabase.systemFont(QFontDatabase.SystemFont.FixedFont));
        output.setPlainText(listing);
        layout.addWidget(output, 1);
        var buttons = new QDialogButtonBox();
        buttons.addButton(QDialogButtonBox.StandardButton.Close);
        buttons.rejected.connect(this::reject);
        layout.addWidget(buttons);
    }
}
