/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui;

import io.oscarui.service.*;
import com.comcast.oscar.configurationfile.TemplateBannerLevel;
import io.oscarui.service.OscarDocumentService.*;
import io.qt.core.QTimer;
import io.qt.core.QEvent;
import io.qt.gui.QHelpEvent;
import io.qt.gui.QMouseEvent;
import io.qt.core.QStandardPaths;
import io.qt.core.QMetaObject;
import io.qt.core.QObject;
import java.io.IOException;
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

    private UpgradeChip upgradeChip;
    private ApplicationMaintenanceController maintenance;





    private final QAction newFile = action("&New...", "fileNew", QKeySequence.StandardKey.New);

    private final QAction open = action("&Open...", "fileOpen", QKeySequence.StandardKey.Open);

    private final QAction closeTab = action("&Close Tab", "fileCloseTab", QKeySequence.StandardKey.Close);

    private final QAction save = action("&Save", "fileSave", QKeySequence.StandardKey.Save);

    private final QAction saveAs = action("&Text...", "fileSaveAs", null);

    private final QAction addTlv = action("Add &TLV...", "addTlv", null);

    private final QAction duplicateTlv = action("Duplicate TLV", "duplicateTlv", null);

    private final QAction deleteTlv = action("&Del TLV...", "deleteTlv", null);

    private final QAction editTlv = action("&Edit TLV...", "editTlv", null);

    private final QAction binarySaveAs = action("&Binary...", "fileSaveAsBinary", null);

    private final QAction saveAsShortcut = action("Save As", "saveAsShortcut", QKeySequence.StandardKey.SaveAs);

    private final QAction inspectHexDump = action("&HexDump", "inspectHexDump", null);

    private final QAction inspectTlvBreakdown = action("&TLV breakdown", "inspectTlvBreakdown", null);

    private final QAction viewTlvHex = action("View TLV &Hex...", "viewTlvHex", null);

    private final QAction configChecks = action("Check Config...", "configChecks", null);

    private final QAction secretsAction = action("&Secrets...", "settingsSecrets", null);

    private final QAction showMicTlvs = action("Show CM/CMTS MIC TLVs", "showMicTlvs", null);

    private final SessionSecrets secrets = new SessionSecrets();

    private QMenu saveAsMenu;

    private QMenu inspectMenu;

    private final QTimer poller = new QTimer(this);

    private final boolean interactive;

    private final AppearanceSettingsStore appearanceSettings;

    private Appearance appearance = Appearance.SYSTEM;

    private final QMetaObject.Connection systemAppearanceConnection;

    private final WorkspacePane workspacePane;

    private final WorkspaceSessions workspace = new WorkspaceSessions();
    private final DocumentTabs documentTabs = new DocumentTabs(this::activateSession, this::closeSession);
    private boolean closeApproved;
    private Document document;

    private boolean modified;

    private boolean showTextTlvNumbers = true;
    private boolean includeTemplateBanner = true;

    private Future<Runnable> pending;
    private WorkspaceSessions.Session pendingOwner;

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
     * worker and clears session secrets. Interactive windows load per-user
     * appearance settings; noninteractive smoke/tests do not access user settings.
     *
     * @param interactive true to show prompts and open the editor after loading;
     *                    false for automated smoke and dialog tests
     */
    public MainWindow(boolean interactive) {
        this(interactive, interactive ? new AppearanceSettingsStore(Path.of(
                QStandardPaths.writableLocation(QStandardPaths.StandardLocation.AppConfigLocation), "settings.json")) : null);
    }

    MainWindow(boolean interactive, AppearanceSettingsStore appearanceSettings) {
        this.appearanceSettings = appearanceSettings;
        this.interactive = interactive;
        setWindowTitle("OSCAR-UI");
        // Keep menus and their corner controls together on every desktop OS.
        menuBar().setNativeMenuBar(false);

        resize(1200, 780);
        workspacePane = new WorkspacePane(this, documentTabs, newFile, open);
        setCentralWidget(workspacePane);
        buildMenus();
        documentTabs.configureMenu(open, newFile);
        viewTlvHex.triggered.connect(this::showSelectedTlvHex);
        workspacePane.configurationTreePane().currentItemChanged.connect((current, previous) -> {
            workspacePane.configurationTextPane().highlight(workspacePane.configurationTreePane().selectedTlv());
            updateActions();
        });
        workspacePane.configurationTreePane().itemDoubleClicked.connect((item, column) -> {
            workspacePane.configurationTreePane().setCurrentItem(item);
            Node selected = workspacePane.configurationTreePane().selectedTlv();
            if (document != null && documentService.canPopulate(document, selected)) {
                populateContainer(selected);
            }
            else {
                editSelectedTlv();
            }
        });
        workspacePane.configurationTreePane().configureContextMenu(List.of(addTlv, editTlv, duplicateTlv, deleteTlv, viewTlvHex));
        poller.timeout.connect(this::receiveResult);
        poller.start(40);
        updateActions();
        if (appearanceSettings != null) {
            try {
                appearance = appearanceSettings.load();
                includeTemplateBanner = appearanceSettings.loadIncludeTemplateBanner();
                secrets.replace("", appearanceSettings.loadObfuscateSnmpSecrets());
            } catch (IOException exception) {
                showStatus("Could not read appearance settings; using System. Save a preference in Settings > Global to replace them.");
            }
        }
        if (interactive) {
            try {
                documentService.commonTlvs().load(Path.of(QStandardPaths.writableLocation(
                        QStandardPaths.StandardLocation.AppConfigLocation), "common-tlvs.json"));
            } catch (IOException exception) {
                showStatus("Could not load Common TLV settings; using minimum-only defaults. Existing file was preserved.");
            }
        }
        upgradeChip = new UpgradeChip(() -> maintenance.showAbout(true));
        maintenance = new ApplicationMaintenanceController(this, () -> pending == null,
                this::withAllDocumentsPermission, () -> {
                    closeApproved = true;
                    close();
                }, upgradeChip::setAvailable);
        int menuInset = workspacePane.contentInset();
        var menuIcon = new QToolButton() {
            /** Reserve menu-bar space for the icon and its horizontal padding. */
            @Override public io.qt.core.QSize sizeHint() {
                return new io.qt.core.QSize(menuInset + 14, 26);
            }

            @Override protected void paintEvent(io.qt.gui.QPaintEvent event) {
                var painter = new io.qt.gui.QPainter(this);
                try {
                    // Match File's actual menu-item center, not the taller corner row.
                    var fileBounds = menuBar().actionGeometry(menuBar().actions().getFirst());
                    var center = mapFrom(menuBar(), fileBounds.center());
                    int iconSize = 20;
                    QApplication.windowIcon().paint(painter, menuInset + (14 - iconSize) / 2,
                            center.y() - iconSize / 2, iconSize, iconSize);
                } finally {
                    painter.end();
                    painter.dispose();
                }
            }
        };
        menuIcon.setObjectName("workspaceAppIcon");
        menuIcon.setAccessibleName("Settings");
        menuIcon.setToolTip("Settings");
        menuIcon.setAutoRaise(true);
        menuIcon.setPopupMode(QToolButton.ToolButtonPopupMode.InstantPopup);
        menuIcon.setMenu(findChild(QMenu.class, "settingsMenu"));
        menuIcon.setFixedSize(menuInset + 14, 26);
        windowIconChanged.connect(icon -> menuIcon.update());
        menuBar().setCornerWidget(menuIcon, Qt.Corner.TopLeftCorner);
        menuBar().setCornerWidget(new WorkspaceLayoutControls(upgradeChip, workspacePane.splitter(),
                workspacePane.configurationTreePane(), workspacePane.textPanel()), Qt.Corner.TopRightCorner);
        if (interactive) {
            upgradeChip.check(() -> new ReleaseUpdateService().checkRelease(
                    AboutDialog.loadBuildInfo().getProperty("ui.version", "unknown")));
        }
        updateMaskingStatus();
        DesktopTheme.apply(appearance);
        systemAppearanceConnection = QApplication.styleHints().colorSchemeChanged.connect(
                scheme -> DesktopTheme.apply(appearance, scheme), Qt.ConnectionType.QueuedConnection);
    }

    /**
     * Starts opening a configuration with automatic format detection.
     *
     * <p>Call on the Qt UI thread. An active operation causes this request to
     * be ignored. Text lint/import findings open a separate repair dialog;
     * other failures retain the current document and update the status.
     *
     * @param configurationPath non-null path of the configuration to open
     */
    public void openFile(Path configurationPath) {
        if (pending != null) {
            return;
        }
        if (activateExisting(configurationPath)) {
            return;
        }
        var repairService = new io.oscarui.service.TextRepairService(documentService);
        submit(() -> repairService.open(configurationPath), opening -> {
            if (opening.document() != null) {
                addSession(opening.document(), false);
                if (interactive) {
                    chooseTlv();
                }
            } else {
                succeeded = false;
                showStatus("Text needs repair before opening.");
                var repair = new TextRepairDialog(this, repairService, opening.source(), opening.validation(),
                        loaded -> {
                            addSession(loaded, false);
                            succeeded = true;
                            showStatus("");
                        }, target -> workspace.forPath(target) == null);
                repair.setAttribute(Qt.WidgetAttribute.WA_DeleteOnClose, true);
                repair.show();
            }
        }, true);
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
        if (activateExisting(configurationPath)) {
            return;
        }
        submit(() -> binaryService.open(configurationPath, configurationType), loaded -> {
            addSession(loaded, false);
            if (interactive) {
                chooseTlv();
            }
        }, true);
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
        var bannerLevel = includeTemplateBanner ? TemplateBannerLevel.SPDX_AUTHOR : TemplateBannerLevel.DISABLE;
        submit(() -> documentService.create(configurationType, includeAllTlvs, bannerLevel), created -> {
            addSession(created, true);
            if (interactive) {
                chooseTlv();
            }
        }, true);
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

    /**
     * Shows the inspection prerequisite even when its menu action is disabled.
     *
     * @param watched object receiving the event
     * @param event event dispatched by Qt on the UI thread
     * @return true when the Inspect tooltip was handled, otherwise Qt's result
     */
    @Override public boolean eventFilter(QObject watched, QEvent event) {
        if (watched == menuBar() && event.type() == QEvent.Type.ToolTip
                && event instanceof QHelpEvent helpEvent && inspectMenu != null
                && menuBar().actionAt(helpEvent.pos()) == inspectMenu.menuAction()) {
            QToolTip.showText(helpEvent.globalPos(), inspectMenu.menuAction().toolTip(), menuBar());
            return true;
        }
        if (watched == menuBar() && inspectMenu != null && event instanceof QMouseEvent mouseEvent
                && event.type() == QEvent.Type.MouseMove) {
            if (menuBar().actionAt(mouseEvent.position().toPoint()) == inspectMenu.menuAction()
                    && !inspectMenu.menuAction().isEnabled()) {
                QToolTip.showText(mouseEvent.globalPosition().toPoint(),
                        inspectMenu.menuAction().toolTip(), menuBar());
            } else {
                QToolTip.hideText();
            }
        }
        if (watched == menuBar() && event.type() == QEvent.Type.Leave) {
            QToolTip.hideText();
        }
        return super.eventFilter(watched, event);
    }

    @Override protected void closeEvent(QCloseEvent event) {
        if (interactive && pending != null) {
            event.ignore();
            showStatus("Wait for the current operation to finish.");
            return;
        }
        if (interactive && !closeApproved && workspace.all().stream().anyMatch(WorkspaceSessions.Session::modified)) {
            event.ignore();
            withAllDocumentsPermission(() -> { closeApproved = true; QTimer.singleShot(0, () -> close()); });
            return;
        }
        QObject.disconnect(systemAppearanceConnection);
        poller.stop();
        if (pending != null) {
            pending.cancel(true);
        }
        worker.shutdownNow();
        upgradeChip.stop();
        secrets.close();
        super.closeEvent(event);
    }

    void withAllDocumentsPermission(Runnable proceed) {
        var toSave = new ArrayList<WorkspaceSessions.Session>();
        var selected = workspace.active();
        for (var session : workspace.all()) {
            if (!session.modified()) {
                continue;
            }
            var answer = QMessageBox.question(this, "Unsaved changes", "Save changes to " + session.name() + "?",
                    new QMessageBox.StandardButtons(QMessageBox.StandardButton.Save,
                            QMessageBox.StandardButton.Discard, QMessageBox.StandardButton.Cancel),
                    QMessageBox.StandardButton.Cancel);
            if (answer == QMessageBox.StandardButton.Cancel) {
                return;
            }
            if (answer == QMessageBox.StandardButton.Save) {
                toSave.add(session);
            }
        }
        saveReviewedDocuments(toSave, selected, proceed);
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
        addAction(saveAsShortcut);
        saveAsShortcut.triggered.connect(this::chooseSaveFormat);
        binarySaveAs.triggered.connect(() -> saveBinary(true, () -> {}));
        closeTab.triggered.connect(() -> closeSession(workspace.active()));
        file.addAction(closeTab);
        var nextTab = action("Next Tab", "nextDocumentTab", QKeySequence.StandardKey.NextChild);
        nextTab.triggered.connect(() -> documentTabs.cycle(workspace, 1));
        addAction(nextTab);
        var previousTab = action("Previous Tab", "previousDocumentTab", QKeySequence.StandardKey.PreviousChild);
        previousTab.triggered.connect(() -> documentTabs.cycle(workspace, -1));
        addAction(previousTab);
        file.addAction(configChecks);
        configChecks.triggered.connect(this::checkBinary);
        file.addSeparator();
        var exit = action("E&xit", "fileExit", QKeySequence.StandardKey.Quit);
        file.addAction(exit);
        newFile.triggered.connect(this::chooseNew);
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
        copy.triggered.connect(workspacePane.configurationTextPane()::copy);
        workspacePane.configurationTextPane().copyAvailable.connect(copy::setEnabled);
        edit.addAction(copy);
        var selectAll = action("Select &All", "editSelectAll", QKeySequence.StandardKey.SelectAll);
        selectAll.setEnabled(false);
        selectAll.triggered.connect(workspacePane.configurationTextPane()::selectAll);
        workspacePane.configurationTextPane().textChanged.connect(() -> selectAll.setEnabled(!workspacePane.configurationTextPane().toPlainText().isEmpty()));
        edit.addAction(selectAll);
        var settings = new QMenu("Settings", this);
        settings.setObjectName("settingsMenu");
        var global = action("&Global...", "globalSettings", null);
        settings.addAction(global);
        showMicTlvs.setCheckable(true);
        showMicTlvs.toggled.connect(checked -> updateMicVisibility());
        global.triggered.connect(() -> {
            var dialog = new GlobalSettingsDialog(this, showMicTlvs.isChecked(), showTextTlvNumbers, appearance, documentService,
                    document == null ? null : document.configurationType());
            dialog.setIncludeTemplateBanner(includeTemplateBanner);
            try {
                if (dialog.exec() == QDialog.DialogCode.Accepted.value()) {
                    try {
                        if (appearanceSettings != null) {
                            appearanceSettings.save(dialog.appearance(), secrets.obfuscateSnmpSecrets(), dialog.includeTemplateBanner());
                        }
                        appearance = dialog.appearance();
                        includeTemplateBanner = dialog.includeTemplateBanner();
                        showMicTlvs.setChecked(dialog.showMicTlvs());
                        DesktopTheme.apply(appearance);
                        showTextTlvNumbers = dialog.showTextTlvNumbers();
                        if (document != null) {
                            workspacePane.configurationTextPane().displayDocument(document, showTextTlvNumbers, secrets.masking());
                            workspacePane.configurationTextPane().highlight(workspacePane.configurationTreePane().selectedTlv());
                        }
                    } catch (IOException exception) {
                        showStatus("Could not save appearance settings. Previous settings were kept.");
                        if (interactive) {
                            QMessageBox.warning(this, "Settings not saved", "Could not write appearance settings. Check configuration-directory permissions.");
                        }
                    }
                }
            } finally { dialog.dispose(); }
        });
        settings.addAction(secretsAction);
        secretsAction.triggered.connect(this::showSecrets);
        inspectMenu = menuBar().addMenu("&Inspect");
        menuBar().setMouseTracking(true);
        menuBar().installEventFilter(this);
        inspectMenu.setObjectName("inspectMenu");
        inspectMenu.addAction(inspectHexDump);
        inspectMenu.addAction(inspectTlvBreakdown);
        inspectHexDump.triggered.connect(() -> showBinaryInspection(BinaryInspectionDialog.View.HEX_DUMP));
        inspectTlvBreakdown.triggered.connect(() -> showBinaryInspection(BinaryInspectionDialog.View.TLV_BREAKDOWN));
        var tools = menuBar().addMenu("&Tools");
        var checkSecret = action("Check Shared Secret...", "checkSharedSecret", null);
        tools.addAction(checkSecret);
        checkSecret.triggered.connect(() -> {
            var dialog = new SharedSecretCheckDialog(this);
            try {
                dialog.exec();
            } finally {
                dialog.dispose();
            }
        });
        var helpMenu = menuBar().addMenu("&Help");
        var about = action("&About", "aboutOscar", null);
        about.triggered.connect(() -> maintenance.showAbout(false));
        var userGuide = action("&User Guide", "userGuide", null);
        userGuide.triggered.connect(() -> {
            if (!io.qt.gui.QDesktopServices.openUrl(new io.qt.core.QUrl(
                    ReleaseUpdateService.UI_PROJECT + "/tree/main/docs/ui"))) {
                QMessageBox.warning(this, "User Guide", "Could not open the user guide in your browser.");
            }
        });
        helpMenu.addAction(userGuide);
        helpMenu.addAction(about);
    }

    private void showSelectedTlvHex() {
        Node selected = workspacePane.configurationTreePane().selectedTlv();
        if (pending != null || document == null || selected == null || selected.tlvPath().isEmpty()) {
            return;
        }
        Document snapshot = document;
        submit(() -> new SelectedTlvHexService().inspect(snapshot, selected), result -> {
            var dialog = new TlvHexDialog(this, secrets.masking().hex(selected, result), secrets.masking().enabled());
            try {
                dialog.exec();
            } finally {
                dialog.dispose();
            }
        });
    }

    private void showBinaryInspection(BinaryInspectionDialog.View view) {
        if (pending != null || document == null || document.path() != null || document.origin() == null) {
            return;
        }
        if (view == BinaryInspectionDialog.View.HEX_DUMP
                && QMessageBox.warning(this, "View HexDump?",
                "HexDump shows the original binary bytes. Community strings, passwords, and other secrets "
                        + "may be visible in clear text, even when masking is enabled. Do you acknowledge and want to continue?",
                new QMessageBox.StandardButtons(QMessageBox.StandardButton.Yes, QMessageBox.StandardButton.Cancel),
                QMessageBox.StandardButton.Cancel) != QMessageBox.StandardButton.Yes) {
            return;
        }
        Document snapshot = document;
        boolean masked = view == BinaryInspectionDialog.View.TLV_BREAKDOWN && secrets.masking().enabled();
        submit(() -> masked ? binaryService.maskedInspection(snapshot, secrets.masking()) : switch (view) {
            case HEX_DUMP -> binaryService.hexDump(snapshot);
            case TLV_BREAKDOWN -> binaryService.tlvBreakdown(snapshot);
        }, listing -> {
            var dialog = new BinaryInspectionDialog(this, view, listing, masked);
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
        var selection = QFileDialog.getOpenFileName(this, "Open configuration", "",
                "Configuration files (*.txt *.conf *.cfg *.bin *.cm *.mta);;All files (*)");
        if (selection != null && selection.result != null && !selection.result.isEmpty()) {
            openFile(Path.of(selection.result));
        }
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

    private boolean activateExisting(Path path) {
        var existing = workspace.forPath(path);
        if (existing == null) {
            return false;
        }
        activateSession(existing);
        completed = true;
        succeeded = true;
        return true;
    }

    private void rememberSession() {
        if (workspace.active() != null) {
            workspace.active().remember(workspacePane.configurationTreePane().rememberView(),
                    workspacePane.configurationTextPane().verticalScrollBar().value(), workspacePane.configurationTextPane().horizontalScrollBar().value());
        }
    }

    private void addSession(Document loaded, boolean changed) {
        var existing = workspace.forPath(loaded.path() != null ? loaded.path() : loaded.origin() == null ? null : loaded.origin().path());
        activateSession(existing == null ? workspace.add(loaded, changed) : existing);
    }

    private void activateSession(WorkspaceSessions.Session session) {
        if (session == workspace.active()) {
            return;
        }
        rememberSession();
        workspace.activate(session);
        renderSession();
    }

    private void renderSession() {
        var session = workspace.active();
        document = session == null ? null : session.document();
        modified = session != null && session.modified();
        if (session == null) {
            workspacePane.configurationTreePane().clearDocument();
            workspacePane.configurationTextPane().clear();
        } else {
            workspacePane.configurationTextPane().displayDocument(document, showTextTlvNumbers, secrets.masking());
            workspacePane.configurationTreePane().displayRoot(document.root(), documentService.hasBinaryTree(document));
            workspacePane.configurationTreePane().applyMask(secrets.masking());
            workspacePane.configurationTreePane().restoreView(session.treeState());
            updateMicVisibility();
            workspacePane.configurationTextPane().verticalScrollBar().setValue(session.textScroll());
            workspacePane.configurationTextPane().horizontalScrollBar().setValue(session.textHorizontalScroll());
        }
        String savedStatus = session == null ? "" : session.status();
        updateDocumentLabels();
        showStatus(savedStatus);
        updateActions();
    }

    private void closeSession(WorkspaceSessions.Session session) {
        if (session == null || pending != null) {
            return;
        }
        activateSession(session);
        Runnable remove = () -> {
            workspace.remove(session);
            renderSession();
        };
        withDiscardPermission(remove);
    }

    private void saveReviewedDocuments(List<WorkspaceSessions.Session> toSave,
                                       WorkspaceSessions.Session selected, Runnable proceed) {
        if (toSave.isEmpty()) {
            if (selected != null) {
                activateSession(selected);
            }
            proceed.run();
            return;
        }
        activateSession(toSave.getFirst());
        saveCurrent(() -> saveReviewedDocuments(toSave.subList(1, toSave.size()), selected, proceed));
    }

    private void display(Document current, boolean changed) {
        if (workspace.active() != null) {
            workspace.active().replace(current, changed);
        }
        document = current;
        modified = changed;
        workspacePane.configurationTextPane().displayDocument(current, showTextTlvNumbers, secrets.masking());
        workspacePane.configurationTreePane().displayRoot(current.root(), documentService.hasBinaryTree(current));
        workspacePane.configurationTreePane().applyMask(secrets.masking());
        updateMicVisibility();
        updateDocumentLabels();
        updateActions();
    }

    private void updateMicVisibility() {
        if (document == null) {
            return;
        }
        workspacePane.configurationTreePane().setMicVisibility(tlvNode -> documentService.isGeneratedMic(document.configurationType(), tlvNode.tlvPath()), showMicTlvs.isChecked());
    }

    private void updateDocumentLabels() {
        workspacePane.showDocumentType(workspace.active() == null ? ""
                : workspace.active().format() == WorkspaceSessions.Format.BINARY ? "Binary" : "Text");
        documentTabs.refresh(workspace);
        showStatus("");
    }

    private void showStatus(String message) {
        if (workspace.active() != null) {
            workspace.active().status(message);
        }
        workspacePane.showStatus(message);
    }

    private Node insertionParent() {
        if (document == null) {
            return null;
        }
        return workspacePane.configurationTreePane().insertionParent();
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
        submit(() -> documentService.addDefinitions(original, parent), choices -> {
            var dialog = new TlvEditorDialog(this, documentService, original, parent, choices);
            dialog.applyMask(secrets.masking());
            if (dialog.exec() == QDialog.DialogCode.Accepted.value()) {
                var selection = dialog.selection();
                submit(() -> documentService.insert(original, parent, selection.definition(), selection.value()),
                        changed -> {
                            display(changed, true);
                            workspacePane.configurationTreePane().selectLastChild(parent);
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
            workspacePane.configurationTreePane().selectCorresponding(container);
            workspacePane.configurationTreePane().expandSelection();
        });
    }

    private boolean canOpenEditor(Node node) {
        return document != null && node != null && (documentService.canEdit(document, node)
                || node.isContainer() && documentService.canDelete(document, node));
    }

    private void editSelectedTlv() {
        Node selected = workspacePane.configurationTreePane().selectedTlv();
        if (pending != null || !canOpenEditor(selected)) {
            return;
        }
        Document original = document;
        var dialog = new TlvEditorDialog(this, documentService, original, workspacePane.configurationTreePane().parentOf(selected),
                List.of(documentService.definition(original, selected)), selected);
        dialog.applyMask(secrets.masking());
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
                workspacePane.configurationTreePane().selectCorresponding(selected);
            });
        } finally { dialog.dispose(); }
    }

    private void duplicateSelectedTlv() {
        Node selected = workspacePane.configurationTreePane().selectedTlv();
        if (pending != null || !documentService.canDuplicate(document, selected)) {
            return;
        }
        Document original = document;
        int index = original.root().childTlvs().indexOf(selected) + 1;
        submit(() -> documentService.duplicate(original, selected), changed -> {
            display(changed, true);
            workspacePane.configurationTreePane().selectCorresponding(changed.root().childTlvs().get(index));
            workspacePane.configurationTreePane().expandSelection();
        });
    }

    private void deleteSelectedTlv() {
        Node selected = workspacePane.configurationTreePane().selectedTlv();
        if (pending != null || document == null || !documentService.canDelete(document, selected)) {
            return;
        }
        if (!TlvEditorDialog.confirmDeletion(this, selected)) {
            return;
        }
        deleteNode(document, selected);
    }

    private void deleteNode(Document original, Node selected) {
        Node parent = workspacePane.configurationTreePane().parentOf(selected);
        submit(() -> documentService.delete(original, selected), changed -> {
            display(changed, true);
            workspacePane.configurationTreePane().selectCorresponding(parent);
        });
    }

    private void updateMaskingStatus() {
        workspacePane.showMasking(secrets.obfuscateSnmpSecrets());
    }

    private void showSecrets() {
        if (pending != null) {
            return;
        }
        var dialog = new SecretsDialog(this, secrets);
        try {
            if (dialog.exec() == QDialog.DialogCode.Accepted.value()) {
                if (document != null) {
                    workspacePane.configurationTextPane().displayDocument(document, showTextTlvNumbers, secrets.masking());
                    workspacePane.configurationTreePane().applyMask(secrets.masking());
                    workspacePane.configurationTextPane().highlight(workspacePane.configurationTreePane().selectedTlv());
                }
                updateMaskingStatus();
                showStatus("");
                if (appearanceSettings != null) {
                    try {
                        appearanceSettings.save(appearance, secrets.obfuscateSnmpSecrets());
                    } catch (IOException exception) {
                        showStatus("Masking applied for this session, but the preference could not be saved.");
                    }
                }
            }
        } finally {
            dialog.dispose();
        }
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
        var occupied = workspace.forPath(target);
        if (occupied != null && occupied != workspace.active()) {
            showStatus("That destination is already open in another tab. Close that tab or choose another path.");
            return;
        }
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
        var occupied = workspace.forPath(target);
        if (occupied != null && occupied != workspace.active()) {
            showStatus("That destination is already open in another tab. Close that tab or choose another path.");
            return;
        }
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
                display(saved, false);
                afterSave.run();
            });
        });
    }

    private void withDiscardPermission(Runnable proceed) {
        if (!modified) {
            proceed.run();
            return;
        }
        var answer = QMessageBox.question(this, "Unsaved changes", "Save changes to " + workspace.active().name() + "?",
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
        submit(operation, apply, false);
    }

    private <Result> void submit(Callable<Result> operation, Consumer<Result> apply, boolean selectNewSession) {
        if (pending != null) {
            return;
        }
        completed = false;
        succeeded = false;
        var owner = workspace.active();
        pendingOwner = owner;
        long revision = owner == null ? 0 : owner.revision();
        pending = worker.submit(() -> {
            Result result = operation.call();
            return () -> {
                if (owner != null && (workspace.find(owner.id()) == null || owner.revision() != revision)) {
                    return;
                }
                var selected = workspace.active();
                if (!selectNewSession && owner != null) {
                    activateSession(owner);
                }
                try {
                    apply.accept(result);
                } finally {
                    if (!selectNewSession && selected != owner && selected != null && workspace.find(selected.id()) != null) {
                        activateSession(selected);
                    }
                }
            };
        });
        showStatus("Working...");
        updateActions();
    }

    private void receiveResult() {
        if (pending == null || !pending.isDone()) {
            return;
        }
        var operationOwner = pendingOwner;
        try {
            Runnable apply = pending.get();
            pending = null;
            completed = true;
            succeeded = true;
            updateActions();
            if (operationOwner != null && operationOwner.status().equals("Working...")) {
                operationOwner.status("");
            }
            showStatus(workspace.active() == null ? "" : workspace.active().status());
            apply.run();
        } catch (ExecutionException | RuntimeException error) {
            pending = null;
            completed = true;
            succeeded = false;
            updateActions();
            Throwable cause = error instanceof ExecutionException ? error.getCause() : error;
            String message = cause instanceof InspectionException inspection ? inspection.displayMessage(secrets.masking().enabled())
                    : "The document operation failed. Check the file and Oscar dependencies.";
            if (secrets.masking().enabled() && !(cause instanceof InspectionException)) {
                message = "The document operation failed. Details are hidden while SNMP obfuscation is enabled.";
            }
            if (operationOwner != null) {
                operationOwner.status(message);
                if (operationOwner != workspace.active()) {
                    message = operationOwner.name() + ": " + message;
                }
            }
            if (operationOwner != null && operationOwner != workspace.active()) {
                // Report the background failure without storing it on an unrelated tab.
                workspacePane.showStatus(message);
            } else {
                showStatus(message);
            }
            if (interactive) {
                if (cause instanceof InspectionException inspection && inspection.binaryReport() != null) {
                    var dialog = new BinaryFailureDialog(this, inspection.binaryReport());
                    try {
                        dialog.exec();
                    } finally {
                        dialog.dispose();
                    }
                } else {
                    QMessageBox.warning(this, "Document operation failed", message);
                }
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
        workspacePane.textPanel().setCurrentIndex(document == null ? 0 : 1);
        newFile.setEnabled(idle);
        open.setEnabled(idle);
        closeTab.setEnabled(idle && document != null);
        save.setEnabled(idle && document != null && modified);
        saveAs.setEnabled(idle && document != null);
        saveAsMenu.setEnabled(idle && document != null);
        binarySaveAs.setEnabled(idle && document != null);
        saveAsShortcut.setEnabled(idle && document != null);
        secretsAction.setEnabled(idle);
        boolean binaryActive = idle && document != null && document.path() == null && document.origin() != null;
        inspectMenu.setEnabled(binaryActive);
        inspectMenu.menuAction().setEnabled(binaryActive);
        inspectMenu.menuAction().setToolTip(binaryActive
                ? "Inspect the active binary file."
                : idle ? "A binary file must be selected to inspect the file."
                : "Wait for the current operation to finish before inspecting.");
        inspectHexDump.setEnabled(binaryActive);
        inspectTlvBreakdown.setEnabled(binaryActive);
        configChecks.setEnabled(idle && document != null);
        Node selected = workspacePane.configurationTreePane().selectedTlv();
        viewTlvHex.setEnabled(idle && document != null && selected != null && !selected.tlvPath().isEmpty()
                && !selected.tlvPath().contains(Node.UNMAPPED_TLV_TYPE_CODE));
        duplicateTlv.setEnabled(idle && documentService.canDuplicate(document, selected));
        deleteTlv.setEnabled(idle && document != null && documentService.canDelete(document, selected));
        editTlv.setEnabled(idle && canOpenEditor(selected));
        Node parent = insertionParent();
        addTlv.setEnabled(idle && parent != null && parent.isContainer() && !parent.tlvPath().contains(Node.UNMAPPED_TLV_TYPE_CODE));
    }
}
