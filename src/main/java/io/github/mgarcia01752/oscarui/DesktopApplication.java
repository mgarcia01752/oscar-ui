package io.github.mgarcia01752.oscarui;

import io.github.mgarcia01752.oscarui.ui.MainWindow;
import io.qt.core.QTimer;
import io.qt.core.QLibraryInfo;
import io.qt.widgets.QApplication;
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
                        boolean success = window.succeeded();
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
}
