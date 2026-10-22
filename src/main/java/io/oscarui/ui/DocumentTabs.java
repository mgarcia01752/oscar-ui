/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui;

import io.qt.core.Qt;
import io.qt.gui.QAction;
import io.qt.gui.QFont;
import io.qt.gui.QFontMetrics;
import io.qt.gui.QPaintEvent;
import io.qt.gui.QResizeEvent;
import io.qt.widgets.*;
import java.util.List;
import java.util.function.Consumer;

/** Filename tabs and an informational path breadcrumb; no document IO or parsing. */
final class DocumentTabs extends QWidget {
    private final QTabBar tabs;
    private final QLabel breadcrumb = new QLabel();
    private List<WorkspaceSessions.Session> sessions = List.of();
    private String fullPath = "";
    private final Consumer<WorkspaceSessions.Session> activate;
    private final Consumer<WorkspaceSessions.Session> close;

    DocumentTabs(Consumer<WorkspaceSessions.Session> activate, Consumer<WorkspaceSessions.Session> close) {
        this.activate = activate;
        this.close = close;
        setObjectName("documentTabs");
        var layout = new QVBoxLayout(this);
        layout.setContentsMargins(0, 0, 0, 0);
        layout.setSpacing(0);
        tabs = new QTabBar() {
            @Override protected void paintEvent(QPaintEvent event) {
                var painter = new QStylePainter(this);
                try {
                    for (int index = 0; index < count(); index++) {
                        var option = new QStyleOptionTab();
                        initStyleOption(option, index);
                        var tabFont = new QFont(font());
                        tabFont.setItalic(index < sessions.size() && sessions.get(index).modified());
                        painter.setFont(tabFont);
                        option.setFontMetrics(new QFontMetrics(tabFont));
                        painter.drawControl(QStyle.ControlElement.CE_TabBarTab, option);
                    }
                } finally {
                    painter.end();
                    painter.dispose();
                }
            }
        };
        tabs.setObjectName("documentTabBar");
        tabs.setExpanding(false);
        tabs.setUsesScrollButtons(true);
        tabs.setTabsClosable(true);
        tabs.setDrawBase(false);
        tabs.setMinimumHeight(28);
        tabs.setAccessibleName("Open configuration files");
        tabs.currentChanged.connect(index -> {
            if (index >= 0 && index < sessions.size()) {
                activate.accept(sessions.get(index));
            }
        });
        tabs.tabCloseRequested.connect(index -> {
            if (index >= 0 && index < sessions.size()) {
                close.accept(sessions.get(index));
            }
        });
        breadcrumb.setObjectName("documentBreadcrumb");
        breadcrumb.setTextFormat(Qt.TextFormat.PlainText);
        breadcrumb.setMinimumWidth(0);
        breadcrumb.setSizePolicy(QSizePolicy.Policy.Ignored, QSizePolicy.Policy.Preferred);
        layout.addWidget(tabs);
        layout.addWidget(breadcrumb);
    }

    void configureMenu(QAction open, QAction create) {
        tabs.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu);
        tabs.customContextMenuRequested.connect(position -> {
            var menu = new QMenu(tabs);
            menu.setObjectName("documentTabsMenu");
            try {
                menu.addAction(open);
                menu.addAction(create);
                menu.exec(tabs.mapToGlobal(position));
            } finally {
                menu.dispose();
            }
        });
    }

    void refresh(WorkspaceSessions workspace) {
        sessions = workspace.all();
        tabs.blockSignals(true);
        try {
            while (tabs.count() > sessions.size()) {
                tabs.removeTab(tabs.count() - 1);
            }
            for (int index = 0; index < sessions.size(); index++) {
                var session = sessions.get(index);
                if (index == tabs.count()) {
                    tabs.addTab(workspace.title(session));
                }
                tabs.setTabText(index, workspace.title(session));
                tabs.setTabToolTip(index, (session.path() == null ? session.name() : session.path().toAbsolutePath().toString())
                        + (session.modified() ? " — Unsaved changes" : ""));
            }
            tabs.setCurrentIndex(workspace.active() == null ? -1 : sessions.indexOf(workspace.active()));
        } finally {
            tabs.blockSignals(false);
        }
        var selected = workspace.active();
        fullPath = selected == null ? "" : selected.path() == null ? "Unsaved › " + selected.name()
                : selected.path().toAbsolutePath().normalize().toString().replace("\\", " › ").replace("/", " › ");
        breadcrumb.setToolTip(selected == null || selected.path() == null ? fullPath : selected.path().toAbsolutePath().toString());
        elidePath();
        tabs.update();
    }

    void cycle(WorkspaceSessions workspace, int direction) {
        if (!sessions.isEmpty()) {
            activate.accept(sessions.get(Math.floorMod(sessions.indexOf(workspace.active()) + direction, sessions.size())));
        }
    }

    @Override protected void resizeEvent(QResizeEvent event) {
        super.resizeEvent(event);
        elidePath();
    }

    private void elidePath() {
        breadcrumb.setText(breadcrumb.fontMetrics().elidedText(fullPath, Qt.TextElideMode.ElideLeft, Math.max(0, width())));
    }
}
