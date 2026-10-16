package io.github.mgarcia01752.oscarui.ui;

import static org.junit.jupiter.api.Assertions.*;
import io.github.mgarcia01752.oscarui.service.OscarDocumentService;
import io.qt.core.*;
import io.qt.gui.QAction;
import io.qt.widgets.*;
import java.nio.file.*;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

@EnabledIfSystemProperty(named = "oscar.ui.tests", matches = "true")
class MainWindowTest {
    @TempDir Path temporary;
    private Throwable callbackFailure;

    @BeforeAll static void initializeQt() {
        QApplication.initialize(new String[0]);
        QApplication.setQuitOnLastWindowClosed(false);
    }
    @AfterAll static void shutdownQt() { QApplication.shutdown(); }

    @Test void createsInsertsSavesAndPreservesDocumentOnCancellation() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            var types = new OscarDocumentService().types();
            int index = java.util.stream.IntStream.range(0, types.size())
                    .filter(position -> types.get(position).header().equals("Docsis")).findFirst().orElseThrow();
            respond(window, dialog -> {
                assertEquals("newDocumentDialog", dialog.objectName());
                assertFalse(dialog.findChild(QPushButton.class, "createDocument").isEnabled());
                dialog.findChild(QComboBox.class, "newFileType").setCurrentIndex(index);
                dialog.grab().save("target/ui-new.png");
                dialog.findChild(QPushButton.class, "createDocument").click();
            });
            action(window, "fileNew").trigger();
            await(() -> window.completed() && window.currentDocument() != null);
            assertCallback();
            assertTrue(window.currentDocument().root().children().isEmpty());
            assertTrue(action(window, "fileSave").isEnabled());
            respond(window, dialog -> {
                assertEquals("addTlvDialog", dialog.objectName());
                dialog.findChild(QLineEdit.class, "tlvSearch").setText("NetworkAccess");
                assertFalse(dialog.findChild(QPushButton.class, "confirmAddTlv").isEnabled());
                dialog.findChild(QLineEdit.class, "tlvValue").setText("bad");
                assertFalse(dialog.findChild(QPushButton.class, "confirmAddTlv").isEnabled());
                dialog.findChild(QLineEdit.class, "tlvValue").setText("1");
                dialog.grab().save("target/ui-add-tlv.png");
                dialog.findChild(QPushButton.class, "confirmAddTlv").click();
            });
            action(window, "addTlv").trigger();
            await(() -> window.completed() && window.currentDocument().root().children().size() == 1);
            assertCallback();
            assertTrue(window.currentDocument().text().contains("NetworkAccess 1;"));
            assertEquals("NetworkAccess", window.findChild(QTreeWidget.class, "configurationTree").currentItem().text(0));
            Path destination = temporary.resolve("saved.txt");
            respond(window, dialog -> {
                assertInstanceOf(QFileDialog.class, dialog);
                dialog.findChild(QLineEdit.class, "fileNameEdit").setText(destination.toString());
                assertEquals(destination.toString(), ((QFileDialog) dialog).selectedFiles().get(0));
                dialog.accept();
            });
            action(window, "fileSave").trigger();
            await(() -> window.completed() && destination.equals(window.currentDocument().path()));
            assertCallback();
            assertEquals(window.currentDocument().text(), Files.readString(destination));
            assertFalse(action(window, "fileSave").isEnabled());
            var saved = window.currentDocument();
            respond(window, QDialog::reject);
            action(window, "fileOpen").trigger();
            assertSame(saved, window.currentDocument());
            respond(window, QDialog::reject);
            action(window, "fileNew").trigger();
            await(window::completed);
            assertCallback();
            assertSame(saved, window.currentDocument());
            respond(window, QDialog::reject);
            action(window, "fileSaveAs").trigger();
            assertSame(saved, window.currentDocument());
        } finally { window.close(); window.dispose(); }
    }

    @Test void opensPopupAfterLoadAndHonorsUnsavedCloseCancellation() throws Exception {
        var window = new MainWindow(true);
        window.show();
        try {
            Path file = temporary.resolve("input.txt");
            Files.writeString(file, "Docsis { NetworkAccess 1; }");
            respond(window, dialog -> {
                assertEquals("addTlvDialog", dialog.objectName());
                dialog.reject();
            });
            window.openFile(file);
            await(() -> window.completed() && window.currentDocument() != null && QApplication.activeModalWidget() == null);
            assertCallback();
            assertEquals(file, window.currentDocument().path());
            respond(window, QDialog::reject);
            window.createDocument(window.currentDocument().type(), false);
            await(() -> window.completed() && window.currentDocument().path() == null && QApplication.activeModalWidget() == null);
            respond(window, dialog -> ((QMessageBox) dialog).button(QMessageBox.StandardButton.Cancel).click());
            window.close();
            assertCallback();
            assertTrue(window.isVisible());
            assertTrue(action(window, "fileSave").isEnabled());
            respond(window, dialog -> ((QMessageBox) dialog).button(QMessageBox.StandardButton.Discard).click());
            window.close();
            assertCallback();
            await(() -> !window.isVisible());
        } finally { window.dispose(); }
    }

    @Test void protectsExistingFilesAcrossOverwriteConfirmation() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            var type = new OscarDocumentService().types().stream().filter(entry -> entry.header().equals("Docsis")).findFirst().orElseThrow();
            window.createDocument(type, false);
            await(() -> window.completed() && window.currentDocument() != null);
            var original = window.currentDocument();
            Path destination = temporary.resolve("existing.txt");
            Files.writeString(destination, "original file");
            boolean[] answered = {false};
            saveAs(window, destination, dialog -> {
                ((QMessageBox) dialog).button(QMessageBox.StandardButton.No).click();
                answered[0] = true;
            });
            await(() -> answered[0] && window.completed());
            assertSame(original, window.currentDocument());
            assertEquals("original file", Files.readString(destination));
            assertTrue(action(window, "fileSave").isEnabled());
            saveAs(window, destination, dialog -> {
                try { Files.writeString(destination, "changed during confirmation"); }
                catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                ((QMessageBox) dialog).button(QMessageBox.StandardButton.Yes).click();
            });
            await(() -> window.completed() && !window.succeeded());
            assertEquals("changed during confirmation", Files.readString(destination));
            assertSame(original, window.currentDocument());
            assertTrue(action(window, "fileSave").isEnabled());
            saveAs(window, destination, dialog -> ((QMessageBox) dialog).button(QMessageBox.StandardButton.Yes).click());
            await(() -> window.completed() && destination.equals(window.currentDocument().path()));
            assertEquals(original.text(), Files.readString(destination));
            assertFalse(action(window, "fileSave").isEnabled());
        } finally { window.close(); window.dispose(); }
    }

    @Test void createsAllTlvsAndPreservesLoadedDocumentOnOpenFailure() throws Exception {
        var window = new MainWindow(false);
        window.show();
        try {
            Path file = temporary.resolve("sample.txt");
            Files.writeString(file, "Docsis { NetworkAccess 1; }");
            window.openFile(file);
            await(() -> window.completed() && window.succeeded());
            var loaded = window.currentDocument();
            window.openFile(temporary.resolve("missing.txt"));
            await(window::completed);
            assertFalse(window.succeeded());
            assertSame(loaded, window.currentDocument());
            assertEquals("NetworkAccess", window.findChild(QTreeWidget.class, "configurationTree").topLevelItem(0).child(0).text(0));
            respond(window, dialog -> {
                var combo = dialog.findChild(QComboBox.class, "newFileType");
                for (int index = 0; index < combo.count(); index++) {
                    if (combo.itemText(index).startsWith("Docsis-4.0 ")) combo.setCurrentIndex(index);
                }
                dialog.findChild(QRadioButton.class, "allTlvsMode").click();
                dialog.findChild(QPushButton.class, "createDocument").click();
            });
            action(window, "fileNew").trigger();
            await(() -> window.completed() && window.currentDocument().reference());
            assertEquals("Docsis-4.0", window.currentDocument().type().header());
            assertNull(window.currentDocument().path());
            var template = window.currentDocument();
            respond(window, QDialog::reject);
            action(window, "addTlv").trigger();
            await(window::completed);
            assertSame(template, window.currentDocument());
        } finally { window.close(); window.dispose(); }
    }

    private void saveAs(MainWindow window, Path destination, Consumer<QDialog> confirmation) {
        respond(window, dialog -> {
            dialog.findChild(QLineEdit.class, "fileNameEdit").setText(destination.toString());
            dialog.accept();
            respond(window, confirmation);
        });
        action(window, "fileSaveAs").trigger();
    }

    private QAction action(MainWindow window, String name) { return window.findChild(QAction.class, name); }

    private void respond(MainWindow window, Consumer<QDialog> response) {
        var timer = new QTimer(window);
        timer.timeout.connect(() -> {
            if (QApplication.activeModalWidget() instanceof QDialog dialog) {
                timer.stop();
                try { response.accept(dialog); }
                catch (Throwable failure) { callbackFailure = failure; dialog.reject(); }
                timer.dispose();
            }
        });
        timer.start(20);
    }

    private void assertCallback() {
        if (callbackFailure != null) fail("Dialog interaction failed", callbackFailure);
    }

    private void await(BooleanSupplier ready) {
        var loop = new QEventLoop();
        var poll = new QTimer(loop);
        var deadline = new QTimer(loop);
        poll.timeout.connect(() -> { if (ready.getAsBoolean() || callbackFailure != null) loop.quit(); });
        deadline.setSingleShot(true);
        deadline.timeout.connect(() -> {
            if (QApplication.activeModalWidget() instanceof QDialog dialog) dialog.reject();
            loop.quit();
        });
        poll.start(20);
        deadline.start(10000);
        loop.exec();
        loop.dispose();
        assertCallback();
        assertTrue(ready.getAsBoolean(), "Qt workflow did not reach the expected state");
    }
}