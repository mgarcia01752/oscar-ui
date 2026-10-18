package io.oscarui.ui;

import io.oscarui.service.OscarDocumentService.Node;
import io.qt.gui.QTextCursor;
import io.qt.gui.QPainter;
import io.qt.gui.QPaintEvent;
import io.qt.gui.QResizeEvent;
import io.qt.gui.QPalette;
import io.qt.widgets.QWidget;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import io.qt.widgets.QPlainTextEdit;

/** Read-only source preview and translation of source offsets to Qt positions. */
final class ConfigurationTextPane extends QPlainTextEdit {
    private String sourceText = "";
    private final Map<Integer, String> tlvNumbersByLine = new TreeMap<>();
    private final QWidget tlvGutter = new QWidget(this) {
        @Override protected void paintEvent(QPaintEvent event) {
            paintTlvNumbers();
        }
    };
    private boolean showTlvNumbers = true;
    private int gutterWidth;


    ConfigurationTextPane() {
        setObjectName("configurationText");
        setReadOnly(true);
        setLineWrapMode(LineWrapMode.NoWrap);
        tlvGutter.setObjectName("textTlvNumbers");
        tlvGutter.setToolTip("TLV numbers corresponding to the names in the configuration text");
        updateRequest.connect((rectangle, scrollPixels) -> tlvGutter.update());
    }

    @Override protected void resizeEvent(QResizeEvent event) {
        super.resizeEvent(event);
        positionGutter();
    }

    void displaySource(String configurationText, Node root, boolean showNumbers) {
        tlvNumbersByLine.clear();
        sourceText = configurationText;
        collectTlvNumbers(root);
        showTlvNumbers = showNumbers;
        displaySource(configurationText);
        updateGutter();
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

    private void displaySource(String configurationText) {
        sourceText = configurationText;
        setPlainText(configurationText);
    }

    private void collectTlvNumbers(Node node) {
        if (!node.tlvPath().isEmpty() && !node.tlvPath().contains(Node.UNMAPPED_TLV_TYPE_CODE)) {
            int lineNumber = 0;
            for (int offset = 0; offset < node.sourceStartOffset(); offset++) {
                if (sourceText.charAt(offset) == '\n') {
                    lineNumber++;
                }
            }
            String dottedNumber = node.tlvPath().stream().map(String::valueOf).collect(Collectors.joining("."));
            tlvNumbersByLine.merge(lineNumber, dottedNumber, (existing, next) -> existing + ", " + next);
        }
        for (Node child : node.childTlvs()) {
            collectTlvNumbers(child);
        }
    }

    private void updateGutter() {
        gutterWidth = showTlvNumbers ? fontMetrics().horizontalAdvance("TLV") + 16 : 0;
        if (showTlvNumbers) {
            for (String dottedNumber : tlvNumbersByLine.values()) {
                gutterWidth = Math.max(gutterWidth, fontMetrics().horizontalAdvance(dottedNumber) + 16);
            }
        }
        setViewportMargins(gutterWidth, 0, 0, 0);
        tlvGutter.setVisible(showTlvNumbers);
        positionGutter();
        tlvGutter.update();
    }

    private void positionGutter() {
        var bounds = contentsRect();
        tlvGutter.setGeometry(bounds.left(), bounds.top(), gutterWidth, bounds.height());
    }

    private void paintTlvNumbers() {
        var painter = new QPainter(tlvGutter);
        try {
            painter.fillRect(tlvGutter.rect(), palette().color(QPalette.ColorRole.AlternateBase));
            painter.setPen(palette().color(QPalette.ColorRole.Text));
            painter.setFont(font());
            var block = firstVisibleBlock();
            while (block.isValid()) {
                var bounds = blockBoundingGeometry(block).translated(contentOffset());
                if (bounds.top() > viewport().height()) {
                    break;
                }
                String dottedNumber = tlvNumbersByLine.get(block.blockNumber());
                if (block.isVisible() && dottedNumber != null) {
                    painter.drawText(8, (int) bounds.top() + fontMetrics().ascent(), dottedNumber);
                }
                block = block.next();
            }
        } finally {
            painter.end();
            painter.dispose();
        }
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
