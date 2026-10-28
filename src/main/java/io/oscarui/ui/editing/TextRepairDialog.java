/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.editing;

import io.oscarui.service.TextRepairService;
import io.oscarui.service.OscarDocumentService;
import io.qt.core.*;
import io.qt.gui.*;
import io.qt.widgets.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Dedicated repair editor; the workspace remains a configuration editor. */
public final class TextRepairDialog extends QDialog {
    private static final int DEBOUNCE_MILLISECONDS = 500;
    private static final int POLL_MILLISECONDS = 50;
    private final TextRepairService service;
    private final TextRepairService.Source source;
    private final Consumer<OscarDocumentService.Document> opened;
    private final String initialEditorText;
    private final java.util.function.Predicate<Path> destinationAvailable;
    private final QPlainTextEdit editor = new QPlainTextEdit();
    private final QPlainTextEdit numbers = new QPlainTextEdit();
    private final QListWidget errors = new QListWidget();
    private final QLabel status = new QLabel();
    private final QPushButton proceed = new QPushButton("Continue…");
    private final QPushButton validate = new QPushButton("Validate");
    private final QTimer debounce = new QTimer(this);
    private final QTimer poll = new QTimer(this);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        var thread = new Thread(r, "text-repair");
        thread.setDaemon(true);
        return thread;
    });
    private Future<?> pending;
    private long revision;
    private long checkedRevision = -1;
    private long submittedRevision;
    private boolean requested;
    private boolean saving;
    private boolean saved;
    private TextRepairService.Validation checked;

    /**
     * Creates the repair editor with asynchronous validation and conflict-protected saving.
     *
     * <p>Call on the Qt UI thread.
     * @param parent owning widget, or null for a top-level dialog
     * @param service service used for registry, validation, preferences, or platform operations
     * @param source original file identity and text captured before repair
     * @param initial validation results for the original text snapshot
     * @param opened UI-thread callback receiving the successfully saved and imported document
     * @param destinationAvailable predicate rejecting destinations already owned by another workspace tab
     */
    public TextRepairDialog(QWidget parent, TextRepairService service, TextRepairService.Source source,
                     TextRepairService.Validation initial, Consumer<OscarDocumentService.Document> opened,
                     java.util.function.Predicate<Path> destinationAvailable) {
        super(parent);
        this.service = service;
        this.source = source;
        this.opened = opened;
        this.destinationAvailable = destinationAvailable;
        setWindowTitle("Repair Text File — " + source.path().getFileName());
        setObjectName("textRepairDialog");
        setModal(true);
        resize(940, 700);
        var layout = new QVBoxLayout(this);
        var explanation = new QLabel("Fix the text below. The original stays unchanged until you choose how to save.");
        explanation.setWordWrap(true);
        layout.addWidget(explanation);
        var find = new QLineEdit();
        find.setObjectName("repairFind");
        find.setPlaceholderText("Find text — press Enter for next match");
        find.returnPressed.connect(() -> {
            if (!editor.find(find.text())) {
                var cursor = editor.textCursor();
                cursor.movePosition(QTextCursor.MoveOperation.Start);
                editor.setTextCursor(cursor);
                editor.find(find.text());
            }
        });
        layout.addWidget(find);
        var editorRow = new QHBoxLayout();
        editor.setObjectName("repairEditor");
        editor.setLineWrapMode(QPlainTextEdit.LineWrapMode.NoWrap);
        editor.setFont(new QFont("monospace", 11));
        editor.setPlainText(source.text());
        initialEditorText = editor.toPlainText();
        numbers.setReadOnly(true);
        numbers.setFocusPolicy(Qt.FocusPolicy.NoFocus);
        numbers.setFixedWidth(65);
        numbers.setFont(editor.font());
        numbers.setLineWrapMode(QPlainTextEdit.LineWrapMode.NoWrap);
        numbers.setVerticalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff);
        numbers.setHorizontalScrollBarPolicy(Qt.ScrollBarPolicy.ScrollBarAlwaysOff);
        editor.verticalScrollBar().valueChanged.connect(value -> numbers.verticalScrollBar().setValue(value));
        editorRow.addWidget(numbers);
        editorRow.addWidget(editor, 1);
        layout.addLayout(editorRow, 1);
        errors.setObjectName("repairErrors");
        errors.setMaximumHeight(170);
        errors.currentRowChanged.connect(this::navigate);
        layout.addWidget(errors);
        status.setObjectName("repairStatus");
        status.setWordWrap(true);
        layout.addWidget(status);
        var buttons = new QHBoxLayout();
        var undo = new QPushButton("Undo");
        var redo = new QPushButton("Redo");
        undo.clicked.connect(editor::undo);
        redo.clicked.connect(editor::redo);
        buttons.addWidget(undo);
        buttons.addWidget(redo);
        buttons.addStretch();
        validate.setObjectName("repairValidate");
        validate.clicked.connect(this::requestValidation);
        proceed.setObjectName("repairContinue");
        proceed.clicked.connect(this::chooseSave);
        var cancel = new QPushButton("Cancel");
        cancel.clicked.connect(this::reject);
        buttons.addWidget(validate);
        buttons.addWidget(proceed);
        buttons.addWidget(cancel);
        layout.addLayout(buttons);
        debounce.setSingleShot(true);
        debounce.setInterval(DEBOUNCE_MILLISECONDS);
        debounce.timeout.connect(this::requestValidation);
        poll.setInterval(POLL_MILLISECONDS);
        poll.timeout.connect(this::collect);
        poll.start();
        editor.textChanged.connect(this::changed);
        finished.connect(result -> {
            debounce.stop();
            poll.stop();
            worker.shutdownNow();
        });
        updateNumbers();
        if (initialEditorText.equals(source.text())) {
            apply(initial);
        } else {
            requestValidation();
        }
    }

    /**
     * Closes only after confirming discard of modified text; never writes the original.
     * Call on the Qt thread. A save already in progress must finish first.
     */
    @Override public void reject() {
        if (saving) {
            return;
        }
        if (!saved && !editor.toPlainText().equals(initialEditorText)
                && QMessageBox.question(this, "Discard repairs?", "Discard your unsaved changes?",
                QMessageBox.StandardButton.Discard, QMessageBox.StandardButton.Cancel)
                != QMessageBox.StandardButton.Discard.value()) {
            return;
        }
        super.reject();
    }

    private void changed() {
        revision++;
        checkedRevision = -1;
        proceed.setEnabled(false);
        editor.setExtraSelections(java.util.List.of());
        errors.clear();
        status.setText("Waiting to check…");
        updateNumbers();
        debounce.start();
    }

    private void updateNumbers() {
        int scroll = editor.verticalScrollBar().value();
        numbers.setPlainText(java.util.stream.IntStream.rangeClosed(1, editor.document().blockCount())
                .mapToObj(Integer::toString).collect(java.util.stream.Collectors.joining("\n")));
        numbers.verticalScrollBar().setValue(scroll);
    }

    private void requestValidation() {
        if (saving) {
            return;
        }
        debounce.stop();
        requested = true;
        proceed.setEnabled(false);
        startValidation();
    }

    private void startValidation() {
        if (pending != null || !requested) {
            return;
        }
        requested = false;
        submittedRevision = revision;
        String snapshot = editor.toPlainText();
        status.setText("Checking syntax and import…");
        pending = worker.submit(() -> service.validate(source, snapshot));
    }

    private void collect() {
        if (pending == null || !pending.isDone()) {
            return;
        }
        try {
            Object result = pending.get();
            pending = null;
            if (saving) {
                saved = true;
                saving = false;
                opened.accept((OscarDocumentService.Document) result);
                accept();
                return;
            }
            if (submittedRevision == revision) {
                apply((TextRepairService.Validation) result);
            }
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            fail("Validation interrupted.");
        } catch (ExecutionException failure) {
            fail("Operation failed: " + failure.getCause().getClass().getSimpleName()
                    + ": " + failure.getCause().getMessage());
        }
        startValidation();
    }

    private void fail(String message) {
        pending = null;
        saving = false;
        editor.setEnabled(true);
        validate.setEnabled(true);
        checkedRevision = -1;
        proceed.setEnabled(false);
        status.setText(message);
    }

    private void apply(TextRepairService.Validation result) {
        checked = result;
        checkedRevision = revision;
        errors.clear();
        var highlights = new ArrayList<QTextEdit.ExtraSelection>();
        for (var diagnostic : result.lint().diagnostics()) {
            errors.addItem("Line " + diagnostic.range().line() + ", column " + diagnostic.range().column()
                    + " — " + diagnostic.message());
            var selection = new QTextEdit.ExtraSelection();
            var cursor = editor.textCursor();
            int length = editor.toPlainText().length();
            int start = Math.min(length, diagnostic.range().startOffset());
            int end = Math.min(length, diagnostic.range().endOffset());
            if (start == end) {
                if (start > 0) {
                    start--;
                } else if (end < length) {
                    end++;
                }
            }
            cursor.setPosition(start);
            cursor.setPosition(end, QTextCursor.MoveMode.KeepAnchor);
            selection.setCursor(cursor);
            var format = new QTextCharFormat();
            format.setUnderlineColor(new QColor("#e5484d"));
            format.setUnderlineStyle(QTextCharFormat.UnderlineStyle.WaveUnderline);
            selection.setFormat(format);
            highlights.add(selection);
        }
        editor.setExtraSelections(highlights);
        if (result.importFailure() != null) {
            errors.addItem("Import: " + result.importFailure());
        }
        proceed.setEnabled(result.accepted());
        status.setText(result.accepted() ? "Lint and import passed. Continue to choose where to save."
                : result.importFailure() != null ? "Syntax passed, but import failed. Review the error above."
                : result.lint().fullyScanned() ? result.lint().diagnostics().size() + " issue(s). Changes are checked automatically."
                : "Check incomplete. Resolve the reported limit or structure error.");
    }

    private void navigate(int row) {
        if (checked == null || checkedRevision != revision || row < 0 || row >= checked.lint().diagnostics().size()) {
            return;
        }
        var cursor = editor.textCursor();
        cursor.setPosition(Math.min(editor.toPlainText().length(), checked.lint().diagnostics().get(row).range().startOffset()));
        editor.setTextCursor(cursor);
        editor.centerCursor();
        editor.setFocus();
    }

    private void chooseSave() {
        if (pending != null || checkedRevision != revision || checked == null || !checked.accepted()) {
            return;
        }
        var choice = new QMessageBox(this);
        choice.setWindowTitle("Save repaired text");
        choice.setText("The text passed lint and import. How would you like to save it?");
        var overwrite = choice.addButton("Overwrite Original", QMessageBox.ButtonRole.AcceptRole);
        var saveAs = choice.addButton("Save As…", QMessageBox.ButtonRole.ActionRole);
        var back = choice.addButton("Back to Editing", QMessageBox.ButtonRole.RejectRole);
        choice.setDefaultButton(back);
        choice.exec();
        Path target;
        String expected;
        try {
            if (choice.clickedButton() == overwrite) {
                target = source.path();
                expected = source.diskHash();
            } else if (choice.clickedButton() == saveAs) {
                var selected = QFileDialog.getSaveFileName(this, "Save repaired text", source.path().toString(), "Text files (*.txt);;All files (*)");
                if (selected.result == null || selected.result.isBlank()) {
                    return;
                }
                target = Path.of(selected.result);
                expected = service.destinationHash(target);
                if (expected != null && QMessageBox.question(this, "Replace file?", "Replace " + target.getFileName() + "?",
                        QMessageBox.StandardButton.Yes, QMessageBox.StandardButton.No) != QMessageBox.StandardButton.Yes.value()) {
                    return;
                }
            } else {
                return;
            }
        } catch (io.oscarui.service.InspectionException failure) {
            status.setText(failure.getMessage());
            return;
        } finally {
            choice.dispose();
        }
        if (!destinationAvailable.test(target.toAbsolutePath().normalize())) {
            status.setText("That destination is already open in the workspace. Choose a different file.");
            return;
        }
        String text = editor.toPlainText();
        saving = true;
        requested = false;
        debounce.stop();
        editor.setEnabled(false);
        proceed.setEnabled(false);
        validate.setEnabled(false);
        status.setText("Saving…");
        pending = worker.submit(() -> service.save(source, text, target, expected));
    }
}
