package io.oscarui.ui;

import io.oscarui.service.ConfigurationTextPreview;
import io.oscarui.service.OscarDocumentService.Node;
import io.qt.gui.QTextCursor;
import io.qt.widgets.QPlainTextEdit;

/** Read-only Oscar source preview with display-only comment visibility. */
final class ConfigurationTextPane extends QPlainTextEdit {
    private ConfigurationTextPreview preview = new ConfigurationTextPreview("", true);

    ConfigurationTextPane() {
        setObjectName("configurationText");
        setReadOnly(true);
        setLineWrapMode(LineWrapMode.NoWrap);
    }

    void displaySource(String configurationText, boolean showTlvComments) {
        preview = new ConfigurationTextPreview(configurationText, showTlvComments);
        setPlainText(preview.text());
    }

    void highlight(Node selectedTlv) {
        if (selectedTlv == null || selectedTlv.tlvPath().isEmpty()) {
            return;
        }
        var selectionCursor = textCursor();
        selectionCursor.setPosition(preview.displayOffset(selectedTlv.sourceStartOffset()));
        selectionCursor.setPosition(preview.displayOffset(selectedTlv.sourceEndExclusive()), QTextCursor.MoveMode.KeepAnchor);
        setTextCursor(selectionCursor);
        ensureCursorVisible();
    }
}
