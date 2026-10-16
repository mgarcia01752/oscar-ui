package io.github.mgarcia01752.oscarui;

import io.github.mgarcia01752.oscarui.ui.MainWindow;
import io.qt.core.QTimer;
import io.qt.core.QLibraryInfo;
import io.qt.widgets.QApplication;
import io.qt.widgets.QPlainTextEdit;
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
            var window = new MainWindow();
            window.show();
            QTimer smokeTimer = new QTimer(window);
            if (smoke) {
                window.openFile(Path.of(args[1]));
                smokeTimer.timeout.connect(() -> {
                    if (window.completed()) {
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
        if (menus.size() != 3
            || !menus.get(0).text().equals("&File")
            || !menus.get(1).text().equals("&Edit")
            || !menus.get(2).text().equals("&About")) return false;
        var fileActions = menus.get(0).menu().actions();
        var editActions = menus.get(1).menu().actions();
        var aboutActions = menus.get(2).menu().actions();
        if (fileActions.size() != 3 || !fileActions.get(0).isEnabled()
            || !fileActions.get(2).isEnabled() || editActions.size() != 2
            || aboutActions.size() != 2 || !aboutActions.get(0).isEnabled()
            || !aboutActions.get(1).isEnabled()) return false;
        var view = window.findChild(QPlainTextEdit.class);
        if (view == null || !view.isReadOnly() || editActions.get(0).isEnabled()
            || !editActions.get(1).isEnabled()) return false;
        editActions.get(1).trigger();
        if (!editActions.get(0).isEnabled()) return false;
        editActions.get(0).trigger();
        return QApplication.clipboard().text().equals(view.toPlainText());
        }
}
