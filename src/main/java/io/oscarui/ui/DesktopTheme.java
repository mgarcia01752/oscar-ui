package io.oscarui.ui;

import io.qt.gui.QPalette;
import io.qt.widgets.QApplication;
import static io.qt.gui.QPalette.ColorRole.*;

/** Application-wide, session-only palette shared by existing and new dialogs. */
final class DesktopTheme {
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
    }

    private DesktopTheme() {}
}
