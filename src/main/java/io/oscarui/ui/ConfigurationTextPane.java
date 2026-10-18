package io.oscarui.ui;

import io.oscarui.service.OscarDocumentService.Node;
import io.qt.gui.QTextCursor;
import io.qt.widgets.QPlainTextEdit;

/** Read-only source preview and translation of source offsets to Qt positions. */
final class ConfigurationTextPane extends QPlainTextEdit {
    private String sourceText = "";

    ConfigurationTextPane() {
        setObjectName("configurationText");
        setReadOnly(true);
        setLineWrapMode(LineWrapMode.NoWrap);
    }

    void displaySource(String configurationText) {
        sourceText = configurationText;
        setPlainText(configurationText);
    }

    void highlight(Node selectedTlv) {
        if (selectedTlv == null || selectedTlv.tlvPath().isEmpty()) {
            return;
        }
        var selectionCursor = textCursor();
        selectionCursor.setPosition(textPosition(selectedTlv.sourceStartOffset()));
        selectionCursor.setPosition(textPosition(selectedTlv.sourceEndExclusive()), QTextCursor.MoveMode.KeepAnchor);
        setTextCursor(selectionCursor);
        ensureCursorVisible();
    }

    private int textPosition(int sourceOffset) {
        // QTextDocument collapses each CRLF pair to one paragraph separator.
        int textOffset = sourceOffset;
        for (int characterIndex = 0; characterIndex < sourceOffset - 1; characterIndex++) {
            if (sourceText.charAt(characterIndex) == '\r' && sourceText.charAt(characterIndex + 1) == '\n') {
                textOffset--;
            }
        }
        return textOffset;
    }
}
