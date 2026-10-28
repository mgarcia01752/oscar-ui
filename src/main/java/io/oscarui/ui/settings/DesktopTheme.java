/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.settings;

import io.qt.gui.QPalette;
import io.qt.core.Qt;
import io.oscarui.service.Appearance;
import io.qt.gui.QIcon;
import io.qt.gui.QPixmap;
import java.io.IOException;
import io.qt.widgets.QApplication;
import static io.qt.gui.QPalette.ColorRole.*;

/** Application-wide palette resolved from a preference and the desktop color scheme. */
public final class DesktopTheme {
    private DesktopTheme() {}

    /**
     * Applies the application palette and icon using the requested appearance and system scheme.
     *
     * <p>Call on the Qt UI thread.
     * @throws IllegalStateException if the bundled application icon cannot be loaded
     * @param appearance non-null appearance preference
     */
    public static void apply(Appearance appearance) {
        apply(appearance, QApplication.styleHints().colorScheme());
    }

    /**
     * Applies the application palette and icon using the requested appearance and system scheme.
     *
     * <p>Call on the Qt UI thread.
     * @throws IllegalStateException if the bundled application icon cannot be loaded
     * @param appearance non-null appearance preference
     * @param systemScheme desktop color scheme used when the preference is System
     */
    public static void apply(Appearance appearance, Qt.ColorScheme systemScheme) {
        boolean dark = appearance == Appearance.DARK
                || (appearance == Appearance.SYSTEM && systemScheme == Qt.ColorScheme.Dark);
        // Fusion honors explicit palettes consistently on Linux and Windows.
        QApplication.setStyle("Fusion");
        var palette = new QPalette(QApplication.style().standardPalette());
        palette.setColor(Window, dark ? "#252526" : "#f4f4f4");
        palette.setColor(WindowText, dark ? "#ededed" : "#202020");
        palette.setColor(Base, dark ? "#1e1e1e" : "#ffffff");
        palette.setColor(AlternateBase, dark ? "#303033" : "#eeeeee");
        palette.setColor(Text, dark ? "#ededed" : "#202020");
        palette.setColor(Button, dark ? "#363638" : "#e8e8e8");
        palette.setColor(ButtonText, dark ? "#ededed" : "#202020");
        palette.setColor(ToolTipBase, dark ? "#363638" : "#ffffdc");
        palette.setColor(ToolTipText, dark ? "#ffffff" : "#202020");
        palette.setColor(Highlight, "#286fc0");
        palette.setColor(HighlightedText, "#ffffff");
        palette.setColor(Link, dark ? "#80bfff" : "#1459a1");
        palette.setColor(PlaceholderText, dark ? "#b0b0b0" : "#666666");
        for (var role : new QPalette.ColorRole[]{WindowText, Text, ButtonText}) {
            palette.setColor(QPalette.ColorGroup.Disabled, role, dark ? "#909090" : "#777777");
        }
        QApplication.setPalette(palette);
        palette.dispose();
        updateIcon();
    }
    /**
     * Refreshes the application icon from the current palette on the Qt UI thread.
     * Existing top-level windows receive the updated icon through Qt.
     * @throws IllegalStateException if the icon resource is missing, invalid, or unreadable
     */
    public static void updateIcon() {
        boolean darkMode = QApplication.palette().color(Window).lightness() < 128;
        String resourcePath = darkMode ? "/icons/oscar-ui-dark-icon.png" : "/icons/oscar-ui-light-icon.png";
        try (var stream = DesktopTheme.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Missing application icon: " + resourcePath);
            }
            var pixmap = new QPixmap();
            try {
                if (!pixmap.loadFromData(stream.readAllBytes())) {
                    throw new IllegalStateException("Invalid application icon: " + resourcePath);
                }
                var icon = new QIcon(pixmap);
                try {
                    QApplication.setWindowIcon(icon);
                } finally {
                    icon.dispose();
                }
            } finally {
                pixmap.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read application icon: " + resourcePath, exception);
        }
    }
}
