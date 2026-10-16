package io.github.mgarcia01752.oscarui;

import io.github.mgarcia01752.oscarui.ui.MainWindow;
import io.qt.core.QTimer;
import io.qt.core.QLibraryInfo;
import io.qt.widgets.QApplication;
import io.qt.widgets.QPlainTextEdit;
import io.qt.widgets.QTreeWidget;
import io.qt.widgets.QSplitter;
import io.qt.gui.QAction;
import java.nio.file.Path;

public final class DesktopApplication {
    public static void main(String[] args) {
        boolean smoke = args.length == 2 && args[0].equals("--smoke-test");
        if (args.length != 0 && !smoke) {
            System.err.println("Usage: Oscar Desktop [--smoke-test configuration.txt]");
            System.exit(2);
        }
        try {
            QApplication.initialize(new String[0]);
            QApplication.setApplicationName("Oscar Desktop");
            var window = new MainWindow(!smoke);
            window.show();
            QTimer smokeTimer = new QTimer(window);
            if (smoke) {
                window.openFile(Path.of(args[1]));
                smokeTimer.timeout.connect(() -> {
                    if (window.completed()) {
                        smokeTimer.stop();
                        boolean success = window.succeeded() && verifyStarterMenus(window);
                        System.out.println(success ? "UI_SMOKE_OK Qt " + QLibraryInfo.version().toString() : "UI_SMOKE_FAILED");
                        QApplication.exit(success ? 0 : 1);
                    }
                });
                smokeTimer.start(100);
                QTimer.singleShot(15000, () -> QApplication.exit(2));
            }
            int result = QApplication.exec();
            window.close();
            QApplication.shutdown();
            if (result != 0) System.exit(result);
        } catch (LinkageError | RuntimeException e) {
            System.err.println("Oscar Desktop could not start. Check Qt 6.8.3 libraries, platform plugins, and display access. " + e.getClass().getSimpleName());
            System.exit(1);
        }
    }

    private static boolean verifyStarterMenus(MainWindow window) {
        var menus = window.menuBar().actions();
        if (menus.size() != 4
            || !menus.get(0).text().equals("&File")
            || !menus.get(1).text().equals("&Edit")
            || !menus.get(2).text().equals("&Settings")
            || !menus.get(3).text().equals("&About")) return false;
        for (String name : new String[]{"fileNew", "fileOpen", "fileSaveAs", "addTlv", "aboutOscar", "aboutQt"}) {
            var action = window.findChild(QAction.class, name);
            if (action == null || !action.isEnabled()) return false;
        }
        if (window.findChild(QAction.class, "fileSave").isEnabled()) return false;
        var view = window.findChild(QPlainTextEdit.class, "configurationText");
        var tree = window.findChild(QTreeWidget.class, "configurationTree");
        var splitter = window.findChild(QSplitter.class);
        if (view == null || !view.isReadOnly() || tree == null || splitter == null
                || splitter.count() != 2 || tree.topLevelItemCount() != 1
                || tree.topLevelItem(0).childCount() != window.currentDocument().root().children().size()) return false;
        if (tree.topLevelItem(0).childCount() > 0) {
            tree.setCurrentItem(tree.topLevelItem(0).child(0));
            var node = window.currentDocument().root().children().get(0);
            if (!view.textCursor().selectedText().equals(window.currentDocument().text().substring(node.start(), node.end()))) return false;
        }
        window.findChild(QAction.class, "editSelectAll").trigger();
        var copy = window.findChild(QAction.class, "editCopy");
        if (!copy.isEnabled()) return false;
        copy.trigger();
        boolean success = QApplication.clipboard().text().equals(view.toPlainText());
        if (success) window.grab().save("target/ui-smoke.png");
        return success;
    }
}
