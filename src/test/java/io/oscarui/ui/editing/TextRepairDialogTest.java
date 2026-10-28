/*
 * Copyright 2026 OSCAR-UI contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.oscarui.ui.editing;

import io.oscarui.ui.settings.DesktopTheme;
import io.oscarui.ui.MainWindow;

import io.oscarui.service.*;
import io.qt.widgets.*;
import io.qt.core.QTimer;
import java.nio.file.*;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "oscar.ui.tests", matches = "true")
class TextRepairDialogTest {
    @TempDir Path temporary;

    @Test void repairHighlightsRevalidatesAndSavesIntoWorkspace() throws Exception {
        QApplication.initialize(new String[0]);
        QApplication.setQuitOnLastWindowClosed(false);
        var window = new MainWindow(false);
        try {
            DesktopTheme.apply(Appearance.DARK);
            var path = Files.writeString(temporary.resolve("repair-example.txt"),
                    "Docsis {\n    Snmp11 1.3.6.1 Integer32 \"abc\";\n    NetworkAccess 1\n}\n");
            String original = Files.readString(path);
            window.show();
            window.openFile(path);
            waitUntil(() -> window.findChild(TextRepairDialog.class, "textRepairDialog") != null);
            var dialog = window.findChild(TextRepairDialog.class, "textRepairDialog");
            var editor = dialog.findChild(QPlainTextEdit.class, "repairEditor");
            var proceed = dialog.findChild(QPushButton.class, "repairContinue");
            var errors = dialog.findChild(QListWidget.class, "repairErrors");
            assertFalse(proceed.isEnabled());
            assertEquals(2, errors.count());
            assertEquals(2, editor.extraSelections().size());
            assertEquals("1", editor.extraSelections().get(1).cursor().selectedText());
            errors.setCurrentRow(0);
            assertEquals(1, editor.textCursor().blockNumber());
            capture(dialog, "text-repair-errors-dark.png");
            DesktopTheme.apply(Appearance.LIGHT);
            capture(dialog, "text-repair-errors-light.png");
            DesktopTheme.apply(Appearance.DARK);
            // Queue a check, then replace the buffer. Older results must never enable Continue.
            editor.setPlainText("Docsis { NetworkAccess 1; }");
            dialog.findChild(QPushButton.class, "repairValidate").click();
            editor.setPlainText("Docsis { NetworkAccess 1 }");
            assertFalse(proceed.isEnabled());
            waitUntil(() -> errors.count() > 0);
            assertFalse(proceed.isEnabled());
            editor.setPlainText("Docsis {\n    NetworkAccess 1;\n}\n");
            waitUntil(proceed::isEnabled);
            assertEquals(0, editor.extraSelections().size());
            assertEquals(original, Files.readString(path));
            capture(dialog, "text-repair-valid-dark.png");
            QTimer.singleShot(100, () -> {
                var prompt = (QMessageBox) QApplication.activeModalWidget();
                capture(prompt, "text-repair-save-choice-dark.png");
                prompt.buttons().stream().filter(b -> b.text().equals("Overwrite Original")).findFirst().orElseThrow().click();
            });
            proceed.click();
            waitUntil(() -> window.currentDocument() != null);
            assertEquals(path, window.currentDocument().path());
            assertEquals(editor.toPlainText(), Files.readString(path));
        } finally {
            window.dispose();
            QApplication.shutdown();
        }
    }

    @Test void cancelAndNormalizedLineEndingsPreserveOriginal() throws Exception {
        QApplication.initialize(new String[0]);
        QApplication.setQuitOnLastWindowClosed(false);
        var service = new TextRepairService(new OscarDocumentService());
        String raw = "Docsis {\r\n    Snmp11 1.3.6.1 OctetString \"😀\";\r\n    NetworkAccess 1\r\n}\r\n";
        var file = Files.writeString(temporary.resolve("crlf.txt"), raw);
        var opening = service.open(file);
        var dialog = new TextRepairDialog(null, service, opening.source(), opening.validation(),
                ignored -> fail("Cancel must not open a document"), ignored -> true);
        try {
            dialog.show();
            var editor = dialog.findChild(QPlainTextEdit.class, "repairEditor");
            var errors = dialog.findChild(QListWidget.class, "repairErrors");
            waitUntil(() -> errors.count() == 1);
            assertFalse(editor.toPlainText().contains("\r"));
            assertEquals("1", editor.extraSelections().getFirst().cursor().selectedText());
            editor.insertPlainText("change");
            QTimer.singleShot(100, () -> {
                var prompt = (QMessageBox) QApplication.activeModalWidget();
                prompt.button(QMessageBox.StandardButton.Cancel).click();
            });
            dialog.reject();
            assertTrue(dialog.isVisible());
            QTimer.singleShot(100, () -> {
                var prompt = (QMessageBox) QApplication.activeModalWidget();
                prompt.button(QMessageBox.StandardButton.Discard).click();
            });
            dialog.reject();
            assertFalse(dialog.isVisible());
            assertEquals(raw, Files.readString(file));
        } finally {
            dialog.dispose();
            QApplication.shutdown();
        }
    }

    private static void capture(QWidget widget, String filename) {
        if (!Boolean.getBoolean("oscar.ui.screenshots")) {
            return;
        }
        try {
            Files.createDirectories(Path.of("target/user-guide-images"));
            QApplication.processEvents();
            assertTrue(widget.grab().save("target/user-guide-images/" + filename));
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }

    private static void waitUntil(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(15).toNanos();
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            QApplication.processEvents();
            Thread.sleep(10);
        }
        assertTrue(condition.getAsBoolean(), "Timed out waiting for repair UI");
    }
}
