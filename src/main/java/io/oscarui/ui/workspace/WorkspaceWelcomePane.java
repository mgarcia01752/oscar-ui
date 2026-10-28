/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.workspace;

import io.oscarui.ui.MainWindow;

import io.qt.core.Qt;
import io.qt.gui.QAction;
import io.qt.gui.QPainter;
import io.qt.gui.QPaintEvent;
import io.qt.gui.QKeySequence;
import io.qt.widgets.*;

/** Empty-workspace actions; document operations remain owned by MainWindow. */
public final class WorkspaceWelcomePane extends QWidget {
    private static final int ICON_SIZE = 192;
    private static final double ICON_OPACITY = 0.35;
    private final QWidget icon = new QWidget() {
        /** Draws the current application icon using the active theme. */
        @Override
        protected void paintEvent(QPaintEvent event) {
            var painter = new QPainter(this);
            try {
                painter.setOpacity(ICON_OPACITY);
                QApplication.windowIcon().paint(painter,
                        (width() - ICON_SIZE) / 2, 0, ICON_SIZE, ICON_SIZE);
            } finally {
                painter.end();
            }
        }
    };

    /**
     * Creates the empty-workspace illustration and command shortcuts on the Qt UI thread.
     * @param newFile shared New action; must be non-null
     * @param openFile shared Open action; must be non-null
     */
    public WorkspaceWelcomePane(QAction newFile, QAction openFile) {
        setObjectName("workspaceWelcome");
        var layout = new QVBoxLayout(this);
        layout.addStretch();
        icon.setFixedHeight(ICON_SIZE);
        layout.addWidget(icon);
        addAction(layout, newFile, "New configuration", "welcomeNew");
        addAction(layout, openFile, "Open file", "welcomeOpen");
        layout.addStretch();
    }

    private void addAction(QVBoxLayout layout, QAction action, String label, String objectName) {
        var button = new QPushButton();
        button.setObjectName(objectName);
        button.setFlat(true);
        Runnable refresh = () -> {
            button.setText(label + "    " + action.shortcut().toString(QKeySequence.SequenceFormat.NativeText));
        };
        refresh.run();
        button.setEnabled(action.isEnabled());
        action.changed.connect(refresh::run);
        action.enabledChanged.connect(button::setEnabled);
        button.clicked.connect(action::trigger);
        layout.addWidget(button, 0, Qt.AlignmentFlag.AlignHCenter);
    }
}
