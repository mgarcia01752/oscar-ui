package io.github.mgarcia01752.oscarui.ui;

import io.github.mgarcia01752.oscarui.service.*;
import io.github.mgarcia01752.oscarui.service.OscarDocumentService.*;
import io.qt.core.QTimer;
import io.qt.core.Qt;
import io.qt.gui.QAction;
import io.qt.gui.QCloseEvent;
import io.qt.gui.QKeySequence;
import io.qt.gui.QTextCursor;
import io.qt.widgets.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class MainWindow extends QMainWindow {
    private final OscarDocumentService service = new OscarDocumentService();
    private final OscarBinaryService binaryService = new OscarBinaryService(service);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "oscar-document");
        thread.setDaemon(true);
        return thread;
    });
    private final QLabel pathLabel = new QLabel("No file selected");
    private final QLabel typeLabel = new QLabel();
    private final QPlainTextEdit content = new QPlainTextEdit();
    private final QTreeWidget tree = new QTreeWidget();
    private final QLabel status = new QLabel("Ready");
    private final Map<QTreeWidgetItem, Node> nodes = new HashMap<>();
    private final Map<Node, Node> parents = new IdentityHashMap<>();
    private final QAction newFile = action("&New...", "fileNew", QKeySequence.StandardKey.New);
    private final QAction open = action("&Open...", "fileOpen", QKeySequence.StandardKey.Open);
    private final QAction save = action("&Text...", "fileSave", null);
    private final QAction saveAs = action("&Text...", "fileSaveAs", null);
    private final QAction addTlv = action("Add &TLV...", "addTlv", null);
    private final QAction deleteTlv = action("&Del TLV...", "deleteTlv", null);
    private final QAction editTlv = action("&Edit TLV...", "editTlv", null);
    private final QAction binarySave = action("&Binary...", "fileSaveBinary", null);
    private final QAction binarySaveAs = action("&Binary...", "fileSaveAsBinary", null);
    private final QAction saveShortcut = action("Save", "saveShortcut", QKeySequence.StandardKey.Save);
    private final QAction saveAsShortcut = action("Save As", "saveAsShortcut", QKeySequence.StandardKey.SaveAs);
    private final QAction configChecks = action("Check Config...", "configChecks", null);
    private final QAction secretsAction = action("&Secrets...", "settingsSecrets", null);
    private final SessionSecrets secrets = new SessionSecrets();
    private QMenu saveMenu;
    private QMenu saveAsMenu;
    private final QTimer poller = new QTimer(this);
    private final boolean interactive;
    private Document document;
    private boolean modified;
    private Future<Runnable> pending;
    private boolean completed;
    private boolean succeeded;

    public MainWindow() { this(true); }

    public MainWindow(boolean interactive) {
        this.interactive = interactive;
        setWindowTitle("Oscar Desktop");
        resize(1200, 780);
        var central = new QWidget();
        var layout = new QVBoxLayout(central);
        var heading = new QHBoxLayout();
        pathLabel.setWordWrap(true);
        typeLabel.setObjectName("documentType");
        heading.addWidget(pathLabel, 1);
        heading.addWidget(typeLabel);
        layout.addLayout(heading);
        status.setWordWrap(true);
        content.setObjectName("configurationText");
        content.setReadOnly(true);
        content.setLineWrapMode(QPlainTextEdit.LineWrapMode.NoWrap);
        tree.setObjectName("configurationTree");
        tree.setHeaderLabels(List.of("Configuration", "Value"));
        tree.setMinimumWidth(240);
        tree.setColumnWidth(0, 240);
        tree.setAlternatingRowColors(true);
        var splitter = new QSplitter(Qt.Orientation.Horizontal);
        splitter.addWidget(tree);
        splitter.addWidget(content);
        splitter.setStretchFactor(0, 1);
        splitter.setStretchFactor(1, 2);
        splitter.setSizes(List.of(380, 780));
        layout.addWidget(splitter, 1);
        layout.addWidget(status);
        setCentralWidget(central);
        buildMenus();
        tree.currentItemChanged.connect((current, previous) -> {
            Node node = nodes.get(current);
            if (node != null && !node.path().isEmpty()) {
                var cursor = content.textCursor();
                cursor.setPosition(textPosition(node.start()));
                cursor.setPosition(textPosition(node.end()), QTextCursor.MoveMode.KeepAnchor);
                content.setTextCursor(cursor);
                content.ensureCursorVisible();
            }
            updateActions();
        });
        tree.itemDoubleClicked.connect((item, column) -> {
            tree.setCurrentItem(item);
            editSelectedTlv();
        });
        tree.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu);
        tree.customContextMenuRequested.connect(point -> {
            var item = tree.itemAt(point);
            if (item == null) return;
            tree.setCurrentItem(item);
            var menu = new QMenu(tree);
            menu.addAction(addTlv);
            menu.addAction(editTlv);
            menu.addAction(deleteTlv);
            menu.exec(tree.viewport().mapToGlobal(point));
            menu.dispose();
        });
        poller.timeout.connect(this::receiveResult);
        poller.start(40);
        updateActions();
    }

    private QAction action(String label, String name, QKeySequence.StandardKey shortcut) {
        var action = new QAction(label, this);
        action.setObjectName(name);
        if (shortcut != null) action.setShortcut(new QKeySequence(shortcut));
        return action;
    }

    private void buildMenus() {
        var file = menuBar().addMenu("&File");
        file.addAction(newFile);
        file.addAction(open);
        saveMenu = file.addMenu("&Save");
        saveMenu.addAction(binarySave);
        saveMenu.addAction(save);
        saveAsMenu = file.addMenu("Save &As");
        saveAsMenu.addAction(binarySaveAs);
        saveAsMenu.addAction(saveAs);
        addAction(saveShortcut);
        addAction(saveAsShortcut);
        saveShortcut.triggered.connect(() -> chooseSaveFormat(false, () -> {}));
        saveAsShortcut.triggered.connect(() -> chooseSaveFormat(true, () -> {}));
        binarySave.triggered.connect(this::saveBinary);
        binarySaveAs.triggered.connect(this::saveBinary);
        file.addAction(configChecks);
        configChecks.triggered.connect(this::checkBinary);
        file.addSeparator();
        var exit = action("E&xit", "fileExit", QKeySequence.StandardKey.Quit);
        file.addAction(exit);
        newFile.triggered.connect(() -> withDiscardPermission(this::chooseNew));
        open.triggered.connect(this::chooseFile);
        save.triggered.connect(() -> saveDocument(false, () -> {}));
        saveAs.triggered.connect(() -> saveDocument(true, () -> {}));
        exit.triggered.connect(() -> close());
        var edit = menuBar().addMenu("&Edit");
        edit.addAction(addTlv);
        addTlv.triggered.connect(this::chooseTlv);
        edit.addAction(editTlv);
        edit.addAction(deleteTlv);
        editTlv.triggered.connect(this::editSelectedTlv);
        deleteTlv.triggered.connect(this::deleteSelectedTlv);
        edit.addSeparator();
        var copy = action("&Copy", "editCopy", QKeySequence.StandardKey.Copy);
        copy.setEnabled(false);
        copy.triggered.connect(content::copy);
        content.copyAvailable.connect(copy::setEnabled);
        edit.addAction(copy);
        var selectAll = action("Select &All", "editSelectAll", QKeySequence.StandardKey.SelectAll);
        selectAll.setEnabled(false);
        selectAll.triggered.connect(content::selectAll);
        content.textChanged.connect(() -> selectAll.setEnabled(!content.toPlainText().isEmpty()));
        edit.addAction(selectAll);
        var settings = menuBar().addMenu("&Settings");
        settings.addAction(secretsAction);
        secretsAction.triggered.connect(this::showSecrets);
        var aboutMenu = menuBar().addMenu("&About");
        var about = action("About &Oscar Desktop", "aboutOscar", null);
        about.triggered.connect(() -> QMessageBox.about(this, "About Oscar Desktop",
                "Oscar Desktop 0.1.0\nOscar 3.1.0\nQt Jambi 6.8.3\n\nApache License 2.0"));
        aboutMenu.addAction(about);
        var aboutQt = action("About &Qt", "aboutQt", null);
        aboutQt.triggered.connect(() -> QMessageBox.aboutQt(this));
        aboutMenu.addAction(aboutQt);
    }

    private void chooseFile() {
        withDiscardPermission(() -> {
            var selection = QFileDialog.getOpenFileName(this, "Open configuration", "",
                    "Configuration text (*.txt *.conf *.cfg);;All files (*)");
            if (selection != null && selection.result != null && !selection.result.isEmpty()) {
                openFile(Path.of(selection.result));
            }
        });
    }

    private void chooseNew() {
        submit(service::types, types -> {
            var dialog = new NewDocumentDialog(this, types);
            if (dialog.exec() == QDialog.DialogCode.Accepted.value()) {
                var selection = dialog.selection();
                createDocument(selection.type(), selection.allTlvs());
            }
            dialog.dispose();
        });
    }

    public void openFile(Path path) {
        if (pending != null) return;
        submit(() -> service.open(path), loaded -> {
            display(loaded, false);
            if (interactive) chooseTlv();
        });
    }

    public void createDocument(FileType type, boolean allTlvs) {
        submit(() -> service.create(type, allTlvs), created -> {
            display(created, true);
            if (interactive) chooseTlv();
        });
    }

    private void display(Document current, boolean changed) {
        document = current;
        modified = changed;
        nodes.clear();
        parents.clear();
        tree.clear();
        content.setPlainText(current.text());
        var rootItem = new QTreeWidgetItem(List.of(current.root().name(), ""));
        tree.addTopLevelItem(rootItem);
        populate(rootItem, current.root());
        rootItem.setExpanded(true);
        tree.setCurrentItem(rootItem);
        typeLabel.setText(current.type().header());
        updateDocumentLabels();
        updateActions();
    }

    private void populate(QTreeWidgetItem item, Node node) {
        nodes.put(item, node);
        item.setToolTip(0, node.path().contains(-1) ? "Unmapped reference entry" : node.path().toString());
        for (Node child : node.children()) {
            var childItem = new QTreeWidgetItem(List.of(child.name(), child.value()));
            item.addChild(childItem);
            parents.put(child, node);
            populate(childItem, child);
        }
    }

    private void updateDocumentLabels() {
        String path = document.path() == null ? "Untitled" : document.path().toString();
        pathLabel.setText(path);
        setWindowTitle((document.path() == null ? "Untitled" : document.path().getFileName().toString())
                + (modified ? " *" : "") + " - Oscar Desktop");
        status.setText(OscarDocumentService.count(document.root()) + " entries | "
                + (document.reference() ? "Reference template - not validated" : "Draft - syntax/catalog inspection only")
                + (modified ? " | Modified" : ""));
    }

    private Node insertionParent() {
        if (document == null) return null;
        Node selected = nodes.get(tree.currentItem());
        if (selected == null) return document.root();
        return selected.container() ? selected : parents.getOrDefault(selected, document.root());
    }

    private void chooseTlv() {
        if (document == null || pending != null) return;
        Document original = document;
        Node parent = insertionParent();
        submit(() -> service.choices(original, parent), choices -> {
            var dialog = new AddTlvDialog(this, service, original, parent, choices);
            if (dialog.exec() == QDialog.DialogCode.Accepted.value()) {
                var selection = dialog.selection();
                submit(() -> service.insert(original, parent, selection.definition(), selection.value()),
                        changed -> {
                            display(changed, true);
                            Node updatedParent = parent.path().isEmpty() ? changed.root() : nodes.values().stream()
                                    .filter(node -> node.start() == parent.start() && node.path().equals(parent.path()))
                                    .findFirst().orElse(changed.root());
                            Node inserted = updatedParent.children().get(updatedParent.children().size() - 1);
                            nodes.forEach((item, node) -> {
                                if (node == inserted) {
                                    for (var ancestor = item.parent(); ancestor != null; ancestor = ancestor.parent()) ancestor.setExpanded(true);
                                    tree.setCurrentItem(item);
                                    tree.scrollToItem(item);
                                }
                            });
                        });
            }
            dialog.dispose();
        });
    }

    private int textPosition(int sourcePosition) {
        // QTextDocument collapses CRLF to one paragraph separator.
        int position = sourcePosition;
        for (int index = 0; index < sourcePosition - 1; index++) {
            if (document.text().charAt(index) == '\r' && document.text().charAt(index + 1) == '\n') position--;
        }
        return position;
    }

    private void selectCorresponding(Node previous) {
        nodes.forEach((item, node) -> {
            if (node.start() == previous.start() && node.path().equals(previous.path())) {
                for (var ancestor = item.parent(); ancestor != null; ancestor = ancestor.parent()) ancestor.setExpanded(true);
                tree.setCurrentItem(item);
                tree.scrollToItem(item);
            }
        });
    }

    private void editSelectedTlv() {
        Node selected = nodes.get(tree.currentItem());
        if (pending != null || document == null || !service.canEdit(document, selected)) return;
        Document original = document;
        var dialog = new AddTlvDialog(this, service, original, parents.get(selected),
                List.of(service.definition(original, selected)), selected);
        try {
            if (dialog.exec() != QDialog.DialogCode.Accepted.value()) return;
            String value = dialog.selection().value();
            submit(() -> service.replaceValue(original, selected, value), changed -> {
                if (changed == original) return;
                display(changed, true);
                selectCorresponding(selected);
            });
        } finally { dialog.dispose(); }
    }

    private void deleteSelectedTlv() {
        Node selected = nodes.get(tree.currentItem());
        if (pending != null || document == null || !service.canDelete(document, selected)) return;
        var answer = QMessageBox.question(this, "Del TLV", "Delete " + selected.name() + "?"
                + (selected.children().isEmpty() ? "" : " All its descendants will also be deleted."),
                new QMessageBox.StandardButtons(QMessageBox.StandardButton.Yes, QMessageBox.StandardButton.Cancel),
                QMessageBox.StandardButton.Cancel);
        if (answer != QMessageBox.StandardButton.Yes) return;
        Document original = document;
        Node parent = parents.get(selected);
        submit(() -> service.delete(original, selected), changed -> {
            display(changed, true);
            selectCorresponding(parent);
        });
    }

    private void showSecrets() {
        if (pending != null) return;
        var dialog = new SecretsDialog(this, secrets);
        try { dialog.exec(); } finally { dialog.dispose(); }
    }

    private void checkBinary() {
        if (pending != null || document == null) return;
        var dialog = new BinaryCheckDialog(this, service, document, secrets::hasShared, this::showSecrets);
        try { dialog.exec(); } finally { dialog.dispose(); }
    }

    private void saveBinary() {
        if (pending != null || document == null) return;
        if (binaryService.requiresSharedSecret(document) && !secrets.hasShared()) {
            showSecrets();
            if (!secrets.hasShared()) return;
        }
        Document original = document;
        var selection = QFileDialog.getSaveFileName(this, "Save configuration binary", "configuration.bin",
                "Binary configuration (*.bin *.cm *.mta);;All files (*)", QFileDialog.Option.DontConfirmOverwrite);
        if (selection == null || selection.result == null || selection.result.isEmpty()) return;
        Path target = Path.of(selection.result).toAbsolutePath().normalize();
        String sharedSecret = secrets.sharedValue();
        submit(() -> service.destinationHash(target), observedHash -> {
            if (observedHash != null && QMessageBox.question(this, "Replace file?",
                    "Replace " + target.getFileName() + "?",
                    new QMessageBox.StandardButtons(QMessageBox.StandardButton.Yes, QMessageBox.StandardButton.No),
                    QMessageBox.StandardButton.No) != QMessageBox.StandardButton.Yes) return;
            submit(() -> binaryService.save(original, target, observedHash, sharedSecret), saved -> {
                status.setText("Binary saved: " + saved + " | Checks skipped; text edits remain unchanged.");
            });
        });
    }

    private void chooseSaveFormat(boolean choosePath, Runnable afterSave) {
        if (pending != null || document == null) return;
        var dialog = new QDialog(this);
        dialog.setObjectName("saveFormatDialog");
        dialog.setWindowTitle("Save configuration");
        var layout = new QVBoxLayout(dialog);
        layout.addWidget(new QLabel("Choose a save format. Binary exports through Oscar; Text preserves editable source."));
        var buttons = new QDialogButtonBox();
        var text = buttons.addButton("Text", QDialogButtonBox.ButtonRole.ActionRole);
        text.setObjectName("chooseTextSave");
        var binary = buttons.addButton("Binary", QDialogButtonBox.ButtonRole.ActionRole);
        binary.setObjectName("chooseBinarySave");
        boolean[] binarySelected = {false};
        text.clicked.connect(dialog::accept);
        binary.clicked.connect(() -> { binarySelected[0] = true; dialog.accept(); });
        buttons.addButton(QDialogButtonBox.StandardButton.Cancel);
        buttons.rejected.connect(dialog::reject);
        layout.addWidget(buttons);
        int result;
        try { result = dialog.exec(); } finally { dialog.dispose(); }
        if (result != QDialog.DialogCode.Accepted.value()) return;
        if (binarySelected[0]) saveBinary();
        else saveDocument(choosePath, afterSave);
    }

    private void saveDocument(boolean choosePath, Runnable afterSave) {
        if (document == null || pending != null) return;
        Path destination = document.path();
        boolean pickedDestination = choosePath || destination == null;
        if (choosePath || destination == null) {
            var selection = QFileDialog.getSaveFileName(this, "Save configuration text",
                    destination == null ? "configuration.txt" : destination.toString(),
                    "Configuration text (*.txt *.conf *.cfg);;All files (*)", QFileDialog.Option.DontConfirmOverwrite);
            if (selection == null || selection.result == null || selection.result.isEmpty()) return;
            destination = Path.of(selection.result).toAbsolutePath().normalize();
        }
        Path target = destination;
        Document original = document;
        submit(() -> service.destinationHash(target), observedHash -> {
            if (pickedDestination && observedHash != null && QMessageBox.question(this, "Replace file?",
                    "Replace " + target.getFileName() + "?",
                    new QMessageBox.StandardButtons(QMessageBox.StandardButton.Yes, QMessageBox.StandardButton.No),
                    QMessageBox.StandardButton.No) != QMessageBox.StandardButton.Yes) return;
            String expected = target.equals(original.path()) ? original.diskHash() : observedHash;
            submit(() -> service.save(original, target, expected), saved -> {
                document = saved;
                modified = false;
                updateDocumentLabels();
                updateActions();
                afterSave.run();
            });
        });
    }

    private void withDiscardPermission(Runnable proceed) {
        if (!modified) { proceed.run(); return; }
        var answer = QMessageBox.question(this, "Unsaved changes", "Save changes to the current configuration?",
                new QMessageBox.StandardButtons(QMessageBox.StandardButton.Save,
                        QMessageBox.StandardButton.Discard, QMessageBox.StandardButton.Cancel),
                QMessageBox.StandardButton.Cancel);
        if (answer == QMessageBox.StandardButton.Save) chooseSaveFormat(false, proceed);
        else if (answer == QMessageBox.StandardButton.Discard) proceed.run();
    }

    private <Result> void submit(Callable<Result> operation, Consumer<Result> apply) {
        if (pending != null) return;
        completed = false;
        succeeded = false;
        pending = worker.submit(() -> {
            Result result = operation.call();
            return () -> apply.accept(result);
        });
        status.setText("Working...");
        updateActions();
    }

    private void receiveResult() {
        if (pending == null || !pending.isDone()) return;
        try {
            Runnable apply = pending.get();
            pending = null;
            completed = true;
            succeeded = true;
            updateActions();
            if (document != null) updateDocumentLabels();
            else status.setText("Ready");
            apply.run();
        } catch (ExecutionException | RuntimeException error) {
            pending = null;
            completed = true;
            succeeded = false;
            updateActions();
            Throwable cause = error instanceof ExecutionException ? error.getCause() : error;
            String message = cause instanceof InspectionException ? cause.getMessage()
                    : "The document operation failed. Check the file and Oscar dependencies.";
            status.setText(message);
            if (interactive) QMessageBox.warning(this, "Document operation failed", message);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            pending = null;
            completed = true;
            succeeded = false;
            updateActions();
            status.setText("Operation interrupted.");
        }
    }

    private void updateActions() {
        boolean idle = pending == null;
        newFile.setEnabled(idle);
        open.setEnabled(idle);
        save.setEnabled(idle && document != null && modified);
        saveAs.setEnabled(idle && document != null);
        saveMenu.setEnabled(idle && document != null);
        saveAsMenu.setEnabled(idle && document != null);
        binarySave.setEnabled(idle && document != null);
        binarySaveAs.setEnabled(idle && document != null);
        saveShortcut.setEnabled(idle && document != null);
        saveAsShortcut.setEnabled(idle && document != null);
        secretsAction.setEnabled(idle);
        configChecks.setEnabled(idle && document != null);
        Node selected = nodes.get(tree.currentItem());
        deleteTlv.setEnabled(idle && document != null && service.canDelete(document, selected));
        editTlv.setEnabled(idle && document != null && service.canEdit(document, selected));
        Node parent = insertionParent();
        addTlv.setEnabled(idle && parent != null && parent.container() && !parent.path().contains(-1));
    }

    public boolean completed() { return completed; }
    public boolean succeeded() { return succeeded && document != null; }
    public Document currentDocument() { return document; }

    @Override protected void closeEvent(QCloseEvent event) {
        if (interactive && pending != null) {
            event.ignore();
            status.setText("Wait for the current operation to finish.");
            return;
        }
        if (interactive && modified) {
            event.ignore();
            withDiscardPermission(() -> { modified = false; QTimer.singleShot(0, () -> close()); });
            return;
        }
        poller.stop();
        if (pending != null) pending.cancel(true);
        worker.shutdownNow();
        secrets.close();
        super.closeEvent(event);
    }
}
