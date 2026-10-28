/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.workspace;

import io.oscarui.service.ConfigurationTextPreview;
import io.oscarui.service.OscarDocumentService.Node;
import io.qt.gui.QTextCursor;
import io.qt.widgets.QPlainTextEdit;

/** Read-only Oscar source preview with display-only comment visibility. */
public final class ConfigurationTextPane extends QPlainTextEdit {
    private ConfigurationTextPreview preview = new ConfigurationTextPreview("", true);

    ConfigurationTextPane() {
        setObjectName("configurationText");
        setReadOnly(true);
        setLineWrapMode(LineWrapMode.NoWrap);
    }

    /**
     * Renders a document preview with the requested comment and masking settings.
     *
     * <p>Call on the Qt UI thread.
     * @param document non-null document snapshot; this operation does not mutate it
     * @param showTlvComments true to retain TLV-number comments in the preview
     * @param masking non-null masking policy used only for presentation
     */
    public void displayDocument(io.oscarui.service.OscarDocumentService.Document document, boolean showTlvComments,
                         io.oscarui.service.SnmpSecretMask masking) {
        preview = new ConfigurationTextPreview(document.text(), showTlvComments, masking.replacements(document));
        setPlainText(preview.text());
    }

    /**
     * Highlights the selected TLV source span; null or root selections leave the cursor unchanged.
     *
     * <p>Call on the Qt UI thread.
     * @param selectedTlv selected source node, or null to leave the cursor unchanged
     */
    public void highlight(Node selectedTlv) {
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
