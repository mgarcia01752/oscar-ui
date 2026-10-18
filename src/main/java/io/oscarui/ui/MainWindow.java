package io.oscarui.ui;

import io.oscarui.service.*;
import io.oscarui.service.OscarDocumentService.*;
import io.qt.core.QTimer;
import io.qt.core.Qt;
import io.qt.gui.QAction;
import io.qt.gui.QCloseEvent;
import io.qt.gui.QKeySequence;
import io.qt.widgets.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class MainWindow extends QMainWindow {
    private final OscarDocumentService documentService = new OscarDocumentService();

    private final OscarBinaryService binaryService = new OscarBinaryService(documentService);

    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "oscar-document");
        thread.setDaemon(true);
        return thread;
    });

    private final QLabel typeLabel = new QLabel();

    private final ConfigurationTextPane configurationTextPane = new ConfigurationTextPane();

    private final ConfigurationTreePane configurationTreePane = new ConfigurationTreePane();

    private final QLabel status = new QLabel();

    private final QAction newFile = action("&New...", "fileNew", QKeySequence.StandardKey.New);

    private final QAction open = action("&Open...", "fileOpen", QKeySequence.StandardKey.Open);

    private final QAction save = action("&Save", "fileSave", null);

    private final QAction saveAs = action("&Text...", "fileSaveAs", null);

    private final QAction addTlv = action("Add &TLV...", "addTlv", null);

    private final QAction duplicateTlv = action("Duplicate TLV", "duplicateTlv", null);

    private final QAction deleteTlv = action("&Del TLV...", "deleteTlv", null);

    private final QAction editTlv = action("&Edit TLV...", "editTlv", null);

    private final QAction binarySaveAs = action("&Binary...", "fileSaveAsBinary", null);

    private final QAction saveShortcut = action("Save", "saveShortcut", QKeySequence.StandardKey.Save);

    private final QAction saveAsShortcut = action("Save As", "saveAsShortcut", QKeySequence.StandardKey.SaveAs);

    private final QAction inspectHexDump = action("&HexDump", "inspectHexDump", null);

    private final QAction inspectTlvBreakdown = action("&TLV breakdown", "inspectTlvBreakdown", null);

    private final QAction configChecks = action("Check Config...", "configChecks", null);

    private final QAction secretsAction = action("&Secrets...", "settingsSecrets", null);

    private final QAction showMicTlvs = action("Show CM/CMTS MIC TLVs", "showMicTlvs", null);

    private final SessionSecrets secrets = new SessionSecrets();

    private QMenu saveAsMenu;

    private final QTimer poller = new QTimer(this);

    private final boolean interactive;

    private Document document;

    private boolean modified;

    private Future<Runnable> pending;

    private boolean completed;

    private boolean succeeded;

    /**
     * Creates the interactive application window on the Qt UI thread.
     *
     * <p>Owns its child widgets and background worker until the window closes.
     */
    public MainWindow() {
        this(true);
    }

    /**
     * Creates the application window with optional interactive prompts.
     *
     * <p>Must be called on the Qt UI thread. Closing the window stops its
     * worker and clears session secrets.
     *
     * @param interactive true to show prompts and open the editor after loading;
     *                    false for automated smoke and dialog tests
     */
    public MainWindow(boolean interactive) {
        this.interactive = interactive;
        setWindowTitle("OSCAR-UI");
        DesktopTheme.updateIcon();
        resize(1200, 780);
        var central = new QWidget();
        var layout = new QVBoxLayout(central);
        typeLabel.setObjectName("documentType");
        statusBar().addPermanentWidget(typeLabel);
        status.setWordWrap(true);
        status.setObjectName("operationStatus");
        status.setVisible(false);
        var splitter = new QSplitter(Qt.Orientation.Horizontal);
        splitter.addWidget(configurationTreePane);
        splitter.addWidget(configurationTextPane);
        splitter.setStretchFactor(0, 1);
        splitter.setStretchFactor(1, 2);
        splitter.setSizes(List.of(380, 780));
        layout.addWidget(splitter, 1);
        layout.addWidget(status);
        setCentralWidget(central);
        buildMenus();
        configurationTreePane.currentItemChanged.connect((current, previous) -> {
            configurationTextPane.highlight(configurationTreePane.selectedTlv());
            updateActions();
        });
        configurationTreePane.itemDoubleClicked.connect((item, column) -> {
            configurationTreePane.setCurrentItem(item);
            Node selected = configurationTreePane.selectedTlv();
            if (document != null && documentService.canPopulate(document, selected)) {
                populateContainer(selected);
            }
            else {
                editSelectedTlv();
            }
        });
        configurationTreePane.configureContextMenu(List.of(addTlv, editTlv, duplicateTlv, deleteTlv));
        poller.timeout.connect(this::receiveResult);
        poller.start(40);
        updateActions();
    }

    /**
     * Starts opening a configuration with automatic format detection.
     *
     * <p>Call on the Qt UI thread. An active operation causes this request to
     * be ignored; failures retain the current document and update the status.
     *
     * @param configurationPath non-null path of the configuration to open
     */
    public void openFile(Path configurationPath) {
        if (pending != null) {
            return;
        }
        submit(() -> binaryService.openAuto(configurationPath), loaded -> {
            display(loaded, false);
            if (interactive) {
                chooseTlv();
            }
        });
    }

    /**
     * Starts decoding a binary file using the specified configuration family.
     *
     * <p>Call on the Qt UI thread. An active operation causes this request to
     * be ignored; a decode failure leaves the current document unchanged.
     *
     * @param configurationPath non-null binary configuration path
     * @param configurationType non-null Oscar configuration type for decoding
     */
    public void openBinaryFile(Path configurationPath, FileType configurationType) {
        if (pending != null) {
            return;
        }
        submit(() -> binaryService.open(configurationPath, configurationType), loaded -> {
            display(loaded, false);
            if (interactive) {
                chooseTlv();
            }
        });
    }

    /**
     * Starts creating a configuration or reference template.
     *
     * <p>Call on the Qt UI thread. The completed document is marked modified.
     * Requests during another operation are ignored.
     *
     * @param configurationType non-null Oscar configuration type to create
     * @param includeAllTlvs true for a reference template, false for a new draft
     */
    public void createDocument(FileType configurationType, boolean includeAllTlvs) {
        submit(() -> documentService.create(configurationType, includeAllTlvs), created -> {
            display(created, true);
            if (interactive) {
                chooseTlv();
            }
        });
    }

    /**
     * Reports whether the most recent background operation has completed.
     *
     * <p>Read on the Qt UI thread; starting another operation clears this flag.
     *
     * @return true after a result or failure has been handled
     */
    public boolean completed() {
        return completed;
    }

    /**
     * Reports whether the last operation succeeded with a loaded document.
     *
     * <p>Read on the Qt UI thread after completion has been observed.
     *
     * @return true when the last operation succeeded and a document is present
     */
    public boolean succeeded() {
        return succeeded && document != null;
    }

    /**
     * Returns the current document snapshot without modifying it.
     *
     * <p>Read on the Qt UI thread; background results replace this snapshot.
     *
     * @return current document, or null before a document has been loaded
     */
    public Document currentDocument() {
        return document;
    }

    @Override protected void closeEvent(QCloseEvent event) {
        if (interactive && pending != null) {
            event.ignore();
            showStatus("Wait for the current operation to finish.");
            return;
        }
        if (interactive && modified) {
            event.ignore();
            withDiscardPermission(() -> { modified = false; QTimer.singleShot(0, () -> close()); });
            return;
        }
        poller.stop();
        if (pending != null) {
            pending.cancel(true);
        }
        worker.shutdownNow();
        secrets.close();
        super.closeEvent(event);
    }

    private QAction action(String label, String name, QKeySequence.StandardKey shortcut) {
        var action = new QAction(label, this);
        action.setObjectName(name);
        if (shortcut != null) {
            action.setShortcut(new QKeySequence(shortcut));
        }
        return action;
    }

    private void buildMenus() {
        var file = menuBar().addMenu("&File");
        file.addAction(newFile);
        file.addAction(open);
        file.addAction(save);
        saveAsMenu = file.addMenu("Save &As");
        saveAsMenu.addAction(binarySaveAs);
        saveAsMenu.addAction(saveAs);
        addAction(saveShortcut);
        addAction(saveAsShortcut);
        saveShortcut.triggered.connect(() -> saveCurrent(() -> {}));
        saveAsShortcut.triggered.connect(this::chooseSaveFormat);
        binarySaveAs.triggered.connect(() -> saveBinary(true, () -> {}));
        file.addAction(configChecks);
        configChecks.triggered.connect(this::checkBinary);
        file.addSeparator();
        var exit = action("E&xit", "fileExit", QKeySequence.StandardKey.Quit);
        file.addAction(exit);
        newFile.triggered.connect(() -> withDiscardPermission(this::chooseNew));
        open.triggered.connect(this::chooseFile);
        save.triggered.connect(() -> saveCurrent(() -> {}));
        saveAs.triggered.connect(() -> saveDocument(true, () -> {}));
        exit.triggered.connect(() -> close());
        var edit = menuBar().addMenu("&Edit");
        edit.addAction(addTlv);
        addTlv.triggered.connect(this::chooseTlv);
        edit.addAction(editTlv);
        edit.addAction(duplicateTlv);
        duplicateTlv.triggered.connect(this::duplicateSelectedTlv);
        edit.addAction(deleteTlv);
        editTlv.triggered.connect(this::editSelectedTlv);
        deleteTlv.triggered.connect(this::deleteSelectedTlv);
        edit.addSeparator();
        var copy = action("&Copy", "editCopy", QKeySequence.StandardKey.Copy);
        copy.setEnabled(false);
        copy.triggered.connect(configurationTextPane::copy);
        configurationTextPane.copyAvailable.connect(copy::setEnabled);
        edit.addAction(copy);
        var selectAll = action("Select &All", "editSelectAll", QKeySequence.StandardKey.SelectAll);
        selectAll.setEnabled(false);
        selectAll.triggered.connect(configurationTextPane::selectAll);
        configurationTextPane.textChanged.connect(() -> selectAll.setEnabled(!configurationTextPane.toPlainText().isEmpty()));
        edit.addAction(selectAll);
        var settings = menuBar().addMenu("&Settings");
        settings.setObjectName("settingsMenu");
        var global = action("&Global...", "globalSettings", null);
        settings.addAction(global);
        showMicTlvs.setCheckable(true);
        showMicTlvs.toggled.connect(checked -> updateMicVisibility());
        global.triggered.connect(() -> {
            var dialog = new GlobalSettingsDialog(this, showMicTlvs.isChecked());
            try {
                if (dialog.exec() == QDialog.DialogCode.Accepted.value()) {
                    showMicTlvs.setChecked(dialog.showMicTlvs());
                    DesktopTheme.apply(dialog.darkMode());
                }
            } finally { dialog.dispose(); }
        });
        settings.addAction(secretsAction);
        secretsAction.triggered.connect(this::showSecrets);
        var inspectMenu = menuBar().addMenu("&Inspect");
        inspectMenu.setObjectName("inspectMenu");
        inspectMenu.addAction(inspectHexDump);
        inspectMenu.addAction(inspectTlvBreakdown);
        inspectHexDump.triggered.connect(() -> showBinaryInspection(BinaryInspectionDialog.View.HEX_DUMP));
        inspectTlvBreakdown.triggered.connect(() -> showBinaryInspection(BinaryInspectionDialog.View.TLV_BREAKDOWN));
        var aboutMenu = menuBar().addMenu("&About");
        var about = action("About &OSCAR-UI", "aboutOscar", null);
        about.triggered.connect(() -> QMessageBox.about(this, "About OSCAR-UI",
                "OSCAR-UI 0.1.0\nOscar 3.1.1\nQt Jambi 6.8.3\n\nApache License 2.0"));
        aboutMenu.addAction(about);
        var aboutQt = action("About &Qt", "aboutQt", null);
        aboutQt.triggered.connect(() -> QMessageBox.aboutQt(this));
        aboutMenu.addAction(aboutQt);
    }

    private void showBinaryInspection(BinaryInspectionDialog.View view) {
        if (pending != null || document == null || document.path() != null || document.origin() == null) {
            return;
        }
        Document snapshot = document;
        submit(() -> switch (view) {
            case HEX_DUMP -> binaryService.hexDump(snapshot);
            case TLV_BREAKDOWN -> binaryService.tlvBreakdown(snapshot);
        }, listing -> {
            var dialog = new BinaryInspectionDialog(this, view, listing);
            try {
                dialog.exec();
            } finally {
                dialog.dispose();
            }
        });
    }

    private void chooseFile() {
        if (pending != null) {
            return;
        }
        withDiscardPermission(() -> {
            var selection = QFileDialog.getOpenFileName(this, "Open configuration", "",
                    "Configuration files (*.txt *.conf *.cfg *.bin *.cm *.mta);;All files (*)");
            if (selection != null && selection.result != null && !selection.result.isEmpty()) {
                openFile(Path.of(selection.result));
            }
        });
    }

    private void chooseNew() {
        submit(documentService::types, types -> {
            var dialog = new NewDocumentDialog(this, types);
            if (dialog.exec() == QDialog.DialogCode.Accepted.value()) {
                var selection = dialog.selection();
                createDocument(selection.configurationType(), selection.includeAllTlvs());
            }
            dialog.dispose();
        });
    }

    private void display(Document current, boolean changed) {
        document = current;
        modified = changed;
        configurationTextPane.displaySource(current.text());
        configurationTreePane.displayRoot(current.root());
        updateMicVisibility();
        updateDocumentLabels();
        updateActions();
    }

    private void updateMicVisibility() {
        if (document == null) {
            return;
        }
        configurationTreePane.setMicVisibility(tlvNode -> documentService.isGeneratedMic(document.configurationType(), tlvNode.tlvPath()), showMicTlvs.isChecked());
    }

    private void updateDocumentLabels() {
        var file = document.path() != null ? document.path()
                : document.origin() == null ? null : document.origin().path();
        String name = file == null ? "Untitled" : file.getFileName().toString();
        typeLabel.setText(document.path() == null && document.origin() != null ? "Binary" : "Text");
        setWindowTitle(name + (modified ? " *" : "") + " - OSCAR-UI");
        showStatus("");
    }

    private void showStatus(String message) {
        status.setText(message);
        status.setVisible(!message.isEmpty());
    }

    private Node insertionParent() {
        if (document == null) {
            return null;
        }
        return configurationTreePane.insertionParent();
    }

    private void chooseTlv() {
        if (document == null || pending != null) {
            return;
        }
        Document original = document;
        Node parent = insertionParent();
        if (documentService.canPopulate(original, parent)) {
            populateContainer(parent);
            return;
        }
        submit(() -> documentService.choices(original, parent), choices -> {
            var dialog = new TlvEditorDialog(this, documentService, original, parent, choices);
            if (dialog.exec() == QDialog.DialogCode.Accepted.value()) {
                var selection = dialog.selection();
                submit(() -> documentService.insert(original, parent, selection.definition(), selection.value()),
                        changed -> {
                            display(changed, true);
                            configurationTreePane.selectLastChild(parent);
                        });
            }
            dialog.dispose();
        });
    }

    private void populateContainer(Node container) {
        if (document == null || pending != null || !documentService.canPopulate(document, container)) {
            return;
        }
        Document original = document;
        submit(() -> documentService.populate(original, container), changed -> {
            display(changed, true);
            configurationTreePane.selectCorresponding(container);
            configurationTreePane.expandSelection();
        });
    }

    private boolean canOpenEditor(Node node) {
        return document != null && node != null && (documentService.canEdit(document, node)
                || node.isContainer() && documentService.canDelete(document, node));
    }

    private void editSelectedTlv() {
        Node selected = configurationTreePane.selectedTlv();
        if (pending != null || !canOpenEditor(selected)) {
            return;
        }
        Document original = document;
        var dialog = new TlvEditorDialog(this, documentService, original, configurationTreePane.parentOf(selected),
                List.of(documentService.definition(original, selected)), selected);
        try {
            if (dialog.exec() != QDialog.DialogCode.Accepted.value()) {
                return;
            }
            if (dialog.deletionRequested()) {
                deleteNode(original, selected);
                return;
            }
            String value = dialog.selection().value();
            submit(() -> documentService.replaceValue(original, selected, value), changed -> {
                if (changed == original) {
                    return;
                }
                display(changed, true);
                configurationTreePane.selectCorresponding(selected);
            });
        } finally { dialog.dispose(); }
    }

    private void duplicateSelectedTlv() {
        Node selected = configurationTreePane.selectedTlv();
        if (pending != null || !documentService.canDuplicate(document, selected)) {
            return;
        }
        Document original = document;
        int index = original.root().childTlvs().indexOf(selected) + 1;
        submit(() -> documentService.duplicate(original, selected), changed -> {
            display(changed, true);
            configurationTreePane.selectCorresponding(changed.root().childTlvs().get(index));
            configurationTreePane.expandSelection();
        });
    }

    private void deleteSelectedTlv() {
        Node selected = configurationTreePane.selectedTlv();
        if (pending != null || document == null || !documentService.canDelete(document, selected)) {
            return;
        }
        if (!TlvEditorDialog.confirmDeletion(this, selected)) {
            return;
        }
        deleteNode(document, selected);
    }

    private void deleteNode(Document original, Node selected) {
        Node parent = configurationTreePane.parentOf(selected);
        submit(() -> documentService.delete(original, selected), changed -> {
            display(changed, true);
            configurationTreePane.selectCorresponding(parent);
        });
    }

    private void showSecrets() {
        if (pending != null) {
            return;
        }
        var dialog = new SecretsDialog(this, secrets);
        try { dialog.exec(); } finally { dialog.dispose(); }
    }

    private void checkBinary() {
        if (pending != null || document == null) {
            return;
        }
        var dialog = new BinaryCheckDialog(this, documentService, document, secrets::hasShared, this::showSecrets);
        try { dialog.exec(); } finally { dialog.dispose(); }
    }

    private void saveCurrent(Runnable afterSave) {
        if (document == null || pending != null) {
            return;
        }
        if (document.path() == null && document.origin() != null) {
            saveBinary(false, afterSave);
        }
        else {
            saveDocument(false, afterSave);
        }
    }

    private void saveBinary(boolean choosePath, Runnable afterSave) {
        if (pending != null || document == null) {
            return;
        }
        if (binaryService.requiresSharedSecret(document) && !secrets.hasShared()) {
            showSecrets();
            if (!secrets.hasShared()) {
                return;
            }
        }
        Document original = document;
        Path destination = original.path() == null && original.origin() != null ? original.origin().path() : null;
        boolean pickedDestination = choosePath || destination == null;
        if (pickedDestination) {
            var selection = QFileDialog.getSaveFileName(this, "Save configuration binary",
                    destination == null ? "configuration.bin" : destination.toString(),
                    "Binary configuration (*.bin *.cm *.mta);;All files (*)", QFileDialog.Option.DontConfirmOverwrite);
            if (selection == null || selection.result == null || selection.result.isEmpty()) {
                return;
            }
            destination = Path.of(selection.result).toAbsolutePath().normalize();
        }
        Path target = destination;
        String sharedSecret = secrets.sharedValue();
        submit(() -> documentService.destinationHash(target), observedHash -> {
            if (pickedDestination && observedHash != null && QMessageBox.question(this, "Replace file?",
                    "Replace " + target.getFileName() + "?",
                    new QMessageBox.StandardButtons(QMessageBox.StandardButton.Yes, QMessageBox.StandardButton.No),
                    QMessageBox.StandardButton.No) != QMessageBox.StandardButton.Yes) {
                return;
            }
            String expected = original.origin() != null && target.equals(original.origin().path())
                    ? original.origin().diskHash() : observedHash;
            submit(() -> binaryService.saveDocument(original, target, expected, sharedSecret), saved -> {
                display(saved, false);
                afterSave.run();
            });
        });
    }

    private void chooseSaveFormat() {
        if (pending != null || document == null) {
            return;
        }
        var dialog = new SaveFormatDialog(this);
        try {
            if (dialog.exec() != QDialog.DialogCode.Accepted.value()) {
                return;
            }
            if (dialog.selectedFormat() == SaveFormatDialog.SaveFormat.BINARY) {
                saveBinary(true, () -> {});
            } else {
                saveDocument(true, () -> {});
            }
        } finally {
            dialog.dispose();
        }
    }

    private void saveDocument(boolean choosePath, Runnable afterSave) {
        if (document == null || pending != null) {
            return;
        }
        Path destination = document.path();
        boolean pickedDestination = choosePath || destination == null;
        if (choosePath || destination == null) {
            var selection = QFileDialog.getSaveFileName(this, "Save configuration text",
                    destination == null ? "configuration.txt" : destination.toString(),
                    "Configuration text (*.txt *.conf *.cfg);;All files (*)", QFileDialog.Option.DontConfirmOverwrite);
            if (selection == null || selection.result == null || selection.result.isEmpty()) {
                return;
            }
            destination = Path.of(selection.result).toAbsolutePath().normalize();
        }
        Path target = destination;
        Document original = document;
        submit(() -> documentService.destinationHash(target), observedHash -> {
            if (pickedDestination && observedHash != null && QMessageBox.question(this, "Replace file?",
                    "Replace " + target.getFileName() + "?",
                    new QMessageBox.StandardButtons(QMessageBox.StandardButton.Yes, QMessageBox.StandardButton.No),
                    QMessageBox.StandardButton.No) != QMessageBox.StandardButton.Yes) {
                return;
            }
            String expected = target.equals(original.path()) ? original.diskHash() : observedHash;
            submit(() -> documentService.save(original, target, expected), saved -> {
                document = saved;
                modified = false;
                updateDocumentLabels();
                updateActions();
                afterSave.run();
            });
        });
    }

    private void withDiscardPermission(Runnable proceed) {
        if (!modified) {
            proceed.run();
            return;
        }
        var answer = QMessageBox.question(this, "Unsaved changes", "Save changes to the current configuration?",
                new QMessageBox.StandardButtons(QMessageBox.StandardButton.Save,
                        QMessageBox.StandardButton.Discard, QMessageBox.StandardButton.Cancel),
                QMessageBox.StandardButton.Cancel);
        if (answer == QMessageBox.StandardButton.Save) {
            saveCurrent(proceed);
        }
        else if (answer == QMessageBox.StandardButton.Discard) {
            proceed.run();
        }
    }

    private <Result> void submit(Callable<Result> operation, Consumer<Result> apply) {
        if (pending != null) {
            return;
        }
        completed = false;
        succeeded = false;
        pending = worker.submit(() -> {
            Result result = operation.call();
            return () -> apply.accept(result);
        });
        showStatus("Working...");
        updateActions();
    }

    private void receiveResult() {
        if (pending == null || !pending.isDone()) {
            return;
        }
        try {
            Runnable apply = pending.get();
            pending = null;
            completed = true;
            succeeded = true;
            updateActions();
            if (document != null) {
                updateDocumentLabels();
            }
            else {
                showStatus("");
            }
            apply.run();
        } catch (ExecutionException | RuntimeException error) {
            pending = null;
            completed = true;
            succeeded = false;
            updateActions();
            Throwable cause = error instanceof ExecutionException ? error.getCause() : error;
            String message = cause instanceof InspectionException ? cause.getMessage()
                    : "The document operation failed. Check the file and Oscar dependencies.";
            showStatus(message);
            if (interactive) {
                QMessageBox.warning(this, "Document operation failed", message);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            pending = null;
            completed = true;
            succeeded = false;
            updateActions();
            showStatus("Operation interrupted.");
        }
    }

    private void updateActions() {
        boolean idle = pending == null;
        newFile.setEnabled(idle);
        open.setEnabled(idle);
        save.setEnabled(idle && document != null && modified);
        saveAs.setEnabled(idle && document != null);
        saveAsMenu.setEnabled(idle && document != null);
        binarySaveAs.setEnabled(idle && document != null);
        saveShortcut.setEnabled(idle && document != null && modified);
        saveAsShortcut.setEnabled(idle && document != null);
        secretsAction.setEnabled(idle);
        boolean binaryActive = idle && document != null && document.path() == null && document.origin() != null;
        inspectHexDump.setEnabled(binaryActive);
        inspectTlvBreakdown.setEnabled(binaryActive);
        configChecks.setEnabled(idle && document != null);
        Node selected = configurationTreePane.selectedTlv();
        duplicateTlv.setEnabled(idle && documentService.canDuplicate(document, selected));
        deleteTlv.setEnabled(idle && document != null && documentService.canDelete(document, selected));
        editTlv.setEnabled(idle && canOpenEditor(selected));
        Node parent = insertionParent();
        addTlv.setEnabled(idle && parent != null && parent.isContainer() && !parent.tlvPath().contains(Node.UNMAPPED_TLV_TYPE_CODE));
    }
}
