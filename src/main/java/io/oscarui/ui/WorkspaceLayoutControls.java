/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui;

import io.qt.gui.QPaintEvent;
import io.qt.gui.QPainter;
import io.qt.gui.QPalette;
import io.qt.widgets.*;
import java.util.List;

/** Update action followed by mutually exclusive workspace layout controls. */
final class WorkspaceLayoutControls extends QWidget {
    private enum Mode { TREE, TEXT, BOTH }

    private final QSplitter splitter;
    private final QWidget tree;
    private final QWidget text;
    private List<Integer> splitSizes;
    private Mode current = Mode.BOTH;

    WorkspaceLayoutControls(UpgradeChip update, QSplitter splitter, QWidget tree, QWidget text) {
        this.splitter = splitter;
        this.tree = tree;
        this.text = text;
        splitSizes = splitter.sizes();
        setObjectName("workspaceLayoutControls");
        var row = new QHBoxLayout(this);
        row.setSizeConstraint(QLayout.SizeConstraint.SetFixedSize);
        row.setContentsMargins(0, 0, 0, 0);
        row.setSpacing(4);
        // Reserve chip space so showing an update cannot overflow the menu corner.
        var updatePolicy = update.sizePolicy();
        updatePolicy.setRetainSizeWhenHidden(true);
        update.setSizePolicy(updatePolicy);
        row.addWidget(update);
        var group = new QButtonGroup(this);
        for (var mode : Mode.values()) {
            var button = new LayoutButton(mode);
            group.addButton(button);
            row.addWidget(button);
            button.clicked.connect(() -> select(mode));
        }
    }

    private void select(Mode mode) {
        if (current == Mode.BOTH && mode != Mode.BOTH) {
            splitSizes = splitter.sizes();
        }
        tree.setVisible(mode != Mode.TEXT);
        text.setVisible(mode != Mode.TREE);
        if (mode == Mode.BOTH) {
            splitter.setSizes(splitSizes);
        }
        current = mode;
    }

    /** Paint palette-aware panel icons so light and dark themes stay readable. */
    private static final class LayoutButton extends QToolButton {
        private final Mode mode;

        LayoutButton(Mode mode) {
            this.mode = mode;
            String label = switch (mode) {
                case TREE -> "Tree only";
                case TEXT -> "Text only";
                case BOTH -> "Both panels";
            };
            setObjectName("layout" + mode.name());
            setToolTip(label);
            setAccessibleName(label);
            setCheckable(true);
            setChecked(mode == Mode.BOTH);
            setFixedSize(28, 26);
        }

        @Override protected void paintEvent(QPaintEvent event) {
            super.paintEvent(event);
            var painter = new QPainter(this);
            try {
                var color = palette().color(QPalette.ColorRole.ButtonText);
                painter.setPen(color);
                painter.drawRoundedRect(6, 6, 16, 13, 2, 2);
                if (mode != Mode.TEXT) {
                    painter.fillRect(8, 8, 5, 9, color);
                }
                if (mode != Mode.TREE) {
                    painter.fillRect(15, 8, 5, 9, color);
                }
            } finally {
                painter.end();
                painter.dispose();
            }
        }
    }
}
