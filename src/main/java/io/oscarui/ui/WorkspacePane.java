/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui;

import io.qt.core.Qt;
import io.qt.gui.QAction;
import io.qt.widgets.*;
import java.util.List;

/** Owns the central workspace composition; document operations stay with MainWindow. */
final class WorkspacePane extends QWidget {
    private final QLabel maskingLabel = new QLabel("Masked");
    private final QFrame maskingDivider = new QFrame();
    private final QLabel typeLabel = new QLabel();
    private final ConfigurationTextPane configurationTextPane = new ConfigurationTextPane();
    private final QStackedWidget textPanel = new QStackedWidget();
    private final ConfigurationTreePane configurationTreePane = new ConfigurationTreePane();
    private final QLabel status = new QLabel();
    private final QSplitter splitter;

    WorkspacePane(QMainWindow window, DocumentTabs documentTabs, QAction newFile, QAction open) {
        var layout = new QVBoxLayout(this);
        typeLabel.setObjectName("documentType");
        maskingLabel.setObjectName("maskingStatus");
        maskingLabel.setToolTip("SNMP secrets are masked in text and hex displays. Saved values are unchanged.");
        maskingDivider.setObjectName("maskingDivider");
        maskingDivider.setFrameShape(QFrame.Shape.VLine);
        maskingDivider.setFrameShadow(QFrame.Shadow.Sunken);
        // Share the pane margins instead of the platform status bar's resize-grip inset.
        var footer = new QHBoxLayout();
        footer.setContentsMargins(0, 0, 0, 0);
        footer.addStretch();
        footer.addWidget(maskingLabel);
        footer.addWidget(maskingDivider);
        footer.addWidget(typeLabel);
        status.setWordWrap(true);
        status.setObjectName("operationStatus");
        status.setVisible(false);
        layout.addWidget(documentTabs);
        splitter = new QSplitter(Qt.Orientation.Horizontal);
        splitter.addWidget(configurationTreePane);
        textPanel.setObjectName("workspaceTextPanel");
        var welcome = new WorkspaceWelcomePane(newFile, open);
        textPanel.addWidget(welcome);
        textPanel.addWidget(configurationTextPane);
        window.windowIconChanged.connect(ignored -> welcome.update());
        splitter.addWidget(textPanel);
        splitter.setStretchFactor(0, 1);
        splitter.setStretchFactor(1, 2);
        splitter.setSizes(List.of(380, 780));
        layout.addWidget(splitter, 1);
        layout.addWidget(status);
        layout.addLayout(footer);

    }

    void showMasking(boolean masked) {
        maskingLabel.setVisible(masked);
        maskingDivider.setVisible(masked);
    }

    void showDocumentType(String format) {
        typeLabel.setText(format);
    }

    void showStatus(String message) {
        status.setText(message);
        status.setVisible(!message.isEmpty());
    }

    ConfigurationTextPane configurationTextPane() {
        return configurationTextPane;
    }

    QStackedWidget textPanel() {
        return textPanel;
    }

    ConfigurationTreePane configurationTreePane() {
        return configurationTreePane;
    }

    QSplitter splitter() {
        return splitter;
    }

    int contentInset() {
        return layout().contentsMargins().left();
    }
}
