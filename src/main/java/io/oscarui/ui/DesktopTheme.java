package io.oscarui.ui;

import io.qt.gui.QPalette;
import io.qt.gui.QIcon;
import io.qt.gui.QPixmap;
import java.io.IOException;
import io.qt.widgets.QApplication;
import static io.qt.gui.QPalette.ColorRole.*;

/** Application-wide, session-only palette shared by existing and new dialogs. */
final class DesktopTheme {
    private DesktopTheme() {}

    static void apply(boolean dark) {
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
    static void updateIcon() {
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
