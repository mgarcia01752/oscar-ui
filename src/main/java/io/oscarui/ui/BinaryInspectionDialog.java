package io.oscarui.ui;

import io.qt.gui.QFontDatabase;
import io.qt.widgets.*;

/** Read-only Oscar binary inspection listing, separate from the editable document. */
final class BinaryInspectionDialog extends QDialog {
    enum View { HEX_DUMP, TLV_BREAKDOWN }

    BinaryInspectionDialog(QWidget parent, View view, String listing, boolean masked) {
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
