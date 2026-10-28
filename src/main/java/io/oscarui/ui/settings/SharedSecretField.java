/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.settings;

import io.qt.core.QEvent;
import io.qt.core.QObject;
import io.qt.core.QSize;
import io.qt.gui.QColor;
import io.qt.gui.QIcon;
import io.qt.gui.QPainter;
import io.qt.gui.QPen;
import io.qt.gui.QPixmap;
import io.qt.widgets.*;

/** Shared press-and-hold reveal control for secret entry dialogs. */
public final class SharedSecretField extends QWidget {
    private final QLineEdit secret;
    private final QToolButton reveal = new QToolButton();

    /**
     * Adds a hold-to-reveal control that remasks the supplied secret field on release.
     *
     * <p>Call on the Qt UI thread.
     * @param dialog owning dialog used to remask the secret on deactivation
     * @param secret password field to control; must be non-null
     */
    public SharedSecretField(QDialog dialog, QLineEdit secret) {
        super(dialog);
        this.secret = secret;
        secret.setEchoMode(QLineEdit.EchoMode.Password);
        var layout = new QHBoxLayout(this);
        layout.setContentsMargins(0, 0, 0, 0);
        layout.addWidget(secret, 1);
        reveal.setObjectName(secret.objectName() + "Reveal");
        reveal.setToolTip("Hold to show shared secret");
        reveal.setAccessibleName("Hold to show shared secret");
        reveal.setIconSize(new QSize(24, 24));
        updateIcon();
        reveal.pressed.connect(() -> {
            if (secret.isEnabled()) {
                secret.setEchoMode(QLineEdit.EchoMode.Normal);
            }
        });
        reveal.released.connect(this::maskSecret);
        layout.addWidget(reveal);
        reveal.installEventFilter(this);
        secret.installEventFilter(this);
        dialog.installEventFilter(this);
        dialog.finished.connect(code -> mask());
    }

    /**
     * Masks the field when a hold is interrupted or the dialog loses activation.
     *
     * @param watched control or dialog receiving the event
     * @param event incoming Qt event
     * @return Qt's default event-filter result; events remain available to controls
     */
    @Override
    public boolean eventFilter(QObject watched, QEvent event) {
        if (event.type() == QEvent.Type.WindowDeactivate || event.type() == QEvent.Type.Hide
                || event.type() == QEvent.Type.FocusOut || event.type() == QEvent.Type.EnabledChange
                || (watched == reveal && event.type() == QEvent.Type.Leave)) {
            maskSecret();
        }
        if (watched == reveal && event.type() == QEvent.Type.PaletteChange) {
            updateIcon();
        }
        return super.eventFilter(watched, event);
    }

    private void maskSecret() {
        secret.setEchoMode(QLineEdit.EchoMode.Password);
    }

    private void updateIcon() {
        var pixmap = new QPixmap(24, 24);
        pixmap.fill(new QColor(0, 0, 0, 0));
        var painter = new QPainter(pixmap);
        painter.setRenderHint(QPainter.RenderHint.Antialiasing);
        painter.setPen(new QPen(reveal.palette().buttonText().color(), 2));
        painter.drawEllipse(2, 6, 20, 12);
        painter.setBrush(reveal.palette().buttonText());
        painter.drawEllipse(9, 9, 6, 6);
        painter.end();
        reveal.setIcon(new QIcon(pixmap));
    }
}
