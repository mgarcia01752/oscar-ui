package io.github.mgarcia01752.oscarui.ui;

import io.github.mgarcia01752.oscarui.service.*;
import io.qt.core.QTimer;
import io.qt.gui.QAction;
import io.qt.gui.QCloseEvent;
import io.qt.gui.QKeySequence;
import io.qt.widgets.*;
import java.nio.file.Path;
import java.util.concurrent.*;

/** All widget access stays on the Qt event thread; the timer receives worker results. */
public final class MainWindow extends QMainWindow {
    private final OscarInspectionService service = new OscarInspectionService();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "oscar-inspection");
        thread.setDaemon(true);
        return thread;
    });
    private final QLabel pathLabel = new QLabel("No file selected");
    private final QPlainTextEdit content = new QPlainTextEdit();
    private final QLabel status = new QLabel("Ready");
    private final QAction open = new QAction("&Open…", this);
    private final QTimer poller = new QTimer(this);
    private Future<Inspection> pending;
    private boolean completed;
    private boolean succeeded;

    public MainWindow() {
        setWindowTitle("Oscar Desktop");
        resize(1000, 700);
        var central = new QWidget();
        var layout = new QVBoxLayout(central);
        pathLabel.setWordWrap(true);
        status.setWordWrap(true);
        content.setReadOnly(true);
        layout.addWidget(pathLabel);
        layout.addWidget(content, 1);
        layout.addWidget(status);
        setCentralWidget(central);
        var fileMenu = menuBar().addMenu("&File");
        fileMenu.addAction(open);
        open.setShortcut(new QKeySequence(QKeySequence.StandardKey.Open));
        open.triggered.connect(this::chooseFile);
        fileMenu.addSeparator();
        var exit = new QAction("E&xit", this);
        exit.setShortcut(new QKeySequence(QKeySequence.StandardKey.Quit));
        exit.triggered.connect(() -> close());
        fileMenu.addAction(exit);

        var editMenu = menuBar().addMenu("&Edit");
        var copy = new QAction("&Copy", this);
        copy.setShortcut(new QKeySequence(QKeySequence.StandardKey.Copy));
        copy.setEnabled(false);
        copy.triggered.connect(content::copy);
        content.copyAvailable.connect(copy::setEnabled);
        editMenu.addAction(copy);
        var selectAll = new QAction("Select &All", this);
        selectAll.setShortcut(new QKeySequence(QKeySequence.StandardKey.SelectAll));
        selectAll.setEnabled(false);
        selectAll.triggered.connect(content::selectAll);
        content.textChanged.connect(() -> selectAll.setEnabled(!content.toPlainText().isEmpty()));
        editMenu.addAction(selectAll);

        var aboutMenu = menuBar().addMenu("&About");
        var about = new QAction("About &Oscar Desktop", this);
        about.triggered.connect(() -> QMessageBox.about(this, "About Oscar Desktop",
            "Oscar Desktop 0.1.0\nOscar 3.1.0\nQt Jambi 6.8.3\n\nApache License 2.0"));
        aboutMenu.addAction(about);
        var aboutQt = new QAction("About &Qt", this);
        aboutQt.triggered.connect(() -> QMessageBox.aboutQt(this));
        aboutMenu.addAction(aboutQt);
        poller.timeout.connect(this::receiveResult);
        poller.start(50);
    }

    private void chooseFile() {
        String filename = QFileDialog.getOpenFileName(this, "Open configuration", "", "Configuration text (*.txt *.conf *.cfg);;All files (*)").result;
        if (filename != null && !filename.isEmpty()) openFile(Path.of(filename));
    }

    public void openFile(Path path) {
        if (pending != null) return;
        completed = false;
        succeeded = false;
        pathLabel.setText(path.toAbsolutePath().toString());
        content.clear();
        status.setText("Reading and inspecting…");
        open.setEnabled(false);
        pending = worker.submit(() -> service.inspect(path));
    }

    private void receiveResult() {
        if (pending == null || !pending.isDone()) return;
        try {
            Inspection result = pending.get();
            content.setPlainText(result.content());
            status.setText(result.summary());
            succeeded = true;
        } catch (ExecutionException e) {
            status.setText(e.getCause() instanceof InspectionException
                    ? e.getCause().getMessage() : "Inspection failed unexpectedly. Check the application dependencies.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            status.setText("Inspection was interrupted.");
        } finally {
            pending = null;
            completed = true;
            open.setEnabled(true);
        }
    }

    public boolean completed() { return completed; }
    public boolean succeeded() { return succeeded && !content.toPlainText().isBlank(); }

    @Override protected void closeEvent(QCloseEvent event) {
        poller.stop();
        if (pending != null) pending.cancel(true);
        worker.shutdownNow();
        super.closeEvent(event);
    }
}
