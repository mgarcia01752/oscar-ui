package io.oscarui;

import io.oscarui.ui.MainWindow;
import io.qt.core.QTimer;
import io.qt.core.QLibraryInfo;
import io.qt.widgets.QApplication;
import io.qt.widgets.QPlainTextEdit;
import io.qt.widgets.QTreeWidget;
import io.qt.widgets.QSplitter;
import io.qt.gui.QAction;
import java.nio.file.Path;

public final class DesktopApplication {
    private static final String APPLICATION_NAME = "OSCAR-UI";
    private static final String LINUX_DESKTOP_FILE_NAME = "oscar-ui-OSCAR-UI";

    /**
     * Starts the Qt application, or runs the requested native smoke check.
     *
     * <p>Initializes Qt on the calling thread and exits with a nonzero status on failure.
     *
     * @param args command-line arguments; empty for the UI or --smoke-test followed by a path
     */
    public static void main(String[] args) {
        boolean smoke = args.length == 2 && args[0].equals("--smoke-test");
        if (args.length != 0 && !smoke) {
            System.err.println("Usage: OSCAR-UI [--smoke-test configuration.txt]");
            System.exit(2);
        }
        try {
            QApplication.initialize(APPLICATION_NAME, new String[0]);
            // An explicit display name makes Qt append it to every native window title.
            // Keep the default name and let each window own its caption.
            QApplication.setDesktopFileName(LINUX_DESKTOP_FILE_NAME);
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
            if (result != 0) {
                System.exit(result);
            }
        } catch (LinkageError | RuntimeException e) {
            System.err.println("OSCAR-UI could not start. Check Qt 6.8.3 libraries, platform plugins, and display access. " + e.getClass().getSimpleName());
            System.exit(1);
        }
    }

    private static boolean verifyStarterMenus(MainWindow window) {
        if (!QApplication.arguments().getFirst().equals(APPLICATION_NAME)
                || !QApplication.applicationName().equals(APPLICATION_NAME)
                || !QApplication.applicationDisplayName().equals(APPLICATION_NAME)
                || !QApplication.desktopFileName().equals(LINUX_DESKTOP_FILE_NAME)) {
            return false;
        }
        var menus = window.menuBar().actions();
        if (menus.size() != 6
            || !menus.get(0).text().equals("&File")
            || !menus.get(1).text().equals("&Edit")
            || !menus.get(2).text().equals("&Settings")
            || !menus.get(3).text().equals("&Inspect")
            || !menus.get(4).text().equals("&Tools")
            || !menus.get(5).text().equals("&About")) {
            return false;
        }
        for (String name : new String[]{"fileNew", "fileOpen", "fileSaveAs", "addTlv", "aboutOscar", "aboutQt", "checkSharedSecret"}) {
            var action = window.findChild(QAction.class, name);
            if (action == null || !action.isEnabled()) {
                return false;
            }
        }
        if (window.findChild(QAction.class, "fileSave").isEnabled()) {
            return false;
        }
        var view = window.findChild(QPlainTextEdit.class, "configurationText");
        var tree = window.findChild(QTreeWidget.class, "configurationTree");
        var splitter = window.findChild(QSplitter.class);
        if (view == null || !view.isReadOnly() || tree == null || splitter == null
                || splitter.count() != 2 || tree.topLevelItemCount() != 1
                || tree.topLevelItem(0).childCount() != window.currentDocument().root().childTlvs().size()) {
            return false;
        }
        if (tree.topLevelItem(0).childCount() > 0) {
            tree.setCurrentItem(tree.topLevelItem(0).child(0));
            var node = window.currentDocument().root().childTlvs().get(0);
            if (!view.textCursor().selectedText().equals(window.currentDocument().text().substring(node.sourceStartOffset(), node.sourceEndExclusive()))) {
                return false;
            }
        }
        window.findChild(QAction.class, "editSelectAll").trigger();
        var copy = window.findChild(QAction.class, "editCopy");
        if (!copy.isEnabled()) {
            return false;
        }
        copy.trigger();
        boolean success = QApplication.clipboard().text().equals(view.toPlainText());
        if (success) {
            window.grab().save("target/ui-smoke.png");
        }
        return success;
    }
}
