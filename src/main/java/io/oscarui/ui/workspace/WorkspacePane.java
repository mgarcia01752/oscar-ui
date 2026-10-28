/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.workspace;

import io.oscarui.ui.MainWindow;

import io.qt.core.Qt;
import io.qt.gui.QAction;
import io.qt.widgets.*;
import java.util.List;

/** Owns the central workspace composition; document operations stay with MainWindow. */
public final class WorkspacePane extends QWidget {
    private final QLabel maskingLabel = new QLabel("Masked");
    private final QFrame maskingDivider = new QFrame();
    private final QLabel typeLabel = new QLabel();
    private final ConfigurationTextPane configurationTextPane = new ConfigurationTextPane();
    private final QStackedWidget textPanel = new QStackedWidget();
    private final ConfigurationTreePane configurationTreePane = new ConfigurationTreePane();
    private final QLabel status = new QLabel();
    private final QSplitter splitter;

    /**
     * Builds the central panes and footer, retaining Qt ownership of child widgets.
     *
     * <p>Call on the Qt UI thread.
     * @param window owning main window; must be non-null
     * @param documentTabs existing tab bar to parent into this pane
     * @param newFile shared New action owned by the main window
     * @param open shared Open action owned by the main window
     */
    public WorkspacePane(QMainWindow window, DocumentTabs documentTabs, QAction newFile, QAction open) {
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

    /**
     * Shows or hides the masking indicator and its divider together.
     *
     * <p>Call on the Qt UI thread.
     * @param masked true when values are hidden by display masking
     */
    public void showMasking(boolean masked) {
        maskingLabel.setVisible(masked);
        maskingDivider.setVisible(masked);
    }

    /**
     * Updates the footer format label without changing the active document.
     *
     * <p>Call on the Qt UI thread.
     * @param format display text for the format label, or an empty string for no document
     */
    public void showDocumentType(String format) {
        typeLabel.setText(format);
    }

    /**
     * Updates the footer message and hides it when the message is empty.
     *
     * <p>Call on the Qt UI thread.
     * @param message non-null display message; empty hides the footer
     */
    public void showStatus(String message) {
        status.setText(message);
        status.setVisible(!message.isEmpty());
    }

    /**
     * Returns the owned text pane for workspace coordination.
     *
     * <p>Call on the Qt UI thread.
     * @return owned text pane; do not dispose it independently
     */
    public ConfigurationTextPane configurationTextPane() {
        return configurationTextPane;
    }

    /**
     * Returns the owned stack used to switch between welcome and document views.
     *
     * <p>Call on the Qt UI thread.
     * @return owned welcome/text stack; do not dispose it independently
     */
    public QStackedWidget textPanel() {
        return textPanel;
    }

    /**
     * Returns the owned tree pane for selection and document coordination.
     *
     * <p>Call on the Qt UI thread.
     * @return owned tree pane; do not dispose it independently
     */
    public ConfigurationTreePane configurationTreePane() {
        return configurationTreePane;
    }

    /**
     * Returns the owned splitter for pane-visibility controls.
     *
     * <p>Call on the Qt UI thread.
     * @return owned workspace splitter; do not dispose it independently
     */
    public QSplitter splitter() {
        return splitter;
    }

    /**
     * Returns the central layout left margin used to align menu controls.
     *
     * <p>Call on the Qt UI thread.
     * @return left inset in Qt logical pixels
     */
    public int contentInset() {
        return layout().contentsMargins().left();
    }
}
